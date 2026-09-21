import { describe, expect, it } from 'vitest'
import {
  PLAYER_MIN_GAMES_FOR_TEAMMATE_STATS,
  TEAMMATE_MIN_GAMES,
  filterTeammateStats,
  formatWinRate,
  hasEnoughGamesForTeammateStats,
} from '@/lib/teammate-stats'
import type { GroupPlayerTeammateStat } from '@/types/api'

function teammate(nickname: string, games: number): GroupPlayerTeammateStat {
  return {
    playerId: games,
    nickname,
    wins: games,
    losses: 0,
    games,
    winRate: 100,
    currentWinStreak: games,
  }
}

describe('teammate stats helpers', () => {
  it('keeps only teammates with at least ten matches together', () => {
    const teammates = [teammate('보이', 22), teammate('스톰', 10), teammate('리드', 9)]

    expect(TEAMMATE_MIN_GAMES).toBe(10)
    expect(filterTeammateStats(teammates).map((item) => item.nickname)).toEqual(['보이', '스톰'])
  })

  it('treats a bad threshold as the default', () => {
    const teammates = [teammate('보이', 10), teammate('리드', 9)]

    expect(filterTeammateStats(teammates, Number.NaN).map((item) => item.nickname)).toEqual(['보이'])
  })

  it('offers the teammate view only to players with at least thirty matches', () => {
    expect(PLAYER_MIN_GAMES_FOR_TEAMMATE_STATS).toBe(30)
    expect(hasEnoughGamesForTeammateStats(30)).toBe(true)
    expect(hasEnoughGamesForTeammateStats(31)).toBe(true)
    expect(hasEnoughGamesForTeammateStats(29)).toBe(false)
    expect(hasEnoughGamesForTeammateStats(undefined)).toBe(false)
  })

  it('formats win rates sent as a percentage or a ratio', () => {
    expect(formatWinRate(66.67)).toBe('66.67%')
    expect(formatWinRate(100)).toBe('100.00%')
    expect(formatWinRate(0.5)).toBe('50.00%')
    expect(formatWinRate(0)).toBe('0.00%')
  })
})
