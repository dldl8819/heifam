import { describe, expect, it } from 'vitest'
import {
  axisTicks,
  formatLedgerDate,
  formatManwon,
  formatSignedWon,
  formatWon,
  monthLabel,
  monthTicks,
  niceAxis,
} from '@/lib/ledger-dashboard'

describe('ledger dashboard helpers', () => {
  it('rounds a balance range out to clean 10만 steps', () => {
    const scale = niceAxis(595_001, 1_057_801)

    expect(scale).toEqual({ min: 500_000, max: 1_100_000, step: 100_000 })
    expect(axisTicks(scale)).toEqual([500_000, 600_000, 700_000, 800_000, 900_000, 1_000_000, 1_100_000])
  })

  it('starts a monthly amount axis at zero', () => {
    expect(niceAxis(0, 462_200)).toEqual({ min: 0, max: 500_000, step: 100_000 })
    expect(niceAxis(0, 30_000)).toEqual({ min: 0, max: 30_000, step: 5_000 })
  })

  it('keeps a usable axis when every value is the same', () => {
    expect(niceAxis(0, 0)).toEqual({ min: 0, max: 10_000, step: 10_000 })
    expect(niceAxis(873_401, 873_401)).toEqual({ min: 870_000, max: 880_000, step: 10_000 })
  })

  it('formats compact axis labels in 만 and 억 units', () => {
    expect(formatManwon(0)).toBe('0')
    expect(formatManwon(150_000)).toBe('15만')
    expect(formatManwon(25_000)).toBe('2.5만')
    expect(formatManwon(-200_000)).toBe('−20만')
    expect(formatManwon(250_000_000)).toBe('2.5억')
    expect(formatManwon(5_000)).toBe('5,000')
  })

  it('formats won amounts with and without a sign', () => {
    expect(formatWon(873_401)).toBe('873,401원')
    expect(formatSignedWon(81_000)).toBe('+81,000원')
    expect(formatSignedWon(-462_200)).toBe('−462,200원')
    expect(formatSignedWon(0)).toBe('0원')
  })

  it('labels months and dates in Korean', () => {
    expect(monthLabel('2026-03')).toBe('3월')
    expect(monthLabel('2027-01', true)).toBe('2027년 1월')
    expect(formatLedgerDate('2026-09-07')).toBe('2026년 9월 7일')
  })

  it('places one month tick per month, the first on the first date', () => {
    expect(monthTicks('2026-11-20', '2027-02-03')).toEqual([
      { month: '2026-11', date: '2026-11-20' },
      { month: '2026-12', date: '2026-12-01' },
      { month: '2027-01', date: '2027-01-01' },
      { month: '2027-02', date: '2027-02-01' },
    ])
  })
})
