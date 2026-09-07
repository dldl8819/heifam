import type { PlayerTierStatus } from '@/types/api'

const TIER_ORDER: Record<PlayerTierStatus, number> = {
  'S+': 0,
  S: 1,
  'S-': 2,
  'A+': 3,
  A: 4,
  'A-': 5,
  'B+': 6,
  B: 7,
  'B-': 8,
  'C+': 9,
  C: 10,
  'C-': 11,
  D: 12,
  UNASSIGNED: 13,
}

export function toTierOrder(tier: PlayerTierStatus): number {
  return TIER_ORDER[tier] ?? Number.MAX_SAFE_INTEGER
}
