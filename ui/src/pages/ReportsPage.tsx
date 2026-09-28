import { useCallback, useEffect, useRef, useState } from 'react'
import { useLocation, useParams } from 'react-router-dom'
import {
  worldsApi, type BattlePayload, type CaughtPayload, type OwnedCity, type Report, type ReportKind,
  type Reports, type SpyPayload,
} from '../api/worlds'
import { MapTopBar } from '../components/MapTopBar'
import { UNIT_ICONS } from '../city/unitIcons'
import { BUILDING_NAMES } from '../city/format'
import { useWorldName } from '../useWorldName'
import woodUrl from '@assets/sprites/hud-wood.png'
import stoneUrl from '@assets/sprites/hud-stone.png'
import silverUrl from '@assets/sprites/hud-silver.png'

interface NavState {
  worldName?: string
  city?: OwnedCity
}

const KINDS: { kind: ReportKind | ''; label: string }[] = [
  { kind: '', label: 'All' },
  { kind: 'BATTLE', label: 'Battles' },
  { kind: 'ESPIONAGE', label: 'Spying' },
  { kind: 'ESPIONAGE_CAUGHT', label: 'Caught' },
]

const when = (iso: string) => new Date(iso).toLocaleString(undefined, { dateStyle: 'short', timeStyle: 'short' })

