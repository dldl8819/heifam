'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import { useAdminAuth } from '@/lib/admin-auth'
import { apiClient } from '@/lib/api'
import { Alert, AlertContent, AlertDescription, AlertIcon, AlertTitle } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { PlayerGameTypeStatsModal } from '@/components/player-game-type-stats-modal'
import { t } from '@/lib/i18n'
import { useMmrVisibility } from '@/lib/mmr-visibility'
import {
  DEFAULT_RANKING_SORT,
  MMR_SORT_KEYS,
  nextRankingSort,
  sortRankingRows,
  type RankingSort,
  type RankingSortKey,
} from '@/lib/ranking-sort'
import type { GroupPlayerRaceStatsItem, PlayerTierStatus, RankingItem } from '@/types/api'

const TEMP_GROUP_ID = 1

const SORT_LABEL_KEYS: Record<RankingSortKey, string> = {
  rank: 'ranking.table.rank',
  currentMmr: 'ranking.table.currentMmr',
  peakMmr: 'ranking.table.peakMmr',
  lowestMmr: 'ranking.table.lowestMmr',
  wins: 'ranking.table.wins',
  losses: 'ranking.table.losses',
  games: 'ranking.table.games',
  winRate: 'ranking.table.winRate',
  streak: 'ranking.table.streak',
  mmrDelta: 'ranking.table.mmrDelta',
}

// What a screen reader says of a sorted column. A rank runs 1, 2, 3 when the best come first.
function ariaSortOf(sort: RankingSort): 'ascending' | 'descending' {
  const highFirst = sort.order === 'high'
  if (sort.key === 'rank') {
    return highFirst ? 'ascending' : 'descending'
  }
  return highFirst ? 'descending' : 'ascending'
}

// A column title that orders the table: from the highest on the first click, turned around on the next.
function SortableHeader({
  sortKey,
  sort,
  onSort,
}: {
  sortKey: RankingSortKey
  sort: RankingSort
  onSort: (key: RankingSortKey) => void
}) {
  const active = sort.key === sortKey
  return (
    <th className="px-4 py-3" aria-sort={active ? ariaSortOf(sort) : 'none'}>
      <button
        type="button"
        onClick={() => onSort(sortKey)}
        className={`inline-flex items-center gap-1 whitespace-nowrap rounded-sm tracking-wide hover:text-slate-900 dark:hover:text-white ${
          active ? 'font-semibold text-slate-900 dark:text-white' : ''
        }`}
      >
        {t(SORT_LABEL_KEYS[sortKey])}
        <span aria-hidden="true">{active ? (sort.order === 'high' ? '▼' : '▲') : '↕'}</span>
      </button>
    </th>
  )
}

function formatWinRate(value: number): string {
  const percentage = value <= 1 ? value * 100 : value
  return `${percentage.toFixed(2)}%`
}

