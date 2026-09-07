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
  scale?: number
  label?: { name: string; points: number }
}

const FOREST = 1
const MOUNTAIN = 3
/** Trees per FOREST tile and the share of MOUNTAIN tiles that carry a peak. */
const TREES_PER_TILE = 5
const PEAK_SHARE = 0.5
/** Fraction of open GRASS tiles that get a decorative bush / rock / dirt patch. */
const BUSH_SHARE = 0.03
const ROCK_SHARE = 0.05
/** Cross-mingling: rocks inside forest tiles, trees inside mountain tiles. */
const FOREST_ROCK_SHARE = 0.15
const MOUNTAIN_TREE_SHARE = 0.5

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
    for (let y = view.startY; y <= view.endY; y++) {
      for (let x = view.startX; x <= view.endX; x++) {
        const code = this.cache.terrainAt(x, y) ?? 0
        if (code === FOREST) {
          // A few trees per tile at stable random offsets (spilling a little into neighbours).
          for (let i = 0; i < TREES_PER_TILE; i++) {
            drawables.push({
              x: x - 0.3 + tileRandom(x, y, 11 + i * 3) * 1.6,
              y: y - 0.3 + tileRandom(x, y, 12 + i * 3) * 1.6,
              key: 'terrain.forest',
              variant: variantFor(x, y, this.sprites.byKey['terrain.forest'].length, 13 + i * 3),
              scale: 0.7 + tileRandom(x, y, 14 + i * 3) * 0.6,
            })
          }
          // A rock now and then, so forests and rocky ground mingle.
          if (tileRandom(x, y, 51) < FOREST_ROCK_SHARE) {
            drawables.push({
              x: x + tileRandom(x, y, 52),
              y: y + tileRandom(x, y, 53),
              key: 'decor.rock',
              variant: variantFor(x, y, this.sprites.byKey['decor.rock'].length, 54),
              scale: 0.6 + tileRandom(x, y, 55) * 0.7,
            })
          }
        } else if (code === MOUNTAIN) {
          if (tileRandom(x, y, 21) < PEAK_SHARE) {
            drawables.push({
              x: x + tileRandom(x, y, 22),
              y: y + tileRandom(x, y, 23),
              key: 'terrain.mountain',
              variant: variantFor(x, y, this.sprites.byKey['terrain.mountain'].length, 24),
            })
          }
          // Trees growing on and around the rocks, so mountains aren't bare.
          if (tileRandom(x, y, 26) < MOUNTAIN_TREE_SHARE) {
            drawables.push({
              x: x - 0.2 + tileRandom(x, y, 27) * 1.4,
              y: y - 0.2 + tileRandom(x, y, 28) * 1.4,
              key: 'terrain.forest',
              variant: variantFor(x, y, this.sprites.byKey['terrain.forest'].length, 29),
              scale: 0.6 + tileRandom(x, y, 30) * 0.5,
            })
          }
        } else {
          const r = tileRandom(x, y, 31)
          if (r < BUSH_SHARE) {
            drawables.push({
              x: x + tileRandom(x, y, 32),
              y: y + tileRandom(x, y, 33),
              key: 'decor.bush',
              variant: variantFor(x, y, this.sprites.byKey['decor.bush'].length, 34),
              scale: 0.7 + tileRandom(x, y, 35) * 0.3,
            })
          } else if (r < BUSH_SHARE + ROCK_SHARE) {
            drawables.push({
              x: x + tileRandom(x, y, 36),
              y: y + tileRandom(x, y, 37),
              key: 'decor.rock',
              variant: variantFor(x, y, this.sprites.byKey['decor.rock'].length, 38),
              scale: 0.4 + tileRandom(x, y, 39) * 1.1,
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
      const s = d.scale ?? 1
      const baseX = origin.sx + d.x * TILE
      const baseY = origin.sy + d.y * TILE
      ctx.drawImage(sprite.image, baseX - sprite.anchorX * s, baseY - sprite.anchorY * s, sprite.width * s, sprite.height * s)
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
