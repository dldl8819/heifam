import type { NoticeComment, NoticeListItem } from '@/types/api'

export type NoticeFilter = 'all' | 'unread'

export const NOTICE_COMMENT_MAX_LENGTH = 500

/** The full list is the default; members can narrow it to what they have not opened yet. */
export function filterNotices(notices: NoticeListItem[], filter: NoticeFilter): NoticeListItem[] {
  return filter === 'unread' ? notices.filter((notice) => !notice.read) : notices
}

/** An unread notice whose edit was announced again says so, which explains why it is bold again. */
export function showsRevisedMark(notice: NoticeListItem): boolean {
  return notice.revised && !notice.read
}

export type NoticeCommentThread = {
  comment: NoticeComment
  replies: NoticeComment[]
}

/**
 * Comments as they are drawn: each comment on the notice with its replies under it, both in the
 * order they were written. A reply whose comment is not in the list is shown as a comment of its
 * own, so nothing a member wrote goes missing.
 */
export function threadNoticeComments(comments: NoticeComment[]): NoticeCommentThread[] {
  const threads: NoticeCommentThread[] = []
  const byCommentId = new Map<number, NoticeCommentThread>()
  for (const comment of comments) {
    if (comment.parentId === undefined || comment.parentId === null) {
      const thread = { comment, replies: [] }
      byCommentId.set(comment.id, thread)
      threads.push(thread)
    }
  }
  for (const comment of comments) {
    if (comment.parentId === undefined || comment.parentId === null) {
      continue
    }
    const thread = byCommentId.get(comment.parentId)
    if (thread) {
      thread.replies.push(comment)
    } else {
      threads.push({ comment, replies: [] })
    }
  }
  return threads.sort((left, right) => left.comment.id - right.comment.id)
}

/** How many comments people can read: an emptied place is not one. */
export function countNoticeComments(comments: NoticeComment[]): number {
  return comments.filter((comment) => !comment.deleted).length
}

/** Returns the error message key for a comment draft, or null when it can be sent. */
export function validateNoticeComment(draft: string): 'commentRequired' | 'commentTooLong' | null {
  const text = draft.trim()
  if (text.length === 0) {
    return 'commentRequired'
  }
  if (text.length > NOTICE_COMMENT_MAX_LENGTH) {
    return 'commentTooLong'
  }
  return null
}
