import { describe, expect, it } from 'vitest'
import { pollIntervalMs } from './CityPage'
import type { CityResources } from '../api/worlds'

const res = (wood: number, stone = wood, iron = wood): CityResources => ({
  wood: { stock: 0, ratePerHour: wood },
  stone: { stock: 0, ratePerHour: stone },
  iron: { stock: 0, ratePerHour: iron },
  capacity: 1000,
  serverTime: '2026-09-22T19:00:00Z',
})

describe('pollIntervalMs', () => {
  it('polls every 5 minutes while no resource grows by more than one unit per minute', () => {
    expect(pollIntervalMs(res(30))).toBe(300_000)
    expect(pollIntervalMs(res(60))).toBe(300_000)
  })
  it('polls every minute once any resource grows faster than 60/h', () => {
    expect(pollIntervalMs(res(30, 30, 64))).toBe(60_000)
    expect(pollIntervalMs(res(2400))).toBe(60_000)
  })
  it('defaults to a minute before the first response', () => {
    expect(pollIntervalMs(undefined)).toBe(60_000)
  })
})
