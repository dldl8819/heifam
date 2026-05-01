import type { PlayerRosterItem, PlayerTierStatus } from '@/types/api'

export type TierChangeTarget = {
  playerId: number
  nickname: string
  snapshotTier: PlayerTierStatus
  currentTier: PlayerTierStatus
  snapshotMmr: number
  currentMmr: number
  snapshotAt: string
}

const TIER_DOWNLOAD_ORDER: Record<PlayerTierStatus, number> = {
  S: 0,
  'A+': 1,
  A: 2,
  'A-': 3,
  'B+': 4,
  B: 5,
  'B-': 6,
  'C+': 7,
  C: 8,
  'C-': 9,
  UNASSIGNED: 10,
}

export function toTierOrder(tier: PlayerTierStatus): number {
  return TIER_DOWNLOAD_ORDER[tier] ?? Number.MAX_SAFE_INTEGER
}

export function buildTierChangeTargets(rows: PlayerRosterItem[]): TierChangeTarget[] {
  return rows
    .flatMap((row) => {
      if (
        row.lastTierSnapshotAt === undefined ||
        row.lastTierSnapshotMmr === undefined ||
        row.lastTierSnapshotTier === undefined ||
        row.liveTier === undefined
      ) {
        return []
      }
      if (row.lastTierSnapshotTier === row.liveTier) {
        return []
      }
      if (row.tierChangeAcknowledgedTier === row.liveTier) {
        return []
      }

      return [{
        playerId: row.id,
        nickname: row.nickname,
        snapshotTier: row.lastTierSnapshotTier,
        currentTier: row.liveTier,
        snapshotMmr: row.lastTierSnapshotMmr,
        currentMmr: row.currentMmr ?? 0,
        snapshotAt: row.lastTierSnapshotAt,
      }]
    })
    .sort((a, b) => {
      const currentTierDiff = toTierOrder(a.currentTier) - toTierOrder(b.currentTier)
      if (currentTierDiff !== 0) {
        return currentTierDiff
      }

      if (b.currentMmr !== a.currentMmr) {
        return b.currentMmr - a.currentMmr
      }

      return a.nickname.localeCompare(b.nickname, 'ko-KR')
    })
}

export function formatTierChangeSnapshotDateLabel(value: string | undefined): string | null {
  if (!value) {
    return null
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return null
  }

  const parts = new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul',
    month: 'numeric',
    day: 'numeric',
  }).formatToParts(date)
  const month = parts.find((part) => part.type === 'month')?.value
  const day = parts.find((part) => part.type === 'day')?.value
  if (!month || !day) {
    return null
  }

  return `${month}월 ${day}일`
}

export function resolveTierChangeSnapshotDateLabel(
  rows: PlayerRosterItem[],
  targets: TierChangeTarget[] = buildTierChangeTargets(rows)
): string | null {
  for (const target of targets) {
    const label = formatTierChangeSnapshotDateLabel(target.snapshotAt)
    if (label !== null) {
      return label
    }
  }
  for (const row of rows) {
    const label = formatTierChangeSnapshotDateLabel(row.lastTierSnapshotAt)
    if (label !== null) {
      return label
    }
  }
  return null
}

type Translate = (path: string, params?: Record<string, string | number>) => string

export function formatTierChangeDescription(snapshotDateLabel: string | null, translate: Translate): string {
  return snapshotDateLabel === null
    ? translate('players.tierChange.description')
    : translate('players.tierChange.descriptionWithDate', { date: snapshotDateLabel })
}
