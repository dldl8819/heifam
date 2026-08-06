import type { PlayerRosterItem } from '@/types/api'

type PlayerActivityTransition = {
  nextActive: boolean
  chatLeftAt?: string
  chatLeftReason?: string
  chatRejoinedAt?: string
}

export function applyPlayerActivityTransition(
  row: PlayerRosterItem,
  transition: PlayerActivityTransition
): PlayerRosterItem {
  const { nextActive, chatLeftAt, chatLeftReason, chatRejoinedAt } = transition

  return {
    ...row,
    active: nextActive,
    identityHidden: nextActive ? false : row.identityHidden,
    lifecycleStatus: nextActive ? 'ACTIVE' : 'INACTIVE',
    identityRetainedUntil: undefined,
    chatLeftAt: nextActive ? undefined : chatLeftAt,
    chatLeftReason: nextActive ? undefined : chatLeftReason,
    chatRejoinedAt: nextActive ? chatRejoinedAt : undefined,
  }
}
