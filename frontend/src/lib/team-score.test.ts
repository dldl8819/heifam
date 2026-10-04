import { describe, expect, it } from 'vitest'
import { formatScoreRate } from '@/lib/team-score'

describe('team score helpers', () => {
  it('shows a rate or a dash when nothing was played', () => {
    expect(formatScoreRate(66.7)).toBe('66.7%')
    expect(formatScoreRate(0)).toBe('0%')
    expect(formatScoreRate(null)).toBe('-')
  })
})
