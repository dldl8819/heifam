import type { BalancePlayerOption } from '@/types/api'

function normalizeAutocompleteValue(value: string): string {
  return value.trim().toLowerCase()
}

export function findUniquePlayerByNicknamePrefix(
  players: BalancePlayerOption[],
  inputValue: string,
): BalancePlayerOption | null {
  const normalizedInput = normalizeAutocompleteValue(inputValue)
  if (normalizedInput.length === 0) {
    return null
  }

  const matches = players.filter((player) =>
    normalizeAutocompleteValue(player.nickname).startsWith(normalizedInput),
  )

  return matches.length === 1 ? matches[0] : null
}
