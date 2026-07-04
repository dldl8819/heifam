'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import { useAdminAuth } from '@/lib/admin-auth'
import { apiClient } from '@/lib/api'
import { Alert, AlertContent, AlertDescription, AlertIcon, AlertTitle } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { PlayerGameTypeStatsModal } from '@/components/player-game-type-stats-modal'
import { t } from '@/lib/i18n'
import { useMmrVisibility } from '@/lib/mmr-visibility'
import type { GroupPlayerRaceStatsItem, PlayerTierStatus, RankingItem } from '@/types/api'

const TEMP_GROUP_ID = 1

function formatWinRate(value: number): string {
  const percentage = value <= 1 ? value * 100 : value
  return `${percentage.toFixed(2)}%`
}

function getRankClass(rank: number): string {
  if (rank === 1) {
    return 'bg-amber-50'
  }

  if (rank === 2) {
    return 'bg-slate-100'
  }

  if (rank === 3) {
    return 'bg-orange-50'
  }

  return 'bg-white'
}

function formatStreak(value: string): string {
  if (!value || value.length < 2) {
    return '-'
  }

  const sign = value.charAt(0)
  const count = value.slice(1)
  if (sign === 'W') {
    return t('ranking.streak.win', { count })
  }
  if (sign === 'L') {
    return t('ranking.streak.loss', { count })
  }
  return value
}

function isHotWinStreak(value: string): boolean {
  if (!value || value.charAt(0) !== 'W') {
    return false
  }

  const count = Number(value.slice(1))
  return Number.isInteger(count) && count >= 3
}

function formatLast10(value: string): string {
  if (!value) {
    return '-'
  }

  return value
    .replace(/W/g, t('ranking.last10.win'))
    .replace(/L/g, t('ranking.last10.loss'))
}

function formatTier(value: string): string {
  return value === 'UNASSIGNED' ? t('common.tierBoard.unassigned') : value
}

function resolveCurrentKstMonthLabel(): string {
  const parts = new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: 'numeric',
  }).formatToParts(new Date())
  const year = parts.find((part) => part.type === 'year')?.value ?? ''
  const month = parts.find((part) => part.type === 'month')?.value ?? ''

  return t('ranking.monthlyPeriod', { year, month })
}

