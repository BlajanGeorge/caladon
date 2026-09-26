import { describe, expect, it } from 'vitest'
import { affordability, formatDuration, formatRequirements, maxAffordable, secondsUntil } from './format'

describe('formatDuration', () => {
  it('renders hours, minutes and seconds like the design tables', () => {
    expect(formatDuration(95)).toBe('1m35s')
    expect(formatDuration(642)).toBe('10m42s')
    expect(formatDuration(4800)).toBe('1h20m00s')
    expect(formatDuration(3725)).toBe('1h02m05s')
    expect(formatDuration(0)).toBe('0s')
    expect(formatDuration(-5)).toBe('0s')
  })
})

describe('secondsUntil', () => {
  it('counts down and stops at zero', () => {
    const now = Date.parse('2026-09-22T19:00:00Z')
    expect(secondsUntil('2026-09-22T19:01:35Z', now)).toBe(95)
    expect(secondsUntil('2026-09-22T18:59:00Z', now)).toBe(0)
  })
})

describe('affordability', () => {
  const stocks = { wood: 100, stone: 100, iron: 100 }
  it('names the short resources, then population', () => {
    expect(affordability(stocks, 5, { wood: 63, stone: 77, iron: 50 }, 1)).toEqual({ ok: true, reason: '' })
    expect(affordability(stocks, 5, { wood: 150, stone: 77, iron: 250 }, 1).reason).toBe('Not enough wood, iron')
    expect(affordability(stocks, 0, { wood: 10, stone: 10, iron: 10 }, 1).reason).toBe('Not enough population')
    expect(affordability(stocks, 5, { wood: 50, stone: 30, iron: 10 }, 1, 3).reason).toBe('Not enough wood')
  })
  it('maxAffordable takes the tightest of the four limits', () => {
    expect(maxAffordable(stocks, 5, { wood: 50, stone: 30, iron: 10 }, 1)).toBe(2)
    expect(maxAffordable(stocks, 1, { wood: 10, stone: 10, iron: 10 }, 1)).toBe(1)
    expect(maxAffordable(stocks, 100, { wood: 0, stone: 0, iron: 0 }, 0)).toBe(0)
  })
})

describe('formatRequirements', () => {
  it('uses display names', () => {
    expect(formatRequirements([{ building: 'TOWN_HALL', level: 3 }, { building: 'FARM', level: 6 }])).toBe('Town Hall level 3, Farm level 6')
  })
})
