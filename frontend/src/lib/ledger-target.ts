import { findUniquePlayerByNicknamePrefix } from '@/lib/player-autocomplete'
import type { BalancePlayerOption } from '@/types/api'

export function normalizeLedgerTargetInput(rawValue: string, roster: BalancePlayerOption[]): string {
  return rawValue
    .split(',')
    .map((token) => token.trim())
    .filter((token) => token.length > 0)
    .map((token) => findUniquePlayerByNicknamePrefix(roster, token)?.nickname ?? token)
    .join(', ')
}
