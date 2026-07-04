'use client'

import { useEffect, useMemo, useState } from 'react'
import { Alert, AlertContent, AlertDescription, AlertIcon, AlertTitle } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { useAdminAuth } from '@/lib/admin-auth'
import { apiClient } from '@/lib/api'
import { t } from '@/lib/i18n'
import type { GroupPlayerGameTypeStat, GroupPlayerRaceStat, GroupPlayerRaceStatsItem } from '@/types/api'

const TEMP_GROUP_ID = 1

function formatWinRate(value: number): string {
  const percentage = value <= 1 ? value * 100 : value
  return `${percentage.toFixed(2)}%`
}

function formatRecord(wins: number, losses: number, games: number, winRate: number): string {
  if (games <= 0) {
    return '-'
  }
  return `${wins}승 ${losses}패 · ${formatWinRate(winRate)}`
}

function RaceStatBadges({ stats }: { stats: GroupPlayerRaceStat[] }) {
  if (stats.length === 0) {
    return <span className="text-xs text-slate-400">{t('stats.emptyStat')}</span>
  }

  return (
    <div className="flex flex-wrap gap-2">
      {stats.map((stat) => (
        <span
          key={stat.race}
          className="inline-flex rounded-md border border-emerald-200 bg-emerald-50 px-2.5 py-1 text-xs font-medium text-emerald-800"
        >
          {stat.race} {formatRecord(stat.wins, stat.losses, stat.games, stat.winRate)}
        </span>
      ))}
    </div>
  )
}

function GameTypeStatBadges({ stats }: { stats: GroupPlayerGameTypeStat[] }) {
  if (stats.length === 0) {
    return <span className="text-xs text-slate-400">{t('stats.emptyStat')}</span>
  }

  return (
    <div className="flex flex-wrap gap-2">
      {stats.map((stat) => (
        <span
          key={stat.gameType}
          className="inline-flex rounded-md border border-sky-200 bg-sky-50 px-2.5 py-1 text-xs font-medium text-sky-800"
        >
          {stat.gameType} {formatRecord(stat.wins, stat.losses, stat.games, stat.winRate)}
        </span>
      ))}
    </div>
  )
}

export default function StatsPage() {
  const { isLoading: authLoading } = useAdminAuth()
  const [rows, setRows] = useState<GroupPlayerRaceStatsItem[]>([])
  const [search, setSearch] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true

    if (authLoading) {
      return () => {
        active = false
      }
    }

    const fetchStats = async () => {
      setLoading(true)
      setError(null)

      try {
        const response = await apiClient.getGroupPlayerRaceStats(TEMP_GROUP_ID)
        if (!active) {
          return
        }
        setRows(response)
      } catch {
        if (!active) {
          return
        }
        setError(t('stats.loadError'))
      } finally {
        if (active) {
          setLoading(false)
        }
      }
    }

    void fetchStats()

    return () => {
      active = false
    }
  }, [authLoading])

  const filteredRows = useMemo(() => {
    const normalizedSearch = search.trim().toLowerCase()
    if (normalizedSearch.length === 0) {
      return rows
    }

    return rows.filter((row) =>
      row.nickname.toLowerCase().includes(normalizedSearch)
        || row.race.toLowerCase().includes(normalizedSearch)
        || row.byRace.some((stat) => stat.race.toLowerCase().includes(normalizedSearch))
        || row.byGameType.some((stat) => stat.gameType.toLowerCase().includes(normalizedSearch))
    )
  }, [rows, search])

  return (
    <section className="space-y-5">
      <div className="rounded-xl border border-slate-200 bg-white px-5 py-4 shadow-sm">
        <h2 className="text-2xl font-semibold tracking-tight text-slate-950">{t('stats.title')}</h2>
        <p className="mt-2 text-sm leading-6 text-slate-600">{t('stats.description')}</p>
        <label className="mt-4 block text-xs font-medium text-slate-600" htmlFor="stats-search">
          {t('stats.searchLabel')}
        </label>
        <input
          id="stats-search"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-slate-900 focus:outline-none"
          placeholder={t('stats.searchPlaceholder')}
        />
      </div>

      <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-sm">
        <table className="min-w-full text-left text-sm">
          <thead className="bg-slate-50 text-xs tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3">{t('stats.table.nickname')}</th>
              <th className="px-4 py-3">{t('stats.table.race')}</th>
              <th className="px-4 py-3">{t('stats.table.total')}</th>
              <th className="px-4 py-3">{t('stats.table.byRace')}</th>
              <th className="px-4 py-3">{t('stats.table.byGameType')}</th>
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr className="border-t border-slate-100">
                <td className="px-4 py-3" colSpan={5}>
                  <LoadingIndicator label={t('common.loading')} />
                </td>
              </tr>
            )}

            {!loading && error && (
              <tr className="border-t border-slate-100">
                <td className="px-4 py-8 text-center text-sm text-slate-500" colSpan={5}>
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

            {!loading && !error && filteredRows.length === 0 && (
              <tr className="border-t border-slate-100">
                <td className="px-4 py-8 text-center text-sm text-slate-500" colSpan={5}>
                  {t('stats.empty')}
                </td>
              </tr>
            )}

            {!loading && !error && filteredRows.map((row) => (
              <tr key={row.playerId} className="border-t border-slate-100 bg-white align-top">
                <td className="px-4 py-3 font-medium text-slate-950">{row.nickname}</td>
                <td className="px-4 py-3 text-slate-700">{row.race}</td>
                <td className="px-4 py-3 whitespace-nowrap text-slate-700">
                  {formatRecord(row.wins, row.losses, row.games, row.winRate)}
                </td>
                <td className="min-w-72 px-4 py-3">
                  <RaceStatBadges stats={row.byRace} />
                </td>
                <td className="min-w-72 px-4 py-3">
                  <GameTypeStatBadges stats={row.byGameType} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}
