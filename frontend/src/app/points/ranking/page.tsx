'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { apiClient } from '@/lib/api'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { PointHistoryModal } from '@/components/point-history-modal'
import { POINT_CARD_CLASS, PointErrorAlert, PointPageHeader } from '@/components/point-page-parts'
import { t } from '@/lib/i18n'
import { currentKstMonth, shiftMonth } from '@/lib/points'
import type { PointMonthlyHistory, PointRankingEntry, PointRankingResponse } from '@/types/api'

function formatMonthLabel(month: string): string {
  const [year, monthNumber] = month.split('-')
  return t('points.ranking.monthLabel', { year, month: Number(monthNumber) })
}

/** The month's ranking by points earned in it; a name opens how they earned them. */
export default function PointRankingPage() {
  const thisMonth = currentKstMonth()

  const [month, setMonth] = useState<string>(thisMonth)
  const [ranking, setRanking] = useState<PointRankingResponse | null>(null)
  const [rankingLoading, setRankingLoading] = useState<boolean>(true)
  const [rankingError, setRankingError] = useState<string | null>(null)
  const [historyEntry, setHistoryEntry] = useState<PointRankingEntry | null>(null)
  const [history, setHistory] = useState<PointMonthlyHistory | null>(null)
  const [historyLoading, setHistoryLoading] = useState<boolean>(false)
  const [historyError, setHistoryError] = useState<string | null>(null)
  // The account last asked for, so a slower answer for an earlier click is dropped.
  const historyRequest = useRef<number | null>(null)

  useEffect(() => {
    let cancelled = false
    setRankingLoading(true)
    setRankingError(null)
    apiClient
      .getPointRanking(month)
      .then((response) => {
        if (!cancelled) {
          setRanking(response)
        }
      })
      .catch(() => {
        if (!cancelled) {
          setRanking(null)
          setRankingError(t('points.ranking.loadError'))
        }
      })
      .finally(() => {
        if (!cancelled) {
          setRankingLoading(false)
        }
      })
    return () => {
      cancelled = true
    }
  }, [month])

  // A person on the ranking: how they earned their points in the month shown.
  const openHistory = async (entry: PointRankingEntry) => {
    if (entry.accountId === null) {
      return
    }
    const accountId = entry.accountId
    historyRequest.current = accountId
    setHistoryEntry(entry)
    setHistory(null)
    setHistoryError(null)
    setHistoryLoading(true)
    try {
      const response = await apiClient.getPointRankingHistory(accountId, month)
      if (historyRequest.current === accountId) {
        setHistory(response)
      }
    } catch {
      if (historyRequest.current === accountId) {
        setHistoryError(t('points.ranking.history.loadError'))
      }
    } finally {
      if (historyRequest.current === accountId) {
        setHistoryLoading(false)
      }
    }
  }

  const closeHistory = useCallback(() => {
    historyRequest.current = null
    setHistoryEntry(null)
    setHistory(null)
    setHistoryError(null)
    setHistoryLoading(false)
  }, [])

  // The history belongs to the month it was opened for.
  useEffect(() => {
    closeHistory()
  }, [closeHistory, month])

  return (
    <section className="space-y-6">
      <PointPageHeader title={t('nav.pointRanking')} description={t('points.ranking.description')} />

      <div className={`${POINT_CARD_CLASS} space-y-3`}>
        <div className="flex flex-wrap items-center justify-end gap-2">
          <div className="flex items-center gap-2 text-sm">
            <button
              type="button"
              onClick={() => setMonth((current) => shiftMonth(current, -1))}
              className="rounded-md border border-slate-300 px-2 py-1 text-xs text-slate-700 hover:bg-slate-50 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
            >
              {t('points.ranking.previous')}
            </button>
            <span className="min-w-[6rem] text-center font-medium text-slate-900 dark:text-slate-100">
              {formatMonthLabel(month)}
            </span>
            <button
              type="button"
              disabled={month >= thisMonth}
              onClick={() => setMonth((current) => shiftMonth(current, 1))}
              className="rounded-md border border-slate-300 px-2 py-1 text-xs text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
            >
              {t('points.ranking.next')}
            </button>
          </div>
        </div>

        {rankingLoading && <LoadingIndicator label={t('common.loading')} />}
        {!rankingLoading && rankingError && <PointErrorAlert message={rankingError} />}
        {!rankingLoading && !rankingError && ranking && ranking.entries.length === 0 && (
          <p className="py-4 text-center text-sm text-slate-500 dark:text-slate-400">{t('points.ranking.empty')}</p>
        )}
        {!rankingLoading && !rankingError && ranking && ranking.entries.length > 0 && (
          <div className="overflow-x-auto">
            <table className="min-w-full text-left text-sm">
              <thead className="bg-slate-50 text-xs text-slate-500 dark:bg-slate-800/80 dark:text-slate-300">
                <tr>
                  <th className="w-16 px-3 py-2">{t('points.ranking.rank')}</th>
                  <th className="px-3 py-2">{t('points.ranking.nickname')}</th>
                  <th className="px-3 py-2 text-right">{t('points.ranking.points')}</th>
                </tr>
              </thead>
              <tbody>
                {ranking.entries.map((entry, index) => (
                  <tr key={`${entry.rank}-${index}`} className="border-t border-slate-100 dark:border-slate-800">
                    <td className="px-3 py-2 font-medium text-slate-900 dark:text-slate-100">{entry.rank}</td>
                    <td className="px-3 py-2 text-slate-800 dark:text-slate-200">
                      {entry.accountId !== null ? (
                        <button
                          type="button"
                          onClick={() => void openHistory(entry)}
                          aria-label={t('points.ranking.openHistory', {
                            nickname: entry.nickname ?? t('points.ranking.unknownNickname'),
                          })}
                          className="font-medium text-indigo-700 underline-offset-2 hover:underline dark:text-indigo-300"
                        >
                          {entry.nickname ?? t('points.ranking.unknownNickname')}
                        </button>
                      ) : (
                        entry.nickname ?? t('points.ranking.unknownNickname')
                      )}
                    </td>
                    <td className="px-3 py-2 text-right font-medium text-slate-900 dark:text-slate-100">
                      {`${entry.points.toLocaleString('ko-KR')}p`}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>


      <PointHistoryModal
        open={historyEntry !== null}
        nickname={historyEntry?.nickname ?? t('points.ranking.unknownNickname')}
        monthLabel={formatMonthLabel(month)}
        history={history}
        loading={historyLoading}
        error={historyError}
        onClose={closeHistory}
      />
    </section>
  )
}
