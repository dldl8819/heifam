import { describe, expect, it } from 'vitest'

import { t } from './i18n'
import {
  buildTierChangeTargets,
  formatTierChangeDescription,
  formatTierChangeSnapshotDateLabel,
} from './player-tier-change'
import type { PlayerRosterItem } from '@/types/api'

const SNAPSHOT_AT = '2026-04-30T23:59:59+09:00'

function player(overrides: Partial<PlayerRosterItem> = {}): PlayerRosterItem {
  return {
    id: 1,
    nickname: '스냅샷',
    race: 'P',
    tier: 'B',
    currentMmr: 1220,
    wins: 0,
    losses: 0,
    games: 0,
    active: true,
    ...overrides,
  }
}

describe('buildTierChangeTargets', () => {
  it('includes players whose snapshot tier differs from live tier', () => {
    const targets = buildTierChangeTargets([
      player({
        lastTierSnapshotAt: SNAPSHOT_AT,
        lastTierSnapshotMmr: 980,
        lastTierSnapshotTier: 'B-',
        liveTier: 'B+',
      }),
    ])

    expect(targets).toEqual([
      {
        playerId: 1,
        nickname: '스냅샷',
        snapshotTier: 'B-',
        currentTier: 'B+',
        snapshotMmr: 980,
        currentMmr: 1220,
        snapshotAt: SNAPSHOT_AT,
      },
    ])
  })

  it('excludes players whose snapshot tier matches live tier', () => {
    const targets = buildTierChangeTargets([
      player({
        lastTierSnapshotAt: SNAPSHOT_AT,
        lastTierSnapshotMmr: 1220,
        lastTierSnapshotTier: 'B+',
        liveTier: 'B+',
      }),
    ])

    expect(targets).toEqual([])
  })

  it.each([
    ['lastTierSnapshotAt', { lastTierSnapshotAt: undefined }],
    ['lastTierSnapshotMmr', { lastTierSnapshotMmr: undefined }],
    ['lastTierSnapshotTier', { lastTierSnapshotTier: undefined }],
  ])('excludes players missing %s', (_, missingSnapshotField) => {
    const targets = buildTierChangeTargets([
      player({
        lastTierSnapshotAt: SNAPSHOT_AT,
        lastTierSnapshotMmr: 980,
        lastTierSnapshotTier: 'B-',
        liveTier: 'B+',
        ...missingSnapshotField,
      }),
    ])

    expect(targets).toEqual([])
  })

  it('does not fall back to base tier or base MMR for eligibility', () => {
    const targets = buildTierChangeTargets([
      player({
        baseMmr: 980,
        baseTier: 'B-',
        liveTier: 'B+',
      }),
    ])

    expect(targets).toEqual([])
  })

  it('uses snapshot tier to live tier and snapshot MMR to current MMR for display data', () => {
    const targets = buildTierChangeTargets([
      player({
        baseMmr: 1220,
        baseTier: 'B+',
        currentMmr: 1410,
        lastTierSnapshotAt: SNAPSHOT_AT,
        lastTierSnapshotMmr: 980,
        lastTierSnapshotTier: 'B-',
        liveTier: 'A-',
      }),
    ])

    expect(targets).toHaveLength(1)
    expect(targets[0]).toMatchObject({
      snapshotTier: 'B-',
      currentTier: 'A-',
      snapshotMmr: 980,
      currentMmr: 1410,
    })
  })
})

describe('tier change snapshot date description', () => {
  it('formats the 2026-04-30 KST snapshot date in the description', () => {
    const label = formatTierChangeSnapshotDateLabel(SNAPSHOT_AT)
    const description = formatTierChangeDescription(label, t)

    expect(label).toBe('4월 30일')
    expect(description).toBe(
      '4월 30일 MMR 기준 티어와 현재 MMR 기준 티어가 달라진 선수입니다. 단톡 안내용으로 확인해 주세요.'
    )
  })
})
