import type { PlayerTierStatus } from '@/types/api'

const TIER_ORDER: Record<PlayerTierStatus, number> = {
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
  D: 10,
  UNASSIGNED: 11,
}

export function toTierOrder(tier: PlayerTierStatus): number {
  return TIER_ORDER[tier] ?? Number.MAX_SAFE_INTEGER
}
