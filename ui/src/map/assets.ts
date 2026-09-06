import '@assets/procedural-sprites.js'
import { TILE } from './camera'

/**
 * Asset registry keyed by BE type (ARCHITECTURE.md, "Asset manifest & rendering").
 * Every key resolves to one or more rasterised sprites (variants picked per tile so a given tile
 * always looks the same). Today each key is a procedural SVG placeholder; to drop in final art,
 * replace an entry's `svg` with a PNG loader — the renderer only sees `Sprite`s.
 */
export type AssetKey =
  | 'terrain.forest'
  | 'terrain.lake'
  | 'terrain.mountain'
  | 'entity.slot'
  | 'entity.city.t1'
  | 'entity.city.t2'
  | 'entity.city.t3'
  | 'entity.barbarian'

export interface Sprite {
  image: CanvasImageSource
  /** CSS px size at which to draw the image. */
  width: number
  height: number
  /** Offset (CSS px) from the image's top-left to its ground anchor (bottom-centre of the tile). */
  anchorX: number
  anchorY: number
}

export interface SpriteSet {
  grass: CanvasPattern
  byKey: Record<AssetKey, Sprite[]>
}

/** Generator canvas: sprites are authored around an anchor at (BOX/2, ANCHOR_Y). */
const BOX = 256
const ANCHOR_Y = 200

interface Placeholder {
  /** SVG markup for one variant, anchored at (cx, cy). */
  svg: (cx: number, cy: number, seed: number) => string
  /** Screen scale relative to the generator's own units (1 unit = 1px at 64px tiles). */
  scale: number
  variants: number
}

const S = () => window.CaladonSprites
const unit = TILE / 64

// Trees: individual sprites of varying size; the renderer scatters several per FOREST tile.
const TREE_SHAPES: [number, number][] = [[34, 11], [40, 12], [46, 13], [52, 14], [58, 15], [44, 16]]
// Mountains: a few peak sizes; the renderer places peaks on a jittered subset of MOUNTAIN tiles.
const PEAK_SIZES = [0.8, 1.0, 1.2, 1.45]

const placeholders: Record<AssetKey, Placeholder> = {
  'terrain.forest': {
    svg: (x, y, seed) => { const [h, w] = TREE_SHAPES[(seed - 1) % TREE_SHAPES.length]; return S().tree(x, y, h, w) },
    scale: unit * 1.1,
    variants: TREE_SHAPES.length,
  },
  // Drawn on lake-edge tiles only (the interior is flat water, see MapRenderer): sized to a tile.
  'terrain.lake': { svg: (x, y) => S().lake(x, y, 1.0), scale: unit * 1.1, variants: 1 },
  'terrain.mountain': {
    svg: (x, y, seed) => S().mountain(x, y, PEAK_SIZES[(seed - 1) % PEAK_SIZES.length]),
    scale: unit * 1.0,
    variants: PEAK_SIZES.length,
  },
  'entity.slot': { svg: (x, y) => S().slot(x, y), scale: unit * 1.2, variants: 1 },
  'entity.city.t1': { svg: (x, y, seed) => S().village(x, y, 1, seed), scale: unit * 1.6, variants: 4 },
  'entity.city.t2': { svg: (x, y, seed) => S().village(x, y, 2, seed), scale: unit * 1.6, variants: 4 },
  'entity.city.t3': { svg: (x, y, seed) => S().village(x, y, 3, seed), scale: unit * 1.6, variants: 4 },
  'entity.barbarian': { svg: (x, y, seed) => S().barbarians(x, y, seed), scale: unit * 1.3, variants: 4 },
}

function loadImage(svg: string, width: number, height: number): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image(width, height)
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error('sprite failed to load'))
    img.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg)
  })
}

async function rasterise(markup: string, scale: number, dpr: number): Promise<Sprite> {
  const css = BOX * scale
  const px = Math.ceil(css * dpr)
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${px}" height="${px}" viewBox="0 0 ${BOX} ${BOX}">${markup}</svg>`
  const img = await loadImage(svg, px, px)
  const canvas = document.createElement('canvas')
  canvas.width = px
  canvas.height = px
  canvas.getContext('2d')!.drawImage(img, 0, 0, px, px)
  return { image: canvas, width: css, height: css, anchorX: css / 2, anchorY: ANCHOR_Y * scale }
}

async function grassPattern(ctx: CanvasRenderingContext2D, dpr: number): Promise<CanvasPattern> {
  const size = 64
  const px = Math.ceil(size * dpr)
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${px}" height="${px}" viewBox="0 0 ${size} ${size}"><defs>${S().grassPattern('g')}</defs><rect width="${size}" height="${size}" fill="url(#g)"/></svg>`
  const img = await loadImage(svg, px, px)
  const canvas = document.createElement('canvas')
  canvas.width = px
  canvas.height = px
  canvas.getContext('2d')!.drawImage(img, 0, 0, px, px)
  const pattern = ctx.createPattern(canvas, 'repeat')!
  // Keep the pattern at 64 CSS px regardless of device pixel ratio.
  pattern.setTransform(new DOMMatrix().scale(1 / dpr))
  return pattern
}

export async function loadSprites(ctx: CanvasRenderingContext2D): Promise<SpriteSet> {
  const dpr = window.devicePixelRatio || 1
  const grass = await grassPattern(ctx, dpr)
  const entries = await Promise.all(
    (Object.keys(placeholders) as AssetKey[]).map(async (key) => {
      const p = placeholders[key]
      const variants = await Promise.all(
        Array.from({ length: p.variants }, (_, i) => rasterise(p.svg(BOX / 2, ANCHOR_Y, i + 1), p.scale, dpr)),
      )
      return [key, variants] as const
    }),
  )
  return { grass, byKey: Object.fromEntries(entries) as Record<AssetKey, Sprite[]> }
}

/** Placeholder thresholds (tunable): t1 < 1000, t2 1000–4999, t3 >= 5000. */
export function cityTierKey(points: number): AssetKey {
  return points >= 5000 ? 'entity.city.t3' : points >= 1000 ? 'entity.city.t2' : 'entity.city.t1'
}

/** Stable per-tile pseudo-random number in [0, 1); `salt` gives independent streams per use. */
export function tileRandom(x: number, y: number, salt = 0): number {
  let h = (x * 73856093) ^ (y * 19349663) ^ (salt * 83492791)
  h = Math.imul(h ^ (h >>> 13), 1274126177)
  h ^= h >>> 16
  return (h >>> 0) / 4294967296
}

/** Stable per-tile variant index so a tile always renders the same. */
export function variantFor(x: number, y: number, count: number, salt = 0): number {
  return Math.floor(tileRandom(x, y, salt) * count)
}
