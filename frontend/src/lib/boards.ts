/** Limits and small rules of the member boards; the backend (BoardService) enforces the same limits. */

import { parseYouTubeVideoId } from '@/lib/youtube'

export const BOARD_TITLE_MAX_LENGTH = 200
export const BOARD_CONTENT_MAX_LENGTH = 5000
export const BOARD_COMMENT_MAX_LENGTH = 500

export type BoardFormProblem = {
  // The name of the message under "boards." that says what is wrong.
  key:
    | 'titleRequired'
    | 'titleTooLong'
    | 'contentRequired'
    | 'contentTooLong'
    | 'commentRequired'
    | 'commentTooLong'
    | 'videoRequired'
    | 'videoInvalid'
  max: number
}

/**
 * What stops a post from being saved, or null when it can be. Lengths are counted after trimming.
 * On the video board the video is the post, so its text may be left empty.
 */
export function validateBoardPost(
  title: string,
  content: string,
  options: { contentOptional?: boolean } = {}
): BoardFormProblem | null {
  const cleanTitle = title.trim()
  const cleanContent = content.trim()
  if (cleanTitle.length === 0) {
    return { key: 'titleRequired', max: BOARD_TITLE_MAX_LENGTH }
  }
  if (cleanTitle.length > BOARD_TITLE_MAX_LENGTH) {
    return { key: 'titleTooLong', max: BOARD_TITLE_MAX_LENGTH }
  }
  if (cleanContent.length === 0 && !options.contentOptional) {
    return { key: 'contentRequired', max: BOARD_CONTENT_MAX_LENGTH }
  }
  if (cleanContent.length > BOARD_CONTENT_MAX_LENGTH) {
    return { key: 'contentTooLong', max: BOARD_CONTENT_MAX_LENGTH }
  }
  return null
}

/** What stops a video post's link from being saved: it must be there and name one YouTube video. */
export function validateBoardVideoLink(link: string): BoardFormProblem | null {
  if (link.trim().length === 0) {
    return { key: 'videoRequired', max: 0 }
  }
  return parseYouTubeVideoId(link) ? null : { key: 'videoInvalid', max: 0 }
}

export function validateBoardComment(draft: string): BoardFormProblem | null {
  const text = draft.trim()
  if (text.length === 0) {
    return { key: 'commentRequired', max: BOARD_COMMENT_MAX_LENGTH }
  }
  if (text.length > BOARD_COMMENT_MAX_LENGTH) {
    return { key: 'commentTooLong', max: BOARD_COMMENT_MAX_LENGTH }
  }
  return null
}

/** How many pages a board has; an empty board still has its first page. */
export function boardPageCount(total: number, pageSize: number): number {
  if (!Number.isFinite(total) || !Number.isFinite(pageSize) || pageSize <= 0 || total <= 0) {
    return 1
  }
  return Math.ceil(total / pageSize)
}
