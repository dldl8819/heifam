'use client'

import { useEffect } from 'react'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { t } from '@/lib/i18n'
import { formatPointAmount, pointReasonKey } from '@/lib/points'
import type { PointMonthlyHistory } from '@/types/api'

type PointHistoryModalProps = {
  open: boolean
  nickname: string
  monthLabel: string
  history: PointMonthlyHistory | null
  loading: boolean
  error: string | null
  onClose: () => void
}

function reasonLabel(reason: string): string {
  const key = pointReasonKey(reason)
  return key ? t(key) : reason
}

function amountClass(amount: number): string {
  return amount < 0 ? 'text-rose-600 dark:text-rose-400' : 'text-emerald-600 dark:text-emerald-400'
}

/** How one person on the monthly ranking earned their points that month. */
export function PointHistoryModal({ open, nickname, monthLabel, history, loading, error, onClose }: PointHistoryModalProps) {
  useEffect(() => {
    if (!open) {
      return
    }
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [open, onClose])

  if (!open) {
    return null
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/50 px-4 py-6 dark:bg-black/70"
      role="dialog"
      aria-modal="true"
      aria-labelledby="point-history-title"
      onClick={(event) => {
        if (event.target === event.currentTarget) {
          onClose()
        }
      }}
    >
      <article className="flex max-h-full w-full max-w-lg flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-2xl dark:border-slate-700 dark:bg-slate-900">
        <div className="flex items-start justify-between gap-4 border-b border-slate-100 px-5 py-4 dark:border-slate-800">
          <div>
            <h2 id="point-history-title" className="text-lg font-bold text-slate-950 dark:text-slate-100">
              {t('points.ranking.history.title', { nickname })}
            </h2>
            <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
              {history
                ? t('points.ranking.history.summary', { month: monthLabel, points: history.points.toLocaleString('ko-KR') })
                : monthLabel}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-md border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-700 transition-colors hover:bg-slate-50 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
          >
            {t('points.ranking.history.close')}
          </button>
        </div>

        <div className="space-y-4 overflow-y-auto p-5">
          {loading && <LoadingIndicator label={t('common.loading')} className="py-8" />}
          {!loading && error && (
            <p className="rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700 dark:border-rose-800 dark:bg-rose-950/40 dark:text-rose-300">
              {error}
            </p>
          )}
          {!loading && !error && history && history.entries.length === 0 && (
            <p className="py-4 text-center text-sm text-slate-500 dark:text-slate-400">{t('points.ranking.history.empty')}</p>
          )}
          {!loading && !error && history && history.entries.length > 0 && (
            <>
              <section className="space-y-2">
                <h3 className="text-xs font-semibold text-slate-700 dark:text-slate-200">{t('points.ranking.history.byReason')}</h3>
                <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200 dark:divide-slate-800 dark:border-slate-700">
                  {history.reasons.map((reason) => (
                    <li key={reason.reason} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
                      <span className="text-slate-800 dark:text-slate-200">
                        {reasonLabel(reason.reason)}
                        <span className="ml-1.5 text-xs text-slate-500 dark:text-slate-400">
                          {t('points.ranking.history.count', { count: reason.count })}
                        </span>
                      </span>
                      <span className={`font-semibold ${amountClass(reason.points)}`}>{formatPointAmount(reason.points)}</span>
                    </li>
                  ))}
                </ul>
              </section>

              <section className="space-y-2">
                <h3 className="text-xs font-semibold text-slate-700 dark:text-slate-200">{t('points.ranking.history.entries')}</h3>
                <table className="min-w-full text-left text-sm">
                  <thead className="bg-slate-50 text-xs text-slate-500 dark:bg-slate-800/80 dark:text-slate-300">
                    <tr>
                      <th className="px-3 py-2">{t('points.history.date')}</th>
                      <th className="px-3 py-2">{t('points.history.reason')}</th>
                      <th className="px-3 py-2 text-right">{t('points.history.amount')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {history.entries.map((entry, index) => (
                      <tr key={`${entry.createdAt}-${index}`} className="border-t border-slate-100 dark:border-slate-800">
                        <td className="whitespace-nowrap px-3 py-2 text-slate-500 dark:text-slate-400">{entry.kstDate}</td>
                        <td className="px-3 py-2 text-slate-800 dark:text-slate-200">
                          {reasonLabel(entry.reason)}
                          {entry.memo && <span className="block text-xs text-slate-500 dark:text-slate-400">{entry.memo}</span>}
                        </td>
                        <td className={`px-3 py-2 text-right font-medium ${amountClass(entry.amount)}`}>
                          {formatPointAmount(entry.amount)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </section>
            </>
          )}
        </div>
      </article>
    </div>
  )
}
