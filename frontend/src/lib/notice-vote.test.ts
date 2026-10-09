import { describe, expect, it } from 'vitest'

import { nextNoticeVote, summarizeNoticeVote } from '@/lib/notice-vote'

describe('summarizeNoticeVote', () => {
  it('has nothing to show while nobody has voted', () => {
    expect(summarizeNoticeVote({ agreeCount: 0, disagreeCount: 0 })).toEqual({
      total: 0,
      agreePercent: 0,
      disagreePercent: 0,
    })
  })

  it('gives both sides in whole percent', () => {
    expect(summarizeNoticeVote({ agreeCount: 7, disagreeCount: 3 })).toEqual({
      total: 10,
      agreePercent: 70,
      disagreePercent: 30,
    })
    expect(summarizeNoticeVote({ agreeCount: 5, disagreeCount: 0 })).toEqual({
      total: 5,
      agreePercent: 100,
      disagreePercent: 0,
    })
    expect(summarizeNoticeVote({ agreeCount: 0, disagreeCount: 4 })).toEqual({
      total: 4,
      agreePercent: 0,
      disagreePercent: 100,
    })
  })

  it('always adds up to 100, also when the shares do not divide evenly', () => {
    for (const [agreeCount, disagreeCount] of [[1, 2], [2, 1], [1, 5], [5, 1], [33, 34], [1, 199]]) {
      const summary = summarizeNoticeVote({ agreeCount, disagreeCount })

      expect(summary.agreePercent + summary.disagreePercent).toBe(100)
      expect(summary.total).toBe(agreeCount + disagreeCount)
    }
    expect(summarizeNoticeVote({ agreeCount: 1, disagreeCount: 2 })).toMatchObject({ agreePercent: 33, disagreePercent: 67 })
  })
})

describe('nextNoticeVote', () => {
  it('casts the pressed side when there is no vote yet', () => {
    expect(nextNoticeVote(null, 'AGREE')).toBe('AGREE')
    expect(nextNoticeVote(undefined, 'DISAGREE')).toBe('DISAGREE')
  })

  it('changes to the other side', () => {
    expect(nextNoticeVote('AGREE', 'DISAGREE')).toBe('DISAGREE')
    expect(nextNoticeVote('DISAGREE', 'AGREE')).toBe('AGREE')
  })

  it('takes the vote back when the chosen side is pressed again', () => {
    expect(nextNoticeVote('AGREE', 'AGREE')).toBeNull()
    expect(nextNoticeVote('DISAGREE', 'DISAGREE')).toBeNull()
  })
})
