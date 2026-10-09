import type { NoticeVote, NoticeVoteChoice } from '@/types/api'

export type NoticeVoteSummary = {
  total: number
  agreePercent: number
  disagreePercent: number
}

/** The two sides of a vote in whole percent that add up to 100; both 0 while nobody has voted. */
export function summarizeNoticeVote(vote: Pick<NoticeVote, 'agreeCount' | 'disagreeCount'>): NoticeVoteSummary {
  const agree = Math.max(0, vote.agreeCount)
  const disagree = Math.max(0, vote.disagreeCount)
  const total = agree + disagree
  if (total === 0) {
    return { total: 0, agreePercent: 0, disagreePercent: 0 }
  }
  const agreePercent = Math.round((agree / total) * 100)
  return { total, agreePercent, disagreePercent: 100 - agreePercent }
}

/** What pressing a side does: the side already chosen takes the vote back, the other one changes it. */
export function nextNoticeVote(
  current: NoticeVoteChoice | null | undefined,
  pressed: NoticeVoteChoice
): NoticeVoteChoice | null {
  return current === pressed ? null : pressed
}
