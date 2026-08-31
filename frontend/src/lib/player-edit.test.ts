import { describe, expect, it } from 'vitest'

import {
  buildOwnPlayerRaceUpdateRequest,
  buildPlayerProfileUpdateRequest,
  resolveDefaultMmrForTier,
  resolveEditableMmrValue,
} from './player-edit'
import type { PlayerRosterItem } from '@/types/api'

function player(overrides: Partial<PlayerRosterItem> = {}): PlayerRosterItem {
  return {
    id: 1,
    nickname: 'PlayerAlpha',
    race: 'P',
    tier: 'B+',
    currentMmr: 1420,
    wins: 0,
    losses: 0,
    games: 0,
    active: true,
    ...overrides,
  }
}

describe('buildPlayerProfileUpdateRequest', () => {
  it('sends race only when playable race changes', () => {
    const payload = buildPlayerProfileUpdateRequest(player(), {
      nickname: 'PlayerAlpha',
      race: 'PTZ',
      tier: 'B+',
    })

    expect(payload).toEqual({ race: 'PTZ' })
  })

  it('does not send tier when tier is unchanged', () => {
    const payload = buildPlayerProfileUpdateRequest(player(), {
      nickname: 'PlayerAlpha',
      race: 'P',
      tier: 'B+',
    })

    expect(payload).toEqual({})
  })

  it('sends tier only when tier changes', () => {
    const payload = buildPlayerProfileUpdateRequest(player(), {
      nickname: 'PlayerAlpha',
      race: 'P',
      tier: 'A',
    })

    expect(payload).toEqual({ tier: 'A' })
  })

})

describe('buildOwnPlayerRaceUpdateRequest', () => {
  it('sends race when it changes', () => {
    const payload = buildOwnPlayerRaceUpdateRequest(player(), 'PTZ')

    expect(payload).toEqual({ race: 'PTZ' })
  })

  it('returns null when race is unchanged', () => {
    const payload = buildOwnPlayerRaceUpdateRequest(player({ race: 'PTZ' }), 'PTZ')

    expect(payload).toBeNull()
  })
})

describe('resolveDefaultMmrForTier', () => {
  it('uses the C- floor MMR for C- tier corrections', () => {
    expect(resolveDefaultMmrForTier('C-')).toBe(200)
  })

  it('keeps D and unassigned boundary values distinct', () => {
    expect(resolveDefaultMmrForTier('D')).toBe(1)
    expect(resolveDefaultMmrForTier('UNASSIGNED')).toBe(0)
  })
})

describe('resolveEditableMmrValue', () => {
  it('preserves an existing non-negative MMR while editing', () => {
    expect(resolveEditableMmrValue(player({ tier: 'C-', currentMmr: 399 }))).toBe('399')
  })

  it('replaces a negative legacy MMR with the current tier default', () => {
    expect(resolveEditableMmrValue(player({ tier: 'C-', currentMmr: -20 }))).toBe('200')
  })

  it('uses zero for unassigned players with invalid legacy MMR', () => {
    expect(resolveEditableMmrValue(player({ tier: 'UNASSIGNED', currentMmr: -1 }))).toBe('0')
  })

  it('uses the tier default when current MMR is missing', () => {
    expect(resolveEditableMmrValue(player({ tier: 'B', currentMmr: undefined }))).toBe('1000')
  })
})
