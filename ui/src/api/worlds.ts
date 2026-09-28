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

/** A barbarian village: a tile that can be raided, hardening as it is. */
export interface BarbarianTile extends Tile {
  id?: number
  /** 1, 2 or 3: how many militia stand there. Absent on an older server. */
  level?: number
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
  barbarians: BarbarianTile[]
}

export interface ResourceStock {
  /** Whole units, settled to `serverTime`. */
  stock: number
  ratePerHour: number
}

export interface CityResources {
  wood: ResourceStock
  stone: ResourceStock
  silver: ResourceStock
  /** Max stock of each resource. */
  capacity: number
  /** ISO instant the stocks are settled to. */
  serverTime: string
}

export type BuildingType =
  | 'FARM' | 'WOODCUTTER' | 'STONE_MINE' | 'SILVER_MINE' | 'DEPOSIT' | 'TOWN_HALL'
  | 'BARRACKS' | 'ACADEMY' | 'WALL' | 'VAULT' | 'CAVE'

export type UnitType =
  | 'SPEARMAN' | 'SWORDSMAN' | 'SCOUT' | 'AXEMAN' | 'ARCHER' | 'LIGHT_CAV' | 'RAM' | 'HEAVY_CAV' | 'CATAPULT' | 'NOBLEMAN'

export interface Cost {
  wood: number
  stone: number
  silver: number
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
  /** What the level cost, which is also what cancelling gives back in full. */
  cost: Cost
  popCost: number
}

