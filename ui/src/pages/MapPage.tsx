import { useEffect, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { worldsApi, type OwnedCity } from '../api/worlds'
import { MapTopBar } from '../components/MapTopBar'
import { useToast } from '../components/Toast'
import { MapView } from '../map/MapView'
import { useWorldName } from '../useWorldName'

interface NavState {
  worldName?: string
  city?: OwnedCity
}

export function MapPage() {
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
        if (cities.length === 0) navigate(`/worlds/${worldId}/city`, { replace: true })
        else setCity(cities[0])
      })
      .catch((err) => {
        if (err instanceof ApiError && (err.code === 'NOT_JOINED' || err.code === 'WORLD_NOT_FOUND')) navigate('/lobby', { replace: true })
        else if (!(err instanceof ApiError && err.code === 'SESSION_EXPIRED')) toast.error('Could not load your city')
      })
  }, [city, worldId, navigate, toast])

  return (
    <div className="map-shell">
      <MapTopBar worldName={worldName} worldId={worldId} city={city} />
      {city ? <MapView worldId={worldId} home={city} /> : <div className="map-body"><div className="map-loading">Loading…</div></div>}
    </div>
  )
}
