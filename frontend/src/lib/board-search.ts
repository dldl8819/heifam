/** Small rules of the search across the notices and the free board; the backend (BoardSearchService) enforces the same limits. */

import type { BoardSearchItem } from '@/types/api'

export const BOARD_SEARCH_MIN_LENGTH = 2
export const BOARD_SEARCH_MAX_LENGTH = 50

export type BoardSearchProblem = {
  // The name of the message under "boardSearch." that says what is wrong.
  key: 'queryTooShort' | 'queryTooLong'
  min: number
  max: number
}

/** What stops a search, or null when the word can be looked for. Its length is counted after trimming. */
export function validateBoardSearchQuery(query: string): BoardSearchProblem | null {
  const word = query.trim()
  if (word.length < BOARD_SEARCH_MIN_LENGTH) {
    return { key: 'queryTooShort', min: BOARD_SEARCH_MIN_LENGTH, max: BOARD_SEARCH_MAX_LENGTH }
  }
  if (word.length > BOARD_SEARCH_MAX_LENGTH) {
    return { key: 'queryTooLong', min: BOARD_SEARCH_MIN_LENGTH, max: BOARD_SEARCH_MAX_LENGTH }
  }
  return null
}

/** The address of the results for a word, and of a later page of them. */
export function boardSearchUrl(query: string, page = 1): string {
  const params = new URLSearchParams({ q: query.trim() })
  if (page > 1) {
    params.set('page', String(page))
  }
  return `/boards/search?${params.toString()}`
}

/** The page number an address asks for: a whole number from 1, whatever was typed into it. */
export function boardSearchPage(value: string | null | undefined): number {
  const page = Number.parseInt(value ?? '', 10)
  return Number.isFinite(page) && page >= 1 ? page : 1
}

/** Where a result leads: a notice or a post on the free board. */
export function boardSearchResultHref(item: Pick<BoardSearchItem, 'kind' | 'id'>): string {
  return item.kind === 'NOTICE' ? `/notices/${item.id}` : `/boards/free/${item.id}`
}
