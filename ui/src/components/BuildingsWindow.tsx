import { useState } from 'react'
import type { BuildingType, BuildingView, CityDetail } from '../api/worlds'
import { formatDuration, formatRequirements, secondsUntil } from '../city/format'
import { describeGain } from '../city/buildingInfo'
import { useNow } from '../city/useNow'
import woodUrl from '@assets/sprites/hud-wood.png'
import stoneUrl from '@assets/sprites/hud-stone.png'
import ironUrl from '@assets/sprites/hud-iron.png'
import peopleUrl from '@assets/sprites/hud-population.png'

interface Props {
  /** Every building, as the buildings endpoint returns it. */
  buildings: BuildingView[]
  /** The Town Hall's level: it sets the queue's length and shortens every build. */
  townHallLevel: number
  /** The city, for what it can afford and what is already queued. */
  detail: CityDetail | null
  busy: boolean
  onUpgrade: (building: BuildingType) => void
  onCancel: (orderId: number) => void
  onClose: () => void
}

type State = 'max' | 'blocked' | 'poor' | 'crowded' | 'full' | 'ready'

function stateOf(b: BuildingView, detail: CityDetail | null): State {
  const next = b.next
  if (!next) return 'max'
  if (next.blockedBy.length > 0) return 'blocked'
  if (detail && detail.buildQueue.length >= detail.buildQueueSlots) return 'full'
  if (detail) {
    const r = detail.resources
    if (next.cost.wood > r.wood.stock || next.cost.stone > r.stone.stock || next.cost.iron > r.iron.stock) return 'poor'
    if (next.popCost > detail.population) return 'crowded'
  }
  return 'ready'
}

/**
 * The Town Hall's own window: every building with its level, what the next one costs and how long it
 * takes, and the build queue underneath. Ordering and cancelling work exactly as in the Academy and the
 * Barracks, except a cancelled build is refunded in full.
 */
export function BuildingsWindow({ buildings, townHallLevel, detail, busy, onUpgrade, onCancel, onClose }: Props) {
  const queue = detail?.buildQueue ?? []
  const tail = queue.at(-1)?.id
  const [confirming, setConfirming] = useState<number | null>(null)
  // The countdown ticks only while something is being built.
  const now = useNow(queue.length > 0)

  return (
    <div className="b-info studies builds" role="dialog" aria-label="Buildings">
      <button type="button" className="b-info-close" onClick={onClose} aria-label="Close">×</button>
      <h3>Buildings</h3>
      <p className="b-info-level">Town Hall level {townHallLevel} · {detail?.buildQueueSlots ?? 0} in the queue at a time</p>
      <p className="b-info-desc">
        Every level is paid for when it is ordered and built in turn. The Town Hall's level decides how many
        orders may wait at once and shortens every build in the city.
      </p>
      <ul className="study-list build-list">
        {buildings.map((b) => {
          const state = stateOf(b, detail)
          const next = b.next
          const gain = describeGain(b.type, b)
          return (
            <li key={b.type} className={state}>
              <span className="study-name">
                {b.name}
                <em>
                  Level {b.level} of {b.maxLevel}
                  {b.queued > 0 ? ` · ${b.queued} ordered` : ''}
                </em>
              </span>
              {next ? (
                <span className="study-cost">
                  <span><img src={woodUrl} alt="Wood" />{next.cost.wood.toLocaleString()}</span>
                  <span><img src={stoneUrl} alt="Stone" />{next.cost.stone.toLocaleString()}</span>
                  <span><img src={ironUrl} alt="Iron" />{next.cost.iron.toLocaleString()}</span>
                  <span><img src={peopleUrl} alt="Population" />{next.popCost.toLocaleString()}</span>
                </span>
              ) : (
                <span className="study-cost" aria-hidden="true" />
              )}
              <span className="study-action">
                {state === 'max' && <b className="plain">Highest level</b>}
                {state === 'blocked' && next && <b className="no">{formatRequirements(next.blockedBy)}</b>}
                {next && state !== 'blocked' && <em>{formatDuration(next.buildTimeSeconds)}</em>}
                {next && state !== 'blocked' && (
                  <button
                    type="button"
                    className="study-go"
                    disabled={busy || state !== 'ready'}
                    title={
                      state === 'poor' ? 'Not enough resources'
                        : state === 'crowded' ? 'Not enough population'
                        : state === 'full' ? 'The build queue is full'
                        : `Build level ${next.level}${gain ? `: ${gain}` : ''}`
                    }
                    onClick={() => onUpgrade(b.type)}
                  >
                    ▶
                  </button>
                )}
              </span>
            </li>
          )
        })}
      </ul>

      {queue.length > 0 && (
        <div className="recruit-queue">
          <h4>Being built</h4>
          <ul>
            {queue.map((o, i) => {
              // A cancelled build comes back in full, and the order carries what it was paid for.
              return (
                <li key={o.id} className={i === 0 ? 'first' : undefined}>
                  <span className="rq-pos">{i + 1}</span>
                  <span className="rq-name">{o.name} <em>level {o.targetLevel}</em></span>
                  <span className="study-cost">
                    <span><img src={woodUrl} alt="Wood" />{o.cost.wood.toLocaleString()}</span>
                    <span><img src={stoneUrl} alt="Stone" />{o.cost.stone.toLocaleString()}</span>
                    <span><img src={ironUrl} alt="Iron" />{o.cost.iron.toLocaleString()}</span>
                    <span><img src={peopleUrl} alt="Population" />{o.popCost.toLocaleString()}</span>
                  </span>
                  <b>{formatDuration(secondsUntil(o.completesAt, now))}</b>
                  {o.id === tail ? (
                    <button
                      type="button"
                      className="study-go cancel"
                      disabled={busy}
                      title="Cancel this build; everything it cost comes back"
                      onClick={() => setConfirming(o.id)}
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

      {confirming !== null && (() => {
        const order = queue.find((o) => o.id === confirming)
        if (!order) return null
        return (
          <div className="confirm">
            <div className="confirm-box" role="alertdialog" aria-label="Cancel this build?">
              <h4>Cancel this build?</h4>
              <p>{order.name} level {order.targetLevel} is dropped from the queue and everything it cost comes back.</p>
              <div className="confirm-buttons">
                <button type="button" className="confirm-no" onClick={() => setConfirming(null)}>Keep building</button>
                <button
                  type="button"
                  className="confirm-yes"
                  disabled={busy}
                  onClick={() => { onCancel(confirming); setConfirming(null) }}
                >
                  Cancel the build
                </button>
              </div>
            </div>
          </div>
        )
      })()}
    </div>
  )
}
