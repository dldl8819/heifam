export const DEFAULT_MULTI_BALANCE_MODE = 'MMR_FIRST'

export const MULTI_BALANCE_MODE_OPTIONS = [
  'MMR_FIRST',
  'DIVERSITY_FIRST',
  'RACE_DISTRIBUTION_FIRST',
]

export function isValidMultiBalanceMode(mode) {
  return MULTI_BALANCE_MODE_OPTIONS.includes(mode)
}

export function normalizeMultiBalanceMode(mode) {
  if (typeof mode !== 'string') {
    return DEFAULT_MULTI_BALANCE_MODE
  }

  const normalized = mode.trim().toUpperCase()
  return isValidMultiBalanceMode(normalized)
    ? normalized
    : DEFAULT_MULTI_BALANCE_MODE
}

export function buildMultiBalanceRequestPayload(groupId, playerIds, balanceMode, raceComposition) {
  return {
    groupId,
    playerIds,
    balanceMode: normalizeMultiBalanceMode(balanceMode),
    raceComposition: typeof raceComposition === 'string' && raceComposition.trim().length > 0
      ? raceComposition.trim().toUpperCase()
      : undefined,
  }
}

export function getMultiBalanceModeLabelKey(mode) {
  return `multiBalance.mode.options.${normalizeMultiBalanceMode(mode)}.label`
}
