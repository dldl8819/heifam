import { describe, expect, it } from 'vitest'
import { DEFAULT_RANKING_SORT, nextRankingSort, sortRankingRows } from '@/lib/ranking-sort'
import type { RankingItem } from '@/types/api'

function row(rank: number, nickname: string, extra: Partial<RankingItem> = {}): RankingItem {
  return {
    rank,
    nickname,
    race: 'P',
    tier: 'B',
    wins: 0,
    losses: 0,
    games: 0,
    winRate: 0,
    streak: '',
    last10: '',
    ...extra,
  }
}

const names = (rows: RankingItem[]) => rows.map((item) => item.nickname)

describe('nextRankingSort', () => {
  it('orders a new column from its highest, and turns it around on the next click', () => {
    const games = nextRankingSort(DEFAULT_RANKING_SORT, 'games')
    expect(games).toEqual({ key: 'games', order: 'high' })
    expect(nextRankingSort(games, 'games')).toEqual({ key: 'games', order: 'low' })
    expect(nextRankingSort(nextRankingSort(games, 'games'), 'games')).toEqual({ key: 'games', order: 'high' })
    expect(nextRankingSort(games, 'wins')).toEqual({ key: 'wins', order: 'high' })
  })

  it('turns the ranking around from the order it starts in', () => {
    expect(nextRankingSort(DEFAULT_RANKING_SORT, 'rank')).toEqual({ key: 'rank', order: 'low' })
  })
})

describe('sortRankingRows', () => {
  const rows = [
    row(1, 'ace', { games: 12, wins: 9, tier: 'A', streak: 'W3', currentMmr: 1700 }),
    row(2, 'busy', { games: 30, wins: 15, tier: 'B+', streak: 'L4', currentMmr: 1600 }),
    row(3, 'tied', { games: 12, wins: 4, tier: 'S', streak: 'W1' }),
    row(4, 'fresh', { games: 0, tier: 'UNASSIGNED', streak: '' }),
  ]

  it('keeps the server order by default', () => {
    expect(names(sortRankingRows([...rows].reverse(), DEFAULT_RANKING_SORT))).toEqual(['ace', 'busy', 'tied', 'fresh'])
  })

  it('finds who played the most, and the fewest, with level rows in ranking order', () => {
    expect(names(sortRankingRows(rows, { key: 'games', order: 'high' }))).toEqual(['busy', 'ace', 'tied', 'fresh'])
    expect(names(sortRankingRows(rows, { key: 'games', order: 'low' }))).toEqual(['fresh', 'ace', 'tied', 'busy'])
  })

  it('reads the rank as higher when better', () => {
    expect(names(sortRankingRows(rows, { key: 'rank', order: 'low' }))).toEqual(['fresh', 'tied', 'busy', 'ace'])
  })

  it('counts a win streak above none and a losing streak below it', () => {
    expect(names(sortRankingRows(rows, { key: 'streak', order: 'high' }))).toEqual(['ace', 'tied', 'fresh', 'busy'])
  })

  it('puts rows without a value last, whichever way', () => {
    expect(names(sortRankingRows(rows, { key: 'currentMmr', order: 'high' }))).toEqual(['ace', 'busy', 'tied', 'fresh'])
    expect(names(sortRankingRows(rows, { key: 'currentMmr', order: 'low' }))).toEqual(['busy', 'ace', 'tied', 'fresh'])
  })
})
