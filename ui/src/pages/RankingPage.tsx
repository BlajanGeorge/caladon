import { useCallback, useEffect, useRef, useState } from 'react'
import { useLocation, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { worldsApi, type OwnedCity, type Ranking, type RankingBoard, type Standing } from '../api/worlds'
import { MapTopBar } from '../components/MapTopBar'
import { useWorldName } from '../useWorldName'

interface NavState {
  worldName?: string
  city?: OwnedCity
}

const BOARDS: { board: RankingBoard; label: string; column: keyof Standing }[] = [
  { board: 'points', label: 'Points', column: 'points' },
  { board: 'battle', label: 'Battle', column: 'battlePoints' },
  { board: 'attack', label: 'Attack', column: 'attackPoints' },
  { board: 'defence', label: 'Defence', column: 'defencePoints' },
]

const SIZES = [10, 50, 100]

/**
 * The world's standings. The board scrolls endlessly by the cursor the server hands back, which cannot
 * skip or repeat a player, and the pager below jumps by page number, which can. The caller's own row is
 * pinned above the table, so they never scroll to find themselves.
 */
export function RankingPage() {
  const { id } = useParams()
  const worldId = Number(id)
  const state = (useLocation().state ?? {}) as NavState
  const worldName = useWorldName(worldId, state.worldName)

  const [board, setBoard] = useState<RankingBoard>('points')
  const [limit, setLimit] = useState(100)
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(1)
  const [data, setData] = useState<Ranking | null>(null)
  const [rows, setRows] = useState<Standing[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const cursor = useRef<string | null>(null)
  const end = useRef(false)

  // A new board, page size, page or search starts the table again.
  const load = useCallback(async (append: boolean) => {
    setLoading(true)
    try {
      const next = await worldsApi.ranking(worldId, {
        board, limit, search,
        after: append ? cursor.current : null,
        page: append ? undefined : page,
      })
      setData(next)
      setRows((old) => (append ? [...old, ...next.rows] : next.rows))
      cursor.current = next.next
      end.current = next.next === null
      setError(null)
    } catch (err) {
      setError(err instanceof ApiError && err.code === 'NOT_JOINED' ? 'You have not joined this world' : 'Could not load the ranking')
    } finally {
      setLoading(false)
    }
  }, [worldId, board, limit, page, search])

  useEffect(() => { cursor.current = null; end.current = false; void load(false) }, [load])

  // Endless scroll: the last row coming into view asks for the next cursor page.
  const tail = useCallback((node: HTMLTableRowElement | null) => {
    if (!node) return
    const io = new IntersectionObserver((entries) => {
      if (entries[0].isIntersecting && !end.current && !loading) void load(true)
    }, { rootMargin: '200px' })
    io.observe(node)
    return () => io.disconnect()
  }, [load, loading])

  const pages = data ? Math.max(1, Math.ceil(data.total / data.limit)) : 1
  const sorted = BOARDS.find((b) => b.board === board)!

  const row = (s: Standing, mine: boolean, ref?: (n: HTMLTableRowElement | null) => void) => (
    <tr key={s.playerId} ref={ref} className={mine ? 'rk-me' : undefined}>
      <td className="rk-rank">{s.rank.toLocaleString()}</td>
      <td className="rk-player">{s.player}</td>
      <td>{s.cities.toLocaleString()}</td>
      {BOARDS.map((b) => (
        <td key={b.board} className={b.board === board ? 'rk-sorted' : undefined}>
          {(s[b.column] as number).toLocaleString()}
        </td>
      ))}
    </tr>
  )

  return (
    <div className="city-shell">
      <MapTopBar showCityButton worldName={worldName} worldId={worldId} city={state.city} />
      <main className="rk-body">
        <div className="rk-panel">
          <div className="rk-head">
            <h1>Ranking</h1>
            <div className="rk-boards" role="group" aria-label="What to rank by">
              {BOARDS.map((b) => (
                <button
                  key={b.board}
                  type="button"
                  className={'send-kind' + (b.board === board ? ' on' : '')}
                  onClick={() => { setBoard(b.board); setPage(1) }}
                >
                  {b.label}
                </button>
              ))}
            </div>
            <input
              className="recruit-count rk-search"
              placeholder="Find a player"
              value={search}
              onChange={(e) => { setSearch(e.target.value); setPage(1) }}
              aria-label="Find a player"
            />
          </div>

          {error ? (
            <p className="rk-note">{error}</p>
          ) : (
            <>
              <table className="rk-table">
                <thead>
                  <tr>
                    <th className="rk-rank">#</th>
                    <th className="rk-player">Player</th>
                    <th>Cities</th>
                    {BOARDS.map((b) => (
                      <th key={b.board} className={b.board === board ? 'rk-sorted' : undefined}>{b.label}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {data?.me && !rows.some((r) => r.playerId === data.me!.playerId) && row(data.me, true)}
                  {rows.map((s, i) => row(s, s.playerId === data?.me?.playerId, i === rows.length - 1 ? tail : undefined))}
                </tbody>
              </table>

              {rows.length === 0 && !loading && <p className="rk-note">Nobody matches that.</p>}
              {loading && <p className="rk-note">Loading…</p>}

              <div className="rk-foot">
                <span className="rk-total">
                  {data ? `${data.total.toLocaleString()} players, by ${sorted.label.toLowerCase()}` : ''}
                </span>
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
                  <button type="button" className="cave-max" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>
                    Back
                  </button>
                  <span className="rk-page">{page} of {pages.toLocaleString()}</span>
                  <button type="button" className="cave-max" disabled={page >= pages} onClick={() => setPage((p) => p + 1)}>
                    Next
                  </button>
                </div>
              </div>
            </>
          )}
        </div>
      </main>
    </div>
  )
}
