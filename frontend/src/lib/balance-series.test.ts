import { describe, expect, it } from 'vitest'
import {
  buildSeriesLineups,
  canChooseSeriesFormat,
  matchBalanceMetrics,
  offRaceAssignments,
  orderBoardSeries,
  previewSeriesGames,
  seriesTeamNumber,
  teamLine,
} from './balance-series'
import type { BalanceSeries, MultiBalanceMatch } from '@/types/api'

function match(homeIds: Array<number | undefined>, awayIds: number[]): MultiBalanceMatch {
  return {
    matchNumber: 1,
    matchType: '3v3',
    teamSize: 3,
    homeTeam: homeIds.map((id, index) => ({ playerId: id, name: `YOUR_USERNAME_H${index}` })),
    awayTeam: awayIds.map((id, index) => ({ playerId: id, name: `YOUR_USERNAME_A${index}` })),
    raceSummary: { home: 'PPP', away: 'PPP' },
    penaltySummary: { repeatTeammatePenalty: 0, repeatMatchupPenalty: 0, racePenalty: 0 },
  }
}

function series(seriesId: number, status: BalanceSeries['status']): BalanceSeries {
  return {
    seriesId,
    matchNumber: null,
    homeTeamNumber: null,
    awayTeamNumber: null,
    status,
    format: 'BEST_OF_THREE',
    teamSize: 3,
    homeWins: 0,
    awayWins: 0,
    winnerTeam: null,
    createdAt: '2026-01-01T00:00:00Z',
    finishedAt: null,
    homePlayers: [],
    awayPlayers: [],
    games: [],
  }
}

describe('buildSeriesLineups', () => {
  it('keeps each match as one lineup', () => {
    expect(buildSeriesLineups([match([1, 2, 3], [4, 5, 6]), match([7, 8, 9], [10, 11, 12])])).toEqual([
      { homePlayerIds: [1, 2, 3], awayPlayerIds: [4, 5, 6] },
      { homePlayerIds: [7, 8, 9], awayPlayerIds: [10, 11, 12] },
    ])
  })

  it('passes on the format chosen for a match', () => {
    const second = { ...match([7, 8, 9], [10, 11, 12]), matchNumber: 2 }
    expect(buildSeriesLineups([match([1, 2, 3], [4, 5, 6]), second], { 2: 'BEST_OF_THREE' })).toEqual([
      { homePlayerIds: [1, 2, 3], awayPlayerIds: [4, 5, 6] },
      { homePlayerIds: [7, 8, 9], awayPlayerIds: [10, 11, 12], format: 'BEST_OF_THREE' },
    ])
  })

  it('refuses a match with a player it cannot name by id', () => {
    expect(buildSeriesLineups([match([1, undefined, 3], [4, 5, 6])])).toBeNull()
    expect(buildSeriesLineups([])).toBeNull()
  })
})

describe('offRaceAssignments', () => {
  const planned = match([1, 2, 3], [4, 5, 6])

  it('names who takes Terran and Zerg, home first', () => {
    expect(
      offRaceAssignments(
        { gameNumber: 2, raceComposition: 'PPT', homeRaces: ['P', 'T', 'P'], awayRaces: ['T', 'P', 'P'] },
        planned.homeTeam,
        planned.awayTeam,
      ),
    ).toEqual([{ race: 'T', players: ['YOUR_USERNAME_H1', 'YOUR_USERNAME_A0'] }])
  })

  it('is empty for an all-Protoss game', () => {
    expect(
      offRaceAssignments(
        { gameNumber: 1, raceComposition: 'PPP', homeRaces: ['P', 'P', 'P'], awayRaces: ['P', 'P', 'P'] },
        planned.homeTeam,
        planned.awayTeam,
      ),
    ).toEqual([])
  })
})

describe('series format choice', () => {
  const mixed: MultiBalanceMatch = {
    ...match([1, 2, 3], [4, 5, 6]),
    seriesPlan: {
      format: 'MIXED_THREE',
      games: [
        { gameNumber: 1, raceComposition: 'PPP', homeRaces: ['P', 'P', 'P'], awayRaces: ['P', 'P', 'P'] },
        { gameNumber: 2, raceComposition: 'PPT', homeRaces: ['T', 'P', 'P'], awayRaces: ['T', 'P', 'P'] },
        { gameNumber: 3, raceComposition: 'PPZ', homeRaces: ['P', 'Z', 'P'], awayRaces: ['Z', 'P', 'P'] },
      ],
    },
  }

  it('offers a choice only to teams that could mix', () => {
    expect(canChooseSeriesFormat(mixed)).toBe(true)
    expect(canChooseSeriesFormat(match([1, 2, 3], [4, 5, 6]))).toBe(false)
  })

  it('previews three Protoss games when the mix is turned down', () => {
    expect(previewSeriesGames(mixed, 'MIXED_THREE').map((game) => game.raceComposition)).toEqual(['PPP', 'PPT', 'PPZ'])
    const protoss = previewSeriesGames(mixed, 'BEST_OF_THREE')
    expect(protoss.map((game) => game.raceComposition)).toEqual(['PPP', 'PPP', 'PPP'])
    expect(offRaceAssignments(protoss[1], mixed.homeTeam, mixed.awayTeam)).toEqual([])
  })
})

describe('orderBoardSeries', () => {
  it('puts running series first, each group in the order started', () => {
    const ordered = orderBoardSeries([
      series(2, 'IN_PROGRESS'),
      series(4, 'COMPLETED'),
      series(1, 'IN_PROGRESS'),
      series(3, 'COMPLETED'),
    ])
    expect(ordered.map((item) => item.seriesId)).toEqual([1, 2, 3, 4])
  })
})

describe('seriesTeamNumber', () => {
  it('keeps the team numbers of the multi-balance', () => {
    const second = { ...series(8, 'IN_PROGRESS'), matchNumber: 2, homeTeamNumber: 3, awayTeamNumber: 4 }
    expect([seriesTeamNumber(second, 'HOME'), seriesTeamNumber(second, 'AWAY')]).toEqual([3, 4])
    expect([seriesTeamNumber(series(1, 'IN_PROGRESS'), 'HOME'), seriesTeamNumber(series(1, 'IN_PROGRESS'), 'AWAY')]).toEqual([1, 2])
  })
})

describe('matchBalanceMetrics', () => {
  it('gives the win rate always and the MMR figures only when MMR is shown', () => {
    expect(
      matchBalanceMetrics({ ...match([1, 2, 3], [4, 5, 6]), homeMmr: 3000, awayMmr: 2961, mmrDiff: 39, expectedHomeWinRate: 0.53 }),
    ).toEqual({ expectedHomeWinRate: 0.53, mmrDiff: 39, averageTeamMmr: 2981 })
    expect(matchBalanceMetrics({ ...match([1, 2, 3], [4, 5, 6]), expectedHomeWinRate: 0.5 })).toEqual({
      expectedHomeWinRate: 0.5,
      mmrDiff: null,
      averageTeamMmr: null,
    })
  })
})

describe('teamLine', () => {
  it('joins the nicknames and marks a hidden player', () => {
    expect(
      teamLine([
        { playerId: 1, nickname: 'YOUR_USERNAME', race: 'P', mmr: null },
        { playerId: null, nickname: null, race: null, mmr: null },
      ]),
    ).toBe('YOUR_USERNAME, -')
  })
})
