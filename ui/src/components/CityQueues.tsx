import { useState, type CSSProperties } from 'react'
import type { CityDetail, Movement, Movements, UnitType, UnitView } from '../api/worlds'
import { UNIT_ICONS } from '../city/unitIcons'
import { BUILDING_ICONS } from '../city/buildingIcons'
import { formatDuration, secondsUntil } from '../city/format'
import { useNow } from '../city/useNow'
import woodUrl from '@assets/sprites/hud-wood.png'
import stoneUrl from '@assets/sprites/hud-stone.png'
import silverUrl from '@assets/sprites/hud-silver.png'
import popUrl from '@assets/sprites/hud-population.png'

/** What the confirmation asks about, and what the city gets back if it is agreed to. */
type Confirm = {
  title: string
  text: string
  back: { wood: number; stone: number; silver: number; pop: number } | null
  keep: string
  go: string
} & (
  | { kind: 'build'; id: number }
  | { kind: 'recruit'; id: number }
  | { kind: 'study'; unit: UnitType }
  | { kind: 'recall'; id: number }
)

/** How each kind of movement is marked in the bar, and what it is called. */
const KIND: Record<Movement['kind'], { mark: string; out: string; back: string }> = {
  ATTACK: { mark: '⚔', out: 'Attacking', back: 'Returning from the attack' },
  SUPPORT: { mark: '✚', out: 'Going to support', back: 'Support coming home' },
  SCOUT: { mark: '◎', out: 'Scouting', back: 'Scouts coming home' },
}

interface Props {
  detail: CityDetail | null
  /** Every unit type, for what a cancelled study or training gives back. */
  units: UnitView[] | null
  /** Troops on the road, ours and anyone else's heading here. */
  movements: Movements | null
  /** Where the bar sits: the scene keeps it clear of the side panel. */
  style?: CSSProperties
  busy: boolean
  onCancelBuild: (orderId: number) => void
  onCancelRecruit: (orderId: number) => void
  onCancelStudy: (unit: UnitType) => void
  /** Turns an outward movement around. */
  onRecall: (movementId: number) => void
  /** Opens the window that owns a section's queue. */
  onOpenBuildings: () => void
  onOpenRecruit: () => void
  onOpenStudies: () => void
  /** Opens the window that sends troops out. */
  onOpenSend: () => void
}

/**
 * The bar along the bottom of the city: what the city is working on, in three sections that mirror the
 * three queues. The first entry of each is running and counts down; the rest wait their turn. A section's
 * header opens the window that owns it, where orders are placed and cancelled.
 *
 * Movements have two sections of their own: Marches, everything of ours on the road in either direction,
 * and Arrivals, everything heading here from somewhere else.
 */
