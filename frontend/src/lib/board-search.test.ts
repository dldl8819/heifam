import { describe, expect, it } from 'vitest'
import {
  BOARD_SEARCH_MAX_LENGTH,
  boardSearchPage,
  boardSearchResultHref,
  boardSearchUrl,
  validateBoardSearchQuery,
} from './board-search'

describe('validateBoardSearchQuery', () => {
  it('takes a word of two letters or more', () => {
    expect(validateBoardSearchQuery('룰')).not.toBeNull()
    expect(validateBoardSearchQuery('공지')).toBeNull()
    expect(validateBoardSearchQuery('  공지  ')).toBeNull()
    expect(validateBoardSearchQuery('x'.repeat(BOARD_SEARCH_MAX_LENGTH))).toBeNull()
  })

  it('refuses nothing, a single letter and blanks', () => {
    for (const query of ['', ' ', ' a ']) {
      expect(validateBoardSearchQuery(query)?.key).toBe('queryTooShort')
    }
  })

  it('refuses a word longer than the limit', () => {
    expect(validateBoardSearchQuery('x'.repeat(BOARD_SEARCH_MAX_LENGTH + 1))?.key).toBe('queryTooLong')
  })
})

describe('boardSearchUrl', () => {
  it('puts the trimmed word into the address, safely', () => {
    expect(boardSearchUrl('  rules  ')).toBe('/boards/search?q=rules')
    expect(boardSearchUrl('a&b=c d')).toBe('/boards/search?q=a%26b%3Dc+d')
    expect(new URLSearchParams(boardSearchUrl('프전 룰?').split('?')[1]).get('q')).toBe('프전 룰?')
  })

  it('names a later page and leaves the first unnamed', () => {
    expect(boardSearchUrl('rules', 1)).toBe('/boards/search?q=rules')
    expect(boardSearchUrl('rules', 3)).toBe('/boards/search?q=rules&page=3')
  })
})

describe('boardSearchPage', () => {
  it('reads a page number and falls back to the first', () => {
    expect(boardSearchPage('4')).toBe(4)
    for (const value of [null, undefined, '', '0', '-2', 'abc']) {
      expect(boardSearchPage(value)).toBe(1)
    }
  })
})

describe('boardSearchResultHref', () => {
  it('leads to the notice or to the post on the free board', () => {
    expect(boardSearchResultHref({ kind: 'NOTICE', id: 12 })).toBe('/notices/12')
    expect(boardSearchResultHref({ kind: 'FREE', id: 7 })).toBe('/boards/free/7')
    expect(boardSearchResultHref({ kind: 'VIDEO', id: 3 })).toBe('/boards/video/3')
  })
})
