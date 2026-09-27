import { describe, expect, it } from 'vitest'
import {
  buildMatchResultUpdateRequest,
  type MatchResultEditSnapshot,
  type ParticipantRaceEdit,
} from '@/lib/match-result-edit'
import type { AssignedRace } from '@/types/api'

const ORIGINAL_RACES: AssignedRace[] = ['P', 'P', 'T', 'P', 'P', 'T']

function raceEdits(races: Array<AssignedRace | null>): ParticipantRaceEdit[] {
  return races.map((race, index) => ({
    playerId: index + 1,
    originalRace: ORIGINAL_RACES[index],
    race,
  }))
}

const completedMatch: MatchResultEditSnapshot = {
  winnerTeam: 'HOME',
  teamSize: 3,
  homeRaceComposition: 'PPT',
  awayRaceComposition: 'PPT',
}

describe('buildMatchResultUpdateRequest', () => {
  it('returns null when winner and shared race composition are unchanged', () => {
    expect(buildMatchResultUpdateRequest(completedMatch, 'HOME', 'PPT')).toBeNull()
    expect(buildMatchResultUpdateRequest(completedMatch, 'HOME', ' ppt ')).toBeNull()
  })

  it('returns a winner-only update when only the winner changes', () => {
    expect(buildMatchResultUpdateRequest(completedMatch, 'AWAY', 'PPT')).toEqual({
      winnerTeam: 'AWAY',
    })
  })

  it('returns a race-only update when only the race composition changes', () => {
    expect(buildMatchResultUpdateRequest(completedMatch, 'HOME', 'PPZ')).toEqual({
      winnerTeam: 'HOME',
      raceComposition: 'PPZ',
    })
  })

  it('returns both changed fields when winner and race composition change', () => {
    expect(buildMatchResultUpdateRequest(completedMatch, 'AWAY', 'PTZ')).toEqual({
      winnerTeam: 'AWAY',
      raceComposition: 'PTZ',
    })
  })

  it('returns null after a race selection is changed back to its original value', () => {
    expect(buildMatchResultUpdateRequest(completedMatch, 'HOME', 'PPZ')).not.toBeNull()
    expect(buildMatchResultUpdateRequest(completedMatch, 'HOME', 'PPT')).toBeNull()
  })

  it('keeps legacy missing or mismatched race compositions unchanged when selection is blank', () => {
    expect(
      buildMatchResultUpdateRequest(
        { ...completedMatch, homeRaceComposition: null, awayRaceComposition: null },
        'HOME',
        '',
      ),
    ).toBeNull()
    expect(
      buildMatchResultUpdateRequest(
        { ...completedMatch, homeRaceComposition: 'PPT', awayRaceComposition: 'PPZ' },
        'HOME',
        '',
      ),
    ).toBeNull()
  })

  it('updates a legacy race composition only after a valid value is selected', () => {
    expect(
      buildMatchResultUpdateRequest(
        { ...completedMatch, homeRaceComposition: 'PPT', awayRaceComposition: 'PPZ' },
        'HOME',
        'PPT',
      ),
    ).toEqual({
      winnerTeam: 'HOME',
      raceComposition: 'PPT',
    })
  })

  it('returns null when no player race changes and the winner stays', () => {
    expect(
      buildMatchResultUpdateRequest(completedMatch, 'HOME', 'PPT', raceEdits(ORIGINAL_RACES)),
    ).toBeNull()
  })

  it('sends only the winner when player races are untouched', () => {
    expect(
      buildMatchResultUpdateRequest(completedMatch, 'AWAY', 'PPT', raceEdits(ORIGINAL_RACES)),
    ).toEqual({ winnerTeam: 'AWAY' })
  })

  it('sends every player race with the composition once someone else played terran', () => {
    expect(
      buildMatchResultUpdateRequest(completedMatch, 'HOME', 'PPT', raceEdits(['T', 'P', 'P', 'P', 'T', 'P'])),
    ).toEqual({
      winnerTeam: 'HOME',
      raceComposition: 'PPT',
      participantRaces: [
        { playerId: 1, race: 'T' },
        { playerId: 2, race: 'P' },
        { playerId: 3, race: 'P' },
        { playerId: 4, race: 'P' },
        { playerId: 5, race: 'T' },
        { playerId: 6, race: 'P' },
      ],
    })
  })

  it('carries a composition change that comes from the player races', () => {
    expect(
      buildMatchResultUpdateRequest(completedMatch, 'HOME', 'PPZ', raceEdits(['P', 'P', 'Z', 'P', 'P', 'Z'])),
    ).toMatchObject({ winnerTeam: 'HOME', raceComposition: 'PPZ' })
  })

  it('leaves player races out while they do not add up to a composition', () => {
    const request = buildMatchResultUpdateRequest(
      completedMatch,
      'HOME',
      null,
      raceEdits(['T', 'P', 'T', 'P', 'P', 'T']),
    )

    expect(request).not.toHaveProperty('participantRaces')
    expect(request).not.toHaveProperty('raceComposition')
  })

  it('ignores race composition for unsupported team sizes while preserving winner changes', () => {
    const unsupportedMatch = { ...completedMatch, teamSize: null }

    expect(buildMatchResultUpdateRequest(unsupportedMatch, 'HOME', 'PPT')).toBeNull()
    expect(buildMatchResultUpdateRequest(unsupportedMatch, 'AWAY', 'PPT')).toEqual({
      winnerTeam: 'AWAY',
    })
  })
})
