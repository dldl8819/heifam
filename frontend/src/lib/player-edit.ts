import type { GroupPlayerUpdateRequest, PlayerRace, PlayerRosterItem, PlayerTierStatus } from '@/types/api'

type PlayerProfileEditInput = {
  nickname: string
  race: PlayerRace
  tier: PlayerTierStatus
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

  return payload
}
