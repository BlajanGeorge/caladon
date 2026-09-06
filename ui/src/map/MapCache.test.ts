import { describe, expect, it } from 'vitest'
import type { MapResponse } from '../api/worlds'
import { MapCache } from './MapCache'

function res(startX: number, startY: number, endX: number, endY: number, fill: number, extra: Partial<MapResponse> = {}): MapResponse {
  const w = endX - startX + 1
  const h = endY - startY + 1
  return { startX, startY, endX, endY, terrain: Array(w * h).fill(fill), slots: [], cities: [], barbarians: [], ...extra }
}

describe('MapCache', () => {
  it('stores a window and answers terrain / entity lookups', () => {
    const cache = new MapCache()
    cache.setWindow(res(10, 10, 19, 19, 1, {
      slots: [{ x: 12, y: 12 }],
      cities: [{ id: 1, x: 15, y: 15, name: 'Thal', points: 3, owner: 'g' }],
      barbarians: [{ x: 18, y: 11 }],
    }))
    expect(cache.terrainAt(10, 10)).toBe(1)
    expect(cache.terrainAt(9, 10)).toBeUndefined()
    expect(cache.entityAt(12, 12)).toEqual({ kind: 'slot', tile: { x: 12, y: 12 } })
    expect(cache.entityAt(15, 15)?.kind).toBe('city')
    expect(cache.entityAt(18, 11)?.kind).toBe('barbarian')
    expect(cache.entityAt(11, 11)).toBeUndefined()
  })

  it('mergeArea replaces entities inside the area only and updates terrain', () => {
    const cache = new MapCache()
    cache.setWindow(res(0, 0, 9, 9, 0, { slots: [{ x: 1, y: 1 }, { x: 8, y: 8 }] }))
    cache.mergeArea(res(0, 0, 4, 4, 2, { cities: [{ id: 7, x: 1, y: 1, name: 'New', points: 0, owner: 'a' }] }))

    expect(cache.entityAt(1, 1)?.kind).toBe('city') // the slot got occupied
    expect(cache.entityAt(8, 8)?.kind).toBe('slot') // outside the area: untouched
    expect(cache.terrainAt(2, 2)).toBe(2)
    expect(cache.terrainAt(7, 7)).toBe(0)
  })
})
