import { describe, expect, it } from 'vitest'
import { RESULT_EDIT_WINDOW_HOURS, isResultEditWindowOpen } from './match-edit-window'

const now = Date.parse('2026-11-11T12:00:00+09:00')
const hoursAgo = (hours: number) => new Date(now - hours * 60 * 60 * 1000).toISOString()

describe('isResultEditWindowOpen', () => {
  it('keeps a result open for 48 hours after it went in', () => {
    expect(RESULT_EDIT_WINDOW_HOURS).toBe(48)
    expect(isResultEditWindowOpen({ winningTeam: 'HOME', resultRecordedAt: hoursAgo(1), playedAt: hoursAgo(240) }, now)).toBe(true)
    expect(isResultEditWindowOpen({ winningTeam: 'HOME', resultRecordedAt: hoursAgo(47.9), playedAt: null }, now)).toBe(true)
  })

  it('closes it at 48 hours', () => {
    expect(isResultEditWindowOpen({ winningTeam: 'AWAY', resultRecordedAt: hoursAgo(48), playedAt: null }, now)).toBe(false)
    expect(isResultEditWindowOpen({ winningTeam: 'AWAY', resultRecordedAt: hoursAgo(100), playedAt: null }, now)).toBe(false)
  })

  it('counts from when it was played for a result kept without its time', () => {
    expect(isResultEditWindowOpen({ winningTeam: 'HOME', resultRecordedAt: null, playedAt: hoursAgo(10) }, now)).toBe(true)
    expect(isResultEditWindowOpen({ winningTeam: 'HOME', resultRecordedAt: null, playedAt: hoursAgo(49) }, now)).toBe(false)
    expect(isResultEditWindowOpen({ winningTeam: 'HOME', resultRecordedAt: null, playedAt: null }, now)).toBe(false)
  })

  it('leaves a match without a result open', () => {
    expect(isResultEditWindowOpen({ winningTeam: null, resultRecordedAt: null, playedAt: hoursAgo(500) }, now)).toBe(true)
  })
})
