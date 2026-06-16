import type { GroupPlayerUpdateRequest, PlayerRace, PlayerRosterItem, PlayerTierStatus } from '@/types/api'

type PlayerProfileEditInput = {
  nickname: string
  race: PlayerRace
  tier: PlayerTierStatus
  dormancyMmrFloorTier?: PlayerTierStatus
}

const DEFAULT_MMR_BY_TIER: Record<PlayerTierStatus, number> = {
  S: 2000,
  'A+': 1800,
  A: 1600,
  'A-': 1400,
  'B+': 1200,
  B: 1000,
  'B-': 800,
  'C+': 600,
  C: 400,
  'C-': 200,
  D: 1,
  UNASSIGNED: 0,
}

export function resolveDefaultMmrForTier(tier: PlayerTierStatus): number {
  return DEFAULT_MMR_BY_TIER[tier] ?? 0
}

export function resolveEditableMmrValue(player: PlayerRosterItem): string {
  if (
    typeof player.currentMmr === 'number' &&
    Number.isFinite(player.currentMmr) &&
    player.currentMmr >= 0
  ) {
    return String(player.currentMmr)
  }

  return String(resolveDefaultMmrForTier(player.tier))
}

export function buildPlayerProfileUpdateRequest(
  current: PlayerRosterItem,
  next: PlayerProfileEditInput
): GroupPlayerUpdateRequest {
  const payload: GroupPlayerUpdateRequest = {}
  const nextNickname = next.nickname.trim()

  if (nextNickname !== current.nickname) {
    payload.nickname = nextNickname
  }
  if (next.race !== current.race) {
    payload.race = next.race
  }
  if (next.tier !== current.tier) {
    payload.tier = next.tier
  }
  if (
    next.dormancyMmrFloorTier !== undefined &&
    next.dormancyMmrFloorTier !== (current.dormancyMmrFloorTier ?? 'UNASSIGNED')
  ) {
    payload.dormancyMmrFloorTier = next.dormancyMmrFloorTier
  }

  return payload
}
