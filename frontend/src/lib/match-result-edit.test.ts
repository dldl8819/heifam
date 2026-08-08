import { describe, expect, it } from 'vitest'
import {
  buildMatchResultUpdateRequest,
  type MatchResultEditSnapshot,
} from '@/lib/match-result-edit'

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

  it('ignores race composition for unsupported team sizes while preserving winner changes', () => {
    const unsupportedMatch = { ...completedMatch, teamSize: null }

    expect(buildMatchResultUpdateRequest(unsupportedMatch, 'HOME', 'PPT')).toBeNull()
    expect(buildMatchResultUpdateRequest(unsupportedMatch, 'AWAY', 'PPT')).toEqual({
      winnerTeam: 'AWAY',
    })
  })
})
