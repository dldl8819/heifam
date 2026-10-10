import { t } from '@/lib/i18n'
import type { PointPolicyResponse } from '@/types/api'

export type PointPolicyRow = {
  name: string
  // Empty when the name says it all.
  detail: string
  points: number
  limit: string
}

export type PointPolicyGroup = {
  title: string
  rows: PointPolicyRow[]
}

function row(key: string, points: number, params: Record<string, number> = {}): PointPolicyRow {
  return {
    name: t(`points.policy.items.${key}.name`),
    detail: t(`points.policy.items.${key}.detail`, params),
    points,
    limit: t(`points.policy.items.${key}.limit`, params),
  }
}

/** The ways to earn points, grouped as the policy page lists them, with the amounts and caps in force. */
export function pointPolicyGroups(policy: PointPolicyResponse): PointPolicyGroup[] {
  return [
    {
      title: t('points.policy.groups.attendance'),
      rows: [row('dailyLogin', policy.dailyLogin)],
    },
    {
      title: t('points.policy.groups.matches'),
      rows: [
        row('matchResult', policy.matchResult.points, { cap: policy.matchResult.dailyCap }),
        row('matchConfirm', policy.matchConfirm.points, {
          cap: policy.matchConfirm.dailyCap,
          hours: policy.matchConfirmWindowHours,
        }),
      ],
    },
    {
      title: t('points.policy.groups.predictions'),
      rows: [row('predictionHit', policy.predictionHit.points, { cap: policy.predictionHit.dailyCap })],
    },
    {
      title: t('points.policy.groups.notices'),
      rows: [
        row('noticeRead', policy.noticeAction),
        row('noticeLike', policy.noticeAction),
        row('noticeComment', policy.noticeAction),
        row('noticeCommentLike', policy.noticeCommentLike.points, { cap: policy.noticeCommentLike.dailyCap }),
      ],
    },
    {
      title: t('points.policy.groups.boards'),
      rows: [
        row('boardPost', policy.boardPost.points, { cap: policy.boardPost.dailyCap }),
        row('boardComment', policy.boardComment.points, { cap: policy.boardComment.dailyCap }),
        row('boardLike', policy.boardLike.points, { cap: policy.boardLike.dailyCap }),
      ],
    },
  ]
}

/** What a row earns: the points, or that the activity earns nothing for now. */
export function formatPolicyPoints(points: number): string {
  return points > 0 ? t('points.policy.points', { points }) : t('points.policy.off')
}
