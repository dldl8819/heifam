import { describe, expect, it } from 'vitest'
import {
  buildRaceCompositionUpdateFields,
  getRaceCompositionOptions,
  normalizeRaceComposition,
  resolveRaceCompositionTeamSize,
  resolveSharedRaceComposition,
} from '@/lib/race-composition'

describe('race composition editing', () => {
  it('limits composition options to the matching team size', () => {
    expect(getRaceCompositionOptions(2)).toEqual(['PP', 'PT', 'PZ'])
    expect(getRaceCompositionOptions(3)).toEqual(['PPP', 'PPT', 'PPZ', 'PTZ'])
  })

  it('resolves only supported and equally sized teams', () => {
    expect(resolveRaceCompositionTeamSize(2, 2)).toBe(2)
    expect(resolveRaceCompositionTeamSize(3, 3)).toBe(3)
    expect(resolveRaceCompositionTeamSize(3, 2)).toBeNull()
    expect(resolveRaceCompositionTeamSize(1, 1)).toBeNull()
  })

  it('initializes an edit only when both teams have the same valid composition', () => {
    expect(resolveSharedRaceComposition(3, ' ppt ', 'PPT')).toBe('PPT')
    expect(resolveSharedRaceComposition(2, 'pt', 'PT')).toBe('PT')
  })

  it('keeps legacy null, mismatched, and invalid compositions unchanged by default', () => {
    expect(resolveSharedRaceComposition(3, null, 'PPT')).toBeNull()
    expect(resolveSharedRaceComposition(3, 'PPT', 'PPZ')).toBeNull()
    expect(resolveSharedRaceComposition(3, 'PT', 'PT')).toBeNull()
    expect(resolveSharedRaceComposition(2, 'PPT', 'PPT')).toBeNull()
  })

  it('normalizes only compositions supported for the selected team size', () => {
    expect(normalizeRaceComposition(3, ' ppz ')).toBe('PPZ')
    expect(normalizeRaceComposition(3, 'PP')).toBeNull()
    expect(normalizeRaceComposition(2, 'PTZ')).toBeNull()
  })

  it('shows the current composition without including an untouched value in an update', () => {
    const selected = resolveSharedRaceComposition(3, 'PPT', 'PPT')

    expect(selected).toBe('PPT')
    expect(buildRaceCompositionUpdateFields(3, selected, false)).toEqual({})
  })

  it('includes a valid composition only after the selection is changed', () => {
    expect(buildRaceCompositionUpdateFields(3, 'PPZ', true)).toEqual({
      raceComposition: 'PPZ',
    })
  })

  it('omits blank, invalid, and unsupported composition updates', () => {
    expect(buildRaceCompositionUpdateFields(3, '', true)).toEqual({})
    expect(buildRaceCompositionUpdateFields(3, 'PT', true)).toEqual({})
    expect(buildRaceCompositionUpdateFields(null, 'PPT', true)).toEqual({})
  })
})
