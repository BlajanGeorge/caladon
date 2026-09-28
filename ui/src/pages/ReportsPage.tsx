import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { useLocation, useParams } from 'react-router-dom'
import {
  worldsApi, type BattlePayload, type CaughtPayload, type OwnedCity, type Report, type ReportFilter,
  type Reports, type SpyPayload,
} from '../api/worlds'
import { MapTopBar } from '../components/MapTopBar'
import { UNIT_ICONS } from '../city/unitIcons'
import { BUILDING_NAMES } from '../city/format'
import { useWorldName } from '../useWorldName'
import attackUrl from '@assets/sprites/hud-move-attack.png'
import wallUrl from '@assets/sprites/icon-wall.png'
import goodsUrl from '@assets/sprites/hud-resources.png'
import spyUrl from '@assets/sprites/hud-move-spy.png'
import woodUrl from '@assets/sprites/hud-wood.png'
import stoneUrl from '@assets/sprites/hud-stone.png'
import silverUrl from '@assets/sprites/hud-silver.png'

interface NavState {
  worldName?: string
  city?: OwnedCity
}

const KINDS: { kind: ReportFilter | ''; label: string }[] = [
  { kind: '', label: 'All' },
  { kind: 'BATTLE', label: 'Battles' },
  // Spying covers a run of our own and one we caught; a player thinks of them as one thing.
  { kind: 'SPYING', label: 'Spying' },
]

/**
 * How a row reads at a glance: whose errand it was, which way it went, and against whom. The arrow is
 * the whole story — out is something we did, in is something done to us.
 */
function headline(r: Report): { mark: string; out: boolean; what: string } {
  if (r.kind === 'BATTLE') {
    const out = r.role === 'ATTACKER'
    return { mark: attackUrl, out, what: out ? 'Attack going out' : 'Attack coming in' }
  }
  if (r.kind === 'ESPIONAGE') return { mark: spyUrl, out: true, what: 'Spying going out' }
  return { mark: spyUrl, out: false, what: 'Spy coming in' }
}

const SIZES = [10, 50, 100]

const when = (iso: string) => new Date(iso).toLocaleString(undefined, { dateStyle: 'short', timeStyle: 'short' })

/** One army's losses, the shape both sides of a battle are shown in; [foot] closes the block. */
function Side({ side, title, foot }: { side: BattlePayload['attacker']; title: string; foot?: ReactNode }) {
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
          {foot}
        </tbody>
      </table>
    </div>
  )
}

/** A closing line of a side's table: an icon and a name in the troops' column, the rest beside it. */
function FootRow({ icon, name, children }: { icon?: string; name: string; children: ReactNode }) {
  return (
    <tr className="rp-foot-row">
      <td className="rp-unit">{icon ? <img src={icon} alt="" /> : <span className="rp-no-icon" />}{name}</td>
      <td colSpan={3}>{children}</td>
    </tr>
  )
}

/** How it ended, said to whoever is reading it rather than about two strangers. */
function verdict(role: BattlePayload['role'], won: boolean): string {
  if (role === 'ATTACKER') return won ? 'Your army carried the field' : 'Your army was destroyed'
  if (role === 'SUPPORTER') return won ? 'The defence you joined held' : 'The defence you joined was broken'
  return won ? 'Your defence held' : 'Your defence was broken'
}

/** What a side earned, when the report was written after battle points were kept. */
function Points({ of }: { of: BattlePayload['attacker'] | null }) {
  if (!of || of.points === undefined) return null
  return (
    <FootRow name="Battle points">
      <span className="rp-points">{of.points.toLocaleString()}</span>
    </FootRow>
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
        {/* One army under the other: they are read in turn, not compared column by column. What each side
            came away with closes its own block — the plunder the attacker took, the Wall the defender
            was left with. */}
        <div className="rp-sides rp-stack">
          <Side
            side={p.attacker}
            title="Attacker"
            foot={
              <>
                {/* A lost attack took nothing, which reads as zeros rather than a word. */}
                <FootRow icon={goodsUrl} name="Stolen">
                  <Goods of={p.plunder ?? { wood: 0, stone: 0, silver: 0 }} />
                </FootRow>
                <Points of={p.attacker} />
              </>
            }
          />
          {p.defender ? (
            <Side
              side={p.defender}
              title="Defender"
              foot={
                <>
                  {p.wall && (
                    <FootRow icon={wallUrl} name="Wall">
                      <span className="rp-wall">{p.wall.before} → {p.wall.after}</span>
                    </FootRow>
                  )}
                  <Points of={p.defender} />
                </>
              }
            />
          ) : (
            <p className="rp-blind">The defending army is not known.</p>
          )}
        </div>
        <p className={'rp-verdict ' + (report.won ? 'won' : 'lost')}>{verdict(p.role, report.won)}</p>
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

  const [kind, setKind] = useState<ReportFilter | ''>('')
  const [limit, setLimit] = useState(50)
  const [page, setPage] = useState(1)
  const [list, setList] = useState<Reports | null>(null)
  const [open, setOpen] = useState<Report | null>(null)
  const [error, setError] = useState<string | null>(null)
  const latest = useRef(0)

  const reload = useCallback(() => {
    const mine = ++latest.current
    worldsApi.reports(worldId, { limit, page, kind })
      .then((next) => { if (mine === latest.current) { setList(next); setError(null) } })
      .catch(() => { if (mine === latest.current) setError('Could not load the reports') })
  }, [worldId, limit, page, kind])

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
                    <img className="rp-mark" src={headline(r).mark} alt="" />
                    <span className="rp-summary">
                      <b className={r.kind === 'BATTLE' ? (r.won ? 'won' : 'lost') : undefined}>
                        {headline(r).what}
                      </b>
                      <em>
                        <i className="rp-way">{headline(r).out ? '→' : '←'}</i>
                        {r.otherCity}{r.otherPlayer ? ` · ${r.otherPlayer}` : ''}
                      </em>
                    </span>
                    <span className="rp-when">{when(r.createdAt)}</span>
                  </button>
                  <button type="button" className="cq-cancel" title="Throw it away" onClick={() => void drop(r)}>×</button>
                </li>
              ))}
            </ul>

            <div className="rp-detail">
              {open ? (
                <>
                  {/* The heading, with when it happened off to the right of it. */}
                  <div className="rp-detail-head">
                    <h3>{open.summary}</h3>
                    <span className="rp-detail-when">{when(open.createdAt)}</span>
                  </div>
                  <Body report={open} />
                </>
              ) : (
                <p className="rk-note">Pick a report.</p>
              )}
            </div>
          </div>

          <div className="rk-foot">
            <span className="rk-total">{list ? `${list.total.toLocaleString()} reports` : ''}</span>
            <div className="rk-sizes" role="group" aria-label="Rows per page">
              {SIZES.map((n) => (
                <button
                  key={n}
                  type="button"
                  className={'cave-max' + (n === limit ? ' on' : '')}
                  onClick={() => { setLimit(n); setPage(1) }}
                >
                  {n}
                </button>
              ))}
            </div>
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
