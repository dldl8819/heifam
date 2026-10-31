import { describe, expect, it } from 'vitest'
import {
  buildPrizeWinners,
  initialPrizeDrafts,
  parseManualEntries,
  parsePrizeAmount,
  prizeEventMetric,
} from '@/lib/prize-events'
import type { PrizeCandidate } from '@/types/api'

const candidates: PrizeCandidate[] = [
  { rank: 1, pointAccountId: 11, nickname: 'YOUR_USERNAME_1', points: 9 },
  { rank: 2, pointAccountId: 12, nickname: 'YOUR_USERNAME_2', points: 7 },
  { rank: 2, pointAccountId: 13, nickname: 'YOUR_USERNAME_3', points: 7 },
]

describe('prize event helpers', () => {
  it('ticks the first candidates up to the winner count', () => {
    const drafts = initialPrizeDrafts(candidates, 2)

    expect(drafts['account:11'].selected).toBe(true)
    expect(drafts['account:12'].selected).toBe(true)
    expect(drafts['account:13'].selected).toBe(false)
  })

  it('reads amounts as whole won', () => {
    expect(parsePrizeAmount('15,000')).toBe(15000)
    expect(parsePrizeAmount(' ')).toBe(0)
    expect(parsePrizeAmount('1.5')).toBeNull()
    expect(parsePrizeAmount('-3')).toBeNull()
  })

  it('sends ticked candidates in place order', () => {
    const drafts = initialPrizeDrafts(candidates, 2)
    drafts['account:11'] = { selected: true, prize: ' 커피 ', amount: '5000' }
    drafts['account:12'] = { selected: false, prize: '', amount: '' }
    drafts['account:13'] = { selected: true, prize: '', amount: '' }

    expect(buildPrizeWinners(candidates, drafts)).toEqual([
      { pointAccountId: 11, playerId: null, prize: '커피', amount: 5000 },
      { pointAccountId: 13, playerId: null, prize: null, amount: 0 },
    ])
    drafts['account:13'].amount = 'abc'
    expect(buildPrizeWinners(candidates, drafts)).toBeNull()
  })

  it('names a matches-played candidate by player', () => {
    const players: PrizeCandidate[] = [
      { rank: 1, pointAccountId: null, playerId: 21, nickname: 'YOUR_USERNAME_1', points: 20 },
      { rank: 2, pointAccountId: null, playerId: 22, nickname: 'YOUR_USERNAME_2', points: 12 },
    ]
    const drafts = initialPrizeDrafts(players, 1)

    expect(Object.keys(drafts)).toEqual(['player:21', 'player:22'])
    expect(buildPrizeWinners(players, drafts)).toEqual([{ pointAccountId: null, playerId: 21, prize: null, amount: 0 }])
    expect(prizeEventMetric({ metric: 'GAMES' })).toBe('GAMES')
    // An event from before the choice.
    expect(prizeEventMetric({})).toBe('POINTS')
  })

  it('reads a ranking pasted from another app against the roster', () => {
    const roster = [
      { id: 1, nickname: 'YOUR_USERNAME_1' },
      { id: 2, nickname: 'YOUR_USERNAME_2' },
      { id: 3, nickname: 'YOUR_USERNAME_3' },
    ]
    const parsed = parseManualEntries(
      ['1위 YOUR_USERNAME_1 1,427', '', 'your_username_2\t359회', '3. YOUR_USERNAME_3 : 297', 'YOUR_OTHER 10', 'YOUR_USERNAME_1 5', '제목 줄'].join('\n'),
      roster,
    )

    expect(parsed.entries).toEqual([
      { playerId: 1, score: 1427 },
      { playerId: 2, score: 359 },
      { playerId: 3, score: 297 },
    ])
    expect(parsed.unknown).toEqual(['YOUR_OTHER'])
    expect(parsed.repeated).toEqual(['YOUR_USERNAME_1'])
    expect(parsed.unreadable).toEqual(['제목 줄'])
    expect(prizeEventMetric({ metric: 'MANUAL' })).toBe('MANUAL')
  })
})
