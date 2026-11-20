import type { RankingItem } from '@/types/api'

// The ranking's columns a reader can order by: those with a value that runs from high to low. The
// tier is not one: it follows the rank anyway, and the tier shown is the roster's live one, which the
// rows do not carry, so ordering by the rows' own tier put shown tiers out of order.
export type RankingSortKey =
  | 'rank'
  | 'currentMmr'
  | 'peakMmr'
  | 'lowestMmr'
  | 'wins'
  | 'losses'
  | 'games'
  | 'winRate'
  | 'streak'
  | 'mmrDelta'

// 'high': the highest first (the best rank, the most games); 'low': the other way.
export type RankingSortOrder = 'high' | 'low'

export type RankingSort = { key: RankingSortKey; order: RankingSortOrder }

// As the server sends it: by rank, the best first.
export const DEFAULT_RANKING_SORT: RankingSort = { key: 'rank', order: 'high' }

// Columns shown only to readers who may see MMR.
export const MMR_SORT_KEYS: readonly RankingSortKey[] = ['currentMmr', 'peakMmr', 'lowestMmr', 'mmrDelta']

/** A first click orders a column from its highest; a click on the same column turns it around. */
export function nextRankingSort(current: RankingSort, key: RankingSortKey): RankingSort {
  if (current.key === key) {
    return { key, order: current.order === 'high' ? 'low' : 'high' }
  }
  return { key, order: 'high' }
}

// A streak as a number: five wins in a row 5, three losses in a row -3, none 0.
function streakValue(streak: string): number {
  const count = Number(streak?.slice(1))
  if (!streak || !Number.isFinite(count)) {
    return 0
  }
  if (streak.startsWith('W')) {
    return count
  }
  return streak.startsWith('L') ? -count : 0
}

// How high a row stands on a column, the higher the better; null when it has no value there.
function sortValue(row: RankingItem, key: RankingSortKey): number | null {
  switch (key) {
    case 'rank':
      return -row.rank
    case 'streak':
      return streakValue(row.streak)
    default: {
      const value = row[key]
      return typeof value === 'number' && Number.isFinite(value) ? value : null
    }
  }
}

/**
 * The rows in the order asked for. Rows level on that column keep the ranking's order, and rows
 * with no value there (no MMR yet) come last either way.
 */
export function sortRankingRows(rows: readonly RankingItem[], sort: RankingSort): RankingItem[] {
  return [...rows].sort((a, b) => {
    const left = sortValue(a, sort.key)
    const right = sortValue(b, sort.key)
    if (left === null || right === null) {
      if (left !== right) {
        return left === null ? 1 : -1
      }
    } else if (left !== right) {
      return sort.order === 'high' ? right - left : left - right
    }
    return a.rank - b.rank
  })
}
