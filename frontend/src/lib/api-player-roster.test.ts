import { describe, expect, it, vi } from 'vitest'

vi.mock('@/lib/supabase', () => ({
  supabase: {
    auth: {},
  },
}))

import { normalizePlayerRosterItem } from '@/lib/api'

describe('normalizePlayerRosterItem', () => {
  it('creates a local-only row key for a masked inactive player without an API id', () => {
    const item = normalizePlayerRosterItem(
      {
        nickname: '탈퇴한 회원',
        wins: 0,
        losses: 0,
        games: 0,
        active: false,
      },
      2
    )

    expect(item).toMatchObject({
      id: -3,
      nickname: '탈퇴한 회원',
      active: false,
      identityHidden: true,
      lifecycleStatus: 'ANONYMIZED',
    })
  })

  it('does not use an API id for a masked inactive player', () => {
    const item = normalizePlayerRosterItem(
      {
        id: 42,
        nickname: '탈퇴한 회원',
        active: false,
      },
      0
    )

    expect(item?.id).toBe(-1)
    expect(item?.identityHidden).toBe(true)
  })

  it('keeps the server id for an active player', () => {
    const item = normalizePlayerRosterItem({
      id: 7,
      nickname: 'ACTIVE_PLAYER',
      race: 'P',
      tier: 'A',
      active: true,
    })

    expect(item?.id).toBe(7)
    expect(item?.identityHidden).toBe(false)
    expect(item?.lifecycleStatus).toBe('ACTIVE')
  })

  it('keeps only the administrator-facing retained fields for an inactive player', () => {
    const item = normalizePlayerRosterItem({
      id: 11,
      nickname: 'RETAINED_PLAYER',
      race: 'T',
      wins: 4,
      losses: 3,
      games: 7,
      active: false,
      lifecycleStatus: 'INACTIVE',
      chatLeftAt: '2026-08-01T12:00:00Z',
      chatLeftReason: '운영상 비활성',
      identityRetainedUntil: '2031-08-01T12:00:00Z',
    })

    expect(item).toMatchObject({
      id: 11,
      nickname: 'RETAINED_PLAYER',
      race: 'T',
      wins: 4,
      losses: 3,
      games: 7,
      active: false,
      identityHidden: false,
      lifecycleStatus: 'INACTIVE',
      chatLeftReason: '운영상 비활성',
      identityRetainedUntil: '2031-08-01T12:00:00Z',
    })
  })

  it('keeps a withdrawn row read-only without treating retained nickname as anonymous', () => {
    const item = normalizePlayerRosterItem(
      {
        nickname: 'WITHDRAWN_PLAYER',
        race: 'P',
        wins: 1,
        losses: 2,
        games: 3,
        active: false,
        lifecycleStatus: 'WITHDRAWN',
      },
      4
    )

    expect(item).toMatchObject({
      id: -5,
      nickname: 'WITHDRAWN_PLAYER',
      active: false,
      identityHidden: false,
      lifecycleStatus: 'WITHDRAWN',
    })
  })
})
