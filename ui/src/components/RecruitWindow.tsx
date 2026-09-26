import { useState } from 'react'
import type { CityDetail, UnitType, UnitView } from '../api/worlds'
import { UNIT_ICONS, UNIT_ORDER } from '../city/unitIcons'
import { formatDuration, formatRequirements, maxAffordable, secondsUntil } from '../city/format'
import { useNow } from '../city/useNow'
import woodUrl from '@assets/sprites/hud-wood.png'
import stoneUrl from '@assets/sprites/hud-stone.png'
import ironUrl from '@assets/sprites/hud-iron.png'
import popUrl from '@assets/sprites/hud-population.png'

interface Props {
  units: UnitView[]
  barracksLevel: number
  detail: CityDetail | null
  busy: boolean
  onRecruit: (unit: UnitType, count: number) => void
  onCancel: (orderId: number) => void
  onClose: () => void
}

/** The Barracks: which units can be trained, what each costs, and the queue with its countdown. */
export function RecruitWindow({ units, barracksLevel, detail, busy, onRecruit, onCancel, onClose }: Props) {
  const [counts, setCounts] = useState<Partial<Record<UnitType, string>>>({})
  const byType = new Map(units.map((u) => [u.type, u]))
  const queue = detail?.recruitQueue ?? []
  const slots = detail?.recruitQueueSlots ?? 0
  const full = queue.length >= slots
  const now = useNow(queue.length > 0)
  const [confirming, setConfirming] = useState<number | null>(null)

  return (
    <div className="b-info studies" role="dialog" aria-label="Barracks">
      <button type="button" className="b-info-close" onClick={onClose} aria-label="Close">×</button>
      <h3>Barracks</h3>
      <p className="b-info-level">Barracks level {barracksLevel}</p>
      <p className="b-info-desc">
        Troops are trained here, one at a time, and each order waits its turn. A unit can be trained once its
        type has been studied in the Academy and the Barracks is high enough.
      </p>

      <ul className="study-list">
        {UNIT_ORDER.filter((t) => byType.has(t)).map((type) => {
          const u = byType.get(type)!
          const blocked = !u.recruitable
          const stocks = detail
            ? { wood: detail.resources.wood.stock, stone: detail.resources.stone.stock, iron: detail.resources.iron.stock }
            : { wood: 0, stone: 0, iron: 0 }
          const most = detail ? maxAffordable(stocks, detail.population, u.cost, u.population) : 0
          const typed = counts[type] ?? ''
          const want = Math.max(0, Math.min(Math.floor(Number(typed) || 0), most))
          const canGo = !blocked && !full && want > 0 && !busy
          return (
            <li key={type} className={blocked ? 'blocked' : 'ready'}>
              <img className="study-icon" src={UNIT_ICONS[type]} alt="" />
              <span className="study-name">
                {u.name}
                <em>
                  {u.academyLevel === null
                    ? `Barracks level ${u.barracksLevel}`
                    : `Barracks level ${u.barracksLevel} · Academy level ${u.academyLevel}`}
                </em>
              </span>
              <span className="study-cost">
                <span><img src={woodUrl} alt="Wood" />{u.cost.wood.toLocaleString()}</span>
                <span><img src={stoneUrl} alt="Stone" />{u.cost.stone.toLocaleString()}</span>
                <span><img src={ironUrl} alt="Iron" />{u.cost.iron.toLocaleString()}</span>
                <span><img src={popUrl} alt="People" />{u.population}</span>
              </span>
              <span className="study-action">
                {blocked ? (
                  <b className="no">
                    {u.blockedBy.length > 0 ? formatRequirements(u.blockedBy) : 'Not studied'}
                  </b>
                ) : (
                  <>
                    <em>{formatDuration(u.recruitSeconds)}</em>
                    <input
                      className="recruit-count"
                      inputMode="numeric"
                      placeholder={String(most)}
                      value={typed}
                      onChange={(e) => setCounts((c) => ({ ...c, [type]: e.target.value.replace(/\D/g, '') }))}
                      aria-label={`How many ${u.name}`}
                    />
                    <button
                      type="button"
                      className="study-go"
                      disabled={!canGo}
                      title={full ? 'The recruitment queue is full' : want === 0 ? 'Enter how many' : `Train ${want}`}
                      onClick={() => { onRecruit(type, want); setCounts((c) => ({ ...c, [type]: '' })) }}
                    >
                      ▶
                    </button>
                  </>
                )}
              </span>
              {want > 0 && (
                <span className="recruit-total">
                  {want.toLocaleString()} × {u.name} costs
                  <span className="study-cost">
                    <span><img src={woodUrl} alt="Wood" />{(u.cost.wood * want).toLocaleString()}</span>
                    <span><img src={stoneUrl} alt="Stone" />{(u.cost.stone * want).toLocaleString()}</span>
                    <span><img src={ironUrl} alt="Iron" />{(u.cost.iron * want).toLocaleString()}</span>
                    <span><img src={popUrl} alt="People" />{(u.population * want).toLocaleString()}</span>
                  </span>
                  <em>{formatDuration(u.recruitSeconds * want)}</em>
                </span>
              )}
            </li>
          )
        })}
      </ul>

      {queue.length > 0 && (
        <div className="recruit-queue">
          <h4>In training</h4>
          <ul>
            {queue.map((o, i) => {
              const u = byType.get(o.unit)
              const paid = u ? { wood: u.cost.wood * o.count, stone: u.cost.stone * o.count, iron: u.cost.iron * o.count, pop: u.population * o.count } : null
              return (
                <li key={o.id} className={i === 0 ? 'first' : undefined}>
                  <span className="rq-pos">{i + 1}</span>
                  <img src={UNIT_ICONS[o.unit]} alt="" />
                  <span className="rq-name">{o.name} ×{o.remaining}{o.remaining !== o.count && <em> of {o.count}</em>}</span>
                  {paid && (
                    <span className="study-cost">
                      <span><img src={woodUrl} alt="Wood" />{paid.wood.toLocaleString()}</span>
                      <span><img src={stoneUrl} alt="Stone" />{paid.stone.toLocaleString()}</span>
                      <span><img src={ironUrl} alt="Iron" />{paid.iron.toLocaleString()}</span>
                      <span><img src={popUrl} alt="People" />{paid.pop.toLocaleString()}</span>
                    </span>
                  )}
                  <b>{formatDuration(secondsUntil(o.completesAt, now))}</b>
                  {o.id === queue.at(-1)?.id ? (
                    <button
                      type="button"
                      className="study-go cancel"
                      disabled={busy}
                      title="Cancel what is left of this order"
                      onClick={() => setConfirming(o.id)}
                    >
                      ×
                    </button>
                  ) : (
                    // Keeps the rows aligned: only the last order carries a button.
                    <span className="study-go placeholder" aria-hidden="true" />
                  )}
                </li>
              )
            })}
          </ul>
        </div>
      )}

      {confirming !== null && (() => {
        const o = queue.find((x) => x.id === confirming)
        const u = o ? byType.get(o.unit) : undefined
        if (!o || !u) return null
        // Half the resources of what is untrained, and all of its population — the server's rule.
        const back = {
          wood: Math.floor((u.cost.wood * o.remaining) / 2),
          stone: Math.floor((u.cost.stone * o.remaining) / 2),
          iron: Math.floor((u.cost.iron * o.remaining) / 2),
          pop: u.population * o.remaining,
        }
        return (
          <div className="confirm">
            <div className="confirm-box" role="alertdialog" aria-label="Cancel this training?">
              <h4>Cancel this training?</h4>
              <p>
                {o.remaining} of {o.count} {u.name} are not trained yet. Half their resources come back, and all
                of their people. Any already trained stay in the city.
              </p>
              <span className="study-cost">
                <span><img src={woodUrl} alt="Wood" />{back.wood.toLocaleString()}</span>
                <span><img src={stoneUrl} alt="Stone" />{back.stone.toLocaleString()}</span>
                <span><img src={ironUrl} alt="Iron" />{back.iron.toLocaleString()}</span>
                <span><img src={popUrl} alt="People" />{back.pop.toLocaleString()}</span>
              </span>
              <div className="confirm-buttons">
                <button type="button" className="confirm-no" onClick={() => setConfirming(null)}>Keep training</button>
                <button
                  type="button"
                  className="confirm-yes"
                  disabled={busy}
                  onClick={() => { onCancel(confirming); setConfirming(null) }}
                >
                  Cancel the order
                </button>
              </div>
            </div>
          </div>
        )
      })()}
    </div>
  )
}
