import { describe, expect, it } from 'vitest'
import { confirmCapReached, formatConfirmDeadline, myTeamWon, unconfirmedMatches } from '@/lib/match-confirmations'
import type { MatchConfirmation, MatchConfirmationList } from '@/types/api'

function confirmation(matchId: number, overrides: Partial<MatchConfirmation> = {}): MatchConfirmation {
  return {
    matchId,
    raceComposition: 'PPP',
    seriesGameNumber: null,
    resultRecordedAt: '2026-10-04T12:00:00Z',
    confirmDeadline: '2026-10-06T12:00:00Z',
    homePlayers: [],
    awayPlayers: [],
    winnerTeam: 'HOME',
    myTeam: 'HOME',
    confirmed: false,
    ...overrides,
  }
}

function list(matches: MatchConfirmation[], confirmedToday = 0): MatchConfirmationList {
  return { matches, confirmedToday, dailyCap: 10, points: 1, windowHours: 48 }
}

describe('match confirmations', () => {
  it('leaves out the matches already confirmed', () => {
    const matches = [confirmation(1, { confirmed: true }), confirmation(2), confirmation(3)]

    expect(unconfirmedMatches(list(matches)).map((match) => match.matchId)).toEqual([2, 3])
  })

  it('stops at the daily cap', () => {
    expect(confirmCapReached(list([], 9))).toBe(false)
    expect(confirmCapReached(list([], 10))).toBe(true)
  })

  it('tells whether my team won', () => {
    expect(myTeamWon(confirmation(1))).toBe(true)
    expect(myTeamWon(confirmation(1, { myTeam: 'AWAY' }))).toBe(false)
  })

  it('shows the deadline in Korean time', () => {
    expect(formatConfirmDeadline('2026-10-06T05:30:00Z')).toContain('14:30')
    expect(formatConfirmDeadline('not a date')).toBe('')
  })
})
