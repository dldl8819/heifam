'use client'

import Link from 'next/link'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useAdminAuth } from '@/lib/admin-auth'
import { apiClient } from '@/lib/api'
import { Alert, AlertContent, AlertDescription, AlertIcon, AlertTitle } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { t } from '@/lib/i18n'
import { useMmrVisibility } from '@/lib/mmr-visibility'
import type { GroupDashboardResponse, PlayerRosterItem, PlayerTierStatus, RatingRecalculationResponse } from '@/types/api'

const TEMP_GROUP_ID = 1
const TIER_BOARD_COLUMNS: PlayerTierStatus[] = [
  'S',
  'A+',
  'A',
  'A-',
  'B+',
  'B',
  'B-',
  'C+',
  'C',
  'C-',
  'UNASSIGNED',
]
const TIER_BOARD_MIN_ROWS = 5
const TIER_BOARD_HEADER_CLASS: Record<PlayerTierStatus, string> = {
  S: 'bg-white text-slate-950',
  'A+': 'bg-[#bdd7ee] text-slate-950',
  A: 'bg-[#bdd7ee] text-slate-950',
  'A-': 'bg-[#bdd7ee] text-slate-950',
  'B+': 'bg-[#fff2cc] text-slate-950',
  B: 'bg-[#fff2cc] text-slate-950',
  'B-': 'bg-[#fff2cc] text-slate-950',
  'C+': 'bg-[#c6e0b4] text-slate-950',
  C: 'bg-[#c6e0b4] text-slate-950',
  'C-': 'bg-[#c6e0b4] text-slate-950',
  UNASSIGNED: 'bg-[#f4b183] text-slate-950',
}

function formatWinRate(value: number): string {
  const percentage = value <= 1 ? value * 100 : value
  return `${percentage.toFixed(2)}%`
}

function formatDateTime(value: string): string {
  const parsed = Date.parse(value)
  if (Number.isNaN(parsed)) {
    return value
  }

  return new Date(parsed).toLocaleString()
}

function formatKstDate(value = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(value)
  const year = parts.find((part) => part.type === 'year')?.value ?? '0000'
  const month = parts.find((part) => part.type === 'month')?.value ?? '00'
  const day = parts.find((part) => part.type === 'day')?.value ?? '00'
  return `${year}-${month}-${day}`
}

function resolveTierBoardLabel(tier: PlayerTierStatus): string {
  return tier === 'UNASSIGNED' ? t('common.tierBoard.unassigned') : tier
}

function resolveTierFromMmr(value: number | undefined): PlayerTierStatus | null {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return null
  }
  if (value <= 0) {
    return 'UNASSIGNED'
  }
  if (value < 400) {
    return 'C-'
  }
  if (value < 600) {
    return 'C'
  }
  if (value < 800) {
    return 'C+'
  }
  if (value < 1000) {
    return 'B-'
  }
  if (value < 1200) {
    return 'B'
  }
  if (value < 1400) {
    return 'B+'
  }
  if (value < 1600) {
    return 'A-'
  }
  if (value < 1800) {
    return 'A'
  }
  if (value < 2000) {
    return 'A+'
  }
  return 'S'
}

function resolveInitialTier(row: PlayerRosterItem): PlayerTierStatus | null {
  return row.baseTier ?? resolveTierFromMmr(row.baseMmr)
}

function formatTierBoardChangeLabel(row: PlayerRosterItem, currentTier: PlayerTierStatus): string | null {
  const initialTier = resolveInitialTier(row)
  if (initialTier === null || initialTier === currentTier) {
    return null
  }

  return `${resolveTierBoardLabel(initialTier)} -> ${resolveTierBoardLabel(currentTier)}`
}

