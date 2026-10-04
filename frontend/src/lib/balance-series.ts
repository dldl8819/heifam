import type {
  BalancePlayerInput,
  BalanceSeries,
  BalanceSeriesLineup,
  MultiBalanceMatch,
  MultiBalanceSeriesGame,
  TournamentPlayer,
  TournamentSeriesFormat,
} from '@/types/api'

export const BALANCE_SERIES_POLL_MS = 15000

const OFF_RACES = ['T', 'Z'] as const

export type OffRaceAssignment = {
  race: (typeof OFF_RACES)[number]
  players: string[]
}

/**
 * One lineup per match of a multi-balance result, with the format chosen for it (by match number);
 * null when a player has no id to start a series with.
 */
export function buildSeriesLineups(
  matches: MultiBalanceMatch[],
  formats: Record<number, TournamentSeriesFormat> = {},
): BalanceSeriesLineup[] | null {
  const lineups: BalanceSeriesLineup[] = []
  for (const match of matches) {
    const homePlayerIds = match.homeTeam.map((player) => player.playerId)
    const awayPlayerIds = match.awayTeam.map((player) => player.playerId)
    if ([...homePlayerIds, ...awayPlayerIds].some((id) => typeof id !== 'number' || !Number.isFinite(id))) {
      return null
    }
    const format = formats[match.matchNumber]
    lineups.push({
      homePlayerIds: homePlayerIds as number[],
      awayPlayerIds: awayPlayerIds as number[],
      ...(format ? { format } : {}),
    })
  }
  return lineups.length > 0 ? lineups : null
}

/** Only teams that could mix get a choice: mix in Terran and Zerg, or keep to Protoss best of three. */
export function canChooseSeriesFormat(match: MultiBalanceMatch): boolean {
  return match.seriesPlan?.format === 'MIXED_THREE'
}

/** The games a match would play in the chosen format; the backend plans the Protoss games the same way. */
export function previewSeriesGames(match: MultiBalanceMatch, format: TournamentSeriesFormat): MultiBalanceSeriesGame[] {
  const plan = match.seriesPlan
  if (!plan) {
    return []
  }
  if (format === plan.format) {
    return plan.games
  }
  const protoss = 'P'.repeat(match.teamSize) as MultiBalanceSeriesGame['raceComposition']
  return [1, 2, 3].map((gameNumber) => ({
    gameNumber,
    raceComposition: protoss,
    homeRaces: match.homeTeam.map(() => 'P' as const),
    awayRaces: match.awayTeam.map(() => 'P' as const),
  }))
}

/** Who takes Terran or Zerg in a planned game, home players first; empty for an all-Protoss game. */
export function offRaceAssignments(
  game: MultiBalanceSeriesGame,
  homeTeam: BalancePlayerInput[],
  awayTeam: BalancePlayerInput[],
): OffRaceAssignment[] {
  return OFF_RACES.flatMap((race) => {
    const players = [
      ...homeTeam.filter((_, index) => game.homeRaces?.[index] === race),
      ...awayTeam.filter((_, index) => game.awayRaces?.[index] === race),
    ].map((player) => player.name)
    return players.length > 0 ? [{ race, players }] : []
  })
}

/** Running series first, then the newest. */
export function orderBoardSeries(series: BalanceSeries[]): BalanceSeries[] {
  return [...series].sort((left, right) => {
    const leftRunning = left.status === 'IN_PROGRESS' ? 0 : 1
    const rightRunning = right.status === 'IN_PROGRESS' ? 0 : 1
    return leftRunning - rightRunning || right.seriesId - left.seriesId
  })
}

export function teamLine(players: TournamentPlayer[]): string {
  return players.map((player) => player.nickname ?? '-').join(', ')
}
