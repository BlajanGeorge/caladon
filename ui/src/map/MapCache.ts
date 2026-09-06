import type { MapCity, MapResponse, Tile } from '../api/worlds'
import { contains, MAP_SIZE, type Rect } from './camera'

export type Entity =
  | { kind: 'city'; city: MapCity }
  | { kind: 'slot'; tile: Tile }
  | { kind: 'barbarian'; tile: Tile }

const key = (x: number, y: number) => y * MAP_SIZE + x

/**
 * Client-side cache of one `/map` window: terrain as a flat array with bounds, entities keyed by
 * tile. `setWindow` replaces everything; `mergeArea` refreshes a sub-rectangle in place.
 */
export class MapCache {
  bounds: Rect | null = null
  private terrain = new Uint8Array(0)
  private width = 0
  readonly slots = new Map<number, Tile>()
  readonly cities = new Map<number, MapCity>()
  readonly barbarians = new Map<number, Tile>()

  setWindow(res: MapResponse) {
    this.bounds = { startX: res.startX, startY: res.startY, endX: res.endX, endY: res.endY }
    this.width = res.endX - res.startX + 1
    this.terrain = Uint8Array.from(res.terrain)
    this.slots.clear()
    this.cities.clear()
    this.barbarians.clear()
    this.addEntities(res)
  }

  mergeArea(res: MapResponse) {
    const area: Rect = { startX: res.startX, startY: res.startY, endX: res.endX, endY: res.endY }
    if (!this.bounds) {
      this.setWindow(res)
      return
    }
    const w = res.endX - res.startX + 1
    for (let y = res.startY; y <= res.endY; y++) {
      for (let x = res.startX; x <= res.endX; x++) {
        if (contains(this.bounds, x, y)) this.terrain[this.index(x, y)] = res.terrain[(y - res.startY) * w + (x - res.startX)]
      }
    }
    for (const map of [this.slots, this.cities, this.barbarians]) {
      for (const [k, t] of map) if (contains(area, t.x, t.y)) map.delete(k)
    }
    this.addEntities(res)
  }

  private addEntities(res: MapResponse) {
    for (const s of res.slots) this.slots.set(key(s.x, s.y), s)
    for (const c of res.cities) this.cities.set(key(c.x, c.y), c)
    for (const b of res.barbarians) this.barbarians.set(key(b.x, b.y), b)
  }

  private index(x: number, y: number): number {
    return (y - this.bounds!.startY) * this.width + (x - this.bounds!.startX)
  }

  /** Terrain code, or undefined when the tile is outside the cached window. */
  terrainAt(x: number, y: number): number | undefined {
    if (!this.bounds || !contains(this.bounds, x, y)) return undefined
    return this.terrain[this.index(x, y)]
  }

  entityAt(x: number, y: number): Entity | undefined {
    const k = key(x, y)
    const city = this.cities.get(k)
    if (city) return { kind: 'city', city }
    const slot = this.slots.get(k)
    if (slot) return { kind: 'slot', tile: slot }
    const barbarian = this.barbarians.get(k)
    if (barbarian) return { kind: 'barbarian', tile: barbarian }
    return undefined
  }
}
