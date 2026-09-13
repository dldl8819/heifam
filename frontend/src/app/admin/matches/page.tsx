'use client'

import { FormEvent, useEffect, useState } from 'react'
import { Alert, AlertContent, AlertDescription, AlertIcon, AlertTitle } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { apiClient, isApiForbiddenError, isApiUnauthorizedError } from '@/lib/api'
import { t } from '@/lib/i18n'
import { useAdminAuth } from '@/lib/admin-auth'
import { useMmrVisibility } from '@/lib/mmr-visibility'
import type { MatchHistoryFilters, RecentMatchItem, TeamSide } from '@/types/api'

const TEMP_GROUP_ID = 1
const MATCH_HISTORY_PAGE_SIZE = 20

type MatchHistoryFilterForm = {
  fromDate: string
  toDate: string
}

function createEmptyMatchHistoryFilters(): MatchHistoryFilterForm {
  return { fromDate: '', toDate: '' }
}

function toApiFilters(filters: MatchHistoryFilterForm): MatchHistoryFilters {
  return {
    fromDate: filters.fromDate,
    toDate: filters.toDate,
  }
}

function formatTeamLabel(team: TeamSide | string | null): string {
  if (team === null) {
    return t('results.recent.noWinner')
  }
  return team === 'HOME'
    ? t('results.team.home')
    : team === 'AWAY'
      ? t('results.team.away')
      : team
}

function formatPlayedAt(value: string): string {
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return value
  }
  return parsed.toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function formatRecordedBy(value: string | null): string {
  if (!value || value.trim().length === 0) {
    return '-'
  }
  return value
}

function formatTeamPlayers(match: RecentMatchItem, team: TeamSide): string {
  const players = team === 'HOME' ? match.homeTeam : match.awayTeam
  if (players.length === 0) {
    return '-'
  }
  return players.map((player) => player.nickname).join(', ')
}

function formatRaceMatchup(match: RecentMatchItem): string {
  const home = match.homeRaceComposition?.trim() ?? ''
  const away = match.awayRaceComposition?.trim() ?? ''
  if (home.length === 0 && away.length === 0) {
    return '-'
  }
  if (home.length > 0 && away.length > 0) {
    return home === away ? home : `${home} / ${away}`
  }
  return home.length > 0 ? home : away
}

