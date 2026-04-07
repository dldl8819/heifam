import type { MultiBalanceMode, MultiBalanceRequest } from '@/types/api'

export const DEFAULT_MULTI_BALANCE_MODE: MultiBalanceMode
export const MULTI_BALANCE_MODE_OPTIONS: MultiBalanceMode[]

export function isValidMultiBalanceMode(mode: string): boolean
export function normalizeMultiBalanceMode(mode: string | null | undefined): MultiBalanceMode
export function buildMultiBalanceRequestPayload(
  groupId: number,
  playerIds: number[],
  balanceMode: string | null | undefined,
  raceComposition?: string | null | undefined
): MultiBalanceRequest
export function getMultiBalanceModeLabelKey(mode: string | null | undefined): string