export interface CityUnit {
  type: UnitType
  name: string
  /** Kept for the panels that only care about the garrison; same as `home`. */
  count: number
  /** The city's own troops standing here. */
  home: number
  /** Other cities' troops sheltering here. */
  supporting: number
  /** This city's troops standing in someone else's city. */
  sentAway: number
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

export interface StudyOrder {
  unit: UnitType
  name: string
  position: number
  orderedAt: string
  completesAt: string
}

export interface CityDetail extends OwnedCity {
  resources: CityResources
  /** Remaining free population. */
  population: number
  /** The Cave's own silver and what it can hold; both 0 until a Cave is built. */
  cave: { silver: number; capacity: number }
  buildings: CityBuilding[]
  buildQueue: BuildOrder[]
  buildQueueSlots: number
  units: CityUnit[]
  recruitQueue: RecruitOrder[]
  /** Recruit-queue slots at the current Barracks level. */
  recruitQueueSlots: number
  /** Unit types studied (completed) in this city. */
  studied: UnitType[]
  /** The Academy's study queue, first entry in progress. */
  studyQueue: StudyOrder[]
  /** Study-queue slots at the current Academy level. */
  studyQueueSlots: number
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
  /** One line on what the building is for, from the server. */
  description: string
  /** What its effect is called, e.g. "Population", from the server. */
  effectLabel: string
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


export type MovementKind = 'ATTACK' | 'SUPPORT' | 'ESPIONAGE'
export type MovementDirection = 'OUTWARD' | 'HOMEWARD'

export interface MovementUnit {
  type: UnitType
  name: string
  count: number
}

/** One body of troops on the road, on its way out or on its way home. */
export interface Movement {
  id: number
  kind: MovementKind
  direction: MovementDirection
  /** The city at the other end: the target on the way out, the origin on the way home. */
  otherCityName: string
  /** Whose city that is. */
  otherPlayerName: string
  x: number
  y: number
  departsAt: string
  arrivesAt: string
  /** Empty for an attack or a scouting run coming at us: we learn nothing until it lands. */
  units: MovementUnit[]
  /** What a homeward leg is carrying; null when there is nothing or we may not see it. */
  carrying: Cost | null
  /** True only for our own movements still on the way out. */
  canRecall: boolean
}

export interface Movements {
  serverTime: string
  /** This city's own movements, out and back. */
  outgoing: Movement[]
  /** Movements heading here from somewhere else. */
  incoming: Movement[]
}

export type RankingBoard = 'points' | 'battle' | 'attack' | 'defence'

export interface Standing {
  rank: number
  playerId: number
  player: string
  cities: number
  points: number
  attackPoints: number
  defencePoints: number
  battlePoints: number
}

export interface Ranking {
  board: RankingBoard
  total: number
  limit: number
  /** Where an endless scroll continues; null at the end of the board. */
  next: string | null
  /** The caller's own standing, wherever it falls. */
  me: Standing | null
  rows: Standing[]
}

export type ReportKind = 'BATTLE' | 'ESPIONAGE' | 'ESPIONAGE_CAUGHT'
/** What the list may be narrowed to: a group, since spying covers a run and one caught. */
export type ReportFilter = 'BATTLE' | 'SPYING'

export interface ReportUnit {
  type: UnitType
  name: string
  sent: number
  lost: number
  left: number
}

export interface ReportSide {
  player: string
  city: string
  units: ReportUnit[]
  /** What this side earned: the population it killed. Absent on reports written before it was kept. */
  points?: number
}

/** A battle as one side saw it; `defender`, `plunder` and `wall` are null to a beaten attacker. */
export interface BattlePayload {
  role: 'ATTACKER' | 'DEFENDER' | 'SUPPORTER'
  attacker: ReportSide
  defender: ReportSide | null
  plunder: Cost | null
  wall: { before: number; after: number } | null
}

export interface SpyPayload {
  success: boolean
  silver: number
  seen: {
    resources: Cost
    buildings: { type: BuildingType; level: number }[]
    units: { type: UnitType; name: string; count: number }[]
  } | null
}

export interface CaughtPayload {
  player: string
  city: string
  silver: number
}

export interface Report {
  id: number
  kind: ReportKind
  createdAt: string
  read: boolean
  subjectCity: string
  otherCity: string
  otherPlayer: string
  won: boolean
  summary: string
  /** Which side of a battle the reader was on; null for a spy report. */
  role: 'ATTACKER' | 'DEFENDER' | 'SUPPORTER' | null
  payload?: BattlePayload | SpyPayload | CaughtPayload
}

export interface Reports {
  unread: number
  total: number
  limit: number
  page: number
  rows: Report[]
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
  cancelStudy: (worldId: number, cityId: number, unit: UnitType) =>
    api<CityDetail>(`/worlds/${worldId}/cities/${cityId}/study-orders/${unit}`, { method: 'DELETE' }),
  storeSilver: (worldId: number, cityId: number, amount: number) =>
    api<CityDetail>(`/worlds/${worldId}/cities/${cityId}/cave`, { method: 'POST', body: { amount } }),
  movements: (worldId: number, cityId: number) => api<Movements>(`/worlds/${worldId}/cities/${cityId}/movements`),
  send: (worldId: number, cityId: number, kind: MovementKind, targetX: number, targetY: number, units: Partial<Record<UnitType, number>>) =>
    api<CityDetail>(`/worlds/${worldId}/cities/${cityId}/movements`, { method: 'POST', body: { kind, targetX, targetY, units } }),
  recall: (worldId: number, cityId: number, movementId: number) =>
    api<CityDetail>(`/worlds/${worldId}/cities/${cityId}/movements/${movementId}`, { method: 'DELETE' }),
  ranking: (worldId: number, q: { board: RankingBoard; limit: number; after?: string | null; page?: number; search?: string }) => {
    const p = new URLSearchParams({ board: q.board, limit: String(q.limit) })
    if (q.after) p.set('after', q.after)
    else if (q.page && q.page > 1) p.set('page', String(q.page))
    if (q.search?.trim()) p.set('q', q.search.trim())
    return api<Ranking>(`/worlds/${worldId}/ranking?${p}`)
  },
  reports: (worldId: number, q: { limit: number; page: number; kind?: ReportFilter | '' }) => {
    const p = new URLSearchParams({ limit: String(q.limit), page: String(q.page) })
    if (q.kind) p.set('kind', q.kind)
    return api<Reports>(`/worlds/${worldId}/reports?${p}`)
  },
  report: (worldId: number, reportId: number) => api<Report>(`/worlds/${worldId}/reports/${reportId}`),
  deleteReport: (worldId: number, reportId: number) =>
    api<void>(`/worlds/${worldId}/reports/${reportId}`, { method: 'DELETE' }),
  spy: (worldId: number, cityId: number, targetX: number, targetY: number, silver: number) =>
    api<CityDetail>(`/worlds/${worldId}/cities/${cityId}/spy`, { method: 'POST', body: { targetX, targetY, silver } }),
  map: (worldId: number, startX: number, startY: number, endX: number, endY: number) =>
    api<MapResponse>(`/worlds/${worldId}/map?startX=${startX}&startY=${startY}&endX=${endX}&endY=${endY}`),
}