export default function AdminMatchHistoryPage() {
  const { isSuperAdmin, canViewMmr, isLoading: adminAuthLoading } = useAdminAuth()
  const { mmrVisible } = useMmrVisibility()
  const showMmr = canViewMmr && mmrVisible

  const [matches, setMatches] = useState<RecentMatchItem[]>([])
  const [page, setPage] = useState<number>(0)
  const [totalPages, setTotalPages] = useState<number>(0)
  const [totalElements, setTotalElements] = useState<number>(0)
  const [draftFilters, setDraftFilters] = useState<MatchHistoryFilterForm>(() => createEmptyMatchHistoryFilters())
  const [appliedFilters, setAppliedFilters] = useState<MatchHistoryFilterForm>(() => createEmptyMatchHistoryFilters())
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (adminAuthLoading) {
      return
    }

    if (!isSuperAdmin) {
      setMatches([])
      setTotalPages(0)
      setTotalElements(0)
      setLoading(false)
      setError(t('matchHistory.superOnly'))
      return
    }

    let active = true
    const loadPage = async () => {
      setLoading(true)
      setError(null)
      try {
        const response = await apiClient.getMatchHistoryPage(TEMP_GROUP_ID, {
          page,
          size: MATCH_HISTORY_PAGE_SIZE,
          ...toApiFilters(appliedFilters),
        })
        if (!active) {
          return
        }
        setMatches(response.items)
        setTotalPages(response.totalPages)
        setTotalElements(response.totalElements)
        if (response.totalPages > 0 && page >= response.totalPages) {
          setPage(response.totalPages - 1)
        }
      } catch (loadError) {
        if (!active) {
          return
        }
        if (isApiUnauthorizedError(loadError)) {
          setError(t('common.adminLoginRequired'))
        } else if (isApiForbiddenError(loadError)) {
          setError(t('common.permissionDenied'))
        } else {
          setError(t('matchHistory.loadError'))
        }
        setMatches([])
        setTotalPages(0)
        setTotalElements(0)
      } finally {
        if (active) {
          setLoading(false)
        }
      }
    }

    void loadPage()
    return () => {
      active = false
    }
  }, [adminAuthLoading, isSuperAdmin, page, appliedFilters])

  const handleFilterSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setPage(0)
    setAppliedFilters({ ...draftFilters })
  }

  const handleFilterReset = () => {
    const emptyFilters = createEmptyMatchHistoryFilters()
    setDraftFilters(emptyFilters)
    setAppliedFilters(emptyFilters)
    setPage(0)
  }

  const handlePreviousPage = () => {
    if (loading || page <= 0) {
      return
    }
    setPage((current) => Math.max(0, current - 1))
  }

  const handleNextPage = () => {
    if (loading || page + 1 >= totalPages) {
      return
    }
    setPage((current) => current + 1)
  }

  return (
    <section className="space-y-6">
      <header className="space-y-1 rounded-xl border border-slate-200 bg-white px-5 py-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
        <h2 className="text-2xl font-semibold tracking-tight text-slate-950 dark:text-slate-100">{t('matchHistory.title')}</h2>
        <p className="text-sm text-slate-600 dark:text-slate-300">{t('matchHistory.description')}</p>
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

      <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
        <div className="flex items-center justify-between gap-3">
          <div>
            <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('matchHistory.listTitle')}</h3>
            <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{t('matchHistory.listDescription')}</p>
          </div>
          <span className="rounded-md border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs font-semibold text-slate-700 dark:border-slate-700 dark:bg-slate-950/60 dark:text-slate-300">
            {t('matchHistory.count', { count: totalElements })}
          </span>
        </div>

        <form onSubmit={handleFilterSubmit} className="mt-4 grid gap-3 border-t border-slate-100 pt-4 dark:border-slate-800 sm:grid-cols-2 lg:grid-cols-4">
          <label className="space-y-1 text-xs font-medium text-slate-600 dark:text-slate-300">
            <span>{t('matchHistory.filters.fromDate')}</span>
            <input
              type="date"
              value={draftFilters.fromDate}
              onChange={(event) => setDraftFilters((current) => ({ ...current, fromDate: event.target.value }))}
              className="w-full rounded-md border border-slate-200 bg-white px-2 py-1.5 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-600 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-amber-400 dark:focus:ring-amber-400/30"
            />
          </label>
          <label className="space-y-1 text-xs font-medium text-slate-600 dark:text-slate-300">
            <span>{t('matchHistory.filters.toDate')}</span>
            <input
              type="date"
              value={draftFilters.toDate}
              onChange={(event) => setDraftFilters((current) => ({ ...current, toDate: event.target.value }))}
              className="w-full rounded-md border border-slate-200 bg-white px-2 py-1.5 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-600 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-amber-400 dark:focus:ring-amber-400/30"
            />
          </label>
          <div className="flex items-end gap-2 sm:col-span-2 lg:col-span-2">
            <button
              type="submit"
              className="rounded-md bg-slate-900 px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white"
            >
              {t('matchHistory.filters.apply')}
            </button>
            <button
              type="button"
              onClick={handleFilterReset}
              className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 transition hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-300 dark:hover:bg-slate-800"
            >
              {t('matchHistory.filters.reset')}
            </button>
          </div>
        </form>

        {loading ? (
          <div className="mt-6 flex justify-center">
            <LoadingIndicator label={t('common.loading')} />
          </div>
        ) : matches.length === 0 ? (
          <p className="mt-4 rounded-lg border border-slate-200 bg-slate-50 px-3 py-3 text-sm text-slate-600 dark:border-slate-700 dark:bg-slate-950/60 dark:text-slate-300">
            {t('matchHistory.empty')}
          </p>
        ) : (
          <>
            <div className="mt-4 overflow-x-auto rounded-lg border border-slate-200 dark:border-slate-700">
              <table className="min-w-[52rem] text-left text-sm">
                <thead className="bg-slate-50 text-xs tracking-wide text-slate-500 dark:bg-slate-800/80 dark:text-slate-300">
                  <tr>
                    <th className="px-3 py-2">{t('matchHistory.table.matchId')}</th>
                    <th className="px-3 py-2">{t('matchHistory.table.playedAt')}</th>
                    <th className="px-3 py-2">{t('matchHistory.table.recordedBy')}</th>
                    <th className="whitespace-nowrap px-3 py-2">{t('matchHistory.table.winner')}</th>
                    <th className="whitespace-nowrap px-3 py-2">{t('matchHistory.table.raceComposition')}</th>
                    <th className="min-w-[8rem] break-keep px-3 py-2">{t('matchHistory.table.homeTeam')}</th>
                    <th className="min-w-[8rem] break-keep px-3 py-2">{t('matchHistory.table.awayTeam')}</th>
                    {showMmr && <th className="px-3 py-2">{t('matchHistory.table.mmrDiff')}</th>}
                  </tr>
                </thead>
                <tbody>
                  {matches.map((match) => (
                    <tr
                      key={`match-history-${match.matchId}`}
                      className="border-t border-slate-100 dark:border-slate-800"
                    >
                      <td className="px-3 py-2 font-medium text-slate-900 dark:text-slate-100">{match.matchId}</td>
                      <td className="px-3 py-2 text-slate-700 dark:text-slate-300">{formatPlayedAt(match.playedAt)}</td>
                      <td className="px-3 py-2 text-slate-700 dark:text-slate-300">
                        {formatRecordedBy(match.resultRecordedByNickname)}
                      </td>
                      <td className="px-3 py-2 text-slate-700 dark:text-slate-300">{formatTeamLabel(match.winningTeam)}</td>
                      <td className="whitespace-nowrap px-3 py-2 text-slate-700 dark:text-slate-300">
                        {formatRaceMatchup(match)}
                      </td>
                      <td
                        className={`min-w-[8rem] break-keep px-3 py-2 ${
                          match.winningTeam === 'HOME'
                            ? 'font-medium text-emerald-700 dark:text-emerald-300'
                            : 'text-slate-700 dark:text-slate-300'
                        }`}
                      >
                        {formatTeamPlayers(match, 'HOME')}
                      </td>
                      <td
                        className={`min-w-[8rem] break-keep px-3 py-2 ${
                          match.winningTeam === 'AWAY'
                            ? 'font-medium text-emerald-700 dark:text-emerald-300'
                            : 'text-slate-700 dark:text-slate-300'
                        }`}
                      >
                        {formatTeamPlayers(match, 'AWAY')}
                      </td>
                      {showMmr && (
                        <td className="px-3 py-2 text-slate-700 dark:text-slate-300">
                          {typeof match.mmrDiff === 'number' ? match.mmrDiff : '-'}
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="mt-4 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
              <p className="text-xs text-slate-500 dark:text-slate-400">
                {t('matchHistory.pagination.status', { page: page + 1, totalPages: Math.max(totalPages, 1) })}
              </p>
              <div className="flex gap-2">
                <button
                  type="button"
                  disabled={loading || page <= 0}
                  onClick={handlePreviousPage}
                  className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 transition-colors hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-300 dark:hover:bg-slate-800"
                >
                  {t('matchHistory.pagination.previous')}
                </button>
                <button
                  type="button"
                  disabled={loading || page + 1 >= totalPages}
                  onClick={handleNextPage}
                  className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 transition-colors hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-300 dark:hover:bg-slate-800"
                >
                  {t('matchHistory.pagination.next')}
                </button>
              </div>
            </div>
          </>
        )}
      </article>
    </section>
  )
}
