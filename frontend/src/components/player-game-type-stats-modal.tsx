'use client'

import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { t } from '@/lib/i18n'
import type { GroupPlayerGameTypeStat, GroupPlayerRaceStatsItem } from '@/types/api'

const DISPLAY_GAME_TYPES = ['PPP', 'PPT', 'PPZ', 'PTZ', 'PP', 'PT', 'PZ'] as const

type PlayerGameTypeStatsModalProps = {
  open: boolean
  playerName: string
  stats: GroupPlayerRaceStatsItem | null
  loading: boolean
  error: string | null
  onClose: () => void
}

function formatWinRate(value: number): string {
  const percentage = value <= 1 ? value * 100 : value
  return `${percentage.toFixed(2)}%`
}

function resolveStat(
  stats: GroupPlayerRaceStatsItem | null,
  gameType: string
): GroupPlayerGameTypeStat {
  const matched = stats?.byGameType.find((item) => item.gameType === gameType)
  if (matched) {
    return matched
  }

  return {
    gameType,
    wins: 0,
    losses: 0,
    games: 0,
    winRate: 0,
  }
}

export function PlayerGameTypeStatsModal({
  open,
  playerName,
  stats,
  loading,
  error,
  onClose,
}: PlayerGameTypeStatsModalProps) {
  if (!open) {
    return null
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/50 px-4 py-6"
      role="dialog"
      aria-modal="true"
      aria-labelledby="player-game-type-stats-title"
    >
      <article className="w-full max-w-xl overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xl">
        <div className="flex items-start justify-between gap-4 border-b border-slate-100 px-5 py-4">
          <div>
            <h2 id="player-game-type-stats-title" className="text-lg font-bold text-slate-950">
              {t('statsModal.title', { nickname: playerName })}
            </h2>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-md border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-700 transition-colors hover:bg-slate-50"
          >
            {t('statsModal.close')}
          </button>
        </div>

        <div className="p-5">
          {loading ? (
            <LoadingIndicator label={t('statsModal.loading')} className="py-8" />
          ) : error ? (
            <p className="rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">
              {error}
            </p>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full text-left text-sm">
                <thead className="bg-slate-50 text-xs text-slate-500">
                  <tr>
                    <th className="px-3 py-2">{t('statsModal.table.gameType')}</th>
                    <th className="px-3 py-2">{t('statsModal.table.wins')}</th>
                    <th className="px-3 py-2">{t('statsModal.table.losses')}</th>
                    <th className="px-3 py-2">{t('statsModal.table.games')}</th>
                    <th className="px-3 py-2">{t('statsModal.table.winRate')}</th>
                  </tr>
                </thead>
                <tbody>
                  {DISPLAY_GAME_TYPES.map((gameType) => {
                    const stat = resolveStat(stats, gameType)
                    return (
                      <tr key={gameType} className="border-t border-slate-100">
                        <td className="px-3 py-2 font-semibold text-slate-900">{gameType}</td>
                        <td className="px-3 py-2 text-slate-700">{stat.wins}</td>
                        <td className="px-3 py-2 text-slate-700">{stat.losses}</td>
                        <td className="px-3 py-2 text-slate-700">{stat.games}</td>
                        <td className="px-3 py-2 text-slate-700">{formatWinRate(stat.winRate)}</td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </article>
    </div>
  )
}
