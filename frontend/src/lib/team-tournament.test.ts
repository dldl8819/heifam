import { describe, expect, it } from 'vitest'
import {
  buildParticipantRaces,
  finalWaitingTeamNumber,
  findNextGame,
  initialRaceDraft,
  orderSeries,
  raceDraftMatchesComposition,
  rankedTeams,
  teamTournamentTeamCount,
  teamTournamentWaitingCount,
} from '@/lib/team-tournament'
import type { TeamTournament, TournamentGame, TournamentSeries } from '@/types/api'

function game(overrides: Partial<TournamentGame> = {}): TournamentGame {
  return {
    gameNumber: 1,
    raceComposition: 'PPT',
    status: 'NEXT',
    matchId: 10,
    winnerTeam: null,
    homePlayers: [
      { playerId: 1, nickname: 'YOUR_USERNAME_1', assignedRace: 'T' },
      { playerId: 2, nickname: 'YOUR_USERNAME_2', assignedRace: 'P' },
      { playerId: null, nickname: null, assignedRace: 'P' },
    ],
    awayPlayers: [
      { playerId: 4, nickname: 'YOUR_USERNAME_4', assignedRace: 'P' },
      { playerId: 5, nickname: 'YOUR_USERNAME_5', assignedRace: 'T' },
      { playerId: 6, nickname: 'YOUR_USERNAME_6', assignedRace: 'P' },
    ],
    ...overrides,
  }
}

function series(round: TournamentSeries['round'], bracketSlot: number, games: TournamentGame[] = []): TournamentSeries {
  return {
    seriesId: bracketSlot,
    round,
    bracketSlot,
    format: 'BEST_OF_THREE',
    status: 'IN_PROGRESS',
    homeTeamNumber: 1,
    awayTeamNumber: 2,
    homeWins: 0,
    awayWins: 0,
    winnerTeamNumber: null,
    games,
  }
}

describe('team tournament helpers', () => {
  it('makes teams from the same player counts as the backend', () => {
    expect(teamTournamentTeamCount(5)).toBeNull()
    expect(teamTournamentTeamCount(6)).toBe(2)
    expect(teamTournamentTeamCount(8)).toBe(2)
    expect(teamTournamentTeamCount(9)).toBe(3)
    expect(teamTournamentTeamCount(11)).toBe(3)
    expect(teamTournamentTeamCount(12)).toBe(4)
    expect(teamTournamentTeamCount(14)).toBe(4)
    expect(teamTournamentTeamCount(15)).toBeNull()
    expect(teamTournamentWaitingCount(14)).toBe(2)
    expect(teamTournamentWaitingCount(10)).toBe(1)
    expect(teamTournamentWaitingCount(15)).toBe(0)
  })

  it('lists semifinals before the final and the third-place series', () => {
    const ordered = orderSeries([series('THIRD_PLACE', 1), series('SEMIFINAL', 2), series('FINAL', 1), series('SEMIFINAL', 1)])

    expect(ordered.map((item) => `${item.round}-${item.bracketSlot}`)).toEqual([
      'SEMIFINAL-1',
      'SEMIFINAL-2',
      'FINAL-1',
      'THIRD_PLACE-1',
    ])
  })

  it('finds the game waiting for its result', () => {
    const played = game({ gameNumber: 1, status: 'PLAYED', winnerTeam: 'HOME' })
    const next = game({ gameNumber: 2, matchId: 11 })
    const upcoming = game({ gameNumber: 3, status: 'UPCOMING', matchId: null })

    expect(findNextGame(series('FINAL', 1, [played, next, upcoming]))).toBe(next)
    expect(findNextGame(series('FINAL', 1, [played]))).toBeNull()
  })

  it('checks the recorded races against the composition', () => {
    const next = game()
    const draft = initialRaceDraft(next)

    expect(raceDraftMatchesComposition(next, draft)).toBe(true)
    expect(raceDraftMatchesComposition(next, { ...draft, HOME: ['P', 'P', 'P'] })).toBe(false)
  })

  it('sends a race for every player it can name', () => {
    expect(buildParticipantRaces(game(), initialRaceDraft(game()))).toEqual([
      { playerId: 1, race: 'T' },
      { playerId: 2, race: 'P' },
      { playerId: 4, race: 'P' },
      { playerId: 5, race: 'T' },
      { playerId: 6, race: 'P' },
    ])
  })

  it('finds the team that waits in the final of a three-team tournament', () => {
    const tournament: TeamTournament = {
      tournamentId: 1,
      status: 'IN_PROGRESS',
      teamCount: 3,
      createdAt: '2026-10-04T10:00:00Z',
      finishedAt: null,
      waitingPlayers: [],
      teams: [1, 2, 3].map((teamNumber) => ({ teamId: teamNumber, teamNumber, finalRank: null, totalMmr: null, members: [] })),
      series: [series('SEMIFINAL', 1)],
    }

    expect(finalWaitingTeamNumber(tournament)).toBe(3)
    expect(finalWaitingTeamNumber({ ...tournament, teamCount: 4 })).toBeNull()
  })

  it('orders finished teams by place', () => {
    const tournament: TeamTournament = {
      tournamentId: 1,
      status: 'COMPLETED',
      teamCount: 2,
      createdAt: '2026-10-04T10:00:00Z',
      finishedAt: '2026-10-04T11:00:00Z',
      waitingPlayers: [],
      teams: [
        { teamId: 1, teamNumber: 1, finalRank: 2, totalMmr: null, members: [] },
        { teamId: 2, teamNumber: 2, finalRank: 1, totalMmr: null, members: [] },
      ],
      series: [],
    }

    expect(rankedTeams(tournament).map((team) => team.teamNumber)).toEqual([2, 1])
  })
})
