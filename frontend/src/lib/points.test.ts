import { describe, expect, it } from 'vitest'
import {
  buildPointMemberOptions,
  currentKstMonth,
  formatPointAmount,
  parseAdjustmentAmount,
  pointReasonKey,
  shiftMonth,
} from '@/lib/points'

describe('points helpers', () => {
  it('counts the month in Korean time', () => {
    expect(currentKstMonth(new Date('2026-09-30T14:59:59Z'))).toBe('2026-09')
    expect(currentKstMonth(new Date('2026-09-30T15:00:00Z'))).toBe('2026-10')
  })

  it('moves across year boundaries', () => {
    expect(shiftMonth('2026-01', -1)).toBe('2025-12')
    expect(shiftMonth('2025-12', 1)).toBe('2026-01')
    expect(shiftMonth('2026-10', 0)).toBe('2026-10')
  })

  it('signs point amounts', () => {
    expect(formatPointAmount(1)).toBe('+1p')
    expect(formatPointAmount(-1)).toBe('-1p')
    expect(formatPointAmount(1000)).toBe('+1,000p')
  })

  it('knows the ledger reasons', () => {
    expect(pointReasonKey('DAILY_LOGIN')).toBe('points.reasons.dailyLogin')
    expect(pointReasonKey('MATCH_RESULT_REVERSED')).toBe('points.reasons.matchResultReversed')
    expect(pointReasonKey('SOMETHING_NEW')).toBeNull()
  })

  it('accepts only whole, non-zero adjustments within the limit', () => {
    expect(parseAdjustmentAmount(' 5 ')).toBe(5)
    expect(parseAdjustmentAmount('-1000')).toBe(-1000)
    expect(parseAdjustmentAmount('+3')).toBe(3)
    expect(parseAdjustmentAmount('0')).toBeNull()
    expect(parseAdjustmentAmount('1001')).toBeNull()
    expect(parseAdjustmentAmount('1.5')).toBeNull()
    expect(parseAdjustmentAmount('')).toBeNull()
    expect(parseAdjustmentAmount('1e3')).toBeNull()
  })

  it('lists each member once, by nickname when there is one', () => {
    const options = buildPointMemberOptions([
      [{ email: 'Admin@example.com', nickname: null, canViewMmr: false }],
      [
        { email: 'admin@example.com', nickname: '운영진', canViewMmr: false },
        { email: 'member@example.com', nickname: ' 가나다 ', canViewMmr: false },
        { email: 'member@example.com', nickname: '다른 이름', canViewMmr: false },
        { email: ' ', nickname: '빈 이메일', canViewMmr: false },
      ],
    ])

    expect(options).toEqual([
      { email: 'member@example.com', label: '가나다' },
      { email: 'admin@example.com', label: '운영진' },
    ])
  })
})
