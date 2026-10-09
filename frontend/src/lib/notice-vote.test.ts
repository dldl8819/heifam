import { describe, expect, it } from 'vitest'

import {
  NOTICE_VOTE_MAX_OPTIONS,
  NOTICE_VOTE_OPTION_MAX_LENGTH,
  cleanNoticeVoteOptions,
  editPreviewNoticeVote,
  noticeVotePercents,
  previewNoticeVote,
  sameNoticeVoteOptions,
  storedNoticeVote,
  validateNewNoticeVoteOption,
  validateNoticeVoteOptions,
} from '@/lib/notice-vote'
import type { NoticeVote } from '@/types/api'

describe('noticeVotePercents', () => {
  it('has nothing to show while nobody has voted', () => {
    expect(noticeVotePercents([0, 0])).toEqual([0, 0])
    expect(noticeVotePercents([0, 0, 0])).toEqual([0, 0, 0])
    expect(noticeVotePercents([])).toEqual([])
  })

  it('gives each option its share in whole percent', () => {
    expect(noticeVotePercents([7, 3])).toEqual([70, 30])
    expect(noticeVotePercents([5, 0])).toEqual([100, 0])
    expect(noticeVotePercents([0, 4])).toEqual([0, 100])
    expect(noticeVotePercents([2, 1, 1])).toEqual([50, 25, 25])
  })

  it('always adds up to 100, also when the shares do not divide evenly', () => {
    for (const counts of [[1, 2], [2, 1], [1, 5], [33, 34], [1, 199], [1, 1, 1], [3, 2, 2], [1, 1, 1, 1, 1, 1, 1], [5, 3, 1, 1, 0, 0, 7]]) {
      const percents = noticeVotePercents(counts)

      expect(percents.reduce((sum, percent) => sum + percent, 0)).toBe(100)
      expect(percents).toHaveLength(counts.length)
    }
    expect(noticeVotePercents([1, 2])).toEqual([33, 67])
    // Three equal shares cannot all be 33: the first takes the point left over.
    expect(noticeVotePercents([1, 1, 1])).toEqual([34, 33, 33])
  })

  it('gives an option nobody chose no share, and never a negative one', () => {
    expect(noticeVotePercents([4, 0, 1])).toEqual([80, 0, 20])
    expect(noticeVotePercents([-3, 2])).toEqual([0, 100])
    expect(noticeVotePercents([Number.NaN, 1])).toEqual([0, 100])
  })
})

describe('vote options in the form', () => {
  it('sends them trimmed and without the rows left empty', () => {
    expect(cleanNoticeVoteOptions([' 30분 ', '', '25분', '   ', '24분'])).toEqual(['30분', '25분', '24분'])
  })

  it('takes two to ten different options', () => {
    expect(validateNoticeVoteOptions(['찬성', '반대'])).toBeNull()
    expect(validateNoticeVoteOptions(['30분', '25분', '', '24분'])).toBeNull()
    expect(validateNoticeVoteOptions(Array.from({ length: NOTICE_VOTE_MAX_OPTIONS }, (_, index) => `option ${index}`))).toBeNull()
  })

  it('asks for at least two and refuses more than a vote holds', () => {
    expect(validateNoticeVoteOptions([])?.key).toBe('voteOptionsTooFew')
    expect(validateNoticeVoteOptions(['30분', ' ', ''])?.key).toBe('voteOptionsTooFew')
    expect(
      validateNoticeVoteOptions(Array.from({ length: NOTICE_VOTE_MAX_OPTIONS + 1 }, (_, index) => `option ${index}`))?.key
    ).toBe('voteOptionsTooMany')
  })

  it('refuses an option that is too long or there twice, whatever its case', () => {
    expect(validateNoticeVoteOptions(['ok', 'x'.repeat(NOTICE_VOTE_OPTION_MAX_LENGTH + 1)])?.key).toBe('voteOptionTooLong')
    expect(validateNoticeVoteOptions(['ok', 'x'.repeat(NOTICE_VOTE_OPTION_MAX_LENGTH)])).toBeNull()
    expect(validateNoticeVoteOptions(['30분', ' 30분 '])?.key).toBe('voteOptionDuplicate')
    expect(validateNoticeVoteOptions(['Yes', 'no', 'YES'])?.key).toBe('voteOptionDuplicate')
  })

  it('knows when an edit left the options as they were', () => {
    expect(sameNoticeVoteOptions(['30분', '25분'], [' 30분', '25분 ', ''])).toBe(true)
    expect(sameNoticeVoteOptions(['30분', '25분'], ['25분', '30분'])).toBe(false)
    expect(sameNoticeVoteOptions(['30분', '25분'], ['30분', '25분', '24분'])).toBe(false)
    expect(sameNoticeVoteOptions(['Yes', 'No'], ['yes', 'No'])).toBe(false)
  })
})