function buildTierBoardBuckets(rows: PlayerRosterItem[]): Record<PlayerTierStatus, PlayerRosterItem[]> {
  const buckets = TIER_BOARD_COLUMNS.reduce<Record<PlayerTierStatus, PlayerRosterItem[]>>(
    (accumulator, tier) => ({
      ...accumulator,
      [tier]: [],
    }),
    {
      S: [],
      'A+': [],
      A: [],
      'A-': [],
      'B+': [],
      B: [],
      'B-': [],
      'C+': [],
      C: [],
      'C-': [],
      UNASSIGNED: [],
    }
  )

  rows
    .filter((row) => row.active !== false)
    .forEach((row) => {
      const liveTier = resolveTierFromMmr(row.currentMmr) ?? row.tier ?? 'UNASSIGNED'
      buckets[liveTier].push(row)
    })

  TIER_BOARD_COLUMNS.forEach((tier) => {
    buckets[tier].sort((a, b) => a.nickname.localeCompare(b.nickname, 'ko-KR'))
  })

  return buckets
}

export default function DashboardPage() {
  const { isAdmin, isSuperAdmin } = useAdminAuth()
  const { mmrVisible } = useMmrVisibility()
  const showMmr = isSuperAdmin && mmrVisible
  const [dashboard, setDashboard] = useState<GroupDashboardResponse | null>(null)
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)
  const [ratingRecalculationLoading, setRatingRecalculationLoading] = useState<'dryRun' | 'execute' | null>(null)
  const [ratingRecalculationError, setRatingRecalculationError] = useState<string | null>(null)
  const [ratingRecalculationMessage, setRatingRecalculationMessage] = useState<string | null>(null)
  const [ratingRecalculationResult, setRatingRecalculationResult] = useState<RatingRecalculationResponse | null>(null)
  const [tierBoardRows, setTierBoardRows] = useState<PlayerRosterItem[]>([])
  const [tierBoardLoading, setTierBoardLoading] = useState<boolean>(false)
  const [tierBoardError, setTierBoardError] = useState<string | null>(null)
  const myRaceSummary = dashboard?.myRaceSummary
  const myGameTypeSummary = dashboard?.myGameTypeSummary
  const currentKFactor = dashboard?.currentKFactor ?? 24
  const tierBoardBuckets = useMemo(() => buildTierBoardBuckets(tierBoardRows), [tierBoardRows])
  const tierBoardRowCount = useMemo(
    () => Math.max(
      TIER_BOARD_MIN_ROWS,
      ...TIER_BOARD_COLUMNS.map((tier) => tierBoardBuckets[tier].length)
    ),
    [tierBoardBuckets]
  )
  const tierBoardTotalCount = useMemo(
    () => TIER_BOARD_COLUMNS.reduce((total, tier) => total + tierBoardBuckets[tier].length, 0),
    [tierBoardBuckets]
  )
  const tierBoardDate = useMemo(() => formatKstDate(), [])
  const canExecuteRatingRecalculation =
    ratingRecalculationLoading === null &&
    ratingRecalculationResult?.dryRun === true &&
    ratingRecalculationResult.averageAbsoluteDeltaDifference > 0

  const fetchDashboard = useCallback(async () => {
    setLoading(true)
    setError(null)

    try {
      const response = await apiClient.getGroupDashboard(TEMP_GROUP_ID)
      setDashboard(response)
    } catch {
      setDashboard(null)
      setError(t('dashboard.loadError'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    let active = true

    const run = async () => {
      setLoading(true)
      setError(null)

      try {
        const response = await apiClient.getGroupDashboard(TEMP_GROUP_ID)
        if (!active) {
          return
        }
        setDashboard(response)
      } catch {
        if (!active) {
          return
        }
        setDashboard(null)
        setError(t('dashboard.loadError'))
      } finally {
        if (active) {
          setLoading(false)
        }
      }
    }

    void run()

    return () => {
      active = false
    }
  }, [])

  useEffect(() => {
    let active = true

    if (!isSuperAdmin) {
      setTierBoardRows([])
      setTierBoardLoading(false)
      setTierBoardError(null)
      return () => {
        active = false
      }
    }

    const run = async () => {
      setTierBoardLoading(true)
      setTierBoardError(null)

      try {
        const response = await apiClient.getGroupPlayers(TEMP_GROUP_ID)
        if (!active) {
          return
        }
        setTierBoardRows(response)
      } catch {
        if (!active) {
          return
        }
        setTierBoardRows([])
        setTierBoardError(t('dashboard.tierBoard.loadError'))
      } finally {
        if (active) {
          setTierBoardLoading(false)
        }
      }
    }

    void run()

    return () => {
      active = false
    }
  }, [isSuperAdmin])

  const handleRatingRecalculation = useCallback(async (dryRun: boolean) => {
    setRatingRecalculationLoading(dryRun ? 'dryRun' : 'execute')
    setRatingRecalculationError(null)
    setRatingRecalculationMessage(null)

    try {
      const response = await apiClient.recalculateRatings({
        dryRun,
        confirm: dryRun ? false : true,
      })
      setRatingRecalculationResult(response)
      setRatingRecalculationMessage(
        dryRun
          ? response.averageAbsoluteDeltaDifference > 0
            ? t('dashboard.ratingRecalculation.messages.dryRunSuccess')
            : t('dashboard.ratingRecalculation.messages.noChanges')
          : t('dashboard.ratingRecalculation.messages.executeSuccess')
      )

      if (!dryRun) {
        await fetchDashboard()
      }
    } catch (requestError) {
      const fallbackMessage = dryRun
        ? t('dashboard.ratingRecalculation.messages.dryRunFailure')
        : t('dashboard.ratingRecalculation.messages.executeFailure')
      if (requestError instanceof Error && requestError.message.trim().length > 0) {
        setRatingRecalculationError(requestError.message)
      } else {
        setRatingRecalculationError(fallbackMessage)
      }
    } finally {
      setRatingRecalculationLoading(null)
    }
  }, [fetchDashboard])

  return (
    <section className="space-y-6">
      <header className="space-y-1 rounded-xl border border-slate-200 bg-white px-5 py-4 shadow-sm">
        <h2 className="text-2xl font-semibold tracking-tight">{t('dashboard.title')}</h2>
        <p className="text-sm text-slate-600">{t('dashboard.description')}</p>
      </header>

      {error && (
        <Alert variant="destructive" appearance="light">
          <AlertIcon icon="destructive">!</AlertIcon>
          <AlertContent>
            <AlertTitle>{t('common.errorPrefix')}</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </AlertContent>
        </Alert>
      )}

      {isSuperAdmin && (
        <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
            <div className="space-y-1">
              <h3 className="text-sm font-semibold text-slate-900">{t('dashboard.ratingRecalculation.title')}</h3>
              <p className="text-sm text-slate-600">{t('dashboard.ratingRecalculation.description')}</p>
              <p className="text-xs font-medium text-slate-500">
                {t('dashboard.ratingRecalculation.currentKFactor', { value: currentKFactor })}
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                onClick={() => void handleRatingRecalculation(true)}
                disabled={ratingRecalculationLoading !== null}
                className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
              >
                {ratingRecalculationLoading === 'dryRun'
                  ? t('dashboard.ratingRecalculation.actions.dryRunLoading')
                  : t('dashboard.ratingRecalculation.actions.dryRun')}
              </button>
              <button
                type="button"
                onClick={() => {
                  if (!window.confirm(t('dashboard.ratingRecalculation.messages.executeConfirm'))) {
                    return
                  }
                  void handleRatingRecalculation(false)
                }}
                disabled={!canExecuteRatingRecalculation}
                className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
              >
                {ratingRecalculationLoading === 'execute'
                  ? t('dashboard.ratingRecalculation.actions.executeLoading')
                  : t('dashboard.ratingRecalculation.actions.execute')}
              </button>
            </div>
          </div>

          {ratingRecalculationResult?.dryRun === true
            && ratingRecalculationResult.averageAbsoluteDeltaDifference <= 0 && (
              <p className="mt-3 text-xs font-medium text-slate-500">
                {t('dashboard.ratingRecalculation.messages.executeDisabledNoChanges')}
              </p>
            )}

          {ratingRecalculationMessage && (
            <Alert appearance="light" className="mt-4 border-emerald-200 bg-emerald-50 text-emerald-900">
              <AlertIcon icon="success">✓</AlertIcon>
              <AlertContent>
                <AlertTitle>{t('dashboard.ratingRecalculation.messages.title')}</AlertTitle>
                <AlertDescription>{ratingRecalculationMessage}</AlertDescription>
              </AlertContent>
            </Alert>
          )}

          {ratingRecalculationError && (
            <Alert variant="destructive" appearance="light" className="mt-4">
              <AlertIcon icon="destructive">!</AlertIcon>
              <AlertContent>
                <AlertTitle>{t('dashboard.ratingRecalculation.messages.errorTitle')}</AlertTitle>
                <AlertDescription>{ratingRecalculationError}</AlertDescription>
              </AlertContent>
            </Alert>
          )}

          {ratingRecalculationResult && (
            <div className="mt-4 space-y-4">
              <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                <div className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700">
                  {t('dashboard.ratingRecalculation.summary.status')}:{' '}
                  <span className="font-semibold text-slate-900">{ratingRecalculationResult.status}</span>
                </div>
                <div className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700">
                  {t('dashboard.ratingRecalculation.summary.processedMatches')}:{' '}
                  <span className="font-semibold text-slate-900">{ratingRecalculationResult.processedMatches}</span>
                </div>
                <div className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700">
                  {t('dashboard.ratingRecalculation.summary.updatedPlayers')}:{' '}
                  <span className="font-semibold text-slate-900">{ratingRecalculationResult.updatedPlayers}</span>
                </div>
                <div className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700">
                  {t('dashboard.ratingRecalculation.summary.durationMs')}:{' '}
                  <span className="font-semibold text-slate-900">{ratingRecalculationResult.durationMs}</span>
                </div>
              </div>

              <div className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700">
                {t('dashboard.ratingRecalculation.summary.averageAbsoluteDeltaDifference')}:{' '}
                <span className="font-semibold text-slate-900">
                  {ratingRecalculationResult.averageAbsoluteDeltaDifference.toFixed(2)}
                </span>
              </div>

              <div className="overflow-x-auto rounded-lg border border-slate-200">
                <table className="min-w-full text-left text-sm">
                  <thead className="bg-slate-50 text-xs tracking-wide text-slate-500">
                    <tr>
                      <th className="px-3 py-2">{t('dashboard.ratingRecalculation.samples.nickname')}</th>
                      <th className="px-3 py-2">{t('dashboard.ratingRecalculation.samples.beforeMmr')}</th>
                      <th className="px-3 py-2">{t('dashboard.ratingRecalculation.samples.afterMmr')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {ratingRecalculationResult.samplePlayerChanges.map((player) => (
                      <tr key={`rating-replay-${player.playerId}`} className="border-t border-slate-100">
                        <td className="px-3 py-2 font-medium text-slate-900">{player.nickname}</td>
                        <td className="px-3 py-2 text-slate-700">{player.beforeMmr}</td>
                        <td className="px-3 py-2 text-slate-700">{player.afterMmr}</td>
                      </tr>
                    ))}
                    {ratingRecalculationResult.samplePlayerChanges.length === 0 && (
                      <tr className="border-t border-slate-100">
                        <td className="px-3 py-4 text-center text-sm text-slate-500" colSpan={3}>
                          {t('dashboard.ratingRecalculation.samples.empty')}
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </section>
      )}

      {isSuperAdmin && (
        <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          {tierBoardError && (
            <Alert variant="destructive" appearance="light">
              <AlertIcon icon="destructive">!</AlertIcon>
              <AlertContent>
                <AlertTitle>{t('common.errorPrefix')}</AlertTitle>
                <AlertDescription>{tierBoardError}</AlertDescription>
              </AlertContent>
            </Alert>
          )}

          {tierBoardLoading ? (
            <div className="mt-4 rounded-lg border border-dashed border-slate-200 px-4 py-10">
              <LoadingIndicator label={t('common.loading')} />
            </div>
          ) : (
            <div className="mt-4 overflow-x-auto rounded-lg border border-[#7b6d5b] bg-[#ded8cc] p-3">
              <div className="min-w-[980px]">
                <div className="border-y-2 border-[#4f4636] py-4 text-center">
                  <h4 className="text-4xl font-semibold tracking-normal text-[#4f4636]">
                    {t('common.tierBoard.title')}
                  </h4>
                </div>

                <table className="mt-4 w-full border-collapse bg-white text-center text-sm">
                  <thead>
                    <tr>
                      <th className="w-12 border border-slate-900 bg-white px-2 py-3 text-base font-semibold">
                        {t('common.tierBoard.index')}
                      </th>
                      {TIER_BOARD_COLUMNS.map((tier) => (
                        <th
                          key={`tier-board-head-${tier}`}
                          className={`min-w-20 border border-slate-900 px-3 py-3 text-xl font-bold ${TIER_BOARD_HEADER_CLASS[tier]}`}
                        >
                          {resolveTierBoardLabel(tier)}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {Array.from({ length: tierBoardRowCount }, (_, rowIndex) => (
                      <tr key={`tier-board-row-${rowIndex}`}>
                        <th className="border border-slate-900 bg-white px-2 py-2 font-normal text-slate-900">
                          {rowIndex + 1}
                        </th>
                        {TIER_BOARD_COLUMNS.map((tier) => {
                          const player = tierBoardBuckets[tier][rowIndex] ?? null
                          const tierChangeLabel = player ? formatTierBoardChangeLabel(player, tier) : null
                          return (
                            <td
                              key={`tier-board-cell-${tier}-${rowIndex}`}
                              title={
                                tierChangeLabel
                                  ? t('dashboard.tierBoard.changeTooltip', { change: tierChangeLabel })
                                  : undefined
                              }
                              className={`h-9 border px-2 py-1 align-middle font-semibold ${
                                tierChangeLabel
                                  ? 'border-solid border-amber-600 bg-amber-100 text-amber-950 shadow-[inset_0_0_0_2px_rgba(217,119,6,0.45)]'
                                  : 'border-dotted border-slate-500 bg-white text-slate-950'
                              }`}
                            >
                              {player ? (
                                <div className="flex items-center justify-center gap-1">
                                  <span>{player.nickname}</span>
                                  {tierChangeLabel && (
                                    <span className="rounded-sm border border-amber-600 bg-amber-200 px-1 text-[10px] leading-4 text-amber-950">
                                      {t('dashboard.tierBoard.changeBadge')}
                                    </span>
                                  )}
                                </div>
                              ) : ''}
                            </td>
                          )
                        })}
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr>
                      <td
                        className="border border-slate-900 bg-white px-2 py-2 text-right text-xs font-semibold text-slate-900"
                        colSpan={TIER_BOARD_COLUMNS.length}
                      >
                        {tierBoardDate}
                      </td>
                      <td className="border border-slate-900 bg-[#c6e0b4] px-2 py-2 text-xs font-semibold text-slate-900">
                        {t('dashboard.tierBoard.total', { count: tierBoardTotalCount })}
                      </td>
                    </tr>
                  </tfoot>
                </table>
              </div>
            </div>
          )}
        </section>
      )}

      <section className={`grid gap-4 sm:grid-cols-2 ${showMmr ? 'xl:grid-cols-4' : 'xl:grid-cols-2'}`}>
        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs tracking-wide text-slate-500">{t('dashboard.kpi.totalPlayers')}</p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">
            {loading ? '...' : dashboard?.kpiSummary.totalPlayers ?? 0}
          </p>
        </article>

        {showMmr && (
          <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <p className="text-xs tracking-wide text-slate-500">{t('dashboard.kpi.topMmr')}</p>
            <p className="mt-2 text-2xl font-semibold text-slate-900">
              {loading ? '...' : dashboard?.kpiSummary.topMmr ?? 0}
            </p>
          </article>
        )}

        {showMmr && (
          <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <p className="text-xs tracking-wide text-slate-500">{t('dashboard.kpi.averageMmr')}</p>
            <p className="mt-2 text-2xl font-semibold text-slate-900">
              {loading ? '...' : dashboard?.kpiSummary.averageMmr ?? 0}
            </p>
          </article>
        )}

        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs tracking-wide text-slate-500">{t('dashboard.kpi.totalGames')}</p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">
            {loading ? '...' : dashboard?.kpiSummary.totalGames ?? 0}
          </p>
        </article>
      </section>

      <section className="grid gap-4 xl:grid-cols-3">
        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm xl:col-span-2">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-semibold text-slate-900">{t('dashboard.topPreview.title')}</h3>
            <Link
              href="/ranking"
              className="text-xs font-medium text-slate-600 underline-offset-2 hover:text-slate-900 hover:underline"
            >
              {t('dashboard.topPreview.openFull')}
            </Link>
          </div>

          <div className="mt-3 overflow-x-auto">
            <table className="min-w-full text-left text-sm">
              <thead className="bg-slate-50 text-xs tracking-wide text-slate-500">
                <tr>
                  <th className="px-3 py-2">{t('dashboard.topPreview.headers.rank')}</th>
                  <th className="px-3 py-2">{t('dashboard.topPreview.headers.nickname')}</th>
                  <th className="px-3 py-2">{t('dashboard.topPreview.headers.race')}</th>
                  {showMmr && <th className="px-3 py-2">{t('dashboard.topPreview.headers.mmr')}</th>}
                  <th className="px-3 py-2">{t('dashboard.topPreview.headers.winRate')}</th>
                </tr>
              </thead>
              <tbody>
                {loading &&
                  (
                    <tr className="border-t border-slate-100">
                      <td className="px-3 py-3" colSpan={showMmr ? 5 : 4}>
                        <LoadingIndicator label={t('common.loading')} />
                      </td>
                    </tr>
                  )}

                {!loading &&
                  (dashboard?.topRankingPreview ?? []).map((row) => (
                    <tr key={`${row.rank}-${row.nickname}`} className="border-t border-slate-100">
                      <td className="px-3 py-2 font-semibold text-slate-900">{row.rank}</td>
                      <td className="px-3 py-2 text-slate-800">{row.nickname}</td>
                      <td className="px-3 py-2 text-slate-700">{row.race}</td>
                      {showMmr && <td className="px-3 py-2 text-slate-700">{row.currentMmr}</td>}
                      <td className="px-3 py-2 text-slate-700">{formatWinRate(row.winRate)}</td>
                    </tr>
                  ))}

                {!loading && (dashboard?.topRankingPreview.length ?? 0) === 0 && (
                  <tr className="border-t border-slate-100">
                    <td className="px-3 py-8 text-center text-sm text-slate-500" colSpan={showMmr ? 5 : 4}>
                      {t('dashboard.topPreview.empty')}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </article>

        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <h3 className="text-sm font-semibold text-slate-900">{t('dashboard.quickActions.title')}</h3>
          <div className="mt-3 grid gap-2">
            {isAdmin && (
              <Link
                href="/balance"
                className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50 hover:text-slate-900"
              >
                {t('dashboard.quickActions.toBalance')}
              </Link>
            )}
            <Link
              href="/results"
              className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50 hover:text-slate-900"
            >
              {t('dashboard.quickActions.toResults')}
            </Link>
            <Link
              href="/ranking"
              className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50 hover:text-slate-900"
            >
              {t('dashboard.quickActions.toRanking')}
            </Link>
          </div>
        </article>
      </section>

      <section className="grid gap-4 xl:grid-cols-3">
        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm xl:col-span-2">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-semibold text-slate-900">{t('dashboard.recentBalance.title')}</h3>
            <span className="text-xs text-slate-500">
              {dashboard?.recentBalancePreview?.createdAt
                ? formatDateTime(dashboard.recentBalancePreview.createdAt)
                : t('dashboard.recentBalance.none')}
            </span>
          </div>

          {!loading && !dashboard?.recentBalancePreview && (
            <div className="mt-3 rounded-lg border border-dashed border-slate-200 px-3 py-8 text-center text-sm text-slate-500">
              {t('dashboard.recentBalance.empty')}
            </div>
          )}

          {dashboard?.recentBalancePreview && (
            <>
              <div className="mt-3 grid gap-3 sm:grid-cols-2">
                <div className="rounded-lg border border-slate-200 p-3">
                  <h4 className="text-xs font-semibold tracking-wide text-slate-500">
                    {t('dashboard.recentBalance.homeTeam')}
                  </h4>
                  <ul className="mt-2 space-y-1">
                    {dashboard.recentBalancePreview.homeTeam.map((player) => (
                      <li
                        key={`dashboard-home-${player.nickname}`}
                        className={`text-sm text-slate-700 ${showMmr ? 'flex items-center justify-between' : ''}`}
                      >
                        <span>{player.nickname}</span>
                        {showMmr && <span>{player.mmr}</span>}
                      </li>
                    ))}
                  </ul>
                  {showMmr && (
                    <p className="mt-2 text-xs text-slate-500">
                      {t('dashboard.recentBalance.homeMmr')}:{' '}
                      <span className="font-semibold text-slate-700">
                        {dashboard.recentBalancePreview.homeMmr}
                      </span>
                    </p>
                  )}
                </div>

                <div className="rounded-lg border border-slate-200 p-3">
                  <h4 className="text-xs font-semibold tracking-wide text-slate-500">
                    {t('dashboard.recentBalance.awayTeam')}
                  </h4>
                  <ul className="mt-2 space-y-1">
                    {dashboard.recentBalancePreview.awayTeam.map((player) => (
                      <li
                        key={`dashboard-away-${player.nickname}`}
                        className={`text-sm text-slate-700 ${showMmr ? 'flex items-center justify-between' : ''}`}
                      >
                        <span>{player.nickname}</span>
                        {showMmr && <span>{player.mmr}</span>}
                      </li>
                    ))}
                  </ul>
                  {showMmr && (
                    <p className="mt-2 text-xs text-slate-500">
                      {t('dashboard.recentBalance.awayMmr')}:{' '}
                      <span className="font-semibold text-slate-700">
                        {dashboard.recentBalancePreview.awayMmr}
                      </span>
                    </p>
                  )}
                </div>
              </div>

              <div className={`mt-3 grid gap-2 ${showMmr ? 'sm:grid-cols-3' : 'sm:grid-cols-2'}`}>
                <div className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700">
                  {t('dashboard.recentBalance.matchId')}:{' '}
                  <span className="font-semibold">
                    {dashboard.recentBalancePreview.matchId}
                  </span>
                </div>
                {showMmr && (
                  <div className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700">
                    {t('dashboard.recentBalance.mmrDiff')}:{' '}
                    <span className="font-semibold">
                      {dashboard.recentBalancePreview.mmrDiff}
                    </span>
                  </div>
                )}
                <div className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700">
                  {t('dashboard.recentBalance.createdAt')}:{' '}
                  <span className="font-semibold">
                    {formatDateTime(dashboard.recentBalancePreview.createdAt)}
                  </span>
                </div>
              </div>
            </>
          )}
        </article>

        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <h3 className="text-sm font-semibold text-slate-900">{t('dashboard.myRace.title')}</h3>
          <p className="mt-1 text-xs text-slate-500">{t('dashboard.myRace.description')}</p>

          {loading && (
            <div className="mt-3">
              <LoadingIndicator label={t('common.loading')} />
            </div>
          )}

          {!loading && dashboard && !myRaceSummary?.linked && (
            <div className="mt-3 rounded-lg border border-dashed border-slate-200 px-3 py-3 text-sm text-slate-500">
              {t('dashboard.myRace.notLinked')}
            </div>
          )}

          {!loading && myRaceSummary && myRaceSummary.linked && (
            <>
              <div className="mt-3 rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700">
                {t('dashboard.myRace.total')}:&nbsp;
                <span className="font-semibold text-slate-900">
                  {myRaceSummary.wins}W {myRaceSummary.losses}L ({myRaceSummary.games})
                </span>
                &nbsp;·&nbsp;
                <span className="font-semibold text-slate-900">
                  {formatWinRate(myRaceSummary.winRate)}
                </span>
              </div>

              <div className="mt-3 overflow-x-auto rounded-lg border border-slate-200">
                <table className="min-w-full text-left text-sm">
                  <thead className="bg-slate-50 text-xs tracking-wide text-slate-500">
                    <tr>
                      <th className="px-3 py-2">{t('dashboard.myRace.headers.race')}</th>
                      <th className="px-3 py-2">{t('dashboard.myRace.headers.wins')}</th>
                      <th className="px-3 py-2">{t('dashboard.myRace.headers.losses')}</th>
                      <th className="px-3 py-2">{t('dashboard.myRace.headers.games')}</th>
                      <th className="px-3 py-2">{t('dashboard.myRace.headers.winRate')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {myRaceSummary.byRace.map((item) => (
                      <tr key={`my-race-${item.race}`} className="border-t border-slate-100">
                        <td className="px-3 py-2 font-medium text-slate-900">{item.race}</td>
                        <td className="px-3 py-2 text-slate-700">{item.wins}</td>
                        <td className="px-3 py-2 text-slate-700">{item.losses}</td>
                        <td className="px-3 py-2 text-slate-700">{item.games}</td>
                        <td className="px-3 py-2 text-slate-700">{formatWinRate(item.winRate)}</td>
                      </tr>
                    ))}
                    {myRaceSummary.byRace.length === 0 && (
                      <tr className="border-t border-slate-100">
                        <td className="px-3 py-4 text-center text-sm text-slate-500" colSpan={5}>
                          {t('dashboard.myRace.empty')}
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </>
          )}

          <div className="mt-4 border-t border-slate-200 pt-4">
            <h4 className="text-sm font-semibold text-slate-900">{t('dashboard.myGameType.title')}</h4>
            <p className="mt-1 text-xs text-slate-500">{t('dashboard.myGameType.description')}</p>

            {!loading && dashboard && !myGameTypeSummary?.linked && (
              <div className="mt-3 rounded-lg border border-dashed border-slate-200 px-3 py-3 text-sm text-slate-500">
                {t('dashboard.myGameType.notLinked')}
              </div>
            )}

            {!loading && myGameTypeSummary && myGameTypeSummary.linked && (
              <>
                <div className="mt-3 rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700">
                  {t('dashboard.myGameType.total')}:&nbsp;
                  <span className="font-semibold text-slate-900">
                    {myGameTypeSummary.wins}W {myGameTypeSummary.losses}L ({myGameTypeSummary.games})
                  </span>
                  &nbsp;·&nbsp;
                  <span className="font-semibold text-slate-900">
                    {formatWinRate(myGameTypeSummary.winRate)}
                  </span>
                </div>

                <div className="mt-3 overflow-x-auto rounded-lg border border-slate-200">
                  <table className="min-w-full text-left text-sm">
                    <thead className="bg-slate-50 text-xs tracking-wide text-slate-500">
                      <tr>
                        <th className="px-3 py-2">{t('dashboard.myGameType.headers.gameType')}</th>
                        <th className="px-3 py-2">{t('dashboard.myGameType.headers.wins')}</th>
                        <th className="px-3 py-2">{t('dashboard.myGameType.headers.losses')}</th>
                        <th className="px-3 py-2">{t('dashboard.myGameType.headers.games')}</th>
                        <th className="px-3 py-2">{t('dashboard.myGameType.headers.winRate')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {myGameTypeSummary.byGameType.map((item) => (
                        <tr key={`my-game-type-${item.gameType}`} className="border-t border-slate-100">
                          <td className="px-3 py-2 font-medium text-slate-900">{item.gameType}</td>
                          <td className="px-3 py-2 text-slate-700">{item.wins}</td>
                          <td className="px-3 py-2 text-slate-700">{item.losses}</td>
                          <td className="px-3 py-2 text-slate-700">{item.games}</td>
                          <td className="px-3 py-2 text-slate-700">{formatWinRate(item.winRate)}</td>
                        </tr>
                      ))}
                      {myGameTypeSummary.byGameType.length === 0 && (
                        <tr className="border-t border-slate-100">
                          <td className="px-3 py-4 text-center text-sm text-slate-500" colSpan={5}>
                            {t('dashboard.myGameType.empty')}
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
              </>
            )}
          </div>

          <div className="mt-4 border-t border-slate-200 pt-4">
            <h4 className="text-sm font-semibold text-slate-900">{t('dashboard.notes.title')}</h4>
            <ul className="mt-3 space-y-2 text-sm text-slate-600">
              <li>{t('dashboard.notes.items.one')}</li>
              <li>{t('dashboard.notes.items.two')}</li>
            </ul>
          </div>
        </article>
      </section>
    </section>
  )
}
