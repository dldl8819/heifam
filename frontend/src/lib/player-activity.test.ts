import { describe, expect, it } from 'vitest'
import { applyPlayerActivityTransition } from '@/lib/player-activity'
import type { PlayerRosterItem } from '@/types/api'

function inactivePlayer(): PlayerRosterItem {
  return {
    id: 17,
    nickname: 'INACTIVE_PLAYER',
    race: 'T',
    tier: 'UNASSIGNED',
    wins: 2,
    losses: 1,
    games: 3,
    active: false,
    identityHidden: false,
    lifecycleStatus: 'INACTIVE',
    identityRetainedUntil: '2031-08-01T12:00:00Z',
    chatLeftAt: '2026-08-01T12:00:00Z',
    chatLeftReason: '운영상 비활성',
  }
}

describe('applyPlayerActivityTransition', () => {
  it('clears inactive retention state after reactivation', () => {
    const updated = applyPlayerActivityTransition(inactivePlayer(), {
      nextActive: true,
      chatRejoinedAt: '2026-08-06T12:00:00Z',
    })

    expect(updated).toMatchObject({
      active: true,
      identityHidden: false,
      lifecycleStatus: 'ACTIVE',
      chatRejoinedAt: '2026-08-06T12:00:00Z',
    })
    expect(updated.identityRetainedUntil).toBeUndefined()
    expect(updated.chatLeftAt).toBeUndefined()
    expect(updated.chatLeftReason).toBeUndefined()
  })

  it('marks a newly deactivated player as operationally inactive', () => {
    const updated = applyPlayerActivityTransition(
      { ...inactivePlayer(), active: true, lifecycleStatus: 'ACTIVE' },
      {
        nextActive: false,
        chatLeftAt: '2026-08-06T12:00:00Z',
        chatLeftReason: '운영상 비활성',
      }
    )

    expect(updated).toMatchObject({
      active: false,
      lifecycleStatus: 'INACTIVE',
      chatLeftAt: '2026-08-06T12:00:00Z',
      chatLeftReason: '운영상 비활성',
    })
    expect(updated.identityRetainedUntil).toBeUndefined()
  })
})
