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
    const inactiveAt = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString()
    const retainedUntil = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString()
    const item = normalizePlayerRosterItem({
      id: 11,
      nickname: 'RETAINED_PLAYER',
      race: 'T',
      wins: 4,
      losses: 3,
      games: 7,
      tier: 'A+',
      currentMmr: 1700,
      lastTierSnapshotAt: '2026-08-01T12:00:00Z',
      lastTierSnapshotMmr: 1700,
      chatRejoinedAt: '2026-08-02T12:00:00Z',
      active: false,
      lifecycleStatus: 'INACTIVE',
      chatLeftAt: inactiveAt,
      chatLeftReason: '운영 정책',
      identityRetainedUntil: retainedUntil,
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
      chatLeftReason: '운영 정책',
      identityRetainedUntil: retainedUntil,
    })
    expect(item?.tier).toBe('UNASSIGNED')
    expect(item?.currentMmr).toBeUndefined()
    expect(item?.lastTierSnapshotAt).toBeUndefined()
    expect(item?.lastTierSnapshotMmr).toBeUndefined()
    expect(item?.chatRejoinedAt).toBeUndefined()
  })

  it('fails closed when a legacy withdrawn row contains identifying fields', () => {
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
      nickname: '탈퇴한 회원',
      active: false,
      identityHidden: true,
      lifecycleStatus: 'WITHDRAWN',
      wins: 0,
      losses: 0,
      games: 0,
    })
    expect(item?.race).toBe('P')
  })

  it('fails closed when an inactive response omits its lifecycle status', () => {
    const item = normalizePlayerRosterItem(
      {
        id: 43,
        nickname: 'LEGACY_INACTIVE_PLAYER',
        race: 'Z',
        wins: 5,
        losses: 4,
        games: 9,
        active: false,
      },
      5
    )

    expect(item).toMatchObject({
      id: -6,
      nickname: '탈퇴한 회원',
      active: false,
      identityHidden: true,
      lifecycleStatus: 'ANONYMIZED',
      wins: 0,
      losses: 0,
      games: 0,
    })
    expect(item?.race).toBe('P')
  })

  it.each([
    ['missing deadline', undefined],
    ['invalid deadline', 'not-a-date'],
    ['expired deadline', new Date(Date.now() - 1000).toISOString()],
  ])('fails closed for retained inactive identity with %s', (_label, identityRetainedUntil) => {
    const item = normalizePlayerRosterItem(
      {
        id: 44,
        nickname: 'INVALID_RETENTION_PLAYER',
        race: 'P',
        active: false,
        lifecycleStatus: 'INACTIVE',
        identityRetainedUntil,
      },
      6
    )

    expect(item).toMatchObject({
      id: -7,
      nickname: '탈퇴한 회원',
      identityHidden: true,
    })
  })

  it('fails closed for unsupported or inconsistent lifecycle values', () => {
    const unsupported = normalizePlayerRosterItem(
      {
        id: 45,
        nickname: 'UNSUPPORTED_LIFECYCLE_PLAYER',
        active: true,
        lifecycleStatus: 'REMOVED',
      },
      7
    )
    const inconsistent = normalizePlayerRosterItem(
      {
        id: 46,
        nickname: 'INCONSISTENT_LIFECYCLE_PLAYER',
        active: true,
        lifecycleStatus: 'INACTIVE',
        identityRetainedUntil: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
      },
      8
    )

    expect(unsupported).toMatchObject({ id: -8, identityHidden: true })
    expect(inconsistent).toMatchObject({ id: -9, identityHidden: true })
  })

  it('fails closed when a retained inactive row has a free-text reason', () => {
    const item = normalizePlayerRosterItem({
      id: 47,
      nickname: 'RETAINED_PLAYER',
      race: 'T',
      active: false,
      lifecycleStatus: 'INACTIVE',
      chatLeftReason: 'FREE_TEXT_REASON',
      chatLeftAt: new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString(),
      identityRetainedUntil: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
    })

    expect(item?.identityHidden).toBe(true)
    expect(item?.chatLeftReason).toBeUndefined()
  })

  it('fails closed when a retained inactive row has no valid inactive time', () => {
    const item = normalizePlayerRosterItem({
      id: 48,
      nickname: 'RETAINED_PLAYER',
      race: 'T',
      active: false,
      lifecycleStatus: 'INACTIVE',
      chatLeftReason: '운영 정책',
      identityRetainedUntil: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
    })

    expect(item?.identityHidden).toBe(true)
  })

  it('fails closed when an active status is omitted', () => {
    const item = normalizePlayerRosterItem({
      id: 50,
      nickname: 'MALFORMED_PLAYER',
      race: 'P',
      lifecycleStatus: 'ACTIVE',
    })

    expect(item).toMatchObject({
      id: -1,
      nickname: '탈퇴한 회원',
      identityHidden: true,
    })
  })

  it.each(['', '   ', '탈퇴한 회원'])(
    'fails closed for a retained inactive row with hidden nickname %j',
    (nickname) => {
      const item = normalizePlayerRosterItem({
        id: 51,
        nickname,
        race: 'T',
        active: false,
        lifecycleStatus: 'INACTIVE',
        chatLeftAt: new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString(),
        chatLeftReason: '운영 정책',
        identityRetainedUntil: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
      })

      expect(item?.identityHidden).toBe(true)
      expect(item?.nickname).toBe('탈퇴한 회원')
    }
  )

  it('fails closed when an inactive retention deadline exceeds one calendar year', () => {
    const inactiveAt = new Date(Date.now() - 24 * 60 * 60 * 1000)
    const excessiveDeadline = new Date(inactiveAt)
    excessiveDeadline.setUTCFullYear(excessiveDeadline.getUTCFullYear() + 1)
    excessiveDeadline.setUTCDate(excessiveDeadline.getUTCDate() + 1)
    const item = normalizePlayerRosterItem({
      id: 49,
      nickname: 'RETAINED_PLAYER',
      race: 'T',
      active: false,
      lifecycleStatus: 'INACTIVE',
      chatLeftAt: inactiveAt.toISOString(),
      chatLeftReason: '운영 정책',
      identityRetainedUntil: excessiveDeadline.toISOString(),
    })

    expect(item?.identityHidden).toBe(true)
  })
})
