import type { BalanceResponse, RankingItem } from '@/types/api'
import { t } from '@/lib/i18n'

export const LAST_BALANCE_RESULT_STORAGE_KEY = 'heifam:last-balance-result'

export type StoredBalanceResult = {
  generatedAt: string
  result: BalanceResponse
}

export const DASHBOARD_FALLBACK_RANKING: RankingItem[] = [
  {
    rank: 1,
    nickname: 'Pulse',
    race: 'P',
    tier: 'S',
    currentMmr: 1620,
    wins: 20,
    losses: 9,
    games: 29,
    winRate: 68.97,
    streak: 'W3',
    last10: 'WWLWWWLWWW',
    mmrDelta: 42,
  },
  {
    rank: 2,
    nickname: 'WarpNode',
    race: 'T',
    tier: 'S',
    currentMmr: 1540,
    wins: 18,
    losses: 11,
    games: 29,
    winRate: 62.07,
    streak: 'W1',
    last10: 'WLWLWWLWWW',
    mmrDelta: 18,
  },
  {
    rank: 3,
    nickname: 'SwarmCore',
    race: 'Z',
    tier: 'S',
    currentMmr: 1495,
    wins: 16,
    losses: 13,
    games: 29,
    winRate: 55.17,
    streak: 'L1',
    last10: 'LWLWWLLWWW',
    mmrDelta: -8,
  },
  {
    rank: 4,
    nickname: 'NexusFox',
    race: 'P',
    tier: 'S',
    currentMmr: 1450,
    wins: 14,
    losses: 14,
    games: 28,
    winRate: 50.0,
    streak: 'W1',
    last10: 'WLLWWLLWLW',
    mmrDelta: 4,
  },
  {
    rank: 5,
    nickname: 'SiegeRain',
    race: 'T',
    tier: 'S',
    currentMmr: 1410,
    wins: 12,
    losses: 15,
    games: 27,
    winRate: 44.44,
    streak: 'L2',
    last10: 'LLWLWLWWLL',
    mmrDelta: -16,
  },
]

export const DASHBOARD_RECENT_BALANCE_PLACEHOLDER: StoredBalanceResult = {
  generatedAt: t('dashboard.recentBalance.none'),
  result: {
    teamSize: 3,
    homeTeam: [
      { name: 'Player A', mmr: 1500 },
      { name: 'Player B', mmr: 1450 },
      { name: 'Player C', mmr: 1400 },
    ],
    awayTeam: [
      { name: 'Player D', mmr: 1490 },
      { name: 'Player E', mmr: 1460 },
      { name: 'Player F', mmr: 1410 },
    ],
    homeMmr: 4350,
    awayMmr: 4360,
    mmrDiff: 10,
    expectedHomeWinRate: 0.4964,
  },
}

function isBalanceResponse(value: unknown): value is BalanceResponse {
  if (value === null || typeof value !== 'object') {
    return false
  }

  const source = value as Record<string, unknown>
  const hasExpectedRate =
    typeof source.expectedHomeWinRate === 'number' ||
    typeof source.expectedWinRate === 'number'
  const hasTeamSize =
    source.teamSize === undefined || typeof source.teamSize === 'number'
  return (
    hasTeamSize &&
    Array.isArray(source.homeTeam) &&
    Array.isArray(source.awayTeam) &&
    typeof source.homeMmr === 'number' &&
    typeof source.awayMmr === 'number' &&
    typeof source.mmrDiff === 'number' &&
    hasExpectedRate
  )
}

export function parseStoredBalanceResult(value: unknown): StoredBalanceResult | null {
  if (value === null || typeof value !== 'object') {
    return null
  }

  const source = value as Record<string, unknown>
  if (typeof source.generatedAt !== 'string') {
    return null
  }

  if (!isBalanceResponse(source.result)) {
    return null
  }

  const rawResult = source.result as Record<string, unknown>
  const expectedHomeWinRate =
    typeof rawResult.expectedHomeWinRate === 'number'
      ? rawResult.expectedHomeWinRate
      : typeof rawResult.expectedWinRate === 'number'
        ? rawResult.expectedWinRate
        : 0.5
  const teamSize = typeof rawResult.teamSize === 'number' ? rawResult.teamSize : 3

  return {
    generatedAt: source.generatedAt,
    result: {
      ...rawResult,
      teamSize,
      expectedHomeWinRate,
    } as BalanceResponse,
  }
}
