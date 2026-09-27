import { useState, type CSSProperties } from 'react'
import type { CityDetail, UnitType, UnitView } from '../api/worlds'
import { UNIT_ICONS } from '../city/unitIcons'
import { BUILDING_ICONS } from '../city/buildingIcons'
import { formatDuration, secondsUntil } from '../city/format'
import { useNow } from '../city/useNow'
import woodUrl from '@assets/sprites/hud-wood.png'
import stoneUrl from '@assets/sprites/hud-stone.png'
import ironUrl from '@assets/sprites/hud-iron.png'
import popUrl from '@assets/sprites/hud-population.png'

/** What the confirmation asks about, and what the city gets back if it is agreed to. */
type Confirm = {
  title: string
  text: string
  back: { wood: number; stone: number; iron: number; pop: number } | null
  keep: string
  go: string
} & ({ kind: 'build'; id: number } | { kind: 'recruit'; id: number } | { kind: 'study'; unit: UnitType })

interface Props {
  detail: CityDetail | null
  /** Every unit type, for what a cancelled study or training gives back. */
  units: UnitView[] | null
  /** Where the bar sits: the scene keeps it clear of the side panel. */
  style?: CSSProperties
  busy: boolean
  onCancelBuild: (orderId: number) => void
  onCancelRecruit: (orderId: number) => void
  onCancelStudy: (unit: UnitType) => void
  /** Opens the window that owns a section's queue. */
  onOpenBuildings: () => void
  onOpenRecruit: () => void
  onOpenStudies: () => void
}

/**
 * The bar along the bottom of the city: what the city is working on, in three sections that mirror the
 * three queues. The first entry of each is running and counts down; the rest wait their turn. A section's
 * header opens the window that owns it, where orders are placed and cancelled.
 *
 * Movements will join it later — attacks coming in and going out, support arriving and leaving, scouts on
 * their way, and troops on their way home — each as another section of the same bar.
 */
export function CityQueues({
  detail, units, style, busy, onOpenBuildings, onOpenRecruit, onOpenStudies,
  onCancelBuild, onCancelRecruit, onCancelStudy,
}: Props) {
  const builds = detail?.buildQueue ?? []
  const troops = detail?.recruitQueue ?? []
  const studies = detail?.studyQueue ?? []
  const working = builds.length + troops.length + studies.length > 0
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
                <img src={BUILDING_ICONS[o.building]} alt="" />
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
                      back: { wood: half(o.cost.wood), stone: half(o.cost.stone), iron: half(o.cost.iron), pop: o.popCost },
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
                          iron: half(u.cost.iron * o.remaining), pop: u.population * o.remaining,
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
                        back: c ? { wood: half(c.wood), stone: half(c.stone), iron: half(c.iron), pop: 0 } : null,
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

      {confirming && (
        <div className="confirm floating">
          <div className="confirm-box" role="alertdialog" aria-label={confirming.title}>
            <h4>{confirming.title}</h4>
            <p>{confirming.text}</p>
            {confirming.back && (
              <span className="study-cost">
                <span><img src={woodUrl} alt="Wood" />{confirming.back.wood.toLocaleString()}</span>
                <span><img src={stoneUrl} alt="Stone" />{confirming.back.stone.toLocaleString()}</span>
                <span><img src={ironUrl} alt="Iron" />{confirming.back.iron.toLocaleString()}</span>
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
