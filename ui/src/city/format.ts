import type { Cost, Requirement } from '../api/worlds'

/** "1h05m12s", "12m34s", "45s" — a countdown reads better with its seconds. */
export function formatDuration(seconds: number): string {
  const s = Math.max(0, Math.round(seconds))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  if (h > 0) return `${h}h${String(m).padStart(2, '0')}m${String(sec).padStart(2, '0')}s`
  if (m > 0) return `${m}m${String(sec).padStart(2, '0')}s`
  return `${sec}s`
}

/** Seconds from `nowMs` until the ISO instant, never negative. */
export function secondsUntil(iso: string, nowMs: number): number {
  return Math.max(0, Math.round((Date.parse(iso) - nowMs) / 1000))
}

export const BUILDING_NAMES: Record<string, string> = {
  FARM: 'Farm', WOODCUTTER: 'Woodcutter', STONE_MINE: 'Stone Mine', IRON_MINE: 'Iron Mine', DEPOSIT: 'Deposit',
  TOWN_HALL: 'Town Hall', BARRACKS: 'Barracks', ACADEMY: 'Academy', WALL: 'Wall', VAULT: 'Vault',
}

/** "Town Hall level 3, Farm level 6". */
export function formatRequirements(reqs: Requirement[]): string {
  return reqs.map((r) => `${BUILDING_NAMES[r.building] ?? r.building} level ${r.level}`).join(', ')
}

export interface Affordability {
  ok: boolean
  /** Human reason when not ok. */
  reason: string
}

/** Why an order of `cost` × `count` plus `popCost` cannot be placed, or ok. */
export function affordability(
  stocks: Cost, population: number, cost: Cost, popCost: number, count = 1,
): Affordability {
  const short: string[] = []
  if (stocks.wood < cost.wood * count) short.push('wood')
  if (stocks.stone < cost.stone * count) short.push('stone')
  if (stocks.iron < cost.iron * count) short.push('iron')
  if (short.length > 0) return { ok: false, reason: `Not enough ${short.join(', ')}` }
  if (population < popCost * count) return { ok: false, reason: 'Not enough population' }
  return { ok: true, reason: '' }
}

/** Largest count of a unit the stocks and population can pay for (0 when none). */
export function maxAffordable(stocks: Cost, population: number, cost: Cost, popCost: number): number {
  const by = (have: number, per: number) => (per > 0 ? Math.floor(have / per) : Number.POSITIVE_INFINITY)
  const n = Math.min(by(stocks.wood, cost.wood), by(stocks.stone, cost.stone), by(stocks.iron, cost.iron), by(population, popCost))
  return Number.isFinite(n) ? Math.max(0, n) : 0
}
