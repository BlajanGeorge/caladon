import { useState } from 'react'
import type { CityDetail, UnitView } from '../api/worlds'
import { affordability, formatDuration, formatRequirements, maxAffordable, secondsUntil } from '../city/format'

interface Props {
  detail: CityDetail
  units: UnitView[]
  now: number
  busy: boolean
  onRecruit: (unit: UnitView['type'], count: number) => void
  onStudy: (unit: UnitView['type']) => void
  onCancel: (orderId: number) => void
}

/** Units at home, the recruitment form per type, study status, and the recruitment queue. */
export function ArmyPanel({ detail, units, now, busy, onRecruit, onStudy, onCancel }: Props) {
  const [counts, setCounts] = useState<Record<string, string>>({})
  const stocks = { wood: detail.resources.wood.stock, stone: detail.resources.stone.stock, iron: detail.resources.iron.stock }

  return (
    <section className="card panel">
      <header className="panel-head">
        <h2>Army</h2>
        <span className="muted">Barracks {detail.buildings.find((b) => b.type === 'BARRACKS')?.level ?? 0} · Academy {detail.buildings.find((b) => b.type === 'ACADEMY')?.level ?? 0}</span>
      </header>

      {detail.recruitQueue.length > 0 && (
        <ul className="queue">
          {detail.recruitQueue.map((o, i) => (
            <li key={o.id}>
              <span className="q-name">{o.remaining} × {o.name}</span>
              <span className="q-time">
                {i === 0 && o.nextCompletesAt ? `next in ${formatDuration(secondsUntil(o.nextCompletesAt, now))}, ` : ''}
                all in {formatDuration(secondsUntil(o.completesAt, now))}
              </span>
              <button className="link" disabled={busy} onClick={() => onCancel(o.id)}>Cancel</button>
            </li>
          ))}
        </ul>
      )}

      <table className="grid">
        <thead>
          <tr><th>Unit</th><th>Have</th><th>Stats</th><th>Cost each</th><th>Recruit</th></tr>
        </thead>
        <tbody>
          {units.map((u) => {
            const count = Number(counts[u.type] ?? '1') || 0
            const canRecruit = u.recruitable
            let reason = ''
            if (u.blockedBy.length > 0) reason = `Needs ${formatRequirements(u.blockedBy)}`
            else if (!u.studied) reason = u.studyCompletesAt ? `Studying, ${formatDuration(secondsUntil(u.studyCompletesAt, now))}` : 'Not studied'
            else if (count < 1) reason = 'Count'
            else {
              const a = affordability(stocks, detail.population, u.cost, u.population, count)
              if (!a.ok) reason = a.reason
            }
            const studyReason = u.studied ? '' : u.studyCompletesAt ? '' : u.studyBlockedBy.length > 0
              ? `Needs ${formatRequirements(u.studyBlockedBy)}`
              : u.studyCost ? affordability(stocks, detail.population, u.studyCost, 0).reason : ''
            const max = maxAffordable(stocks, detail.population, u.cost, u.population)
            return (
              <tr key={u.type} className={canRecruit ? '' : 'unbuilt'}>
                <td className="b-name">
                  {u.name}
                  <div className="muted small-text">{u.role}</div>
                  <div className="muted small-text">Barracks {u.barracksLevel}{u.academyLevel !== null ? `, study at Academy ${u.academyLevel}` : ''}</div>
                </td>
                <td>{u.count}</td>
                <td className="small-text">atk {u.attack} · def {u.defence}/{u.defenceCavalry}/{u.defenceArcher} · {u.speed} min/field · carry {u.carry}</td>
                <td className="small-text">
                  {u.cost.wood.toLocaleString()} <i>wood</i> · {u.cost.stone.toLocaleString()} <i>stone</i> · {u.cost.iron.toLocaleString()} <i>iron</i> · {u.population} <i>pop</i>
                  <div className="muted">{formatDuration(u.recruitSeconds)} each</div>
                </td>
                <td>
                  {u.studied ? (
                    <div className="recruit-form">
                      <input
                        type="number" min={1} max={10000} value={counts[u.type] ?? '1'}
                        onChange={(e) => setCounts((c) => ({ ...c, [u.type]: e.target.value }))}
                        disabled={busy || !canRecruit}
                      />
                      <button className="link" disabled={busy || !canRecruit || max < 1} onClick={() => setCounts((c) => ({ ...c, [u.type]: String(max) }))} title="Recruit as many as you can afford">max {max}</button>
                      <button className="primary small" disabled={busy || reason !== ''} title={reason} onClick={() => onRecruit(u.type, count)}>Recruit</button>
                    </div>
                  ) : u.studyCompletesAt ? (
                    <span className="muted">Studying, {formatDuration(secondsUntil(u.studyCompletesAt, now))}</span>
                  ) : (
                    <>
                      <button className="secondary small" disabled={busy || studyReason !== ''} title={studyReason} onClick={() => onStudy(u.type)}>
                        Study{u.studyCost ? ` (${u.studyCost.wood.toLocaleString()} / ${u.studyCost.stone.toLocaleString()} / ${u.studyCost.iron.toLocaleString()}${u.studySeconds ? `, ${formatDuration(u.studySeconds)}` : ''})` : ''}
                      </button>
                      {studyReason && <div className="reason">{studyReason}</div>}
                    </>
                  )}
                  {u.studied && reason && reason !== 'Count' && <div className="reason">{reason}</div>}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </section>
  )
}
