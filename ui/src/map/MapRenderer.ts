import type { SpriteSet, AssetKey } from './assets'
import { cityTierKey, tileRandom, variantFor } from './assets'
import { MAP_SIZE, TILE, visibleRect } from './camera'
import type { MapCache } from './MapCache'

export interface Camera {
  x: number
  y: number
}

interface Drawable {
  /** Ground anchor in tile units (fractional for scattered objects). */
  x: number
  y: number
  key: AssetKey
  variant: number
  label?: { name: string; points: number }
}

const FOREST = 1
const LAKE = 2
const MOUNTAIN = 3
const WATER = '#3b7aa8'
const SHORE = '#c9b884'
const SHORE_PX = 5
/** Subtle ground tints so patches read as areas even between scattered objects. */
const FOREST_GROUND = 'rgba(40, 90, 40, 0.08)'
const MOUNTAIN_GROUND = 'rgba(120, 118, 110, 0.3)'
/** Trees per FOREST tile and the share of MOUNTAIN tiles that carry a peak. */
const TREES_PER_TILE = 3
const PEAK_SHARE = 0.55

/** Canvas 2D renderer: grass ground, then one depth-sorted pass of terrain objects + entities, then labels. */
export class MapRenderer {
  private readonly ctx: CanvasRenderingContext2D
  private width = 0
  private height = 0
  private dirty = true
  private frame: number | null = null

  constructor(
    private readonly canvas: HTMLCanvasElement,
    private readonly sprites: SpriteSet,
    private readonly cache: MapCache,
    private readonly camera: Camera,
  ) {
    this.ctx = canvas.getContext('2d')!
    this.resize()
  }

  resize() {
    const dpr = window.devicePixelRatio || 1
    const rect = this.canvas.getBoundingClientRect()
    this.width = Math.max(1, Math.round(rect.width))
    this.height = Math.max(1, Math.round(rect.height))
    this.canvas.width = Math.round(this.width * dpr)
    this.canvas.height = Math.round(this.height * dpr)
    this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
    this.markDirty()
  }

  markDirty() {
    this.dirty = true
    if (this.frame === null) this.frame = requestAnimationFrame(() => this.tick())
  }

  dispose() {
    if (this.frame !== null) cancelAnimationFrame(this.frame)
    this.frame = null
  }

  private tick() {
    this.frame = null
    if (!this.dirty) return
    this.dirty = false
    this.render()
  }

  /** Screen position (CSS px) of a tile's top-left corner. Fractional camera => sub-pixel smooth panning. */
  tileToScreen(x: number, y: number): { sx: number; sy: number } {
    return {
      sx: this.width / 2 + (x - this.camera.x - 0.5) * TILE,
      sy: this.height / 2 + (y - this.camera.y - 0.5) * TILE,
    }
  }

  screenToTile(px: number, py: number): { x: number; y: number } {
    return {
      x: Math.floor(this.camera.x + (px - this.width / 2) / TILE + 0.5),
      y: Math.floor(this.camera.y + (py - this.height / 2) / TILE + 0.5),
    }
  }

