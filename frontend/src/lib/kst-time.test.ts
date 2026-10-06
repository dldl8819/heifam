import { describe, expect, it } from 'vitest'
import { formatKstDateTime } from '@/lib/kst-time'

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
