import { describe, expect, it } from 'vitest'

import {
  BOARD_COMMENT_MAX_LENGTH,
  BOARD_CONTENT_MAX_LENGTH,
  BOARD_TITLE_MAX_LENGTH,
  boardPageCount,
  validateBoardComment,
  validateBoardPost,
} from '@/lib/boards'

describe('validateBoardPost', () => {
  it('lets a post with a title and a text through', () => {
    expect(validateBoardPost('YOUR_TITLE', 'YOUR_CONTENT')).toBeNull()
    expect(validateBoardPost('x'.repeat(BOARD_TITLE_MAX_LENGTH), 'y'.repeat(BOARD_CONTENT_MAX_LENGTH))).toBeNull()
  })

  it('needs a title and a text, and looks at the title first', () => {
    expect(validateBoardPost('  ', 'YOUR_CONTENT')?.key).toBe('titleRequired')
    expect(validateBoardPost('YOUR_TITLE', '\n ')?.key).toBe('contentRequired')
    expect(validateBoardPost('', '')?.key).toBe('titleRequired')
  })

  it('names the limit that was passed', () => {
    expect(validateBoardPost('x'.repeat(BOARD_TITLE_MAX_LENGTH + 1), 'YOUR_CONTENT')).toEqual({
      key: 'titleTooLong',
      max: BOARD_TITLE_MAX_LENGTH,
    })
    expect(validateBoardPost('YOUR_TITLE', 'y'.repeat(BOARD_CONTENT_MAX_LENGTH + 1))).toEqual({
      key: 'contentTooLong',
      max: BOARD_CONTENT_MAX_LENGTH,
    })
  })

  it('counts length after trimming, as the backend does', () => {
    expect(validateBoardPost(` ${'x'.repeat(BOARD_TITLE_MAX_LENGTH)} `, ` ${'y'.repeat(BOARD_CONTENT_MAX_LENGTH)} `)).toBeNull()
  })
})

describe('validateBoardComment', () => {
  it('needs some text within the limit', () => {
    expect(validateBoardComment('   ')?.key).toBe('commentRequired')
    expect(validateBoardComment('z'.repeat(BOARD_COMMENT_MAX_LENGTH + 1))?.key).toBe('commentTooLong')
    expect(validateBoardComment(` ${'z'.repeat(BOARD_COMMENT_MAX_LENGTH)} `)).toBeNull()
  })
})

describe('boardPageCount', () => {
  it('gives an empty board its one page', () => {
    expect(boardPageCount(0, 20)).toBe(1)
    expect(boardPageCount(-3, 20)).toBe(1)
    expect(boardPageCount(5, 0)).toBe(1)
  })

  it('rounds up to whole pages', () => {
    expect(boardPageCount(1, 20)).toBe(1)
    expect(boardPageCount(20, 20)).toBe(1)
    expect(boardPageCount(21, 20)).toBe(2)
    expect(boardPageCount(100, 20)).toBe(5)
  })
})
