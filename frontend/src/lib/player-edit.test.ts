import { describe, expect, it } from 'vitest'

import {
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

  it('sends dormancy floor tier when it changes', () => {
    const payload = buildPlayerProfileUpdateRequest(player({ dormancyMmrFloorTier: 'B+' }), {
      nickname: 'PlayerAlpha',
      race: 'P',
      tier: 'B+',
      dormancyMmrFloorTier: 'A',
    })

    expect(payload).toEqual({ dormancyMmrFloorTier: 'A' })
  })

  it('treats missing dormancy floor tier as default policy', () => {
    const payload = buildPlayerProfileUpdateRequest(player(), {
      nickname: 'PlayerAlpha',
      race: 'P',
      tier: 'B+',
      dormancyMmrFloorTier: 'UNASSIGNED',
    })

    expect(payload).toEqual({})
  })

  it('sends default policy when clearing a configured dormancy floor tier', () => {
    const payload = buildPlayerProfileUpdateRequest(player({ dormancyMmrFloorTier: 'B+' }), {
      nickname: 'PlayerAlpha',
      race: 'P',
      tier: 'B+',
      dormancyMmrFloorTier: 'UNASSIGNED',
    })

    expect(payload).toEqual({ dormancyMmrFloorTier: 'UNASSIGNED' })
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