export default function RankingPage() {
  const { isSuperAdmin, isLoading: authLoading } = useAdminAuth()
  const { mmrVisible } = useMmrVisibility()
  const showMmr = isSuperAdmin && mmrVisible
  const [rows, setRows] = useState<RankingItem[]>([])
  const [tierByNickname, setTierByNickname] = useState<Map<string, PlayerTierStatus>>(new Map())
  const [playerIdByNickname, setPlayerIdByNickname] = useState<Map<string, number>>(new Map())
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)
  const [gameTypeStatsPlayer, setGameTypeStatsPlayer] =
    useState<{ id: number; nickname: string } | null>(null)
  const [gameTypeStats, setGameTypeStats] = useState<GroupPlayerRaceStatsItem | null>(null)
  const [gameTypeStatsLoading, setGameTypeStatsLoading] = useState<boolean>(false)
  const [gameTypeStatsError, setGameTypeStatsError] = useState<string | null>(null)
  const [monthlyPeriodLabel, setMonthlyPeriodLabel] = useState<string>('')

  useEffect(() => {
    setMonthlyPeriodLabel(resolveCurrentKstMonthLabel())
  }, [])

  useEffect(() => {
    let active = true

    if (authLoading) {
      return () => {
        active = false
      }
    }

    const fetchRanking = async () => {
      setLoading(true)
      setError(null)

      try {
        const [rankingResponse, rosterResponse] = await Promise.all([
          apiClient.getRanking(TEMP_GROUP_ID),
          apiClient.getGroupPlayers(TEMP_GROUP_ID).catch(() => []),
        ])

        if (!active) {
          return
        }

        setRows(rankingResponse)
        setTierByNickname(
          new Map(
            rosterResponse.map((player) => [
              player.nickname,
              player.tier,
            ])
          )
        )
        setPlayerIdByNickname(
          new Map(
            rosterResponse.map((player) => [
              player.nickname,
              player.id,
            ])
          )
        )
      } catch {
        if (!active) {
          return
        }

        setError(t('ranking.loadError'))
      } finally {
        if (active) {
          setLoading(false)
        }
      }
    }

    void fetchRanking()

    return () => {
      active = false
    }
  }, [authLoading, isSuperAdmin, showMmr])

  const sortedRows = useMemo(
    () =>
      [...rows].sort((a, b) => {
        const aMmr = typeof a.currentMmr === 'number' && Number.isFinite(a.currentMmr)
          ? a.currentMmr
          : null
        const bMmr = typeof b.currentMmr === 'number' && Number.isFinite(b.currentMmr)
          ? b.currentMmr
          : null

        if (aMmr !== null || bMmr !== null) {
          if (aMmr === null) {
            return 1
          }
          if (bMmr === null) {
            return -1
          }
          if (bMmr !== aMmr) {
            return bMmr - aMmr
          }
        }

        if (a.rank !== b.rank) {
          return a.rank - b.rank
        }
        if (b.wins !== a.wins) {
          return b.wins - a.wins
        }
        if (b.games !== a.games) {
          return b.games - a.games
        }
        return a.nickname.localeCompare(b.nickname, 'ko-KR')
      }),
    [rows]
  )

  const resolveDisplayTier = (row: RankingItem): PlayerTierStatus =>
    tierByNickname.get(row.nickname) ?? row.tier

  const tableColumnCount = showMmr ? 13 : 11

  const handleOpenGameTypeStats = useCallback(async (row: RankingItem) => {
    const playerId = playerIdByNickname.get(row.nickname)

    setGameTypeStatsPlayer({ id: playerId ?? -1, nickname: row.nickname })
    setGameTypeStats(null)
    setGameTypeStatsError(null)

    if (typeof playerId !== 'number') {
      setGameTypeStatsError(t('statsModal.playerNotFound'))
      setGameTypeStatsLoading(false)
      return
    }

    setGameTypeStatsLoading(true)
    try {
      const response = await apiClient.getGroupPlayerMonthlyRaceStatsForPlayer(TEMP_GROUP_ID, playerId)
      setGameTypeStats(response)
    } catch {
      setGameTypeStatsError(t('statsModal.loadError'))
    } finally {
      setGameTypeStatsLoading(false)
    }
  }, [playerIdByNickname])

  const handleCloseGameTypeStats = useCallback(() => {
    setGameTypeStatsPlayer(null)
    setGameTypeStats(null)
    setGameTypeStatsError(null)
    setGameTypeStatsLoading(false)
  }, [])

  return (
    <section className="space-y-6">
      <PlayerGameTypeStatsModal
        open={gameTypeStatsPlayer !== null}
        playerName={gameTypeStatsPlayer?.nickname ?? ''}
        stats={gameTypeStats}
        loading={gameTypeStatsLoading}
        error={gameTypeStatsError}
        onClose={handleCloseGameTypeStats}
      />

      <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-sm">
        {monthlyPeriodLabel.length > 0 && (
          <div className="min-w-full border-b border-slate-100 px-4 py-3 text-sm font-semibold text-slate-700">
            {monthlyPeriodLabel}
          </div>
        )}
        <table className="min-w-full text-left text-sm">
          <thead className="bg-slate-50 text-xs tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3">{t('ranking.table.rank')}</th>
              <th className="px-4 py-3">{t('ranking.table.nickname')}</th>
              <th className="px-4 py-3">{t('ranking.table.race')}</th>
              <th className="px-4 py-3">{t('ranking.table.tier')}</th>
              {showMmr && <th className="px-4 py-3">{t('ranking.table.currentMmr')}</th>}
              <th className="px-4 py-3">{t('ranking.table.wins')}</th>
              <th className="px-4 py-3">{t('ranking.table.losses')}</th>
              <th className="px-4 py-3">{t('ranking.table.games')}</th>
              <th className="px-4 py-3">{t('ranking.table.winRate')}</th>
              <th className="px-4 py-3">{t('ranking.table.gameTypeStats')}</th>
              <th className="px-4 py-3">{t('ranking.table.streak')}</th>
              <th className="px-4 py-3">{t('ranking.table.last10')}</th>
              {showMmr && <th className="px-4 py-3">{t('ranking.table.mmrDelta')}</th>}
            </tr>
          </thead>

          <tbody>
            {loading &&
              (
                <tr className="border-t border-slate-100">
                  <td className="px-4 py-3" colSpan={tableColumnCount}>
                    <LoadingIndicator label={t('common.loading')} />
                  </td>
                </tr>
              )}

            {!loading && error && (
              <tr className="border-t border-slate-100">
                <td className="px-4 py-8 text-center text-sm text-slate-500" colSpan={tableColumnCount}>
                  <Alert variant="destructive" appearance="light">
                    <AlertIcon icon="destructive">!</AlertIcon>
                    <AlertContent>
                      <AlertTitle>{t('common.errorPrefix')}</AlertTitle>
                      <AlertDescription>{error}</AlertDescription>
                    </AlertContent>
                  </Alert>
                </td>
              </tr>
            )}

            {!loading && !error && sortedRows.length === 0 && (
              <tr className="border-t border-slate-100">
                <td className="px-4 py-8 text-center text-sm text-slate-500" colSpan={tableColumnCount}>
                  {t('ranking.empty')}
                </td>
              </tr>
            )}

            {!loading &&
              !error &&
              sortedRows.map((row) => {
                const rowMmrDelta =
                  typeof row.mmrDelta === 'number' ? row.mmrDelta : null
                const playerId = playerIdByNickname.get(row.nickname) ?? null
                const isStatsLoadingForRow =
                  gameTypeStatsLoading && gameTypeStatsPlayer?.id === playerId
                const hotStreak = isHotWinStreak(row.streak)

                return (
                  <tr
                    key={`${row.rank}-${row.nickname}`}
                    className={`border-t border-slate-100 ${getRankClass(row.rank)}`}
                  >
                    <td className="px-4 py-3 font-semibold text-slate-900">{row.rank}</td>
                    <td className="px-4 py-3 font-medium text-slate-900">
                      {hotStreak && (
                        <span
                          className="mr-1.5 inline-block"
                          title={t('ranking.hotStreakLabel')}
                          aria-label={t('ranking.hotStreakLabel')}
                          role="img"
                        >
                          🔥
                        </span>
                      )}
                      {row.nickname}
                    </td>
                    <td className="px-4 py-3 text-slate-700">{row.race}</td>
                    <td className="px-4 py-3 text-slate-700">{formatTier(resolveDisplayTier(row))}</td>
                    {showMmr && (
                      <td className="px-4 py-3 text-slate-700">
                        {typeof row.currentMmr === 'number' ? row.currentMmr : '-'}
                      </td>
                    )}
                    <td className="px-4 py-3 text-slate-700">{row.wins}</td>
                    <td className="px-4 py-3 text-slate-700">{row.losses}</td>
                    <td className="px-4 py-3 text-slate-700">{row.games}</td>
                    <td className="px-4 py-3 text-slate-700">{formatWinRate(row.winRate)}</td>
                    <td className="px-4 py-3">
                      <button
                        type="button"
                        disabled={typeof playerId !== 'number' || isStatsLoadingForRow}
                        onClick={() => handleOpenGameTypeStats(row)}
                        className="rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-700 transition-colors hover:border-emerald-600 hover:bg-emerald-600 hover:text-white disabled:cursor-not-allowed disabled:opacity-60"
                      >
                        {isStatsLoadingForRow ? t('statsModal.buttonLoading') : t('statsModal.button')}
                      </button>
                    </td>
                    <td className="px-4 py-3 text-slate-700">{formatStreak(row.streak)}</td>
                    <td className="px-4 py-3 text-xs text-slate-700">{formatLast10(row.last10)}</td>
                    {showMmr && rowMmrDelta !== null ? (
                      <td
                        className={`px-4 py-3 font-medium ${
                          rowMmrDelta >= 0 ? 'text-emerald-600' : 'text-rose-600'
                        }`}
                      >
                        {rowMmrDelta >= 0 ? `+${rowMmrDelta}` : rowMmrDelta}
                      </td>
                    ) : showMmr ? (
                      <td className="px-4 py-3 text-slate-700">-</td>
                    ) : null}
                  </tr>
                )
              })}
          </tbody>
        </table>
      </div>
    </section>
  )
}
