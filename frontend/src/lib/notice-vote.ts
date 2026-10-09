/** Small rules of a notice's vote; the backend (NoticeVotes) enforces the same limits. */

import type { NoticeDetail, NoticeVote, NoticeVoteKept, NoticeVoteStatus } from '@/types/api'

export const NOTICE_VOTE_MIN_OPTIONS = 2
export const NOTICE_VOTE_MAX_OPTIONS = 10
export const NOTICE_VOTE_OPTION_MAX_LENGTH = 50

/** What a vote starts with in the form: for or against, ready to be typed over. */
export const NOTICE_VOTE_DEFAULT_OPTIONS = ['찬성', '반대']

export type NoticeVoteProblem = {
  // The name of the message under "notices.posts." that says what is wrong.
  key: 'voteOptionsTooFew' | 'voteOptionsTooMany' | 'voteOptionTooLong' | 'voteOptionDuplicate' | 'voteOptionRequired'
  min: number
  max: number
  length: number
}

function problem(key: NoticeVoteProblem['key']): NoticeVoteProblem {
  return { key, min: NOTICE_VOTE_MIN_OPTIONS, max: NOTICE_VOTE_MAX_OPTIONS, length: NOTICE_VOTE_OPTION_MAX_LENGTH }
}

/** The options as they are sent: trimmed, the rows left empty dropped. */
export function cleanNoticeVoteOptions(drafts: string[]): string[] {
  return drafts.map((draft) => draft.trim()).filter((draft) => draft.length > 0)
}

/** What stops a vote's options from being saved, or null when they can be. */
export function validateNoticeVoteOptions(drafts: string[]): NoticeVoteProblem | null {
  const options = cleanNoticeVoteOptions(drafts)
  if (options.length < NOTICE_VOTE_MIN_OPTIONS) {
    return problem('voteOptionsTooFew')
  }
  if (options.length > NOTICE_VOTE_MAX_OPTIONS) {
    return problem('voteOptionsTooMany')
  }
  if (options.some((option) => option.length > NOTICE_VOTE_OPTION_MAX_LENGTH)) {
    return problem('voteOptionTooLong')
  }
  // The same option twice, in whatever case, would split its votes.
  if (new Set(options.map((option) => option.toLowerCase())).size !== options.length) {
    return problem('voteOptionDuplicate')
  }
  return null
}

/** What stops one more option from being added to a vote that already has these. */
export function validateNewNoticeVoteOption(draft: string, existing: string[]): NoticeVoteProblem | null {
  const label = draft.trim()
  if (label.length === 0) {
    return problem('voteOptionRequired')
  }
  if (label.length > NOTICE_VOTE_OPTION_MAX_LENGTH) {
    return problem('voteOptionTooLong')
  }
  if (existing.length >= NOTICE_VOTE_MAX_OPTIONS) {
    return problem('voteOptionsTooMany')
  }
  if (existing.some((option) => option.trim().toLowerCase() === label.toLowerCase())) {
    return problem('voteOptionDuplicate')
  }
  return null
}

/** Whether two lists name the same options in the same order, as they would be sent. */
export function sameNoticeVoteOptions(left: string[], right: string[]): boolean {
  const a = cleanNoticeVoteOptions(left)
  const b = cleanNoticeVoteOptions(right)
  return a.length === b.length && a.every((option, index) => option === b[index])
}

/**
 * Each option's share in whole percent. The shares add up to 100 (the points lost to rounding go
 * to the options that were cut the most), or are all 0 while nobody has voted.
 */
export function noticeVotePercents(counts: number[]): number[] {
  const safe = counts.map((count) => (Number.isFinite(count) && count > 0 ? count : 0))
  const total = safe.reduce((sum, count) => sum + count, 0)
  if (total === 0) {
    return safe.map(() => 0)
  }
  const exact = safe.map((count) => (count / total) * 100)
  const percents = exact.map(Math.floor)
  const missing = 100 - percents.reduce((sum, percent) => sum + percent, 0)
  const byRemainder = exact
    .map((value, index) => ({ index, remainder: value - Math.floor(value) }))
    .sort((left, right) => right.remainder - left.remainder || left.index - right.index)
  for (let step = 0; step < missing; step += 1) {
    percents[byRemainder[step % byRemainder.length].index] += 1
  }
  return percents
}

/** The vote as the form shows it before it exists: the options typed so far, nobody having voted. */
export function previewNoticeVote(
  drafts: string[],
  settings: { status?: NoticeVoteStatus; anonymous: boolean; allowAdditions: boolean }
): NoticeVote {
  return {
    status: settings.status === 'CLOSED' ? 'CLOSED' : 'OPEN',
    anonymous: settings.anonymous,
    allowAdditions: settings.allowAdditions,
    canAddOption: false,
    canRemoveOptions: false,
    totalVoters: 0,
    myOptionId: null,
    options: cleanNoticeVoteOptions(drafts).map((label, index) => ({
      // Not ids of anything: the preview cannot be voted on.
      id: -(index + 1),
      label,
      count: 0,
      mine: false,
      voters: settings.anonymous ? null : [],
    })),
  }
}

/**
 * What a notice has stored about its vote, whether it shows the vote or keeps one that was taken
 * off it: what the edit form starts from. Null for a notice that never had a vote.
 */
export function storedNoticeVote(notice: Pick<NoticeDetail, 'vote' | 'voteKept'>): NoticeVoteKept | null {
  if (notice.vote) {
    return {
      options: (notice.vote.options ?? []).map((option) => option.label),
      totalVoters: notice.vote.totalVoters ?? 0,
      anonymous: notice.vote.anonymous ?? true,
      allowAdditions: notice.vote.allowAdditions ?? false,
    }
  }
  return notice.voteKept ?? null
}

/**
 * The vote as the edit form shows it: the notice's own vote with its counts while the options are
 * left as they are, otherwise an empty one on the options typed. Either way with the settings
 * chosen in the form, and with nothing in it to add or take away.
 */
export function editPreviewNoticeVote(
  current: NoticeVote | null | undefined,
  drafts: string[],
  settings: { status?: NoticeVoteStatus; anonymous: boolean; allowAdditions: boolean }
): NoticeVote {
  const options = current?.options ?? []
  if (!current || !sameNoticeVoteOptions(drafts, options.map((option) => option.label))) {
    return previewNoticeVote(drafts, settings)
  }
  return {
    ...current,
    status: settings.status === 'CLOSED' ? 'CLOSED' : 'OPEN',
    anonymous: settings.anonymous,
    allowAdditions: settings.allowAdditions,
    canAddOption: false,
    canRemoveOptions: false,
    options: options.map((option) => ({ ...option, voters: settings.anonymous ? null : (option.voters ?? []) })),
  }
}