function getRankClass(rank: number): string {
  if (rank === 1) {
    return 'bg-amber-50 dark:bg-amber-950/30'
  }

  if (rank === 2) {
    return 'bg-slate-100 dark:bg-slate-800'
  }

  if (rank === 3) {
    return 'bg-orange-50 dark:bg-orange-950/30'
  }

  return 'bg-white dark:bg-slate-900'
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

function resolveStreakEmoji(value: string): string | null {
  if (!value || value.length < 2) {
    return null
  }

  const sign = value.charAt(0)
  const count = Number(value.slice(1))
  if (!Number.isInteger(count)) {
    return null
  }

  if (sign === 'W') {
    if (count >= 10) {
      return '👑🔥'
    }
    if (count >= 5) {
      return '🔥🔥'
    }
    if (count >= 3) {
      return '🔥'
    }
    return null
  }

  if (sign === 'L') {
    if (count >= 10) {
      return '💀'
    }
    if (count >= 5) {
      return '🧊'
    }
    if (count >= 3) {
      return '🥶'
    }
    return null
  }

  return null
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
  const { canViewMmr, isLoading: authLoading } = useAdminAuth()
  const { mmrVisible } = useMmrVisibility()
  const showMmr = canViewMmr && mmrVisible
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
  const [sort, setSort] = useState<RankingSort>(DEFAULT_RANKING_SORT)
  const [sortAnnouncement, setSortAnnouncement] = useState<string>('')

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
              player.liveTier ?? player.tier,
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
  }, [authLoading, canViewMmr, showMmr])

  // A column hidden from this reader (MMR switched off) orders nothing: the ranking's own order then.
  const activeSort = useMemo<RankingSort>(
    () => (!showMmr && MMR_SORT_KEYS.includes(sort.key) ? DEFAULT_RANKING_SORT : sort),
    [showMmr, sort]
  )
  // The server's rank already orders by MMR and puts unassigned players last; a column title reorders.
  const sortedRows = useMemo(() => sortRankingRows(rows, activeSort), [rows, activeSort])

  const handleSort = useCallback((key: RankingSortKey) => {
    const next = nextRankingSort(activeSort, key)
    setSort(next)
    setSortAnnouncement(
      t('ranking.sort.announce', {
        column: t(SORT_LABEL_KEYS[key]),
        order: t(next.order === 'high' ? 'ranking.sort.high' : 'ranking.sort.low'),
      })
    )
  }, [activeSort])

  const resolveDisplayTier = (row: RankingItem): PlayerTierStatus =>
    tierByNickname.get(row.nickname) ?? row.tier

  const tableColumnCount = showMmr ? 15 : 11

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

      <p className="sr-only" aria-live="polite">{sortAnnouncement}</p>

      <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-xs dark:border-slate-700 dark:bg-slate-900">
        {monthlyPeriodLabel.length > 0 && (
          <div className="flex min-w-full flex-wrap items-baseline justify-between gap-x-4 gap-y-1 border-b border-slate-100 px-4 py-3 dark:border-slate-800">
            <span className="text-sm font-semibold text-slate-700 dark:text-slate-200">{monthlyPeriodLabel}</span>
            <span className="text-xs text-slate-500 dark:text-slate-400">{t('ranking.sort.hint')}</span>
          </div>
        )}
        <table className="min-w-full text-left text-sm">
            <thead className="bg-slate-50 text-xs tracking-wide text-slate-500 dark:bg-slate-800/80 dark:text-slate-300">
            <tr>
              <SortableHeader sortKey="rank" sort={activeSort} onSort={handleSort} />
              <th className="px-4 py-3">{t('ranking.table.nickname')}</th>
              <th className="whitespace-nowrap px-4 py-3">{t('ranking.table.race')}</th>
              {/* Not a sort: the tier follows the rank. */}
              <th className="whitespace-nowrap px-4 py-3">{t('ranking.table.tier')}</th>
              {showMmr && <SortableHeader sortKey="currentMmr" sort={activeSort} onSort={handleSort} />}
              {showMmr && <SortableHeader sortKey="peakMmr" sort={activeSort} onSort={handleSort} />}
              {showMmr && <SortableHeader sortKey="lowestMmr" sort={activeSort} onSort={handleSort} />}
              <SortableHeader sortKey="wins" sort={activeSort} onSort={handleSort} />
              <SortableHeader sortKey="losses" sort={activeSort} onSort={handleSort} />
              <SortableHeader sortKey="games" sort={activeSort} onSort={handleSort} />
              <SortableHeader sortKey="winRate" sort={activeSort} onSort={handleSort} />
              <th className="px-4 py-3">{t('ranking.table.gameTypeStats')}</th>
              <SortableHeader sortKey="streak" sort={activeSort} onSort={handleSort} />
              <th className="px-4 py-3">{t('ranking.table.last10')}</th>
              {showMmr && <SortableHeader sortKey="mmrDelta" sort={activeSort} onSort={handleSort} />}
            </tr>
          </thead>

          <tbody>
            {loading &&
              (
              <tr className="border-t border-slate-100 dark:border-slate-800">
                  <td className="px-4 py-3" colSpan={tableColumnCount}>
                    <LoadingIndicator label={t('common.loading')} />
                  </td>
                </tr>
              )}

            {!loading && error && (
              <tr className="border-t border-slate-100 dark:border-slate-800">
                <td className="px-4 py-8 text-center text-sm text-slate-500 dark:text-slate-400" colSpan={tableColumnCount}>
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
              <tr className="border-t border-slate-100 dark:border-slate-800">
                <td className="px-4 py-8 text-center text-sm text-slate-500 dark:text-slate-400" colSpan={tableColumnCount}>
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
                const streakEmoji = resolveStreakEmoji(row.streak)

                return (
                  <tr
                    key={`${row.rank}-${row.nickname}`}
                    className={`border-t border-slate-100 dark:border-slate-800 ${getRankClass(row.rank)}`}
                  >
                    <td className="px-4 py-3 font-semibold text-slate-900 dark:text-slate-100">{row.rank}</td>
                    <td className="px-4 py-3 font-medium text-slate-900 dark:text-slate-100">
                      {streakEmoji && (
                        <span
                          className="mr-1.5 inline-block"
                          title={formatStreak(row.streak)}
                          aria-label={formatStreak(row.streak)}
                          role="img"
                        >
                          {streakEmoji}
                        </span>
                      )}
                      {row.nickname}
                      {row.isNew && (
                        <span className="ml-1.5 inline-block rounded-full bg-emerald-100 px-1.5 py-0.5 align-middle text-[11px] font-bold leading-none tracking-wide text-emerald-700 dark:bg-emerald-900/50 dark:text-emerald-300">
                          {t('ranking.newBadge')}
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-slate-700 dark:text-slate-300">{row.race}</td>
                    <td className="px-4 py-3 text-slate-700 dark:text-slate-300">{formatTier(resolveDisplayTier(row))}</td>
                    {showMmr && (
                      <td className="px-4 py-3 text-slate-700 dark:text-slate-300">
                        {typeof row.currentMmr === 'number' ? row.currentMmr : '-'}
                      </td>
                    )}
                    {showMmr && (
                      <td className="px-4 py-3 text-slate-700 dark:text-slate-300">
                        {typeof row.peakMmr === 'number' ? row.peakMmr : '-'}
                      </td>
                    )}
                    {showMmr && (
                      <td className="px-4 py-3 text-slate-700 dark:text-slate-300">
                        {typeof row.lowestMmr === 'number' ? row.lowestMmr : '-'}
                      </td>
                    )}
                    <td className="px-4 py-3 text-slate-700 dark:text-slate-300">{row.wins}</td>
                    <td className="px-4 py-3 text-slate-700 dark:text-slate-300">{row.losses}</td>
                    <td className="px-4 py-3 text-slate-700 dark:text-slate-300">{row.games}</td>
                    <td className="px-4 py-3 text-slate-700 dark:text-slate-300">{formatWinRate(row.winRate)}</td>
                    <td className="px-4 py-3">
                      <button
                        type="button"
                        disabled={typeof playerId !== 'number' || isStatsLoadingForRow}
                        onClick={() => handleOpenGameTypeStats(row)}
                        className="whitespace-nowrap rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-700 transition-colors hover:border-emerald-600 hover:bg-emerald-600 hover:text-white disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-200 dark:hover:border-emerald-400 dark:hover:bg-emerald-700"
                      >
                        {isStatsLoadingForRow ? t('statsModal.buttonLoading') : t('statsModal.button')}
                      </button>
                    </td>
                    <td className="px-4 py-3 text-slate-700 dark:text-slate-300">{formatStreak(row.streak)}</td>
                    <td className="px-4 py-3 text-xs text-slate-700 dark:text-slate-300">{formatLast10(row.last10)}</td>
                    {showMmr && rowMmrDelta !== null ? (
                      <td
                        className={`px-4 py-3 font-medium ${
                          rowMmrDelta >= 0 ? 'text-emerald-700 dark:text-emerald-300' : 'text-rose-600 dark:text-rose-300'
                        }`}
                      >
                        {rowMmrDelta >= 0 ? `+${rowMmrDelta}` : rowMmrDelta}
                      </td>
                    ) : showMmr ? (
                      <td className="px-4 py-3 text-slate-700 dark:text-slate-300">-</td>
                    ) : null}
                  </tr>
                )
              })}
          </tbody>
        </table>
      </div>

      {/* Team scores (TeamScoreBoard) stay off the page until team tournaments are played. */}
    </section>
  )
}
