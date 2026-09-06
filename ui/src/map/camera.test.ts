import { describe, expect, it } from 'vitest'
import { clampCamera, needsRefetch, parseCoordinate, rectAround, visibleRect, windowFor } from './camera'

describe('windowFor', () => {
  it('centres a 90x90 window on the camera', () => {
    expect(windowFor(250, 250)).toEqual({ startX: 205, startY: 205, endX: 294, endY: 294 })
  })

  it('shifts the window to stay inside the map', () => {
    expect(windowFor(3, 497)).toEqual({ startX: 0, startY: 410, endX: 89, endY: 499 })
    expect(rectAround(0, 0, 90)).toEqual({ startX: 0, startY: 0, endX: 89, endY: 89 })
  })
})

describe('needsRefetch', () => {
  const bounds = windowFor(250, 250) // 205..294
  it('is false in the middle of the window', () => {
    expect(needsRefetch(bounds, 250, 250)).toBe(false)
  })
  it('is true within the margin of an inner edge', () => {
    expect(needsRefetch(bounds, 215, 250)).toBe(true)
    expect(needsRefetch(bounds, 250, 285)).toBe(true)
  })
  it('ignores edges that coincide with the map edge', () => {
    expect(needsRefetch(windowFor(0, 0), 2, 2)).toBe(false)
    expect(needsRefetch(windowFor(0, 0), 80, 2)).toBe(true)
  })
})

describe('visibleRect', () => {
  it('covers ~30 tiles across a 1440px canvas, clamped to the map', () => {
    const r = visibleRect(250, 250, 1440, 960, 0)
    expect(r.endX - r.startX + 1).toBe(31)
    expect(r.endY - r.startY + 1).toBe(21)
    expect(visibleRect(0, 0, 1440, 960, 2).startX).toBe(0)
  })
})

describe('parseCoordinate', () => {
  it('accepts x,y within the map', () => {
    expect(parseCoordinate('12, 340')).toEqual({ x: 12, y: 340 })
    expect(parseCoordinate('499 0')).toEqual({ x: 499, y: 0 })
  })
  it('rejects malformed or out-of-range input', () => {
    expect(parseCoordinate('500,1')).toBeNull()
    expect(parseCoordinate('a,b')).toBeNull()
    expect(parseCoordinate('')).toBeNull()
  })
})

describe('clampCamera', () => {
  // 1440x960 px at 48 px/tile shows 30x20 tiles: the centre must stay 15 / 10 tiles from the edges.
  it('keeps the viewport inside the map', () => {
    expect(clampCamera(0, 0, 1440, 960)).toEqual({ x: 15, y: 10 })
    expect(clampCamera(499, 499, 1440, 960)).toEqual({ x: 485, y: 490 })
    expect(clampCamera(250, 250, 1440, 960)).toEqual({ x: 250, y: 250 })
  })

  it('centres the map when the viewport is larger than the map', () => {
    expect(clampCamera(10, 10, 48 * 600, 48 * 600)).toEqual({ x: 250, y: 250 })
  })
})
