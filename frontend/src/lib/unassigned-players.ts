import type { BalancePlayerOption } from '@/types/api'

/**
 * The players among these whose tier is still to be set (배정 필요), by nickname. Teams with them can
 * be balanced and played, but their result cannot be entered until an admin sets the tier; the
 * backend refuses it as well.
 */
export function unassignedNicknames(
  playerIds: ReadonlyArray<number | null | undefined>,
  players: ReadonlyArray<BalancePlayerOption>
): string[] {
  const wanted = new Set(playerIds.filter((id): id is number => typeof id === 'number'))
  return players
    .filter((player) => wanted.has(player.id) && player.tier === 'UNASSIGNED')
    .map((player) => player.nickname)
    .sort((first, second) => first.localeCompare(second, 'ko-KR'))
}
