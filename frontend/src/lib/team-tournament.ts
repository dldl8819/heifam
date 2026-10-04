import { composeTeamRaces } from '@/lib/participant-races'
import type {
  AssignedRace,
  TeamSide,
  TeamTournament,
  TournamentGame,
  TournamentSeries,
  TournamentTeam,
} from '@/types/api'

export const TEAM_TOURNAMENT_POLL_MS = 15000
export const TEAM_SIDES: readonly TeamSide[] = ['HOME', 'AWAY']

const ROUND_ORDER: Record<TournamentSeries['round'], number> = {
  SEMIFINAL: 0,
  FINAL: 1,
  THIRD_PLACE: 2,
}

export type GameRaceDraft = Record<TeamSide, Array<AssignedRace | null>>

/** Three-player teams from 6 to 14 players (2 to 4 teams), as the backend forms them; null otherwise. */
export function teamTournamentTeamCount(playerCount: number): number | null {
  return playerCount >= 6 && playerCount <= 14 ? Math.floor(playerCount / 3) : null
}

export function teamTournamentWaitingCount(playerCount: number): number {
  const teamCount = teamTournamentTeamCount(playerCount)
  return teamCount === null ? 0 : playerCount - teamCount * 3
}

/** Semifinals first, then the final and the third-place series. */
export function orderSeries(series: TournamentSeries[]): TournamentSeries[] {
  return [...series].sort(
    (left, right) => ROUND_ORDER[left.round] - ROUND_ORDER[right.round] || left.bracketSlot - right.bracketSlot,
  )
}

export function findNextGame(series: TournamentSeries): TournamentGame | null {
  return series.games.find((game) => game.status === 'NEXT' && game.matchId !== null) ?? null
}

export function initialRaceDraft(game: TournamentGame): GameRaceDraft {
  return {
    HOME: game.homePlayers.map((player) => player.assignedRace),
    AWAY: game.awayPlayers.map((player) => player.assignedRace),
  }
}

/** Each team's races must add up to the game's composition, as on the balance page. */
export function raceDraftMatchesComposition(game: TournamentGame, draft: GameRaceDraft): boolean {
  if (game.raceComposition === null) {
    return true
  }
  return (
    composeTeamRaces(draft.HOME) === game.raceComposition && composeTeamRaces(draft.AWAY) === game.raceComposition
  )
}

export function buildParticipantRaces(
  game: TournamentGame,
  draft: GameRaceDraft,
): Array<{ playerId: number; race: AssignedRace }> {
  return TEAM_SIDES.flatMap((side) =>
    (side === 'HOME' ? game.homePlayers : game.awayPlayers).flatMap((player, index) => {
      const race = draft[side][index] ?? null
      return typeof player.playerId === 'number' && race !== null ? [{ playerId: player.playerId, race }] : []
    }),
  )
}

/** With three teams, the one outside the semifinal waits for its winner in the final. */
export function finalWaitingTeamNumber(tournament: TeamTournament): number | null {
  if (tournament.teamCount !== 3) {
    return null
  }
  const semifinal = tournament.series.find((series) => series.round === 'SEMIFINAL')
  if (!semifinal) {
    return null
  }
  return (
    tournament.teams.find(
      (team) => team.teamNumber !== semifinal.homeTeamNumber && team.teamNumber !== semifinal.awayTeamNumber,
    )?.teamNumber ?? null
  )
}

/** Teams by final place once the tournament is over. */
export function rankedTeams(tournament: TeamTournament): TournamentTeam[] {
  return tournament.teams
    .filter((team) => team.finalRank !== null)
    .sort((left, right) => (left.finalRank ?? 0) - (right.finalRank ?? 0))
}
