import '@assets/procedural-sprites.js'
import cityT1Url from '@assets/sprites/city-t1.png'
import cityT2Url from '@assets/sprites/city-t2.png'
import grassUrl from '@assets/sprites/grass2.png'
import forestUrl from '@assets/sprites/forest.png'
import rockhillUrl from '@assets/sprites/rockhill.png'
import treeFirUrl from '@assets/sprites/tree-fir.png'
import treeOakUrl from '@assets/sprites/tree-oak.png'
import bush1Url from '@assets/sprites/bush1.png'
import bush2Url from '@assets/sprites/bush2.png'
import bush3Url from '@assets/sprites/bush3.png'
import bush4Url from '@assets/sprites/bush4.png'
import rock1Url from '@assets/sprites/rock1.png'
import rock2Url from '@assets/sprites/rock2.png'
import rock3Url from '@assets/sprites/rock3.png'
import slotUrl from '@assets/sprites/slot.png'
import barbarianUrl from '@assets/sprites/barbarian.png'
import { TILE } from './camera'

/**
 * Asset registry keyed by BE type (ARCHITECTURE.md, "Asset manifest & rendering").
 * Every key resolves to one or more rasterised sprites (variants picked per tile so a given tile
 * always looks the same). Today each key is a procedural SVG placeholder; to drop in final art,
 * replace an entry's `svg` with a PNG loader — the renderer only sees `Sprite`s.
 */
export type AssetKey =
  | 'terrain.forest'
  | 'terrain.mountain'
  | 'entity.slot'
  | 'entity.city.t1'
  | 'entity.city.t2'
  | 'entity.city.t3'
  | 'entity.barbarian'
  | 'decor.bush'
  | 'decor.rock'

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

/** Final PNG art for a key: overrides the procedural placeholder in `loadSprites`. One or more
 *  variant images; the renderer picks a stable variant per tile. */
interface ImageSprite {
  urls: string[]
  /** Drawn width, in tile widths. */
  widthTiles: number
  /** Ground-anchor height as a fraction of the drawn height (bottom-centre contact point). */
  anchorYFrac: number
}

const imageSprites: Partial<Record<AssetKey, ImageSprite>> = {
  'terrain.forest': { urls: [forestUrl, treeFirUrl, treeOakUrl], widthTiles: 0.9, anchorYFrac: 0.96 },
  'terrain.mountain': { urls: [rockhillUrl], widthTiles: 2.6, anchorYFrac: 0.85 },
  'decor.bush': { urls: [bush1Url, bush2Url, bush3Url, bush4Url], widthTiles: 0.28, anchorYFrac: 0.85 },
  'decor.rock': { urls: [rock1Url, rock2Url, rock3Url], widthTiles: 0.45, anchorYFrac: 0.85 },
  'entity.slot': { urls: [slotUrl], widthTiles: 1.5, anchorYFrac: 0.72 },
  'entity.city.t1': { urls: [cityT1Url], widthTiles: 2.2, anchorYFrac: 0.86 },
  'entity.city.t2': { urls: [cityT2Url], widthTiles: 2.2, anchorYFrac: 0.9 },
  'entity.barbarian': { urls: [barbarianUrl], widthTiles: 1.8, anchorYFrac: 0.9 },
}

const S = () => window.CaladonSprites
const unit = TILE / 64

// Trees: individual sprites of varying size; the renderer scatters several per FOREST tile.
const TREE_SHAPES: [number, number][] = [[34, 11], [40, 12], [46, 13], [52, 14], [58, 15], [44, 16]]
// Mountains: a few peak sizes; the renderer places peaks on a jittered subset of MOUNTAIN tiles.
const PEAK_SIZES = [0.8, 1.0, 1.2, 1.45]

const placeholders: Partial<Record<AssetKey, Placeholder>> = {
  'terrain.forest': {
    svg: (x, y, seed) => { const [h, w] = TREE_SHAPES[(seed - 1) % TREE_SHAPES.length]; return S().tree(x, y, h, w) },
    scale: unit * 1.1,
    variants: TREE_SHAPES.length,
  },
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

function loadPng(url: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error('png sprite failed to load: ' + url))
    img.src = url
  })
}

async function rasterisePng(url: string, cfg: ImageSprite): Promise<Sprite> {
  const img = await loadPng(url)
  const width = cfg.widthTiles * TILE
  const height = (width * img.height) / img.width
  return { image: img, width, height, anchorX: width / 2, anchorY: height * cfg.anchorYFrac }
}

/** CSS px of one texture repeat on screen (tune for how tight the detail reads). */
const GRASS_TILE_PX = 900

async function texturePattern(ctx: CanvasRenderingContext2D, dpr: number, url: string, tilePx: number): Promise<CanvasPattern> {
  const img = await loadPng(url)
  const px = Math.ceil(tilePx * dpr)
  const canvas = document.createElement('canvas')
  canvas.width = px
  canvas.height = px
  canvas.getContext('2d')!.drawImage(img, 0, 0, px, px)
  const pattern = ctx.createPattern(canvas, 'repeat')!
  pattern.setTransform(new DOMMatrix().scale(1 / dpr))
  return pattern
}

export async function loadSprites(ctx: CanvasRenderingContext2D): Promise<SpriteSet> {
  const dpr = window.devicePixelRatio || 1
  const grass = await texturePattern(ctx, dpr, grassUrl, GRASS_TILE_PX)
  const allKeys = Array.from(
    new Set<AssetKey>([...(Object.keys(placeholders) as AssetKey[]), ...(Object.keys(imageSprites) as AssetKey[])]),
  )
  const entries = await Promise.all(
    allKeys.map(async (key) => {
      const image = imageSprites[key]
      if (image) return [key, await Promise.all(image.urls.map((u) => rasterisePng(u, image)))] as const
      const p = placeholders[key]!
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
