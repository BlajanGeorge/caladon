import { useCallback, useEffect, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { worldsApi, type CityDetail, type OwnedCity } from '../api/worlds'
import { MapTopBar } from '../components/MapTopBar'
import { ResourceStrip } from '../components/ResourceStrip'
import { useToast } from '../components/Toast'
import { useWorldName } from '../useWorldName'

interface NavState {
  worldName?: string
  city?: OwnedCity
}

/** How often the City view re-syncs resources and population with the server. */
const POLL_MS = 60_000

/** City view: the HUD top bar with the resource strip, plus the city card (buildings come later). */
export function CityPage() {
  const { id } = useParams()
  const worldId = Number(id)
  const navigate = useNavigate()
  const toast = useToast()
  const state = (useLocation().state ?? {}) as NavState
  const worldName = useWorldName(worldId, state.worldName)
  const [city, setCity] = useState<OwnedCity | null>(state.city ?? null)
  const [detail, setDetail] = useState<CityDetail | null>(null)

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

  // Resources + population: fetch on entry, then every minute; paused while the tab is hidden.
  useEffect(() => {
    if (!city) return
    let cancelled = false
    let timer: number | null = null

    const load = () => {
      worldsApi.cityDetail(worldId, city.id)
        .then((d) => { if (!cancelled) setDetail(d) })
        .catch((err) => { if (!cancelled) leaveIfGone(err, 'Could not load resources') })
    }
    const start = () => {
      if (timer !== null) return
      load()
      timer = window.setInterval(load, POLL_MS)
    }
    const stop = () => {
      if (timer !== null) window.clearInterval(timer)
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
  }, [city, worldId, leaveIfGone])

  const shown = detail ?? city

  return (
    <div className="city-shell">
      <MapTopBar
        worldName={worldName}
        worldId={worldId}
        city={city}
        strip={<ResourceStrip resources={detail?.resources} population={detail?.population} />}
      />
      <main className="page">
        <div className="card city-card">
          <h1>{shown ? shown.name : 'Your city'}</h1>
          {shown ? (
            <>
              <dl>
                <dt>Coordinates</dt><dd>{shown.x}, {shown.y}</dd>
                <dt>Points</dt><dd>{shown.points}</dd>
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
      </main>
    </div>
  )
}
