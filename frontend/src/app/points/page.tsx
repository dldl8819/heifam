'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { useAdminAuth } from '@/lib/admin-auth'
import { apiClient, isApiForbiddenError } from '@/lib/api'
import { Alert, AlertContent, AlertDescription, AlertIcon } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { PointHistoryModal } from '@/components/point-history-modal'
import { PrizeEventsPanel } from '@/components/prize-events-panel'
import { t } from '@/lib/i18n'
import {
  currentKstMonth,
  formatPointAmount,
  pointReasonKey,
  shiftMonth,
} from '@/lib/points'
import type {
  PointMonthlyHistory,
  PointRankingEntry,
  PointRankingResponse,
  PointSummaryResponse,
} from '@/types/api'

const TEMP_GROUP_ID = 1
const CARD_CLASS = 'rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900'

function ErrorAlert({ message }: { message: string }) {
  return (
    <Alert variant="destructive" appearance="light">
      <AlertIcon icon="destructive">!</AlertIcon>
      <AlertContent>
        <AlertDescription>{message}</AlertDescription>
      </AlertContent>
    </Alert>
  )
}

function SummaryStat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg bg-slate-50 px-3 py-2 dark:bg-slate-800/60">
      <p className="text-xs text-slate-500 dark:text-slate-400">{label}</p>
      <p className="mt-1 text-base font-semibold text-slate-900 dark:text-slate-100">{value}</p>
    </div>
  )
}

function formatMonthLabel(month: string): string {
  const [year, monthNumber] = month.split('-')
  return t('points.ranking.monthLabel', { year, month: Number(monthNumber) })
}

export default function PointsPage() {
  const { isAdmin, isSuperAdmin } = useAdminAuth()
  const thisMonth = currentKstMonth()

  const [summary, setSummary] = useState<PointSummaryResponse | null>(null)
  const [summaryLoading, setSummaryLoading] = useState<boolean>(true)
  const [summaryError, setSummaryError] = useState<string | null>(null)

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
      <div className="space-y-1">
        <h1 className="text-lg font-semibold text-slate-900 dark:text-slate-100">{t('points.title')}</h1>
        <p className="text-sm text-slate-500 dark:text-slate-400">{t('points.description')}</p>
      </div>

      <div className={`${CARD_CLASS} space-y-4`}>
        <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('points.summary.title')}</h2>
        {summaryLoading && !summary && <LoadingIndicator label={t('common.loading')} />}
        {summaryError && <ErrorAlert message={summaryError} />}
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

      <div className={`${CARD_CLASS} space-y-3`}>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="space-y-0.5">
            <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('points.ranking.title')}</h2>
            <p className="text-xs text-slate-500 dark:text-slate-400">{t('points.ranking.description')}</p>
          </div>
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
        {!rankingLoading && rankingError && <ErrorAlert message={rankingError} />}
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

      {/* Prize events stay with admins; the backend enforces the same. */}
      {isAdmin && <PrizeEventsPanel groupId={TEMP_GROUP_ID} canManage={isSuperAdmin} />}

    </section>
  )
}
