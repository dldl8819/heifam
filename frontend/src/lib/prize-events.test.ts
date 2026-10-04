import { describe, expect, it } from 'vitest'
import { buildPrizeWinners, initialPrizeDrafts, parsePrizeAmount } from '@/lib/prize-events'
import type { PrizeCandidate } from '@/types/api'

const candidates: PrizeCandidate[] = [
  { rank: 1, pointAccountId: 11, nickname: 'YOUR_USERNAME_1', points: 9 },
  { rank: 2, pointAccountId: 12, nickname: 'YOUR_USERNAME_2', points: 7 },
  { rank: 2, pointAccountId: 13, nickname: 'YOUR_USERNAME_3', points: 7 },
]

describe('prize event helpers', () => {
  it('ticks the first candidates up to the winner count', () => {
    const drafts = initialPrizeDrafts(candidates, 2)

    expect(drafts[11].selected).toBe(true)
    expect(drafts[12].selected).toBe(true)
    expect(drafts[13].selected).toBe(false)
  })

  it('reads amounts as whole won', () => {
    expect(parsePrizeAmount('15,000')).toBe(15000)
    expect(parsePrizeAmount(' ')).toBe(0)
    expect(parsePrizeAmount('1.5')).toBeNull()
    expect(parsePrizeAmount('-3')).toBeNull()
  })

  it('sends ticked candidates in place order', () => {
    const drafts = initialPrizeDrafts(candidates, 2)
    drafts[11] = { selected: true, prize: ' 커피 ', amount: '5000' }
    drafts[12] = { selected: false, prize: '', amount: '' }
    drafts[13] = { selected: true, prize: '', amount: '' }

    expect(buildPrizeWinners(candidates, drafts)).toEqual([
      { pointAccountId: 11, prize: '커피', amount: 5000 },
      { pointAccountId: 13, prize: null, amount: 0 },
    ])
    drafts[13].amount = 'abc'
    expect(buildPrizeWinners(candidates, drafts)).toBeNull()
  })
})