  render() {
    const { ctx, width, height } = this
    ctx.fillStyle = '#2b3a25'
    ctx.fillRect(0, 0, width, height)

    // Ground: the grass pattern scrolls with the world, clipped to the map's extent.
    const origin = this.tileToScreen(0, 0)
    ctx.save()
    ctx.beginPath()
    ctx.rect(origin.sx, origin.sy, MAP_SIZE * TILE, MAP_SIZE * TILE)
    ctx.clip()
    ctx.translate(origin.sx, origin.sy)
    ctx.fillStyle = this.sprites.grass
    ctx.fillRect(-origin.sx, -origin.sy, width, height)
    ctx.restore()

    // Depth-sorted objects. Extra rows above/below so tall sprites near the edges still show.
    const view = visibleRect(this.camera.x, this.camera.y, width, height, 4)
    const drawables: Drawable[] = []
    const isLake = (x: number, y: number) => { const t = this.cache.terrainAt(x, y); return t === undefined || t === LAKE }
    for (let y = view.startY; y <= view.endY; y++) {
      for (let x = view.startX; x <= view.endX; x++) {
        const code = this.cache.terrainAt(x, y) ?? 0
        if (code === LAKE) {
          // Lakes are contiguous patches: flat water with a sandy shore on the sides facing land.
          // (The registry's `terrain.lake` sprite is unused by this placeholder; a PNG can take over.)
          const { sx, sy } = this.tileToScreen(x, y)
          ctx.fillStyle = WATER
          ctx.fillRect(sx, sy, TILE + 0.5, TILE + 0.5)
          ctx.fillStyle = SHORE
          if (!isLake(x, y - 1)) ctx.fillRect(sx, sy, TILE + 0.5, SHORE_PX)
          if (!isLake(x, y + 1)) ctx.fillRect(sx, sy + TILE - SHORE_PX, TILE + 0.5, SHORE_PX)
          if (!isLake(x - 1, y)) ctx.fillRect(sx, sy, SHORE_PX, TILE + 0.5)
          if (!isLake(x + 1, y)) ctx.fillRect(sx + TILE - SHORE_PX, sy, SHORE_PX, TILE + 0.5)
        } else if (code === FOREST) {
          // A few trees per tile at stable random offsets (spilling a little into neighbours).
          const { sx, sy } = this.tileToScreen(x, y)
          ctx.fillStyle = FOREST_GROUND
          ctx.fillRect(sx, sy, TILE + 0.5, TILE + 0.5)
          for (let i = 0; i < TREES_PER_TILE; i++) {
            drawables.push({
              x: x - 0.2 + tileRandom(x, y, 11 + i * 3) * 1.4,
              y: y - 0.2 + tileRandom(x, y, 12 + i * 3) * 1.4,
              key: 'terrain.forest',
              variant: variantFor(x, y, this.sprites.byKey['terrain.forest'].length, 13 + i * 3),
            })
          }
        } else if (code === MOUNTAIN) {
          // Rocky ground everywhere; a peak of random size on a jittered subset of tiles.
          const { sx, sy } = this.tileToScreen(x, y)
          ctx.fillStyle = MOUNTAIN_GROUND
          ctx.fillRect(sx, sy, TILE + 0.5, TILE + 0.5)
          if (tileRandom(x, y, 21) < PEAK_SHARE) {
            drawables.push({
              x: x + tileRandom(x, y, 22),
              y: y + tileRandom(x, y, 23),
              key: 'terrain.mountain',
              variant: variantFor(x, y, this.sprites.byKey['terrain.mountain'].length, 24),
            })
          }
        }
      }
    }
    const inView = (x: number, y: number) => x >= view.startX && x <= view.endX && y >= view.startY && y <= view.endY
    const entity = (x: number, y: number, key: AssetKey): Drawable =>
      ({ x: x + 0.5, y: y + 1, key, variant: variantFor(x, y, this.sprites.byKey[key].length) })
    for (const s of this.cache.slots.values()) if (inView(s.x, s.y)) drawables.push(entity(s.x, s.y, 'entity.slot'))
    for (const b of this.cache.barbarians.values()) if (inView(b.x, b.y)) drawables.push(entity(b.x, b.y, 'entity.barbarian'))
    for (const c of this.cache.cities.values()) {
      if (inView(c.x, c.y)) drawables.push({ ...entity(c.x, c.y, cityTierKey(c.points)), label: { name: c.name, points: c.points } })
    }
    drawables.sort((a, b) => a.y - b.y || a.x - b.x)

    const labels: { sx: number; sy: number; name: string; points: number }[] = []
    for (const d of drawables) {
      const sprite = this.sprites.byKey[d.key][d.variant]
      const baseX = origin.sx + d.x * TILE
      const baseY = origin.sy + d.y * TILE
      ctx.drawImage(sprite.image, baseX - sprite.anchorX, baseY - sprite.anchorY, sprite.width, sprite.height)
      if (d.label) labels.push({ sx: baseX, sy: baseY - TILE - 6, ...d.label })
    }

    ctx.font = '600 12px system-ui, sans-serif'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'bottom'
    ctx.lineWidth = 3
    ctx.lineJoin = 'round'
    for (const l of labels) {
      ctx.strokeStyle = 'rgba(20, 30, 15, 0.85)'
      ctx.fillStyle = '#ffffff'
      ctx.strokeText(l.name, l.sx, l.sy - 13)
      ctx.fillText(l.name, l.sx, l.sy - 13)
      ctx.fillStyle = '#f4e3a1'
      ctx.strokeText(`${l.points} pts`, l.sx, l.sy)
      ctx.fillText(`${l.points} pts`, l.sx, l.sy)
    }
  }
}
