/** How many rows a page of a long list shows (results, operation log): 10 unless the reader picks another. */
export const PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const
export type PageSize = (typeof PAGE_SIZE_OPTIONS)[number]
export const DEFAULT_PAGE_SIZE: PageSize = 10

export function toPageSize(value: unknown): PageSize {
  const parsed = Number(value)
  return (PAGE_SIZE_OPTIONS as readonly number[]).includes(parsed) ? (parsed as PageSize) : DEFAULT_PAGE_SIZE
}

/**
 * How many pages a list fills when it is held to `reach` rows (the recent results: 30 for members,
 * 100 for admins) and its route answers without a total: a page that came back short
 * (`lastPageSeen`, 0-based) marks the end when there are fewer rows than the reach.
 */
export function pageCountWithinReach(reach: number, pageSize: number, lastPageSeen: number | null): number {
  const byReach = Math.max(1, Math.ceil(Math.max(0, reach) / Math.max(1, pageSize)))
  return lastPageSeen === null ? byReach : Math.max(1, Math.min(byReach, lastPageSeen + 1))
}

/**
 * The page buttons to show (0-based page numbers, null for a gap): every page when there are 7 or
 * fewer, otherwise the first and last, the current one with its neighbours, and enough beside them
 * near either end to keep 7 slots, e.g. 1 2 3 4 5 … 10, 1 … 4 5 6 … 10, 1 … 6 7 8 9 10.
 */
export function visiblePageNumbers(current: number, total: number): Array<number | null> {
  if (total <= 0) {
    return []
  }
  if (total <= 7) {
    return Array.from({ length: total }, (_, index) => index)
  }

  const last = total - 1
  const page = Math.min(Math.max(0, current), last)
  let start = Math.max(1, page - 1)
  let end = Math.min(last - 1, page + 1)
  if (page <= 3) {
    start = 1
    end = 4
  } else if (page >= last - 3) {
    start = last - 4
    end = last - 1
  }

  const pages: Array<number | null> = [0]
  if (start > 1) {
    pages.push(null)
  }
  for (let number = start; number <= end; number += 1) {
    pages.push(number)
  }
  if (end < last - 1) {
    pages.push(null)
  }
  pages.push(last)
  return pages
}
