import { describe, expect, it } from 'vitest'
import { formatPolicyPoints, pointPolicyGroups } from '@/lib/point-policy'
import type { PointPolicyResponse } from '@/types/api'

const POLICY: PointPolicyResponse = {
  dailyLogin: 1,
  matchResult: { points: 1, dailyCap: 10 },
  matchConfirm: { points: 1, dailyCap: 10 },
  matchConfirmWindowHours: 48,
  predictionHit: { points: 1, dailyCap: 10 },
  noticeAction: 1,
  noticeCommentLike: { points: 1, dailyCap: 10 },
  boardPost: { points: 2, dailyCap: 3 },
  boardComment: { points: 1, dailyCap: 10 },
  boardLike: { points: 0, dailyCap: 10 },
}

describe('point policy', () => {
  it('lists every way to earn, grouped, with the caps in force', () => {
    const groups = pointPolicyGroups(POLICY)

    expect(groups.map((group) => group.title)).toEqual([
      '출석',
      '경기',
      '승부 예측',
      '공지사항',
      '게시판 (자유게시판·추천영상)',
    ])
    expect(groups.map((group) => group.rows.length)).toEqual([1, 2, 1, 4, 3])

    const boards = groups[4].rows
    expect(boards.map((row) => [row.name, row.points, row.limit])).toEqual([
      ['글 작성', 2, '하루 3개'],
      ['댓글', 1, '하루 10개'],
      ['좋아요', 0, '하루 10개'],
    ])
    expect(groups[1].rows[1].detail).toContain('48시간')
    expect(groups[2].rows[0].limit).toBe('하루 10p')
  })

  it('leaves no placeholder unfilled', () => {
    for (const row of pointPolicyGroups(POLICY).flatMap((group) => group.rows)) {
      expect(`${row.name} ${row.detail} ${row.limit}`).not.toMatch(/[{}]|points\.policy/)
    }
  })

  it('says when an activity earns nothing for now', () => {
    expect(formatPolicyPoints(2)).toBe('+2p')
    expect(formatPolicyPoints(0)).toBe('지금은 없음')
  })
})
