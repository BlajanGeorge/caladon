import { api } from './client'

export interface PlayableWorld {
  id: number
  name: string
  joined: boolean
}

export interface OwnedCity {
  id: number
  x: number
  y: number
  name: string
  points: number
}

export interface JoinResponse {
  worldId: number
  startCity: OwnedCity
}

export interface Tile {
  x: number
  y: number
}

export interface MapCity extends Tile {
  id: number
  name: string
  points: number
  owner: string
}

export interface MapResponse {
  startX: number
  startY: number
  endX: number
  endY: number
  /** Row-major from (startX, startY): 0=GRASS 1=FOREST 2=LAKE 3=MOUNTAIN. */
  terrain: number[]
  slots: Tile[]
  cities: MapCity[]
  barbarians: Tile[]
}

export interface ResourceStock {
  /** Floored stock, settled to `serverTime`. */
  stock: number
  ratePerMinute: number
}

export interface CityResources {
  wood: ResourceStock
  stone: ResourceStock
  iron: ResourceStock
  /** Max stock of each resource. */
  capacity: number
  /** ISO instant the stocks are settled to; the client extrapolates from here. */
  serverTime: string
}

export interface CityDetail extends OwnedCity {
  resources: CityResources
  /** Remaining free population. */
  population: number
}

export const worldsApi = {
  list: () => api<PlayableWorld[]>('/worlds'),
  mine: () => api<{ id: number; name: string }[]>('/worlds/mine'),
  join: (worldId: number) => api<JoinResponse>(`/worlds/${worldId}/join`, { method: 'POST' }),
  myCities: (worldId: number) => api<OwnedCity[]>(`/worlds/${worldId}/cities/mine`),
  cityDetail: (worldId: number, cityId: number) => api<CityDetail>(`/worlds/${worldId}/cities/${cityId}`),
  map: (worldId: number, startX: number, startY: number, endX: number, endY: number) =>
    api<MapResponse>(`/worlds/${worldId}/map?startX=${startX}&startY=${startY}&endX=${endX}&endY=${endY}`),
}
