'use client'

import { useEffect, useState } from 'react'
import { apiClient } from '@/lib/api'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { t } from '@/lib/i18n'
import { formatScoreRate } from '@/lib/team-score'
import type { TeamScoreEntry } from '@/types/api'

type TeamScoreBoardProps = {
  groupId: number
}

const HEADER_CLASS = 'px-3 py-2'
const CELL_CLASS = 'px-3 py-2 text-slate-700 dark:text-slate-300'

export function TeamScoreBoard({ groupId }: TeamScoreBoardProps) {
  const [entries, setEntries] = useState<TeamScoreEntry[]>([])
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    apiClient
      .getTeamScores(groupId)
      .then((response) => {
        if (active) {
          setEntries(response.entries)
        }
      })
      .catch(() => {
        if (active) {
          setError(t('teamScore.loadError'))
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false)
        }
      })
    return () => {
      active = false
    }
  }, [groupId])

  return (
    <section className="space-y-3 rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
      <div className="space-y-1">
        <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('teamScore.title')}</h3>
        <p className="text-xs text-slate-500 dark:text-slate-400">{t('teamScore.description')}</p>
        <p className="text-xs text-amber-700 dark:text-amber-300">{t('teamScore.trialNotice')}</p>
      </div>

      {loading && <LoadingIndicator label={t('common.loading')} />}
      {!loading && error && <p className="text-sm text-rose-600 dark:text-rose-300">{error}</p>}
      {!loading && !error && entries.length === 0 && (
        <p className="text-sm text-slate-500 dark:text-slate-400">{t('teamScore.empty')}</p>
      )}
      {!loading && !error && entries.length > 0 && (
        <div className="overflow-x-auto">
          <table className="min-w-full text-left text-sm">
            <thead className="bg-slate-50 text-xs text-slate-500 dark:bg-slate-800/80 dark:text-slate-300">
              <tr>
                <th className={HEADER_CLASS}>{t('teamScore.table.rank')}</th>
                <th className={HEADER_CLASS}>{t('teamScore.table.nickname')}</th>
                <th className={HEADER_CLASS}>{t('teamScore.table.points')}</th>
                <th className={HEADER_CLASS}>{t('teamScore.table.places')}</th>
                <th className={HEADER_CLASS}>{t('teamScore.table.tournaments')}</th>
                <th className={HEADER_CLASS}>{t('teamScore.table.series')}</th>
                <th className={HEADER_CLASS}>{t('teamScore.table.seriesWinRate')}</th>
                <th className={HEADER_CLASS}>{t('teamScore.table.winRate')}</th>
              </tr>
            </thead>
            <tbody>
              {entries.map((entry) => (
                <tr key={entry.playerId} className="border-t border-slate-100 dark:border-slate-800">
                  <td className="px-3 py-2 font-semibold text-slate-900 dark:text-slate-100">{entry.rank}</td>
                  <td className="px-3 py-2 font-medium text-slate-900 dark:text-slate-100">{entry.nickname ?? '-'}</td>
                  <td className="px-3 py-2 font-semibold text-indigo-700 dark:text-indigo-300">{entry.points}</td>
                  <td className={CELL_CLASS}>
                    {t('teamScore.placesValue', {
                      first: entry.championships,
                      second: entry.runnerUps,
                      third: entry.thirdPlaces,
                    })}
                  </td>
                  <td className={CELL_CLASS}>{entry.tournaments}</td>
                  <td className={CELL_CLASS}>
                    {t('teamScore.seriesValue', { wins: entry.seriesWins, losses: entry.seriesLosses })}
                  </td>
                  <td className={CELL_CLASS}>{formatScoreRate(entry.seriesWinRate)}</td>
                  <td className={CELL_CLASS}>
                    {formatScoreRate(entry.winRate)}
                    <span className="ml-1 text-xs text-slate-500 dark:text-slate-400">
                      {t('teamScore.seriesValue', { wins: entry.wins, losses: entry.losses })}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
