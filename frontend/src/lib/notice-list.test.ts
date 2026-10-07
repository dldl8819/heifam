import { describe, expect, it } from 'vitest'
import {
  countNoticeComments,
  filterNotices,
  NOTICE_COMMENT_MAX_LENGTH,
  showsRevisedMark,
  threadNoticeComments,
  validateNoticeComment,
} from './notice-list'
import type { NoticeComment, NoticeListItem } from '@/types/api'

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

function comment(id: number, parentId?: number | null, deleted = false): NoticeComment {
  return {
    id,
    parentId,
    content: deleted ? '' : `YOUR_COMMENT_${id}`,
    createdAt: '2026-01-01T00:00:00Z',
    deleted,
    mine: false,
    canDelete: false,
  }
}

describe('threadNoticeComments', () => {
  const shape = (comments: NoticeComment[]) =>
    threadNoticeComments(comments).map((thread) => [thread.comment.id, thread.replies.map((reply) => reply.id)])

  it('puts replies under the comment they answer, in the order written', () => {
    expect(shape([comment(1), comment(2), comment(3, 1), comment(4, 2), comment(5, 1)])).toEqual([
      [1, [3, 5]],
      [2, [4]],
    ])
  })

  it('treats comments from before replies existed as comments on the notice', () => {
    const old = [{ id: 1, content: 'a', createdAt: '', mine: false, canDelete: false }, comment(2, null)]

    expect(shape(old)).toEqual([[1, []], [2, []]])
  })

  it('keeps an emptied comment in place for its replies', () => {
    const threads = threadNoticeComments([comment(1, null, true), comment(2, 1)])

    expect(threads).toHaveLength(1)
    expect(threads[0].comment.deleted).toBe(true)
    expect(threads[0].replies.map((reply) => reply.id)).toEqual([2])
  })

  it('still shows a reply whose comment is not in the list', () => {
    expect(shape([comment(1), comment(7, 99), comment(8)])).toEqual([[1, []], [7, []], [8, []]])
  })

  it('has nothing to show for no comments', () => {
    expect(threadNoticeComments([])).toEqual([])
  })
})

describe('countNoticeComments', () => {
  it('counts comments and replies but not emptied places', () => {
    expect(countNoticeComments([comment(1, null, true), comment(2, 1), comment(3), comment(4, 3)])).toBe(3)
    expect(countNoticeComments([])).toBe(0)
  })
})
