import { describe, expect, it } from 'vitest'
import {
  composeTeamRaces,
  formatRecordedRacePlayer,
  normalizeAssignedRace,
  resolveCompositionFromTeamRaces,
} from '@/lib/participant-races'

describe('formatRecordedRacePlayer', () => {
  it('marks players recorded on Terran or Zerg', () => {
    expect(formatRecordedRacePlayer('alpha', 'T', true)).toBe('alpha (T)')
    expect(formatRecordedRacePlayer('bravo', 'Z', true)).toBe('bravo (Z)')
  })

  it('leaves protoss players and unknown races unmarked', () => {
    expect(formatRecordedRacePlayer('charlie', 'P', true)).toBe('charlie')
    expect(formatRecordedRacePlayer('delta', null, true)).toBe('delta')
  })

  it('does not mark races that were only assigned automatically', () => {
    expect(formatRecordedRacePlayer('echo', 'T', false)).toBe('echo')
  })
})

describe('normalizeAssignedRace', () => {
  it('accepts a single race in any case and rejects everything else', () => {
    expect(normalizeAssignedRace(' t ')).toBe('T')
    expect(normalizeAssignedRace('PT')).toBeNull()
    expect(normalizeAssignedRace(null)).toBeNull()
  })
})

describe('composeTeamRaces', () => {
  it('orders races the way compositions are written', () => {
    expect(composeTeamRaces(['Z', 'P', 'T'])).toBe('PTZ')
    expect(composeTeamRaces(['T', 'P', 'P'])).toBe('PPT')
  })

  it('returns null while any race is still unknown', () => {
    expect(composeTeamRaces(['P', null, 'T'])).toBeNull()
    expect(composeTeamRaces([])).toBeNull()
  })
})

describe('resolveCompositionFromTeamRaces', () => {
  it('returns the shared composition when both teams add up to the same one', () => {
    expect(resolveCompositionFromTeamRaces(3, ['T', 'P', 'P'], ['P', 'P', 'T'])).toBe('PPT')
  })

  it('rejects teams with different compositions', () => {
    expect(resolveCompositionFromTeamRaces(3, ['P', 'P', 'T'], ['P', 'P', 'Z'])).toBeNull()
  })

  it('rejects compositions the format does not support', () => {
    expect(resolveCompositionFromTeamRaces(3, ['T', 'T', 'Z'], ['T', 'T', 'Z'])).toBeNull()
  })
})
