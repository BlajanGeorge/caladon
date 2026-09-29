import { useCallback, useEffect, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import {
  worldsApi, type BuildingType, type BuildingView, type CityDetail, type CityResources, type Movements, type OwnedCity,
  type MovementKind, type UnitType, type UnitView,
} from '../api/worlds'
import { ArmyPanel } from '../components/ArmyPanel'
import { BuildingsPanel } from '../components/BuildingsPanel'
import { CityScene } from '../components/CityScene'
import { MapTopBar } from '../components/MapTopBar'
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
  const max = Math.max(resources.wood.ratePerHour, resources.stone.ratePerHour, resources.silver.ratePerHour)
  return max > 60 ? 60_000 : 300_000
}

/** Only the city picture is shown for now; flip to bring back the city card and the panels. */
const SHOW_PANELS: boolean = false

/**
 * Refusals the windows already show before you can click: the button is disabled with the reason on
 * hover, so a rejected order only refreshes the city instead of announcing anything.
 */
const EXPECTED = new Set([
  'NOT_ENOUGH_RESOURCES', 'NOT_ENOUGH_POPULATION', 'REQUIREMENTS_NOT_MET', 'QUEUE_FULL', 'MAX_LEVEL',
  'NOT_STUDIED', 'ALREADY_STUDIED', 'ORDER_NOT_FOUND', 'NOT_LAST_IN_QUEUE',
  'SAME_CITY', 'OWN_CITY', 'NOT_ENOUGH_UNITS', 'NO_UNITS', 'SCOUTS_ONLY', 'MOVEMENT_NOT_FOUND', 'ALREADY_ARRIVED',
])

/** City view: the HUD top bar with the resource strip, the city card, the buildings and army panels. */
export function CityPage() {
  const { id } = useParams()
  const worldId = Number(id)
  const navigate = useNavigate()
  const state = (useLocation().state ?? {}) as NavState
  const worldName = useWorldName(worldId, state.worldName)
  const [city, setCity] = useState<OwnedCity | null>(state.city ?? null)
  const [cities, setCities] = useState<OwnedCity[]>([])
  const [detail, setDetail] = useState<CityDetail | null>(null)
  const [buildings, setBuildings] = useState<BuildingView[] | null>(null)
  const [army, setArmy] = useState<UnitView[] | null>(null)
  const [movements, setMovements] = useState<Movements | null>(null)
  const [busy, setBusy] = useState(false)
  const [refreshKey, setRefreshKey] = useState(0)
  const hasQueue = (detail?.buildQueue.length ?? 0) > 0 || (detail?.recruitQueue.length ?? 0) > 0 || (detail?.studyQueue.length ?? 0) > 0
  const now = useNow(hasQueue)

  // The city view says nothing on the side: a city that is gone sends the player back to the lobby, and
  // anything else is simply retried on the next poll.
  const leaveIfGone = useCallback((err: unknown) => {
    if (err instanceof ApiError && (err.code === 'NOT_JOINED' || err.code === 'WORLD_NOT_FOUND' || err.code === 'CITY_NOT_FOUND')) {
      navigate('/lobby', { replace: true })
    }
  }, [navigate])

  // Every city the player holds here: the one being looked at, and the others the panel can switch to.
  useEffect(() => {
    worldsApi.myCities(worldId)
      .then((mine) => {
        if (mine.length === 0) { navigate('/lobby', { replace: true }); return }
        setCities(mine)
        setCity((current) => current ?? mine[0])
      })
      .catch(leaveIfGone)
  }, [worldId, navigate, leaveIfGone])

  /** Switching city: everything on screen belongs to the old one, so it goes before the new one loads. */
  const pickCity = useCallback((next: OwnedCity) => {
    if (next.id === city?.id) return
    setDetail(null); setBuildings(null); setArmy(null); setMovements(null)
    setCity(next)
  }, [city])

  // Detail + panels: fetch on entry, then on a rate-dependent cadence; paused while the tab is hidden.
  useEffect(() => {
    if (!city) return
    let cancelled = false
    let timer: number | null = null
    let running = false

    const schedule = (ms: number) => { timer = window.setTimeout(tick, ms) }
    const tick = () => {
      timer = null
      Promise.all([
        worldsApi.cityDetail(worldId, city.id), worldsApi.buildings(worldId, city.id),
        worldsApi.army(worldId, city.id), worldsApi.movements(worldId, city.id),
      ])
        .then(([d, b, a, m]) => {
          if (cancelled) return
          setDetail(d); setBuildings(b); setArmy(a); setMovements(m)
          if (running) schedule(pollIntervalMs(d.resources))
        })
        .catch((err) => {
          if (cancelled) return
          leaveIfGone(err)
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
  const act = useCallback(async (run: () => Promise<CityDetail>) => {
    if (!city) return
    setBusy(true)
    try {
      const d = await run()
      setDetail(d)
      const [b, a, m] = await Promise.all([
        worldsApi.buildings(worldId, city.id), worldsApi.army(worldId, city.id), worldsApi.movements(worldId, city.id),
      ])
      setBuildings(b); setArmy(a); setMovements(m)
    } catch (err) {
      if (err instanceof ApiError && EXPECTED.has(err.code)) {
        // The view was out of date; pull the city again so the buttons match what the server allows.
        setRefreshKey((k) => k + 1)
      } else {
        leaveIfGone(err)
      }
    } finally {
      setBusy(false)
    }
  }, [city, worldId, leaveIfGone])

  const onUpgrade = (b: BuildingType) => act(() => worldsApi.upgrade(worldId, city!.id, b))
  const onCancelBuild = (orderId: number) => act(() => worldsApi.cancelBuild(worldId, city!.id, orderId))
  const onRecruit = (u: UnitType, count: number) => act(() => worldsApi.recruit(worldId, city!.id, u, count))
  const onStudy = (u: UnitType) => act(() => worldsApi.study(worldId, city!.id, u))
  const onCancelRecruit = (orderId: number) => act(() => worldsApi.cancelRecruit(worldId, city!.id, orderId))
  const onCancelStudy = (u: UnitType) => act(() => worldsApi.cancelStudy(worldId, city!.id, u))
  const onRecall = (movementId: number) => act(() => worldsApi.recall(worldId, city!.id, movementId))
  const onStoreSilver = (amount: number) => act(() => worldsApi.storeSilver(worldId, city!.id, amount))
  const onSpy = (x: number, y: number, silver: number) => act(() => worldsApi.spy(worldId, city!.id, x, y, silver))
  const onSend = (kind: MovementKind, x: number, y: number, units: Partial<Record<UnitType, number>>) =>
    act(() => worldsApi.send(worldId, city!.id, kind, x, y, units))

  const shown = detail ?? city

  return (
    <div className="city-shell">
      <MapTopBar worldName={worldName} worldId={worldId} city={city} showWorldButton />
      <main className="city-body">
        <CityScene buildings={buildings} city={city} cities={cities} onPickCity={pickCity} detail={detail} units={army} busy={busy} onStudy={onStudy} onRecruit={onRecruit} onCancelStudy={onCancelStudy} onCancelRecruit={onCancelRecruit} onUpgrade={onUpgrade} onCancelBuild={onCancelBuild} movements={movements} onRecall={onRecall} onSend={onSend} onStoreSilver={onStoreSilver} onSpy={onSpy} />
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
