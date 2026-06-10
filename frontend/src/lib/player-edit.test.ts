import { describe, expect, it } from 'vitest'

import { buildPlayerProfileUpdateRequest } from './player-edit'
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
