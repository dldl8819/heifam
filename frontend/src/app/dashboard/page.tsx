'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { useAdminAuth } from '@/lib/admin-auth'
import { apiClient } from '@/lib/api'
import { Alert, AlertContent, AlertDescription, AlertIcon, AlertTitle } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { t } from '@/lib/i18n'
import type { GroupDashboardResponse } from '@/types/api'

const TEMP_GROUP_ID = 1

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

export default function DashboardPage() {
  const { isAdmin } = useAdminAuth()
  const [dashboard, setDashboard] = useState<GroupDashboardResponse | null>(null)
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true

    const fetchDashboard = async () => {
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

    void fetchDashboard()

    return () => {
      active = false
    }
  }, [])

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

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs tracking-wide text-slate-500">{t('dashboard.kpi.totalPlayers')}</p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">
            {loading ? '...' : dashboard?.kpiSummary.totalPlayers ?? 0}
          </p>
        </article>

        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs tracking-wide text-slate-500">{t('dashboard.kpi.topMmr')}</p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">
            {loading ? '...' : isAdmin ? dashboard?.kpiSummary.topMmr ?? 0 : '-'}
          </p>
        </article>

        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs tracking-wide text-slate-500">{t('dashboard.kpi.averageMmr')}</p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">
            {loading ? '...' : isAdmin ? dashboard?.kpiSummary.averageMmr ?? 0 : '-'}
          </p>
        </article>

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
                  {isAdmin && <th className="px-3 py-2">{t('dashboard.topPreview.headers.mmr')}</th>}
                  <th className="px-3 py-2">{t('dashboard.topPreview.headers.winRate')}</th>
                </tr>
              </thead>
              <tbody>
                {loading &&
                  (
                    <tr className="border-t border-slate-100">
                      <td className="px-3 py-3" colSpan={isAdmin ? 5 : 4}>
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
                      {isAdmin && <td className="px-3 py-2 text-slate-700">{row.currentMmr}</td>}
                      <td className="px-3 py-2 text-slate-700">{formatWinRate(row.winRate)}</td>
                    </tr>
                  ))}

                {!loading && (dashboard?.topRankingPreview.length ?? 0) === 0 && (
                  <tr className="border-t border-slate-100">
                    <td className="px-3 py-8 text-center text-sm text-slate-500" colSpan={isAdmin ? 5 : 4}>
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
                        className={`text-sm text-slate-700 ${isAdmin ? 'flex items-center justify-between' : ''}`}
                      >
                        <span>{player.nickname}</span>
                        {isAdmin && <span>{player.mmr}</span>}
                      </li>
                    ))}
                  </ul>
                  {isAdmin && (
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
                        className={`text-sm text-slate-700 ${isAdmin ? 'flex items-center justify-between' : ''}`}
                      >
                        <span>{player.nickname}</span>
                        {isAdmin && <span>{player.mmr}</span>}
                      </li>
                    ))}
                  </ul>
                  {isAdmin && (
                    <p className="mt-2 text-xs text-slate-500">
                      {t('dashboard.recentBalance.awayMmr')}:{' '}
                      <span className="font-semibold text-slate-700">
                        {dashboard.recentBalancePreview.awayMmr}
                      </span>
                    </p>
                  )}
                </div>
              </div>

              <div className={`mt-3 grid gap-2 ${isAdmin ? 'sm:grid-cols-3' : 'sm:grid-cols-2'}`}>
                <div className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700">
                  {t('dashboard.recentBalance.matchId')}:{' '}
                  <span className="font-semibold">
                    {dashboard.recentBalancePreview.matchId}
                  </span>
                </div>
                {isAdmin && (
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
          <h3 className="text-sm font-semibold text-slate-900">{t('dashboard.notes.title')}</h3>
          <ul className="mt-3 space-y-2 text-sm text-slate-600">
            <li>{t('dashboard.notes.items.one')}</li>
            <li>{t('dashboard.notes.items.two')}</li>
          </ul>
        </article>
      </section>
    </section>
  )
}
