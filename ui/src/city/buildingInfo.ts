import type { BuildingType, BuildingView, Effect } from '../api/worlds'

/** The building's current effect in words: "240 population", "30 wood per hour". */
export function describeEffect(type: BuildingType, e: Effect): string {
  const n = Math.round(e.value).toLocaleString()
  switch (type) {
    case 'FARM': return `${n} people`
    case 'WOODCUTTER': return `${n} wood per hour`
    case 'STONE_MINE': return `${n} stone per hour`
    case 'IRON_MINE': return `${n} iron per hour`
    case 'DEPOSIT': return `${n} of each resource`
    case 'VAULT': return `${n} of each resource hidden`
    case 'WALL': return `+${n}% defence`
    default: return `${n} ${e.unit}`.trim()
  }
}

/** What the next level adds, in words: "+41 people", "+5 wood per hour". */
export function describeGain(type: BuildingType, view: BuildingView): string | null {
  if (!view.next) return null
  const d = Math.round(view.next.effect.value - view.effect.value)
  if (d === 0) return null
  const n = Math.abs(d).toLocaleString()
  const sign = d > 0 ? '+' : '−'
  switch (type) {
    case 'FARM': return `${sign}${n} people`
    case 'WOODCUTTER': return `${sign}${n} wood per hour`
    case 'STONE_MINE': return `${sign}${n} stone per hour`
    case 'IRON_MINE': return `${sign}${n} iron per hour`
    case 'DEPOSIT': return `${sign}${n} of each resource`
    case 'VAULT': return `${sign}${n} hidden`
    case 'WALL': return `${sign}${n}% defence`
    default: return `${sign}${n} ${view.next.effect.unit}`.trim()
  }
}