export function CityQueues({
  detail, units, movements, style, busy, onOpenBuildings, onOpenRecruit, onOpenStudies,
  onCancelBuild, onCancelRecruit, onCancelStudy, onRecall, onOpenSend,
}: Props) {
  const builds = detail?.buildQueue ?? []
  const troops = detail?.recruitQueue ?? []
  const studies = detail?.studyQueue ?? []
  const outgoing = movements?.outgoing ?? []
  const incoming = movements?.incoming ?? []
  const working = builds.length + troops.length + studies.length + outgoing.length + incoming.length > 0
  const now = useNow(working)
  // Only the tail of a queue can go, the rule the server enforces, and never without being asked first.
  const [confirming, setConfirming] = useState<Confirm | null>(null)
  const byType = new Map((units ?? []).map((u) => [u.type, u]))
  const half = (n: number) => Math.floor(n / 2)

  return (
    <div className="city-queues" style={style} aria-label="What the city is working on">
      <section className="cq-section">
        <button type="button" className="cq-head" onClick={onOpenBuildings} title="Open the Town Hall">
          Construction{builds.length > 0 ? <em>{builds.length}</em> : null}
          <span className="cq-go" aria-hidden="true"><i>Town Hall</i>›</span>
        </button>
        {builds.length === 0 ? (
          <p className="cq-empty">Nothing being built</p>
        ) : (
          <ul className="cq-list">
            {builds.map((o, i) => (
              <li key={o.id} className={i === 0 ? 'running' : undefined}>
                {BUILDING_ICONS[o.building] ? <img src={BUILDING_ICONS[o.building]} alt="" /> : null}
                <span className="cq-name" title={`${o.name} level ${o.targetLevel}`}>{o.name} <em>level {o.targetLevel}</em></span>
                <b>{formatDuration(secondsUntil(o.completesAt, now))}</b>
                {i === builds.length - 1 ? (
                  <button
                    type="button" className="cq-cancel" disabled={busy}
                    title="Cancel this build" aria-label={`Cancel ${o.name} level ${o.targetLevel}`}
                    onClick={() => setConfirming({
                      kind: 'build', id: o.id,
                      title: 'Cancel this build?',
                      text: `${o.name} level ${o.targetLevel} is dropped from the queue. Half of what it cost comes back, and all of its population.`,
                      back: { wood: half(o.cost.wood), stone: half(o.cost.stone), silver: half(o.cost.silver), pop: o.popCost },
                      keep: 'Keep building', go: 'Cancel the build',
                    })}
                  >×</button>
                ) : <span className="cq-cancel placeholder" aria-hidden="true" />}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="cq-section">
        <button type="button" className="cq-head" onClick={onOpenRecruit} title="Open the Barracks">
          Training{troops.length > 0 ? <em>{troops.length}</em> : null}
          <span className="cq-go" aria-hidden="true"><i>Barracks</i>›</span>
        </button>
        {troops.length === 0 ? (
          <p className="cq-empty">No troops in training</p>
        ) : (
          <ul className="cq-list">
            {troops.map((o, i) => (
              <li key={o.id} className={i === 0 ? 'running' : undefined}>
                <img src={UNIT_ICONS[o.unit]} alt="" />
                <span className="cq-name" title={`${o.remaining.toLocaleString()} × ${o.name}`}>{o.remaining.toLocaleString()} × {o.name}</span>
                <b>{formatDuration(secondsUntil(o.completesAt, now))}</b>
                {i === troops.length - 1 && byType.has(o.unit) ? (() => {
                  const u = byType.get(o.unit)!
                  return (
                    <button
                      type="button" className="cq-cancel" disabled={busy}
                      title="Cancel this training" aria-label={`Cancel the ${o.name} order`}
                      onClick={() => setConfirming({
                        kind: 'recruit', id: o.id,
                        title: 'Cancel this training?',
                        text: `${o.remaining} of ${o.count} ${u.name} are not trained yet. Half their resources come back, and all of their people. Any already trained stay in the city.`,
                        back: {
                          wood: half(u.cost.wood * o.remaining), stone: half(u.cost.stone * o.remaining),
                          silver: half(u.cost.silver * o.remaining), pop: u.population * o.remaining,
                        },
                        keep: 'Keep training', go: 'Cancel the order',
                      })}
                    >×</button>
                  )
                })() : <span className="cq-cancel placeholder" aria-hidden="true" />}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="cq-section">
        <button type="button" className="cq-head" onClick={onOpenStudies} title="Open the Academy">
          Studies{studies.length > 0 ? <em>{studies.length}</em> : null}
          <span className="cq-go" aria-hidden="true"><i>Academy</i>›</span>
        </button>
        {studies.length === 0 ? (
          <p className="cq-empty">Nothing being studied</p>
        ) : (
          <ul className="cq-list">
            {studies.map((o, i) => (
              <li key={o.unit} className={i === 0 ? 'running' : undefined}>
                <img src={UNIT_ICONS[o.unit]} alt="" />
                <span className="cq-name" title={o.name}>{o.name}</span>
                <b>{formatDuration(secondsUntil(o.completesAt, now))}</b>
                {i === studies.length - 1 ? (
                  <button
                    type="button" className="cq-cancel" disabled={busy}
                    title="Cancel this study" aria-label={`Cancel the ${o.name} study`}
                    onClick={() => {
                      const c = byType.get(o.unit)?.studyCost
                      setConfirming({
                        kind: 'study', unit: o.unit,
                        title: 'Cancel this study?',
                        text: `The ${o.name} study stops and half of what it cost comes back.`,
                        back: c ? { wood: half(c.wood), stone: half(c.stone), silver: half(c.silver), pop: 0 } : null,
                        keep: 'Keep studying', go: 'Cancel the study',
                      })
                    }}
                  >×</button>
                ) : <span className="cq-cancel placeholder" aria-hidden="true" />}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="cq-section">
        <button type="button" className="cq-head" onClick={onOpenSend} title="Send troops somewhere">
          Marches{outgoing.length > 0 ? <em>{outgoing.length}</em> : null}
          <span className="cq-go" aria-hidden="true"><i>Send troops</i>›</span>
        </button>
        {outgoing.length === 0 ? (
          <p className="cq-empty">Nobody is marching</p>
        ) : (
          <ul className="cq-list">
            {outgoing.map((m) => (
              <li key={m.id} className={m.direction === 'HOMEWARD' ? 'back' : 'running'}>
                <span className={`cq-mark ${m.kind.toLowerCase()}`} aria-hidden="true">{KIND[m.kind].mark}</span>
                <span className="cq-name" title={`${m.direction === 'OUTWARD' ? KIND[m.kind].out : KIND[m.kind].back}: ${m.otherCityName} (${m.x}|${m.y})`}>
                  {m.direction === 'OUTWARD' ? '→' : '←'} {m.otherCityName}
                </span>
                <b>{formatDuration(secondsUntil(m.arrivesAt, now))}</b>
                {m.canRecall ? (
                  <button
                    type="button" className="cq-cancel" disabled={busy}
                    title="Turn them around" aria-label={`Recall the troops sent to ${m.otherCityName}`}
                    onClick={() => setConfirming({
                      kind: 'recall', id: m.id,
                      title: 'Turn them around?',
                      text: `The troops on their way to ${m.otherCityName} turn back now. They take as long to come home as they have been flying.`,
                      back: null, keep: 'Let them go on', go: 'Turn them around',
                    })}
                  >×</button>
                ) : <span className="cq-cancel placeholder" aria-hidden="true" />}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="cq-section">
        <div className="cq-head as-label">
          Arrivals{incoming.length > 0 ? <em className={incoming.some((m) => m.kind === 'ATTACK') ? 'alarm' : undefined}>{incoming.length}</em> : null}
        </div>
        {incoming.length === 0 ? (
          <p className="cq-empty">Nothing is arriving</p>
        ) : (
          <ul className="cq-list">
            {incoming.map((m) => (
              <li key={m.id} className={m.kind === 'ATTACK' ? 'alarm' : undefined}>
                <span className={`cq-mark ${m.kind.toLowerCase()}`} aria-hidden="true">{KIND[m.kind].mark}</span>
                <span className="cq-name" title={`${KIND[m.kind].out} from ${m.otherCityName} (${m.x}|${m.y})`}>
                  {m.otherCityName}
                </span>
                <b>{formatDuration(secondsUntil(m.arrivesAt, now))}</b>
                <span className="cq-cancel placeholder" aria-hidden="true" />
              </li>
            ))}
          </ul>
        )}
      </section>

      {confirming && (
        <div className="confirm floating">
          <div className="confirm-box" role="alertdialog" aria-label={confirming.title}>
            <h4>{confirming.title}</h4>
            <p>{confirming.text}</p>
            {confirming.back && (
              <span className="study-cost">
                <span><img src={woodUrl} alt="Wood" />{confirming.back.wood.toLocaleString()}</span>
                <span><img src={stoneUrl} alt="Stone" />{confirming.back.stone.toLocaleString()}</span>
                <span><img src={silverUrl} alt="Silver" />{confirming.back.silver.toLocaleString()}</span>
                <span><img src={popUrl} alt="People" />{confirming.back.pop.toLocaleString()}</span>
              </span>
            )}
            <div className="confirm-buttons">
              <button type="button" className="confirm-no" onClick={() => setConfirming(null)}>{confirming.keep}</button>
              <button
                type="button"
                className="confirm-yes"
                disabled={busy}
                onClick={() => {
                  const c = confirming
                  setConfirming(null)
                  if (c.kind === 'build') onCancelBuild(c.id)
                  else if (c.kind === 'recruit') onCancelRecruit(c.id)
                  else if (c.kind === 'recall') onRecall(c.id)
                  else onCancelStudy(c.unit)
                }}
              >
                {confirming.go}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