describe('validateNewNoticeVoteOption', () => {
  const existing = ['30분', '25분']

  it('takes an option the vote does not have yet', () => {
    expect(validateNewNoticeVoteOption(' 24분 ', existing)).toBeNull()
  })

  it('refuses an empty one, one too long, one already there and one too many', () => {
    expect(validateNewNoticeVoteOption('   ', existing)?.key).toBe('voteOptionRequired')
    expect(validateNewNoticeVoteOption('x'.repeat(NOTICE_VOTE_OPTION_MAX_LENGTH + 1), existing)?.key).toBe('voteOptionTooLong')
    expect(validateNewNoticeVoteOption(' 30분 ', existing)?.key).toBe('voteOptionDuplicate')
    expect(validateNewNoticeVoteOption('yes', ['Yes', 'No'])?.key).toBe('voteOptionDuplicate')
    expect(
      validateNewNoticeVoteOption('one more', Array.from({ length: NOTICE_VOTE_MAX_OPTIONS }, (_, index) => `option ${index}`))?.key
    ).toBe('voteOptionsTooMany')
  })
})

describe('previewNoticeVote', () => {
  it('shows the options typed so far with nobody having voted', () => {
    const vote = previewNoticeVote([' 30분 ', '', '25분'], { anonymous: true, allowAdditions: true })

    expect(vote.status).toBe('OPEN')
    expect(vote.options.map((option) => [option.label, option.count, option.mine])).toEqual([
      ['30분', 0, false],
      ['25분', 0, false],
    ])
    expect(vote.totalVoters).toBe(0)
    expect(vote.anonymous).toBe(true)
    expect(vote.allowAdditions).toBe(true)
    // A preview is looked at, not used: nothing can be added to it or taken from it.
    expect(vote.canAddOption).toBe(false)
    expect(vote.canRemoveOptions).toBe(false)
    expect(vote.options.every((option) => option.id < 0)).toBe(true)
  })

  it('carries the status and whether names will show', () => {
    const closed = previewNoticeVote(['a', 'b'], { status: 'CLOSED', anonymous: false, allowAdditions: false })

    expect(closed.status).toBe('CLOSED')
    expect(closed.anonymous).toBe(false)
    expect(closed.options.map((option) => option.voters)).toEqual([[], []])
    expect(previewNoticeVote(['a', 'b'], { status: 'NONE', anonymous: true, allowAdditions: false }).status).toBe('OPEN')
    expect(previewNoticeVote(['a', 'b'], { anonymous: true, allowAdditions: false }).options.map((option) => option.voters)).toEqual([
      null,
      null,
    ])
  })
})

const LIVE: NoticeVote = {
  status: 'OPEN',
  anonymous: false,
  allowAdditions: true,
  canAddOption: true,
  canRemoveOptions: true,
  totalVoters: 3,
  myOptionId: 21,
  options: [
    { id: 21, label: '30분', count: 2, mine: true, voters: ['YOUR_USERNAME', null] },
    { id: 22, label: '25분', count: 1, mine: false, voters: ['OtherUser'] },
  ],
}

describe('storedNoticeVote', () => {
  it('reads the vote the notice shows', () => {
    expect(storedNoticeVote({ vote: LIVE })).toEqual({
      options: ['30분', '25분'],
      totalVoters: 3,
      anonymous: false,
      allowAdditions: true,
    })
  })

  it('reads a vote that was taken off the notice but kept', () => {
    const kept = { options: ['찬성', '반대'], totalVoters: 9, anonymous: true, allowAdditions: false }

    expect(storedNoticeVote({ vote: null, voteKept: kept })).toEqual(kept)
  })

  it('has nothing for a notice that never had a vote', () => {
    expect(storedNoticeVote({})).toBeNull()
    expect(storedNoticeVote({ vote: null, voteKept: null })).toBeNull()
  })
})

describe('editPreviewNoticeVote', () => {
  it('keeps the counts while the options are left as they are', () => {
    const vote = editPreviewNoticeVote(LIVE, [' 30분', '25분 ', ''], { status: 'CLOSED', anonymous: false, allowAdditions: false })

    expect(vote.status).toBe('CLOSED')
    expect(vote.options.map((option) => [option.id, option.count, option.mine])).toEqual([
      [21, 2, true],
      [22, 1, false],
    ])
    expect(vote.options[0].voters).toEqual(['YOUR_USERNAME', null])
    expect(vote.allowAdditions).toBe(false)
    // Looked at in the form, not used.
    expect(vote.canAddOption).toBe(false)
    expect(vote.canRemoveOptions).toBe(false)
  })

  it('hides the names as soon as the form says anonymous', () => {
    const vote = editPreviewNoticeVote(LIVE, ['30분', '25분'], { status: 'OPEN', anonymous: true, allowAdditions: true })

    expect(vote.anonymous).toBe(true)
    expect(vote.options.map((option) => option.voters)).toEqual([null, null])
    expect(vote.options.map((option) => option.count)).toEqual([2, 1])
  })

  it('starts empty on the options typed once they differ, or when the notice has no vote yet', () => {
    const changed = editPreviewNoticeVote(LIVE, ['30분', '25분', '24분'], { status: 'OPEN', anonymous: true, allowAdditions: false })
    const fresh = editPreviewNoticeVote(null, ['찬성', '반대'], { status: 'OPEN', anonymous: true, allowAdditions: false })

    expect(changed.options.map((option) => [option.label, option.count])).toEqual([
      ['30분', 0],
      ['25분', 0],
      ['24분', 0],
    ])
    expect(changed.totalVoters).toBe(0)
    expect(fresh.options.map((option) => option.label)).toEqual(['찬성', '반대'])
  })
})
