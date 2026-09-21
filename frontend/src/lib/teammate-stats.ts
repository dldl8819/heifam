import type { GroupPlayerTeammateStat } from '@/types/api'

/**
 * A pair needs this many matches on the same team before their win rate means anything; below it
 * one lucky night would top the list at 100%.
 */
export const TEAMMATE_MIN_GAMES = 10

/** Players with fewer matches than this are not offered the teammate view at all. */
export const PLAYER_MIN_GAMES_FOR_TEAMMATE_STATS = 30

export function filterTeammateStats(
  teammates: GroupPlayerTeammateStat[],
  minGames: number = TEAMMATE_MIN_GAMES
): GroupPlayerTeammateStat[] {
  const threshold = Number.isFinite(minGames) ? Math.max(1, Math.trunc(minGames)) : TEAMMATE_MIN_GAMES
  return teammates.filter((teammate) => teammate.games >= threshold)
}

export function hasEnoughGamesForTeammateStats(games: number | undefined): boolean {
  return (games ?? 0) >= PLAYER_MIN_GAMES_FOR_TEAMMATE_STATS
}

/** The server sends a percentage (66.67); older payloads sent a ratio, so treat <= 1 as a ratio. */
export function formatWinRate(value: number): string {
  const percentage = value <= 1 ? value * 100 : value
  return `${percentage.toFixed(2)}%`
}
