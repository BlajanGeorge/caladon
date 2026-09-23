import type { BuildingView, CityDetail } from '../api/worlds'
import { affordability, formatDuration, formatRequirements, secondsUntil } from '../city/format'

interface Props {
  detail: CityDetail
  buildings: BuildingView[]
  now: number
  busy: boolean
  onUpgrade: (building: BuildingView['type']) => void
  onCancel: (orderId: number) => void
}

/** Every building with its level and effect, the next level's price, and the build queue. */
export function BuildingsPanel({ detail, buildings, now, busy, onUpgrade, onCancel }: Props) {
  const stocks = { wood: detail.resources.wood.stock, stone: detail.resources.stone.stock, iron: detail.resources.iron.stock }
  const queueFull = detail.buildQueue.length >= detail.buildQueueSlots

  return (
    <section className="card panel">
      <header className="panel-head">
        <h2>Buildings</h2>
        <span className="muted">Queue {detail.buildQueue.length} / {detail.buildQueueSlots}</span>
      </header>

      {detail.buildQueue.length > 0 && (
        <ul className="queue">
          {detail.buildQueue.map((o, i) => (
            <li key={o.id}>
              <span className="q-name">{o.name} → {o.targetLevel}</span>
              <span className="q-time">{i === 0 ? formatDuration(secondsUntil(o.completesAt, now)) : `after ${formatDuration(secondsUntil(o.completesAt, now))}`}</span>
              {i === detail.buildQueue.length - 1 && <button className="link" disabled={busy} onClick={() => onCancel(o.id)}>Cancel</button>}
            </li>
          ))}
        </ul>
      )}

      <table className="grid">
        <thead>
          <tr><th>Building</th><th>Level</th><th>Effect</th><th>Next level</th><th></th></tr>
        </thead>
        <tbody>
          {buildings.map((b) => {
            const n = b.next
            let reason = ''
            if (!n) reason = 'Max level'
            else if (n.blockedBy.length > 0) reason = `Needs ${formatRequirements(n.blockedBy)}`
            else if (queueFull) reason = 'Queue full'
            else {
              const a = affordability(stocks, detail.population, n.cost, n.popCost)
              if (!a.ok) reason = a.reason
            }
            return (
              <tr key={b.type} className={b.level === 0 ? 'unbuilt' : ''}>
                <td className="b-name">{b.name}{b.queued > 0 && <span className="muted"> (+{b.queued} queued)</span>}</td>
                <td>{b.level}<span className="muted"> / {b.maxLevel}</span></td>
                <td>{b.level > 0 ? `${b.effect.value.toLocaleString()} ${b.effect.unit}` : <span className="muted">not built</span>}</td>
                <td className="b-next">
                  {n ? (
                    <>
                      <span className="cost">{n.cost.wood.toLocaleString()} <i>wood</i> · {n.cost.stone.toLocaleString()} <i>stone</i> · {n.cost.iron.toLocaleString()} <i>iron</i> · {n.popCost} <i>pop</i></span>
                      <span className="muted"> → {n.effect.value.toLocaleString()} {n.effect.unit}, {formatDuration(n.buildTimeSeconds)}, +{n.points - b.points} pts</span>
                    </>
                  ) : <span className="muted">—</span>}
                </td>
                <td>
                  <button className="primary small" disabled={busy || reason !== ''} title={reason} onClick={() => n && onUpgrade(b.type)}>
                    {b.level === 0 && b.queued === 0 ? 'Build' : 'Upgrade'}
                  </button>
                  {reason && <div className="reason">{reason}</div>}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </section>
  )
}
