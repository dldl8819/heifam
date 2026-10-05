import { describe, expect, it } from 'vitest'
import {
  currentKstMonth,
  formatPointAmount,
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
    expect(pointReasonKey('MATCH_CONFIRM')).toBe('points.reasons.matchConfirm')
    expect(pointReasonKey('MATCH_CONFIRM_REVERSED')).toBe('points.reasons.matchConfirmReversed')
    expect(pointReasonKey('SOMETHING_NEW')).toBeNull()
  })


})
