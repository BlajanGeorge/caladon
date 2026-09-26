import type { CityDetail, UnitType, UnitView } from '../api/worlds'
import { UNIT_ICONS, UNIT_ORDER } from '../city/unitIcons'
import { formatDuration, formatRequirements, secondsUntil } from '../city/format'
import { useNow } from '../city/useNow'
import { useState } from 'react'
import woodUrl from '@assets/sprites/hud-wood.png'
import stoneUrl from '@assets/sprites/hud-stone.png'
import ironUrl from '@assets/sprites/hud-iron.png'

interface Props {
  /** Every unit type, as the army endpoint returns it. */
  units: UnitView[]
  /** The Academy's level, to say what is within reach. */
  academyLevel: number
  /** The city, for what it can afford and what is already queued. */
  detail: CityDetail | null
  busy: boolean
  onStudy: (unit: UnitType) => void
  onCancel: (unit: UnitType) => void
  onClose: () => void
}

type State = 'free' | 'studied' | 'queued' | 'ready' | 'poor' | 'blocked' | 'full'

function stateOf(u: UnitView, detail: CityDetail | null, queued: boolean): State {
  if (u.academyLevel === null) return 'free'
  if (u.studied) return 'studied'
  if (queued || u.studyCompletesAt) return 'queued'
  if (u.studyBlockedBy.length > 0) return 'blocked'
  if (detail && detail.studyQueue.length >= detail.studyQueueSlots) return 'full'
  const c = u.studyCost
  if (c && detail) {
    const r = detail.resources
    if (c.wood > r.wood.stock || c.stone > r.stone.stock || c.iron > r.iron.stock) return 'poor'
  }
  return 'ready'
}

/** The Academy's studies: which unit types are known, which can be started, and what each costs. */
export function StudiesWindow({ units, academyLevel, detail, busy, onStudy, onCancel, onClose }: Props) {
  const byType = new Map(units.map((u) => [u.type, u]))
  const orders = new Map((detail?.studyQueue ?? []).map((o) => [o.unit, o]))
  const queued = new Set(orders.keys())
  const tail = detail?.studyQueue.at(-1)?.unit
  const [confirming, setConfirming] = useState<UnitType | null>(null)
  // The countdown ticks only while something is being studied.
  const now = useNow(queued.size > 0)
  const listed = UNIT_ORDER.filter((t) => byType.has(t))
  return (
    <div className="b-info studies" role="dialog" aria-label="Studies">
      <button type="button" className="b-info-close" onClick={onClose} aria-label="Close">×</button>
      <h3>Studies</h3>
      <p className="b-info-level">Academy level {academyLevel}</p>
      <p className="b-info-desc">
        A unit type must be studied here before the Barracks can recruit it. Each study is bought once for this
        city; the Academy's level decides what may be studied and how quickly.
      </p>
      <ul className="study-list">
        {listed.map((type) => {
          const u = byType.get(type)!
          const state = stateOf(u, detail, queued.has(type))
          const c = u.studyCost
          return (
            <li key={type} className={state}>
              <img className="study-icon" src={UNIT_ICONS[type]} alt="" />
              <span className="study-name">
                {u.name}
                <em>
                  {u.academyLevel === null ? `Barracks level ${u.barracksLevel}` : `Academy level ${u.academyLevel} · Barracks level ${u.barracksLevel}`}
                </em>
              </span>
              {c && (
                <span className="study-cost">
                  <span><img src={woodUrl} alt="Wood" />{c.wood.toLocaleString()}</span>
                  <span><img src={stoneUrl} alt="Stone" />{c.stone.toLocaleString()}</span>
                  <span><img src={ironUrl} alt="Iron" />{c.iron.toLocaleString()}</span>
                </span>
              )}
              <span className="study-action">
                {state === 'free' && <b className="plain">Already available</b>}
                {state === 'studied' && <b className="ok">Studied</b>}
                {state === 'queued' && <b className="plain">Studying</b>}
                {state === 'blocked' && <b className="no">{formatRequirements(u.studyBlockedBy)}</b>}
                {u.studySeconds !== null && (state === 'ready' || state === 'poor' || state === 'full') && (
                  <em>{formatDuration(u.studySeconds)}</em>
                )}
                {(state === 'ready' || state === 'poor' || state === 'full') && (
                  <button
                    type="button"
                    className="study-go"
                    disabled={busy || state !== 'ready'}
                    title={
                      state === 'poor' ? 'Not enough resources'
                        : state === 'full' ? 'The study queue is full'
                        : `Study for ${formatDuration(u.studySeconds ?? 0)}`
                    }
                    onClick={() => onStudy(type)}
                  >
                    ▶
                  </button>
                )}
              </span>
            </li>
          )
        })}
      </ul>
      {orders.size > 0 && (
        <div className="recruit-queue">
          <h4>Being studied</h4>
          <ul>
            {[...orders.values()].map((o, i) => {
              const u = byType.get(o.unit)
              const back = u?.studyCost
                ? {
                    wood: Math.floor(u.studyCost.wood / 2),
                    stone: Math.floor(u.studyCost.stone / 2),
                    iron: Math.floor(u.studyCost.iron / 2),
                  }
                : null
              return (
                <li key={o.unit} className={i === 0 ? 'first' : undefined}>
                  <span className="rq-pos">{i + 1}</span>
                  <img src={UNIT_ICONS[o.unit]} alt="" />
                  <span className="rq-name">{o.name}</span>
                  {back && (
                    <span className="study-cost">
                      <span><img src={woodUrl} alt="Wood" />{back.wood.toLocaleString()}</span>
                      <span><img src={stoneUrl} alt="Stone" />{back.stone.toLocaleString()}</span>
                      <span><img src={ironUrl} alt="Iron" />{back.iron.toLocaleString()}</span>
                    </span>
                  )}
                  <b>{formatDuration(secondsUntil(o.completesAt, now))}</b>
                  {o.unit === tail ? (
                    <button
                      type="button"
                      className="study-go cancel"
                      disabled={busy}
                      title="Cancel this study; half of what it cost comes back"
                      onClick={() => setConfirming(o.unit)}
                    >
                      ×
                    </button>
                  ) : (
                    <span className="study-go placeholder" aria-hidden="true" />
                  )}
                </li>
              )
            })}
          </ul>
        </div>
      )}

      {confirming && (() => {
        const u = byType.get(confirming)!
        const c = u.studyCost
        // Half, rounded down, the same rule the server applies when it refunds.
        const back = c ? { wood: Math.floor(c.wood / 2), stone: Math.floor(c.stone / 2), iron: Math.floor(c.iron / 2) } : null
        return (
          <div className="confirm">
            <div className="confirm-box" role="alertdialog" aria-label="Cancel this study?">
              <h4>Cancel this study?</h4>
              <p>
                The {u.name} study stops and half of what it cost comes back.
              </p>
              {back && (
                <span className="study-cost">
                  <span><img src={woodUrl} alt="Wood" />{back.wood.toLocaleString()}</span>
                  <span><img src={stoneUrl} alt="Stone" />{back.stone.toLocaleString()}</span>
                  <span><img src={ironUrl} alt="Iron" />{back.iron.toLocaleString()}</span>
                </span>
              )}
              <div className="confirm-buttons">
                <button type="button" className="confirm-no" onClick={() => setConfirming(null)}>Keep studying</button>
                <button
                  type="button"
                  className="confirm-yes"
                  disabled={busy}
                  onClick={() => { onCancel(confirming); setConfirming(null) }}
                >
                  Cancel the study
                </button>
              </div>
            </div>
          </div>
        )
      })()}
    </div>
  )
}
