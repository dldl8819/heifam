import { describe, expect, it } from 'vitest'
import { formatKstDateTime, formatKstFullDateTime } from '@/lib/kst-time'

describe('formatKstFullDateTime', () => {
  it('shows the year, the day and the minute in Korean time', () => {
    // 20:51 on Oct 6 in Korea.
    expect(formatKstFullDateTime('2026-10-06T11:51:00Z')).toBe('2026. 10. 06. 20:51')
    // A comment written just after midnight in Korea belongs to the new day, and to the new year.
    expect(formatKstFullDateTime('2026-10-06T15:05:00Z')).toBe('2026. 10. 07. 00:05')
    expect(formatKstFullDateTime('2026-12-31T15:00:00Z')).toBe('2027. 01. 01. 00:00')
  })

  it('reads a time with an offset as the same moment', () => {
    expect(formatKstFullDateTime('2026-10-06T20:51:00+09:00')).toBe('2026. 10. 06. 20:51')
    expect(formatKstFullDateTime('2026-10-06T11:51:00.123456+00:00')).toBe('2026. 10. 06. 20:51')
  })

  it('is empty for a missing or bad value', () => {
    expect(formatKstFullDateTime(null)).toBe('')
    expect(formatKstFullDateTime(undefined)).toBe('')
    expect(formatKstFullDateTime('not a date')).toBe('')
  })
})

describe('formatKstDateTime', () => {
  it('shows a moment in Korean time, on the Korean day', () => {
    // 20:51 on Oct 6 in Korea.
    const text = formatKstDateTime('2026-10-06T11:51:00Z')

    expect(text).toContain('20:51')
    expect(text).toContain('10')
    expect(text).toContain('6')
    // 00:30 on Oct 7 in Korea is still Oct 6 in UTC.
    expect(formatKstDateTime('2026-10-06T15:30:00Z')).toContain('7')
  })

  it('is empty for a missing or bad value', () => {
    expect(formatKstDateTime(null)).toBe('')
    expect(formatKstDateTime(undefined)).toBe('')
    expect(formatKstDateTime('not a date')).toBe('')
  })
})
