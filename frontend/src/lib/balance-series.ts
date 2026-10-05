import type {
  BalancePlayerInput,
  BalanceSeries,
  TeamSide,
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

// Series started by one multi-balance are saved together, moments apart.
const SAME_MULTI_BALANCE_MS = 60_000

/**
 * The series of one multi-balance stay together in match order, so match 1 is left of match 2
 * whichever of them is over. Multi-balances with a series still running come first, then the
 * newest. Series of one multi-balance have rising ids and match numbers and start together.
 */
export function orderBoardSeries(series: BalanceSeries[]): BalanceSeries[] {
  const groups: BalanceSeries[][] = []
  for (const item of [...series].sort((left, right) => left.seriesId - right.seriesId)) {
    const group = groups[groups.length - 1]
    const previous = group?.[group.length - 1]
    const sameMultiBalance =
      previous !== undefined &&
      item.matchNumber !== null &&
      previous.matchNumber !== null &&
      item.matchNumber > previous.matchNumber &&
      Math.abs(Date.parse(item.createdAt) - Date.parse(group[0].createdAt)) <= SAME_MULTI_BALANCE_MS
    if (sameMultiBalance) {
      group.push(item)
    } else {
      groups.push([item])
    }
  }
  const running = (group: BalanceSeries[]) => (group.some((item) => item.status === 'IN_PROGRESS') ? 0 : 1)
  return groups
    .sort((left, right) => running(left) - running(right) || right[0].seriesId - left[0].seriesId)
    .flat()
}

/** The team numbers the multi-balance showed (match n is teams 2n-1 and 2n); 1 and 2 for older series. */
export function seriesTeamNumber(series: BalanceSeries, side: TeamSide): number {
  return side === 'HOME' ? (series.homeTeamNumber ?? 1) : (series.awayTeamNumber ?? 2)
}

export type MatchBalanceMetrics = {
  expectedHomeWinRate: number | null
  mmrDiff: number | null
  averageTeamMmr: number | null
}

/** The balance page's metrics for one multi-balance match; MMR ones are null when MMR is hidden. */
export function matchBalanceMetrics(match: MultiBalanceMatch): MatchBalanceMetrics {
  const bothTotals = typeof match.homeMmr === 'number' && typeof match.awayMmr === 'number'
  return {
    expectedHomeWinRate: typeof match.expectedHomeWinRate === 'number' ? match.expectedHomeWinRate : null,
    mmrDiff: typeof match.mmrDiff === 'number' ? match.mmrDiff : null,
    averageTeamMmr: bothTotals ? Math.round(((match.homeMmr as number) + (match.awayMmr as number)) / 2) : null,
  }
}

export function teamLine(players: TournamentPlayer[]): string {
  return players.map((player) => player.nickname ?? '-').join(', ')
}
