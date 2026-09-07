import { useCallback, useEffect, useRef, useState, type PointerEvent as ReactPointerEvent } from 'react'
import { Link } from 'react-router-dom'
import { ApiError } from '../api/client'
import { worldsApi, type MapResponse, type OwnedCity } from '../api/worlds'
import { useToast } from '../components/Toast'
import { loadSprites } from './assets'
import recenterUrl from '@assets/sprites/ctl-recenter.png'
import cityUrl from '@assets/sprites/ctl-city.png'
import goUrl from '@assets/sprites/ctl-go.png'
import { clampCamera, needsRefetch, parseCoordinate, TILE, VISIBLE_PAD, visibleRect, windowFor, type Rect } from './camera'
import { MapCache, type Entity } from './MapCache'
import { MapRenderer, type Camera } from './MapRenderer'

interface Props {
  worldId: number
  /** The player's city: initial camera position and Home target. */
  home: OwnedCity
}

const POLL_MS = 15_000
const PAN_END_MS = 400

export function MapView({ worldId, home }: Props) {
  const toast = useToast()
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const camera = useRef<Camera>({ x: home.x, y: home.y })
  const cache = useRef(new MapCache())
  const renderer = useRef<MapRenderer | null>(null)
  const fetching = useRef<Promise<void> | null>(null)
  const panEndTimer = useRef<number | null>(null)
  const drag = useRef<{ startX: number; startY: number; camX: number; camY: number; moved: boolean } | null>(null)

  const [ready, setReady] = useState(false)
  const [dragging, setDragging] = useState(false)
  const [hover, setHover] = useState<{ px: number; py: number; text: string } | null>(null)
  const [selected, setSelected] = useState<Entity | null>(null)
  const [goto, setGoto] = useState('')
  const [center, setCenter] = useState({ x: home.x, y: home.y })

  const redraw = useCallback(() => renderer.current?.markDirty(), [])

  const reportError = useCallback((err: unknown) => {
    if (err instanceof ApiError && err.code === 'SESSION_EXPIRED') return
    toast.error('Could not load the map')
  }, [toast])

  /** Fetch the padded window around the camera and replace the cache. Never blocks rendering. */
  const fetchWindow = useCallback((): Promise<void> => {
    if (fetching.current) return fetching.current
    const r = windowFor(camera.current.x, camera.current.y)
    fetching.current = worldsApi.map(worldId, r.startX, r.startY, r.endX, r.endY)
      .then((res: MapResponse) => { cache.current.setWindow(res); redraw() })
      .catch(reportError)
      .finally(() => { fetching.current = null })
    return fetching.current
  }, [worldId, redraw, reportError])

  /** Refresh entities in a rectangle (visible area) without dropping the rest of the cache. */
  const refreshArea = useCallback((r: Rect) => {
    worldsApi.map(worldId, r.startX, r.startY, r.endX, r.endY)
      .then((res) => { cache.current.mergeArea(res); redraw() })
      .catch(reportError)
  }, [worldId, redraw, reportError])

  const visibleArea = useCallback((): Rect => {
    const c = canvasRef.current!
    return visibleRect(camera.current.x, camera.current.y, c.clientWidth, c.clientHeight, VISIBLE_PAD)
  }, [])

  const moveCamera = useCallback((x: number, y: number) => {
    const c = canvasRef.current!
    const clamped = clampCamera(x, y, c.clientWidth, c.clientHeight)
    camera.current.x = clamped.x
    camera.current.y = clamped.y
    setCenter({ x: Math.round(camera.current.x), y: Math.round(camera.current.y) })
    redraw()
    const bounds = cache.current.bounds
    if (!bounds || needsRefetch(bounds, camera.current.x, camera.current.y)) void fetchWindow()
    if (panEndTimer.current !== null) window.clearTimeout(panEndTimer.current)
    panEndTimer.current = window.setTimeout(() => refreshArea(visibleArea()), PAN_END_MS)
  }, [redraw, fetchWindow, refreshArea, visibleArea])

  // Boot: sprites, renderer, first window, resize handling, polling.
  useEffect(() => {
    const canvas = canvasRef.current!
    let disposed = false
    loadSprites(canvas.getContext('2d')!).then((sprites) => {
      if (disposed) return
      const clamped = clampCamera(camera.current.x, camera.current.y, canvas.clientWidth, canvas.clientHeight)
      camera.current.x = clamped.x
      camera.current.y = clamped.y
      renderer.current = new MapRenderer(canvas, sprites, cache.current, camera.current)
      setReady(true)
      void fetchWindow()
    }).catch(() => toast.error('Could not load map graphics'))

    const observer = new ResizeObserver(() => {
      renderer.current?.resize()
      const clamped = clampCamera(camera.current.x, camera.current.y, canvas.clientWidth, canvas.clientHeight)
      camera.current.x = clamped.x
      camera.current.y = clamped.y
      renderer.current?.markDirty()
    })
    observer.observe(canvas)
    const poll = window.setInterval(() => { if (cache.current.bounds) void fetchWindow() }, POLL_MS)

    return () => {
      disposed = true
      observer.disconnect()
      window.clearInterval(poll)
      if (panEndTimer.current !== null) window.clearTimeout(panEndTimer.current)
      renderer.current?.dispose()
      renderer.current = null
    }
  }, [fetchWindow, toast])

  // ---- interaction ----

  function onPointerDown(e: ReactPointerEvent<HTMLCanvasElement>) {
    if (e.button !== 0) return
    e.currentTarget.setPointerCapture(e.pointerId)
    drag.current = { startX: e.clientX, startY: e.clientY, camX: camera.current.x, camY: camera.current.y, moved: false }
    setHover(null)
  }

  function onPointerMove(e: ReactPointerEvent<HTMLCanvasElement>) {
    const d = drag.current
    if (d) {
      const dx = e.clientX - d.startX
      const dy = e.clientY - d.startY
      if (!d.moved && Math.hypot(dx, dy) > 4) { d.moved = true; setDragging(true) }
      if (d.moved) moveCamera(d.camX - dx / TILE, d.camY - dy / TILE)
      return
    }
    const entity = entityUnder(e)
    if (entity) {
      const rect = e.currentTarget.getBoundingClientRect()
      setHover({ px: e.clientX - rect.left, py: e.clientY - rect.top, text: describe(entity) })
    } else setHover(null)
  }

  function onPointerUp(e: ReactPointerEvent<HTMLCanvasElement>) {
    const d = drag.current
    drag.current = null
    setDragging(false)
    if (d && !d.moved) {
      const entity = entityUnder(e)
      setSelected(entity ?? null)
    }
  }

  function entityUnder(e: ReactPointerEvent<HTMLCanvasElement>): Entity | undefined {
    if (!renderer.current) return undefined
    const rect = e.currentTarget.getBoundingClientRect()
    const t = renderer.current.screenToTile(e.clientX - rect.left, e.clientY - rect.top)
    return cache.current.entityAt(t.x, t.y)
  }

  function goHome() {
    moveCamera(home.x, home.y)
  }

  function goToCoordinate() {
    const c = parseCoordinate(goto)
    if (!c) { toast.error('Enter a coordinate as x,y (0–499)'); return }
    moveCamera(c.x, c.y)
  }

  return (
    <div className="map-body">
      <canvas
        ref={canvasRef}
        className={dragging ? 'dragging' : ''}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerLeave={() => setHover(null)}
      />
      {!ready && <div className="map-loading">Loading map…</div>}
      <div className="map-controls">
        <button className="map-ctl-btn" onClick={goHome} title="Recentre on your city" aria-label="Recentre on your city">
          <img src={recenterUrl} alt="" />
        </button>
        <input
          placeholder="x, y"
          value={goto}
          onChange={(e) => setGoto(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') goToCoordinate() }}
          aria-label="Go to coordinate"
        />
        <button className="map-ctl-btn" onClick={goToCoordinate} title="Go to coordinate" aria-label="Go to coordinate">
          <img src={goUrl} alt="" />
        </button>
        <Link className="map-ctl-btn" to={`/worlds/${worldId}/city`} title="Back to your city" aria-label="Back to your city" style={{ textDecoration: 'none' }}>
          <img src={cityUrl} alt="" />
        </Link>
      </div>
      <div className="map-status">centre {center.x}, {center.y}</div>
      {hover && !dragging && <div className="map-tooltip" style={{ left: hover.px, top: hover.py }}>{hover.text}</div>}
      {selected && <InfoPanel entity={selected} onClose={() => setSelected(null)} />}
    </div>
  )
}

function describe(entity: Entity): string {
  switch (entity.kind) {
    case 'city': return `${entity.city.name} (${entity.city.x}, ${entity.city.y})`
    case 'slot': return `Free slot (${entity.tile.x}, ${entity.tile.y})`
    case 'barbarian': return `Barbarian village (${entity.tile.x}, ${entity.tile.y})`
  }
}

/** Info only — no actions yet. */
function InfoPanel({ entity, onClose }: { entity: Entity; onClose: () => void }) {
  return (
    <aside className="map-panel">
      <button className="close" onClick={onClose} aria-label="Close" />
      {entity.kind === 'city' && (
        <>
          <h3>{entity.city.name}</h3>
          <dl>
            <dt>Owner</dt><dd>{entity.city.owner}</dd>
            <dt>Points</dt><dd>{entity.city.points}</dd>
            <dt>Coordinates</dt><dd>{entity.city.x}, {entity.city.y}</dd>
          </dl>
        </>
      )}
      {entity.kind === 'slot' && (
        <>
          <h3>Free city slot</h3>
          <dl><dt>Coordinates</dt><dd>{entity.tile.x}, {entity.tile.y}</dd></dl>
        </>
      )}
      {entity.kind === 'barbarian' && (
        <>
          <h3>Barbarian village</h3>
          <dl><dt>Coordinates</dt><dd>{entity.tile.x}, {entity.tile.y}</dd></dl>
        </>
      )}
    </aside>
  )
}
