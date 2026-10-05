import type { NoticeListItem } from '@/types/api'

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
