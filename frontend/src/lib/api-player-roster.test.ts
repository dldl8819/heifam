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
  })
})
