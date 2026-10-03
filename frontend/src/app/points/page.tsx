'use client'

import { FormEvent, useCallback, useEffect, useState } from 'react'
import { useAdminAuth } from '@/lib/admin-auth'
import { apiClient, isApiForbiddenError } from '@/lib/api'
import { Alert, AlertContent, AlertDescription, AlertIcon } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { t } from '@/lib/i18n'
import {
  POINT_ADJUSTMENT_MAX,
  POINT_MEMO_MAX_LENGTH,
  buildPointMemberOptions,
  currentKstMonth,
  formatPointAmount,
  parseAdjustmentAmount,
  pointReasonKey,
  shiftMonth,
  type PointMemberOption,
} from '@/lib/points'
import type { PointRankingResponse, PointSummaryResponse } from '@/types/api'

const CARD_CLASS = 'rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900'
const INPUT_CLASS = 'w-full rounded-md border border-slate-300 px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-900'

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
  const { isSuperAdmin } = useAdminAuth()
  const thisMonth = currentKstMonth()

  const [summary, setSummary] = useState<PointSummaryResponse | null>(null)
  const [summaryLoading, setSummaryLoading] = useState<boolean>(true)
  const [summaryError, setSummaryError] = useState<string | null>(null)

  const [month, setMonth] = useState<string>(thisMonth)
  const [ranking, setRanking] = useState<PointRankingResponse | null>(null)
  const [rankingLoading, setRankingLoading] = useState<boolean>(true)
  const [rankingError, setRankingError] = useState<string | null>(null)
  const [rankingVersion, setRankingVersion] = useState<number>(0)

  const [memberOptions, setMemberOptions] = useState<PointMemberOption[]>([])
  const [membersError, setMembersError] = useState<string | null>(null)
  const [targetEmail, setTargetEmail] = useState<string>('')
  const [amountText, setAmountText] = useState<string>('')
  const [memo, setMemo] = useState<string>('')
  const [adjusting, setAdjusting] = useState<boolean>(false)
  const [adjustError, setAdjustError] = useState<string | null>(null)
  const [adjustMessage, setAdjustMessage] = useState<string | null>(null)

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
  }, [month, rankingVersion])

  useEffect(() => {
    if (!isSuperAdmin) {
      return
    }
    let cancelled = false
    Promise.all([apiClient.getAdminEmailList(), apiClient.getAllowedEmailList()])
      .then(([admins, allowed]) => {
        if (!cancelled) {
          setMemberOptions(buildPointMemberOptions([admins.superAdmins, admins.admins, allowed.allowedUsers]))
        }
      })
      .catch(() => {
        if (!cancelled) {
          setMembersError(t('points.adjust.membersLoadError'))
        }
      })
    return () => {
      cancelled = true
    }
  }, [isSuperAdmin])

  const handleAdjust = useCallback(
    async (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault()
      setAdjustMessage(null)
      if (!targetEmail) {
        setAdjustError(t('points.adjust.targetRequired'))
        return
      }
      const amount = parseAdjustmentAmount(amountText)
      if (amount === null) {
        setAdjustError(t('points.adjust.amountInvalid', { max: POINT_ADJUSTMENT_MAX }))
        return
      }

      setAdjustError(null)
      setAdjusting(true)
      try {
        const response = await apiClient.adjustPoints({
          email: targetEmail,
          amount,
          memo: memo.trim().length > 0 ? memo.trim() : null,
        })
        setAdjustMessage(
          t('points.adjust.success', {
            nickname: response.nickname ?? t('points.ranking.unknownNickname'),
            amount: formatPointAmount(response.amount),
            balance: response.balance.toLocaleString('ko-KR'),
          })
        )
        setAmountText('')
        setMemo('')
        setRankingVersion((version) => version + 1)
        void loadSummary()
      } catch {
        setAdjustError(t('points.adjust.error'))
      } finally {
        setAdjusting(false)
      }
    },
    [amountText, loadSummary, memo, targetEmail]
  )

  return (
    <section className="space-y-6">
      <div className="space-y-1">
        <h1 className="text-lg font-semibold text-slate-900 dark:text-slate-100">{t('points.title')}</h1>
        <p className="text-sm text-slate-500 dark:text-slate-400">{t('points.description')}</p>
      </div>

      <p className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200">
        {t('points.trialNotice')}
      </p>

      <div className={`${CARD_CLASS} space-y-4`}>
        <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('points.summary.title')}</h2>
        {summaryLoading && !summary && <LoadingIndicator label={t('common.loading')} />}
        {summaryError && <ErrorAlert message={summaryError} />}
        {summary && (
          <>
            <div className="grid gap-3 sm:grid-cols-3">
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
                      {entry.nickname ?? t('points.ranking.unknownNickname')}
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

      {isSuperAdmin && (
        <form onSubmit={handleAdjust} className={`${CARD_CLASS} space-y-3`}>
          <div className="space-y-0.5">
            <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('points.adjust.title')}</h2>
            <p className="text-xs text-slate-500 dark:text-slate-400">{t('points.adjust.description')}</p>
          </div>
          {membersError && <ErrorAlert message={membersError} />}
          {adjustError && (
            <p className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700 dark:border-rose-800 dark:bg-rose-950/40 dark:text-rose-300">
              {adjustError}
            </p>
          )}
          {adjustMessage && (
            <p className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300">
              {adjustMessage}
            </p>
          )}
          <div className="grid gap-3 sm:grid-cols-[2fr_1fr]">
            <label className="space-y-1 text-xs text-slate-600 dark:text-slate-300">
              <span>{t('points.adjust.target')}</span>
              <select value={targetEmail} onChange={(event) => setTargetEmail(event.target.value)} className={INPUT_CLASS}>
                <option value="">{t('points.adjust.targetPlaceholder')}</option>
                {memberOptions.map((option) => (
                  <option key={option.email} value={option.email}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
            <label className="space-y-1 text-xs text-slate-600 dark:text-slate-300">
              <span>{t('points.adjust.amount')}</span>
              <input
                type="text"
                inputMode="numeric"
                value={amountText}
                onChange={(event) => setAmountText(event.target.value)}
                placeholder={t('points.adjust.amountPlaceholder')}
                className={INPUT_CLASS}
              />
            </label>
          </div>
          <label className="block space-y-1 text-xs text-slate-600 dark:text-slate-300">
            <span>{t('points.adjust.memo')}</span>
            <input
              type="text"
              value={memo}
              maxLength={POINT_MEMO_MAX_LENGTH}
              onChange={(event) => setMemo(event.target.value)}
              placeholder={t('points.adjust.memoPlaceholder')}
              className={INPUT_CLASS}
            />
          </label>
          <button
            type="submit"
            disabled={adjusting}
            className="rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-medium text-white hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white"
          >
            {adjusting ? t('points.adjust.submitting') : t('points.adjust.submit')}
          </button>
        </form>
      )}
    </section>
  )
}
