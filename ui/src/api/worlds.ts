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
  /** Whole units, settled to `serverTime`. */
  stock: number
  ratePerHour: number
}

export interface CityResources {
  wood: ResourceStock
  stone: ResourceStock
  iron: ResourceStock
  /** Max stock of each resource. */
  capacity: number
  /** ISO instant the stocks are settled to. */
  serverTime: string
}

export type BuildingType =
  | 'FARM' | 'WOODCUTTER' | 'STONE_MINE' | 'IRON_MINE' | 'DEPOSIT' | 'TOWN_HALL'
  | 'BARRACKS' | 'ACADEMY' | 'WALL' | 'VAULT'

export type UnitType =
  | 'SPEARMAN' | 'SWORDSMAN' | 'SCOUT' | 'AXEMAN' | 'ARCHER' | 'LIGHT_CAV' | 'RAM' | 'HEAVY_CAV' | 'CATAPULT' | 'NOBLEMAN'

export interface Cost {
  wood: number
  stone: number
  iron: number
}

export interface Effect {
  value: number
  unit: string
}

export interface Requirement {
  building: BuildingType
  level: number
}

export interface CityBuilding {
  type: BuildingType
  name: string
  level: number
  points: number
}

export interface BuildOrder {
  id: number
  building: BuildingType
  name: string
  targetLevel: number
  startedAt: string
  completesAt: string
}

export interface CityUnit {
  type: UnitType
  name: string
  count: number
}

export interface RecruitOrder {
  id: number
  unit: UnitType
  name: string
  count: number
  remaining: number
  nextCompletesAt: string | null
  completesAt: string
}

export interface Study {
  unit: UnitType
  completesAt: string
  studied: boolean
}

export interface CityDetail extends OwnedCity {
  resources: CityResources
  /** Remaining free population. */
  population: number
  buildings: CityBuilding[]
  buildQueue: BuildOrder[]
  buildQueueSlots: number
  units: CityUnit[]
  recruitQueue: RecruitOrder[]
  studies: Study[]
}

export interface NextLevel {
  level: number
  cost: Cost
  popCost: number
  points: number
  effect: Effect
  buildTimeSeconds: number
  blockedBy: Requirement[]
}

export interface BuildingView {
  type: BuildingType
  name: string
  level: number
  maxLevel: number
  founded: boolean
  points: number
  effect: Effect
  queued: number
  next: NextLevel | null
}

export interface UnitView {
  type: UnitType
  name: string
  role: string
  count: number
  cost: Cost
  population: number
  attack: number
  defence: number
  defenceCavalry: number
  defenceArcher: number
  speed: number
  carry: number
  barracksLevel: number
  academyLevel: number | null
  recruitSeconds: number
  studied: boolean
  studyCompletesAt: string | null
  studyCost: Cost | null
  studySeconds: number | null
  studyBlockedBy: Requirement[]
  blockedBy: Requirement[]
  recruitable: boolean
}

export const worldsApi = {
  list: () => api<PlayableWorld[]>('/worlds'),
  mine: () => api<{ id: number; name: string }[]>('/worlds/mine'),
  join: (worldId: number) => api<JoinResponse>(`/worlds/${worldId}/join`, { method: 'POST' }),
  myCities: (worldId: number) => api<OwnedCity[]>(`/worlds/${worldId}/cities/mine`),
  cityDetail: (worldId: number, cityId: number) => api<CityDetail>(`/worlds/${worldId}/cities/${cityId}`),
  buildings: (worldId: number, cityId: number) => api<BuildingView[]>(`/worlds/${worldId}/cities/${cityId}/buildings`),
  upgrade: (worldId: number, cityId: number, building: BuildingType) =>
    api<CityDetail>(`/worlds/${worldId}/cities/${cityId}/buildings/${building}/upgrade`, { method: 'POST' }),
  cancelBuild: (worldId: number, cityId: number, orderId: number) =>
    api<CityDetail>(`/worlds/${worldId}/cities/${cityId}/build-orders/${orderId}`, { method: 'DELETE' }),
  army: (worldId: number, cityId: number) => api<UnitView[]>(`/worlds/${worldId}/cities/${cityId}/army`),
  recruit: (worldId: number, cityId: number, unit: UnitType, count: number) =>
    api<CityDetail>(`/worlds/${worldId}/cities/${cityId}/army/recruit`, { method: 'POST', body: { unit, count } }),
  study: (worldId: number, cityId: number, unit: UnitType) =>
    api<CityDetail>(`/worlds/${worldId}/cities/${cityId}/army/study`, { method: 'POST', body: { unit } }),
  cancelRecruit: (worldId: number, cityId: number, orderId: number) =>
    api<CityDetail>(`/worlds/${worldId}/cities/${cityId}/recruit-orders/${orderId}`, { method: 'DELETE' }),
  map: (worldId: number, startX: number, startY: number, endX: number, endY: number) =>
    api<MapResponse>(`/worlds/${worldId}/map?startX=${startX}&startY=${startY}&endX=${endX}&endY=${endY}`),
}