/** One army's losses, the shape both sides of a battle are shown in. */
function Side({ side, title }: { side: BattlePayload['attacker']; title: string }) {
  return (
    <div className="rp-side">
      <h4>{title} <em>{side.player} · {side.city}</em></h4>
      <table className="rp-units">
        <thead>
          <tr><th /><th>Sent</th><th>Lost</th><th>Left</th></tr>
        </thead>
        <tbody>
          {side.units.map((u) => (
            <tr key={u.type} className={u.left === 0 ? 'gone' : undefined}>
              <td className="rp-unit"><img src={UNIT_ICONS[u.type]} alt="" />{u.name}</td>
              <td>{u.sent.toLocaleString()}</td>
              <td className="rp-lost">{u.lost.toLocaleString()}</td>
              <td>{u.left.toLocaleString()}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function Goods({ of }: { of: { wood: number; stone: number; silver: number } }) {
  return (
    <span className="mw-load">
      <img src={woodUrl} alt="Wood" />{of.wood.toLocaleString()}
      <img src={stoneUrl} alt="Stone" />{of.stone.toLocaleString()}
      <img src={silverUrl} alt="Silver" />{of.silver.toLocaleString()}
    </span>
  )
}

/** The body of one report, which differs entirely by kind. */
function Body({ report }: { report: Report }) {
  if (!report.payload) return null

  if (report.kind === 'BATTLE') {
    const p = report.payload as BattlePayload
    return (
      <>
        <div className="rp-sides">
          <Side side={p.attacker} title="Attacker" />
          {p.defender
            ? <Side side={p.defender} title="Defender" />
            : <p className="rp-blind">Nobody came back to say what stood there.</p>}
        </div>
        <dl className="b-info-now rp-facts">
          {p.wall && <><dt>Wall</dt><dd>{p.wall.before} → {p.wall.after}</dd></>}
          <dt>Plunder</dt>
          <dd>{p.plunder ? <Goods of={p.plunder} /> : 'nothing'}</dd>
        </dl>
      </>
    )
  }

  if (report.kind === 'ESPIONAGE') {
    const p = report.payload as SpyPayload
    if (!p.seen) {
      return (
        <p className="rp-blind">
          The attempt failed: {p.silver.toLocaleString()} silver spent, and the city keeps its secrets.
        </p>
      )
    }
    return (
      <>
        <dl className="b-info-now rp-facts">
          <dt>Spent</dt><dd><span className="mw-load"><img src={silverUrl} alt="Silver" />{p.silver.toLocaleString()}</span></dd>
          <dt>Resources</dt><dd><Goods of={p.seen.resources} /></dd>
        </dl>
        <div className="rp-sides">
          <div className="rp-side">
            <h4>Troops</h4>
            <table className="rp-units">
              <tbody>
                {p.seen.units.length === 0 && <tr><td className="rp-unit">None standing there</td></tr>}
                {p.seen.units.map((u) => (
                  <tr key={u.type}>
                    <td className="rp-unit"><img src={UNIT_ICONS[u.type]} alt="" />{u.name}</td>
                    <td>{u.count.toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="rp-side">
            <h4>Buildings</h4>
            <table className="rp-units">
              <tbody>
                {p.seen.buildings.map((b) => (
                  <tr key={b.type}>
                    <td className="rp-unit">{BUILDING_NAMES[b.type] ?? b.type}</td>
                    <td>{b.level}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </>
    )
  }

  const p = report.payload as CaughtPayload
  return (
    <p className="rp-blind">
      {p.player} tried to spy on this city from {p.city} and was caught. The attempt cost them{' '}
      {p.silver.toLocaleString()} silver.
    </p>
  )
}

/** Everything that happened while the player was not looking, newest first. */
export function ReportsPage() {
  const { id } = useParams()
  const worldId = Number(id)
  const state = (useLocation().state ?? {}) as NavState
  const worldName = useWorldName(worldId, state.worldName)

  const [kind, setKind] = useState<ReportKind | ''>('')
  const [page, setPage] = useState(1)
  const [list, setList] = useState<Reports | null>(null)
  const [open, setOpen] = useState<Report | null>(null)
  const [error, setError] = useState<string | null>(null)
  const latest = useRef(0)

  const reload = useCallback(() => {
    const mine = ++latest.current
    worldsApi.reports(worldId, { limit: 50, page, kind })
      .then((next) => { if (mine === latest.current) { setList(next); setError(null) } })
      .catch(() => { if (mine === latest.current) setError('Could not load the reports') })
  }, [worldId, page, kind])

  useEffect(reload, [reload])

  const read = async (row: Report) => {
    const full = await worldsApi.report(worldId, row.id)
    setOpen(full)
    // Reading marks it read on the server, so the list and the unread count follow.
    reload()
  }

  const drop = async (row: Report) => {
    await worldsApi.deleteReport(worldId, row.id)
    if (open?.id === row.id) setOpen(null)
    reload()
  }

  const pages = list ? Math.max(1, Math.ceil(list.total / list.limit)) : 1

  return (
    <div className="city-shell">
      <MapTopBar showCityButton showWorldButton worldName={worldName} worldId={worldId} city={state.city} />
      <main className="rk-body rp-body">
        <div className="rk-panel rp-panel">
          <div className="rk-head">
            <h1>Reports</h1>
            <div className="rk-boards" role="group" aria-label="What to show">
              {KINDS.map((k) => (
                <button
                  key={k.label}
                  type="button"
                  className={'send-kind' + (k.kind === kind ? ' on' : '')}
                  onClick={() => { setKind(k.kind); setPage(1); setOpen(null) }}
                >
                  {k.label}
                </button>
              ))}
            </div>
            <span className="rp-unread">{list ? `${list.unread.toLocaleString()} unread` : ''}</span>
          </div>

          {error && <p className="rk-note">{error}</p>}

          <div className="rp-split">
            <ul className="rp-list">
              {list?.rows.length === 0 && <li className="rp-none">Nothing has happened yet.</li>}
              {list?.rows.map((r) => (
                <li key={r.id} className={(r.read ? '' : 'unread ') + (open?.id === r.id ? 'open' : '')}>
                  <button type="button" className="rp-row" onClick={() => void read(r)}>
                    <span className={'rp-dot ' + (r.kind === 'BATTLE' ? (r.won ? 'won' : 'lost') : r.kind.toLowerCase())} />
                    <span className="rp-summary">{r.summary}</span>
                    <span className="rp-when">{when(r.createdAt)}</span>
                  </button>
                  <button type="button" className="cq-cancel" title="Throw it away" onClick={() => void drop(r)}>×</button>
                </li>
              ))}
            </ul>

            <div className="rp-detail">
              {open ? (
                <>
                  <h3>{open.summary}</h3>
                  <p className="b-info-level">{when(open.createdAt)} · {open.otherPlayer} · {open.otherCity}</p>
                  <Body report={open} />
                </>
              ) : (
                <p className="rk-note">Pick a report.</p>
              )}
            </div>
          </div>

          <div className="rk-foot">
            <span className="rk-total">{list ? `${list.total.toLocaleString()} reports` : ''}</span>
            <div className="rk-pager">
              <button type="button" className="cave-max" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>Back</button>
              <span className="rk-page">{page} of {pages.toLocaleString()}</span>
              <button type="button" className="cave-max" disabled={page >= pages} onClick={() => setPage((p) => p + 1)}>Next</button>
            </div>
          </div>
        </div>
      </main>
    </div>
  )
}
