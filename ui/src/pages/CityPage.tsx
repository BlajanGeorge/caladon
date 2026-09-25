import { useCallback, useEffect, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import {
  worldsApi, type BuildingType, type BuildingView, type CityDetail, type CityResources, type OwnedCity, type UnitType, type UnitView,
} from '../api/worlds'
import { ArmyPanel } from '../components/ArmyPanel'
import { BuildingsPanel } from '../components/BuildingsPanel'
import { CityScene } from '../components/CityScene'
import { MapTopBar } from '../components/MapTopBar'
import { useToast } from '../components/Toast'
import { useNow } from '../city/useNow'
import { useWorldName } from '../useWorldName'

interface NavState {
  worldName?: string
  city?: OwnedCity
}

/**
 * How often the City view re-syncs with the server: every minute when a stock can change every minute
 * (more than 60/h), otherwise every 5 minutes (at 30/h a number changes only every two minutes).
 */
export function pollIntervalMs(resources?: CityResources): number {
  if (!resources) return 60_000
  const max = Math.max(resources.wood.ratePerHour, resources.stone.ratePerHour, resources.iron.ratePerHour)
  return max > 60 ? 60_000 : 300_000
}

/** Only the city picture is shown for now; flip to bring back the city card and the panels. */
const SHOW_PANELS: boolean = false

const ERRORS: Record<string, string> = {
  NOT_ENOUGH_RESOURCES: 'Not enough resources',
  NOT_ENOUGH_POPULATION: 'Not enough population',
  REQUIREMENTS_NOT_MET: 'Requirements not met',
  QUEUE_FULL: 'The build queue is full',
  MAX_LEVEL: 'Already at max level',
  NOT_STUDIED: 'Study this unit first',
  ALREADY_STUDIED: 'Already studied',
  ORDER_NOT_FOUND: 'That order is gone',
  NOT_LAST_IN_QUEUE: 'Only the last order in a queue can be cancelled',
}

/** City view: the HUD top bar with the resource strip, the city card, the buildings and army panels. */
export function CityPage() {
  const { id } = useParams()
  const worldId = Number(id)
  const navigate = useNavigate()
  const toast = useToast()
  const state = (useLocation().state ?? {}) as NavState
  const worldName = useWorldName(worldId, state.worldName)
  const [city, setCity] = useState<OwnedCity | null>(state.city ?? null)
  const [detail, setDetail] = useState<CityDetail | null>(null)
  const [buildings, setBuildings] = useState<BuildingView[] | null>(null)
  const [army, setArmy] = useState<UnitView[] | null>(null)
  const [busy, setBusy] = useState(false)
  const [refreshKey, setRefreshKey] = useState(0)
  const hasQueue = (detail?.buildQueue.length ?? 0) > 0 || (detail?.recruitQueue.length ?? 0) > 0 || (detail?.studyQueue.length ?? 0) > 0
  const now = useNow(hasQueue)

  const leaveIfGone = useCallback((err: unknown, message: string) => {
    if (err instanceof ApiError && (err.code === 'NOT_JOINED' || err.code === 'WORLD_NOT_FOUND' || err.code === 'CITY_NOT_FOUND')) {
      navigate('/lobby', { replace: true })
    } else if (!(err instanceof ApiError && err.code === 'SESSION_EXPIRED')) {
      toast.error(message)
    }
  }, [navigate, toast])

  useEffect(() => {
    if (city) return
    worldsApi.myCities(worldId)
      .then((cities) => {
        if (cities.length === 0) toast.error('You have no city in this world')
        else setCity(cities[0])
      })
      .catch((err) => leaveIfGone(err, 'Could not load your city'))
  }, [city, worldId, toast, leaveIfGone])

  // Detail + panels: fetch on entry, then on a rate-dependent cadence; paused while the tab is hidden.
  useEffect(() => {
    if (!city) return
    let cancelled = false
    let timer: number | null = null
    let running = false

    const schedule = (ms: number) => { timer = window.setTimeout(tick, ms) }
    const tick = () => {
      timer = null
      Promise.all([worldsApi.cityDetail(worldId, city.id), worldsApi.buildings(worldId, city.id), worldsApi.army(worldId, city.id)])
        .then(([d, b, a]) => {
          if (cancelled) return
          setDetail(d); setBuildings(b); setArmy(a)
          if (running) schedule(pollIntervalMs(d.resources))
        })
        .catch((err) => {
          if (cancelled) return
          leaveIfGone(err, 'Could not load the city')
          if (running) schedule(60_000)
        })
    }
    const start = () => {
      if (running) return
      running = true
      tick()
    }
    const stop = () => {
      running = false
      if (timer !== null) window.clearTimeout(timer)
      timer = null
    }
    const onVisibility = () => (document.hidden ? stop() : start())

    if (!document.hidden) start()
    document.addEventListener('visibilitychange', onVisibility)
    return () => {
      cancelled = true
      stop()
      document.removeEventListener('visibilitychange', onVisibility)
    }
  }, [city, worldId, leaveIfGone, refreshKey])

  /** Runs a mutation, takes its returned detail, then refreshes the panels (their views depend on it). */
  const act = useCallback(async (run: () => Promise<CityDetail>, done?: string) => {
    if (!city) return
    setBusy(true)
    try {
      const d = await run()
      setDetail(d)
      const [b, a] = await Promise.all([worldsApi.buildings(worldId, city.id), worldsApi.army(worldId, city.id)])
      setBuildings(b); setArmy(a)
      if (done) toast.info(done)
    } catch (err) {
      if (err instanceof ApiError && ERRORS[err.code]) {
        const extra = err.details ? ' (' + Object.entries(err.details).map(([k, v]) => `${k.toLowerCase().replace('_', ' ')} ${v}`).join(', ') + ')' : ''
        toast.error(ERRORS[err.code] + extra)
        setRefreshKey((k) => k + 1)
      } else {
        leaveIfGone(err, 'The action failed')
      }
    } finally {
      setBusy(false)
    }
  }, [city, worldId, toast, leaveIfGone])

  const onUpgrade = (b: BuildingType) => act(() => worldsApi.upgrade(worldId, city!.id, b))
  const onCancelBuild = (orderId: number) => act(() => worldsApi.cancelBuild(worldId, city!.id, orderId), 'Order cancelled and refunded')
  const onRecruit = (u: UnitType, count: number) => act(() => worldsApi.recruit(worldId, city!.id, u, count))
  const onStudy = (u: UnitType) => act(() => worldsApi.study(worldId, city!.id, u))
  const onCancelRecruit = (orderId: number) => act(() => worldsApi.cancelRecruit(worldId, city!.id, orderId), 'Order cancelled and refunded')
  const onCancelStudy = (u: UnitType) => act(() => worldsApi.cancelStudy(worldId, city!.id, u), 'Study cancelled and refunded')

  const shown = detail ?? city

  return (
    <div className="city-shell">
      <MapTopBar worldName={worldName} worldId={worldId} city={city} showWorldButton />
      <main className="city-body">
        <CityScene buildings={buildings} city={city} detail={detail} units={army} />
        {/* City card, buildings and army panels are hidden for now: only the picture is shown.
            The data still loads so the information panel and the scene labels work. */}
        {SHOW_PANELS && (
          <>
            <div className="card city-card">
              <h1>{shown ? shown.name : 'Your city'}</h1>
              {shown ? (
                <>
                  <dl>
                    <dt>Coordinates</dt><dd>{shown.x}, {shown.y}</dd>
                    <dt>Points</dt><dd>{shown.points.toLocaleString()}</dd>
                  </dl>
                  <button
                    className="primary"
                    onClick={() => navigate(`/worlds/${worldId}/map`, { state: { worldName, city } })}
                  >
                    Map
                  </button>
                </>
              ) : (
                <p className="muted">Loading…</p>
              )}
            </div>
            {detail && buildings && (
              <BuildingsPanel detail={detail} buildings={buildings} now={now} busy={busy} onUpgrade={onUpgrade} onCancel={onCancelBuild} />
            )}
            {detail && army && (
              <ArmyPanel detail={detail} units={army} now={now} busy={busy} onRecruit={onRecruit} onStudy={onStudy} onCancel={onCancelRecruit} onCancelStudy={onCancelStudy} />
            )}
          </>
        )}
      </main>
    </div>
  )
}
