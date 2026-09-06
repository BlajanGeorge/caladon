import { useEffect, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { worldsApi, type OwnedCity } from '../api/worlds'
import { TopBar } from '../components/TopBar'
import { useToast } from '../components/Toast'
import { useWorldName } from '../useWorldName'

interface NavState {
  worldName?: string
  city?: OwnedCity
}

/** Placeholder until the city feature (resources/buildings) exists. */
export function CityPage() {
  const { id } = useParams()
  const worldId = Number(id)
  const navigate = useNavigate()
  const toast = useToast()
  const state = (useLocation().state ?? {}) as NavState
  const worldName = useWorldName(worldId, state.worldName)
  const [city, setCity] = useState<OwnedCity | null>(state.city ?? null)

  useEffect(() => {
    if (city) return
    worldsApi.myCities(worldId)
      .then((cities) => {
        if (cities.length === 0) toast.error('You have no city in this world')
        else setCity(cities[0])
      })
      .catch((err) => {
        if (err instanceof ApiError && (err.code === 'NOT_JOINED' || err.code === 'WORLD_NOT_FOUND')) navigate('/lobby', { replace: true })
        else if (!(err instanceof ApiError && err.code === 'SESSION_EXPIRED')) toast.error('Could not load your city')
      })
  }, [city, worldId, navigate, toast])

  return (
    <>
      <TopBar worldName={worldName} links={[{ to: '/lobby', label: 'Back to Lobby' }]} />
      <main className="page">
        <div className="card city-card">
          <h1>{city ? city.name : 'Your city'}</h1>
          {city ? (
            <>
              <dl>
                <dt>Coordinates</dt><dd>{city.x}, {city.y}</dd>
                <dt>Points</dt><dd>{city.points}</dd>
              </dl>
              <button
                className="primary"
                onClick={() => navigate(`/worlds/${worldId}/map`, { state: { worldName, city } })}
              >
                Map
              </button>
              <p className="muted" style={{ marginTop: 20 }}>Resources and buildings are coming with the city feature.</p>
            </>
          ) : (
            <p className="muted">Loading…</p>
          )}
        </div>
      </main>
    </>
  )
}
