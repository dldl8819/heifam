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
})
