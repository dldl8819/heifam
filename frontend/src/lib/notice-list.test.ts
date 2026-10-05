import { describe, expect, it } from 'vitest'
import { filterNotices, NOTICE_COMMENT_MAX_LENGTH, showsRevisedMark, validateNoticeComment } from './notice-list'
import type { NoticeListItem } from '@/types/api'

function notice(id: number, read: boolean, revised = false): NoticeListItem {
  return {
    id,
    title: `YOUR_TITLE_${id}`,
    createdAt: '2026-01-01T00:00:00Z',
    adminOnly: false,
    read,
    revised,
    likeCount: 0,
    commentCount: 0,
  }
}

describe('filterNotices', () => {
  const notices = [notice(3, false), notice(2, true), notice(1, false)]

  it('keeps every notice by default', () => {
    expect(filterNotices(notices, 'all').map((item) => item.id)).toEqual([3, 2, 1])
  })

  it('narrows to the notices not opened yet', () => {
    expect(filterNotices(notices, 'unread').map((item) => item.id)).toEqual([3, 1])
  })
})

describe('showsRevisedMark', () => {
  it('marks a re-announced notice until it is read again', () => {
    expect(showsRevisedMark(notice(1, false, true))).toBe(true)
    expect(showsRevisedMark(notice(1, true, true))).toBe(false)
  })

  it('leaves notices that were never announced again unmarked', () => {
    expect(showsRevisedMark(notice(1, false))).toBe(false)
  })

  it('keeps a re-announced notice in the unread filter', () => {
    const notices = [notice(2, false, true), notice(1, true)]

    expect(filterNotices(notices, 'unread').map((item) => item.id)).toEqual([2])
  })
})

describe('validateNoticeComment', () => {
  it('needs some text', () => {
    expect(validateNoticeComment('   ')).toBe('commentRequired')
  })

  it('caps the length after trimming', () => {
    expect(validateNoticeComment(` ${'x'.repeat(NOTICE_COMMENT_MAX_LENGTH)} `)).toBeNull()
    expect(validateNoticeComment('x'.repeat(NOTICE_COMMENT_MAX_LENGTH + 1))).toBe('commentTooLong')
  })
})
