import { describe, expect, it } from 'vitest'
import { formatMultiBalanceChatText, formatMultiBalanceMatchChatText } from '@/lib/multi-balance-chat'
import type { BalancePlayerInput, MultiBalanceMatch, MultiBalanceResponse } from '@/types/api'

function player(name: string, overrides: Partial<BalancePlayerInput> = {}): BalancePlayerInput {
  return { name, mmr: 1500, ...overrides } as BalancePlayerInput
}

function match(matchNumber: number, home: string[], away: string[], overrides: Partial<MultiBalanceMatch> = {}): MultiBalanceMatch {
  return {
    matchNumber,
    matchType: '3v3',
    teamSize: 3,
    homeTeam: home.map((name) => player(name)),
    awayTeam: away.map((name) => player(name)),
    expectedHomeWinRate: 0.5123,
    raceSummary: { home: 'PPP', away: 'PPP' },
    penaltySummary: { repeatTeammatePenalty: 0, repeatMatchupPenalty: 0, racePenalty: 0 },
    ...overrides,
  }
}

describe('multi-balance chat text', () => {
  it('writes a match as one chat line with the page\'s team numbers', () => {
    const second = match(2, ['YOUR_USERNAME_4', 'YOUR_USERNAME_5'], ['YOUR_USERNAME_6', 'YOUR_USERNAME_7'])

    expect(formatMultiBalanceMatchChatText(second, 1)).toBe(
      '3팀: YOUR_USERNAME_4, YOUR_USERNAME_5 / 4팀: YOUR_USERNAME_6, YOUR_USERNAME_7 / 3팀 승률 51.23%',
    )
  })

  it('leaves MMR out and shows a dash when the win rate is not sent', () => {
    const line = formatMultiBalanceMatchChatText(
      match(1, ['YOUR_USERNAME_1'], ['YOUR_USERNAME_2'], { expectedHomeWinRate: undefined }),
      0,
    )

    expect(line).toBe('1팀: YOUR_USERNAME_1 / 2팀: YOUR_USERNAME_2 / 1팀 승률 -')
    expect(line).not.toContain('1500')
  })

  it('marks a player given a race', () => {
    const withRace = match(1, [], [], {
      homeTeam: [player('YOUR_USERNAME_1', { assignedRace: 'T' })],
      awayTeam: [player('YOUR_USERNAME_2')],
    })

    expect(formatMultiBalanceMatchChatText(withRace, 0)).toContain('1팀: YOUR_USERNAME_1(T) / 2팀: YOUR_USERNAME_2')
  })

  it('lists every match on its own line and then the players left waiting', () => {
    const result: MultiBalanceResponse = {
      balanceMode: 'MMR_FIRST',
      totalPlayers: 5,
      assignedPlayers: 4,
      waitingPlayers: [{ id: 9, nickname: 'YOUR_USERNAME_9' }],
      matchCount: 2,
      matches: [match(1, ['YOUR_USERNAME_1'], ['YOUR_USERNAME_2']), match(2, ['YOUR_USERNAME_3'], ['YOUR_USERNAME_4'])],
    }

    expect(formatMultiBalanceChatText(result).split('\n')).toEqual([
      '경기1 1팀: YOUR_USERNAME_1 / 2팀: YOUR_USERNAME_2 / 1팀 승률 51.23%',
      '경기2 3팀: YOUR_USERNAME_3 / 4팀: YOUR_USERNAME_4 / 3팀 승률 51.23%',
      '대기: YOUR_USERNAME_9',
    ])
  })

  it('has no waiting line when everyone plays', () => {
    const result: MultiBalanceResponse = {
      balanceMode: 'MMR_FIRST',
      totalPlayers: 2,
      assignedPlayers: 2,
      waitingPlayers: [],
      matchCount: 1,
      matches: [match(1, ['YOUR_USERNAME_1'], ['YOUR_USERNAME_2'])],
    }

    expect(formatMultiBalanceChatText(result)).not.toContain('대기')
  })
})
