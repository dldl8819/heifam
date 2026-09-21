'use client'

import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { t } from '@/lib/i18n'
import { TEAMMATE_MIN_GAMES, filterTeammateStats, formatWinRate } from '@/lib/teammate-stats'
import type { GroupPlayerTeammateStats } from '@/types/api'

type PlayerTeammateStatsModalProps = {
  open: boolean
  playerName: string
  stats: GroupPlayerTeammateStats | null
  loading: boolean
  error: string | null
  onClose: () => void
}

const key = (name: string) => `teammateStatsModal.${name}`

export function PlayerTeammateStatsModal({
  open,
  playerName,
  stats,
  loading,
  error,
  onClose,
}: PlayerTeammateStatsModalProps) {
  if (!open) {
    return null
  }

  const teammates = stats ? filterTeammateStats(stats.teammates) : []

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/50 px-4 py-6 dark:bg-black/70"
      role="dialog"
      aria-modal="true"
      aria-labelledby="player-teammate-stats-title"
    >
      <article className="flex max-h-full w-full max-w-2xl flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xl dark:border-slate-700 dark:bg-slate-900">
        <div className="flex items-start justify-between gap-4 border-b border-slate-100 px-5 py-4 dark:border-slate-800">
          <div>
            <h2 id="player-teammate-stats-title" className="text-lg font-bold text-slate-950 dark:text-slate-100">
              {t(key('title'), { nickname: playerName })}
            </h2>
            {stats && (
              <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
                {t(key('summary'), {
                  games: String(stats.games),
                  wins: String(stats.wins),
                  losses: String(stats.losses),
                  winRate: formatWinRate(stats.winRate),
                })}
              </p>
            )}
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-md border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-700 transition-colors hover:bg-slate-50 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
          >
            {t(key('close'))}
          </button>
        </div>

        <div className="overflow-y-auto p-5">
          {loading ? (
            <LoadingIndicator label={t(key('loading'))} className="py-8" />
          ) : error ? (
            <p className="rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700 dark:border-rose-800 dark:bg-rose-950/40 dark:text-rose-300">
              {error}
            </p>
          ) : (
            <div className="space-y-3">
              <p className="text-xs text-slate-500 dark:text-slate-400">
                {t(key('hint'), { count: String(TEAMMATE_MIN_GAMES) })}
              </p>

              {teammates.length === 0 ? (
                <p className="rounded-md border border-dashed border-slate-300 px-3 py-6 text-center text-sm text-slate-500 dark:border-slate-700 dark:text-slate-400">
                  {t(key('empty'), { count: String(TEAMMATE_MIN_GAMES) })}
                </p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="min-w-full text-left text-sm">
                    <thead className="bg-slate-50 text-xs text-slate-500 dark:bg-slate-800/80 dark:text-slate-300">
                      <tr>
                        <th className="whitespace-nowrap px-3 py-2">{t(key('table.teammate'))}</th>
                        <th className="whitespace-nowrap px-3 py-2 text-right">{t(key('table.games'))}</th>
                        <th className="whitespace-nowrap px-3 py-2 text-right">{t(key('table.wins'))}</th>
                        <th className="whitespace-nowrap px-3 py-2 text-right">{t(key('table.losses'))}</th>
                        <th className="whitespace-nowrap px-3 py-2 text-right">{t(key('table.winRate'))}</th>
                        <th className="whitespace-nowrap px-3 py-2 text-right">{t(key('table.streak'))}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {teammates.map((teammate) => (
                        <tr key={teammate.playerId} className="border-t border-slate-100 dark:border-slate-800">
                          <td className="px-3 py-2 font-semibold text-slate-900 dark:text-slate-100">
                            {teammate.nickname}
                          </td>
                          <td className="px-3 py-2 text-right tabular-nums text-slate-700 dark:text-slate-300">
                            {teammate.games}
                          </td>
                          <td className="px-3 py-2 text-right tabular-nums text-slate-700 dark:text-slate-300">
                            {teammate.wins}
                          </td>
                          <td className="px-3 py-2 text-right tabular-nums text-slate-700 dark:text-slate-300">
                            {teammate.losses}
                          </td>
                          <td className="px-3 py-2 text-right tabular-nums font-semibold text-slate-900 dark:text-slate-100">
                            {formatWinRate(teammate.winRate)}
                          </td>
                          <td className="px-3 py-2 text-right tabular-nums text-slate-700 dark:text-slate-300">
                            {teammate.currentWinStreak > 0
                              ? t(key('streakValue'), { count: String(teammate.currentWinStreak) })
                              : '-'}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}
        </div>
      </article>
    </div>
  )
}
