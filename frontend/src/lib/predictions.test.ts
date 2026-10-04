import { describe, expect, it } from 'vitest'
import {
  formatCountdown,
  formatPredictionPlayers,
  hitRate,
  remainingSeconds,
  serverOffsetMs,
} from '@/lib/predictions'

describe('prediction helpers', () => {
  it('counts down by the server clock', () => {
    const clientNow = Date.parse('2026-10-04T12:00:00Z')
    const offset = serverOffsetMs('2026-10-04T12:00:10Z', clientNow)

    expect(offset).toBe(10000)
    expect(remainingSeconds('2026-10-04T12:01:00Z', offset, clientNow)).toBe(50)
    expect(remainingSeconds('2026-10-04T11:59:00Z', offset, clientNow)).toBe(0)
    expect(remainingSeconds(null, offset, clientNow)).toBe(0)
  })

  it('shows minutes and seconds', () => {
    expect(formatCountdown(179)).toBe('2:59')
    expect(formatCountdown(5)).toBe('0:05')
    expect(formatCountdown(-3)).toBe('0:00')
  })

  it('rounds the hit rate to one decimal and skips it with no results', () => {
    expect(hitRate({ resolved: 3, hits: 2 })).toBe(66.7)
    expect(hitRate({ resolved: 0, hits: 0 })).toBeNull()
  })

  it('marks who takes Terran or Zerg', () => {
    expect(
      formatPredictionPlayers([
        { nickname: 'YOUR_USERNAME_1', assignedRace: 'P' },
        { nickname: 'YOUR_USERNAME_2', assignedRace: 'T' },
        { nickname: null, assignedRace: 'Z' },
      ]),
    ).toBe('YOUR_USERNAME_1, YOUR_USERNAME_2 (T), - (Z)')
  })
})
