'use client'

import { useCallback, useEffect, useState } from 'react'
import { apiClient, isApiForbiddenError } from '@/lib/api'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { POINT_CARD_CLASS, PointErrorAlert, PointPageHeader } from '@/components/point-page-parts'
import { t } from '@/lib/i18n'
import { formatPointAmount, pointReasonKey } from '@/lib/points'
import type { PointSummaryResponse } from '@/types/api'

function SummaryStat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg bg-slate-50 px-3 py-2 dark:bg-slate-800/60">
      <p className="text-xs text-slate-500 dark:text-slate-400">{label}</p>
      <p className="mt-1 text-base font-semibold text-slate-900 dark:text-slate-100">{value}</p>
    </div>
  )
}

/** A member's own points: the balance, what can still be earned today, the rules and recent history. */
export default function MyPointsPage() {
  const [summary, setSummary] = useState<PointSummaryResponse | null>(null)
  const [summaryLoading, setSummaryLoading] = useState<boolean>(true)
  const [summaryError, setSummaryError] = useState<string | null>(null)

  const loadSummary = useCallback(async () => {
    setSummaryLoading(true)
    setSummaryError(null)
    try {
      setSummary(await apiClient.getMyPoints())
    } catch (error) {
      setSummaryError(isApiForbiddenError(error) ? t('points.forbidden') : t('points.loadError'))
    } finally {
      setSummaryLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadSummary()
  }, [loadSummary])

  useEffect(() => {
    void loadSummary()
  }, [loadSummary])

  return (
    <section className="space-y-6">
      <PointPageHeader title={t('points.summary.title')} description={t('points.description')} />

      <div className={`${POINT_CARD_CLASS} space-y-4`}>
        {summaryLoading && !summary && <LoadingIndicator label={t('common.loading')} />}
        {summaryError && <PointErrorAlert message={summaryError} />}
        {summary && (
          <>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
              <SummaryStat label={t('points.summary.balance')} value={`${summary.balance.toLocaleString('ko-KR')}p`} />
              <SummaryStat
                label={t('points.summary.dailyLogin')}
                value={
                  summary.dailyLoginEarnedToday
                    ? t('points.summary.dailyLoginDone', { points: summary.dailyLoginPoints })
                    : t('points.summary.dailyLoginPending')
                }
              />
              <SummaryStat
                label={t('points.summary.matchResults')}
                value={t('points.summary.matchResultsValue', {
                  count: summary.matchResultsToday,
                  cap: summary.matchResultDailyCap,
                })}
              />
              <SummaryStat
                label={t('points.summary.predictions')}
                value={t('points.summary.predictionsValue', {
                  points: summary.predictionPointsToday,
                  cap: summary.predictionHitDailyCap,
                })}
              />
              <SummaryStat
                label={t('points.summary.matchConfirms')}
                value={t('points.summary.matchConfirmsValue', {
                  count: summary.matchConfirmsToday,
                  cap: summary.matchConfirmDailyCap,
                })}
              />
            </div>

            <div className="space-y-1 text-xs text-slate-600 dark:text-slate-300">
              <p className="font-medium text-slate-700 dark:text-slate-200">{t('points.rules.title')}</p>
              <ul className="list-disc space-y-0.5 pl-4">
                <li>{t('points.rules.dailyLogin', { points: summary.dailyLoginPoints })}</li>
                <li>
                  {t('points.rules.matchResult', {
                    points: summary.matchResultPoints,
                    cap: summary.matchResultDailyCap,
                  })}
                </li>
                <li>
                  {t('points.rules.prediction', {
                    points: summary.predictionHitPoints,
                    cap: summary.predictionHitDailyCap,
                  })}
                </li>
                <li>
                  {t('points.rules.matchConfirm', {
                    points: summary.matchConfirmPoints,
                    cap: summary.matchConfirmDailyCap,
                    hours: summary.matchConfirmWindowHours,
                  })}
                </li>
                {typeof summary.noticeCommentLikeDailyCap === 'number' && (
                  <li>
                    {t('points.rules.noticeCommentLike', {
                      points: summary.noticeCommentLikePoints ?? 1,
                      cap: summary.noticeCommentLikeDailyCap,
                    })}
                  </li>
                )}
                <li>{t('points.rules.reversal')}</li>
              </ul>
            </div>

            <div className="space-y-2">
              <h3 className="text-xs font-semibold text-slate-700 dark:text-slate-200">{t('points.history.title')}</h3>
              {summary.recent.length === 0 ? (
                <p className="text-sm text-slate-500 dark:text-slate-400">{t('points.history.empty')}</p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="min-w-full text-left text-sm">
                    <thead className="bg-slate-50 text-xs text-slate-500 dark:bg-slate-800/80 dark:text-slate-300">
                      <tr>
                        <th className="px-3 py-2">{t('points.history.date')}</th>
                        <th className="px-3 py-2">{t('points.history.reason')}</th>
                        <th className="px-3 py-2 text-right">{t('points.history.amount')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {summary.recent.map((item, index) => {
                        const reasonKey = pointReasonKey(item.reason)
                        return (
                          <tr key={`${item.createdAt}-${index}`} className="border-t border-slate-100 dark:border-slate-800">
                            <td className="whitespace-nowrap px-3 py-2 text-slate-500 dark:text-slate-400">
                              {item.kstDate.replace(/-/g, '.')}
                            </td>
                            <td className="px-3 py-2 text-slate-800 dark:text-slate-200">
                              {reasonKey ? t(reasonKey) : item.reason}
                              {item.memo && (
                                <span className="ml-2 text-xs text-slate-500 dark:text-slate-400">{item.memo}</span>
                              )}
                            </td>
                            <td
                              className={`whitespace-nowrap px-3 py-2 text-right font-medium ${
                                item.amount < 0 ? 'text-rose-600 dark:text-rose-400' : 'text-emerald-600 dark:text-emerald-400'
                              }`}
                            >
                              {formatPointAmount(item.amount)}
                            </td>
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </>
        )}
      </div>

    </section>
  )
}
