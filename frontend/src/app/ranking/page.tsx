'use client'

import { useEffect, useMemo, useState } from 'react'
import { useAdminAuth } from '@/lib/admin-auth'
import { apiClient } from '@/lib/api'
import { Alert, AlertContent, AlertDescription, AlertIcon, AlertTitle } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { t } from '@/lib/i18n'
import type { RankingItem } from '@/types/api'

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

function formatLast10(value: string): string {
  if (!value) {
    return '-'
  }

  return value
    .replace(/W/g, t('ranking.last10.win'))
    .replace(/L/g, t('ranking.last10.loss'))
}

export default function RankingPage() {
  const { isAdmin } = useAdminAuth()
  const [rows, setRows] = useState<RankingItem[]>([])
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true

    const fetchRanking = async () => {
      setLoading(true)
      setError(null)

      try {
        const response = await apiClient.getRanking(TEMP_GROUP_ID)

        if (!active) {
          return
        }

        setRows(response)
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
  }, [])

  const sortedRows = useMemo(
    () =>
      isAdmin
        ? [...rows].sort((a, b) => b.currentMmr - a.currentMmr)
        : [...rows].sort((a, b) => a.nickname.localeCompare(b.nickname, 'ko-KR')),
    [rows, isAdmin]
  )

  return (
    <section className="space-y-6">
      <header className="space-y-1 rounded-xl border border-slate-200 bg-white px-5 py-4 shadow-sm">
        <h2 className="text-2xl font-semibold tracking-tight">{t('ranking.title')}</h2>
        <p className="text-sm text-slate-600">{t('ranking.description')}</p>
      </header>

      <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-sm">
        <table className="min-w-full text-left text-sm">
          <thead className="bg-slate-50 text-xs tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3">{t('ranking.table.rank')}</th>
              <th className="px-4 py-3">{t('ranking.table.nickname')}</th>
              <th className="px-4 py-3">{t('ranking.table.race')}</th>
              {isAdmin && <th className="px-4 py-3">{t('ranking.table.currentMmr')}</th>}
              <th className="px-4 py-3">{t('ranking.table.wins')}</th>
              <th className="px-4 py-3">{t('ranking.table.losses')}</th>
              <th className="px-4 py-3">{t('ranking.table.games')}</th>
              <th className="px-4 py-3">{t('ranking.table.winRate')}</th>
              <th className="px-4 py-3">{t('ranking.table.streak')}</th>
              <th className="px-4 py-3">{t('ranking.table.last10')}</th>
              {isAdmin && <th className="px-4 py-3">{t('ranking.table.mmrDelta')}</th>}
            </tr>
          </thead>

          <tbody>
            {loading &&
              (
                <tr className="border-t border-slate-100">
                  <td className="px-4 py-3" colSpan={isAdmin ? 11 : 9}>
                    <LoadingIndicator label={t('common.loading')} />
                  </td>
                </tr>
              )}

            {!loading && error && (
              <tr className="border-t border-slate-100">
                <td className="px-4 py-8 text-center text-sm text-slate-500" colSpan={isAdmin ? 11 : 9}>
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
                <td className="px-4 py-8 text-center text-sm text-slate-500" colSpan={isAdmin ? 11 : 9}>
                  {t('ranking.empty')}
                </td>
              </tr>
            )}

            {!loading &&
              !error &&
              sortedRows.map((row) => (
                <tr
                  key={`${row.rank}-${row.nickname}`}
                  className={`border-t border-slate-100 ${getRankClass(row.rank)}`}
                >
                  <td className="px-4 py-3 font-semibold text-slate-900">{row.rank}</td>
                  <td className="px-4 py-3 font-medium text-slate-900">{row.nickname}</td>
                  <td className="px-4 py-3 text-slate-700">{row.race}</td>
                  {isAdmin && <td className="px-4 py-3 text-slate-700">{row.currentMmr}</td>}
                  <td className="px-4 py-3 text-slate-700">{row.wins}</td>
                  <td className="px-4 py-3 text-slate-700">{row.losses}</td>
                  <td className="px-4 py-3 text-slate-700">{row.games}</td>
                  <td className="px-4 py-3 text-slate-700">{formatWinRate(row.winRate)}</td>
                  <td className="px-4 py-3 text-slate-700">{formatStreak(row.streak)}</td>
                  <td className="px-4 py-3 text-xs text-slate-700">{formatLast10(row.last10)}</td>
                  {isAdmin && (
                    <td
                      className={`px-4 py-3 font-medium ${
                        row.mmrDelta >= 0 ? 'text-emerald-600' : 'text-rose-600'
                      }`}
                    >
                      {row.mmrDelta >= 0 ? `+${row.mmrDelta}` : row.mmrDelta}
                    </td>
                  )}
                </tr>
              ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}
