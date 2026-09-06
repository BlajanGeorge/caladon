export const MAP_SIZE = 500
/** Screen pixels per tile (CSS px). ~30 tiles fit across a 1440px-wide canvas. */
export const TILE = 48
/** Side of the padded window fetched around the camera. */
export const WINDOW = 90
/** Refetch when the camera gets this close to a cached-window edge that is not a map edge. */
export const REFETCH_MARGIN = 15
/** Extra tiles fetched around the visible area on pan-end refreshes. */
export const VISIBLE_PAD = 3

export interface Rect {
  startX: number
  startY: number
  endX: number
  endY: number
}

export function clampCoord(v: number): number {
  return Math.min(MAP_SIZE - 1, Math.max(0, v))
}

/**
 * Clamps a camera centre so the viewport (`width x height` CSS px) never shows tiles outside the
 * map. If the viewport is larger than the map along an axis, the map is centred on that axis.
 */
export function clampCamera(x: number, y: number, width: number, height: number): { x: number; y: number } {
  const halfW = width / TILE / 2
  const halfH = height / TILE / 2
  const clampAxis = (v: number, half: number) =>
    half * 2 >= MAP_SIZE ? MAP_SIZE / 2 : Math.min(MAP_SIZE - half, Math.max(half, v))
  return { x: clampAxis(x, halfW), y: clampAxis(y, halfH) }
}

/** A `side x side` rectangle centred on (cx, cy), shifted to stay inside the map. */
export function rectAround(cx: number, cy: number, side: number): Rect {
  const s = Math.min(side, MAP_SIZE)
  const half = Math.floor(s / 2)
  let startX = Math.round(cx) - half
  let startY = Math.round(cy) - half
  startX = Math.max(0, Math.min(startX, MAP_SIZE - s))
  startY = Math.max(0, Math.min(startY, MAP_SIZE - s))
  return { startX, startY, endX: startX + s - 1, endY: startY + s - 1 }
}

export function windowFor(camX: number, camY: number): Rect {
  return rectAround(camX, camY, WINDOW)
}

/** Tiles visible on a `width x height` (CSS px) canvas, clamped to the map, with a small pad. */
export function visibleRect(camX: number, camY: number, width: number, height: number, pad = VISIBLE_PAD): Rect {
  const halfW = width / TILE / 2
  const halfH = height / TILE / 2
  return {
    startX: clampCoord(Math.floor(camX - halfW) - pad),
    startY: clampCoord(Math.floor(camY - halfH) - pad),
    endX: clampCoord(Math.ceil(camX + halfW) + pad),
    endY: clampCoord(Math.ceil(camY + halfH) + pad),
  }
}

/** True when the camera is within REFETCH_MARGIN of a window edge that is not a map edge. */
export function needsRefetch(bounds: Rect, camX: number, camY: number): boolean {
  if (bounds.startX > 0 && camX - bounds.startX < REFETCH_MARGIN) return true
  if (bounds.startY > 0 && camY - bounds.startY < REFETCH_MARGIN) return true
  if (bounds.endX < MAP_SIZE - 1 && bounds.endX - camX < REFETCH_MARGIN) return true
  if (bounds.endY < MAP_SIZE - 1 && bounds.endY - camY < REFETCH_MARGIN) return true
  return false
}

export function contains(r: Rect, x: number, y: number): boolean {
  return x >= r.startX && x <= r.endX && y >= r.startY && y <= r.endY
}

export function parseCoordinate(input: string): { x: number; y: number } | null {
  const m = input.trim().match(/^(\d{1,3})\s*[,;\s]\s*(\d{1,3})$/)
  if (!m) return null
  const x = Number(m[1])
  const y = Number(m[2])
  if (x >= MAP_SIZE || y >= MAP_SIZE) return null
  return { x, y }
}
