'use client'

import { FormEvent, useCallback, useEffect, useState } from 'react'
import { apiClient } from '@/lib/api'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { t } from '@/lib/i18n'
import { buildPrizeWinners, initialPrizeDrafts, type PrizeDraft } from '@/lib/prize-events'
import type { PrizeEvent } from '@/types/api'

type PrizeEventsPanelProps = {
  groupId: number
  canManage: boolean
}

const CARD_CLASS = 'rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900'
const INPUT_CLASS = 'rounded-md border border-slate-300 px-2 py-1 text-sm dark:border-slate-600 dark:bg-slate-900'
const PRIMARY_BUTTON_CLASS =
  'rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-medium text-white hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white'
const SECONDARY_BUTTON_CLASS =
  'rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800'

function describeError(error: unknown, fallback: string): string {
  return error instanceof Error && error.message.trim().length > 0 ? `${fallback} (${error.message})` : fallback
}

function todayInKorea(): string {
  return new Date(Date.now() + 9 * 60 * 60 * 1000).toISOString().slice(0, 10)
}

export function PrizeEventsPanel({ groupId, canManage }: PrizeEventsPanelProps) {
  const [events, setEvents] = useState<PrizeEvent[]>([])
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [busy, setBusy] = useState<boolean>(false)
  const [title, setTitle] = useState<string>('')
  const [periodStart, setPeriodStart] = useState<string>('')
  const [periodEnd, setPeriodEnd] = useState<string>('')
  const [winnerCount, setWinnerCount] = useState<string>('3')
  const [drafts, setDrafts] = useState<Record<number, Record<number, PrizeDraft>>>({})
  const [paidOn, setPaidOn] = useState<Record<number, string>>({})

  const load = useCallback(async () => {
    try {
      const response = await apiClient.getPrizeEvents(groupId)
      setEvents(response.events)
      setDrafts((previous) => {
        const next = { ...previous }
        response.events.forEach((event) => {
          if (event.status === 'OPEN' && !next[event.eventId]) {
            next[event.eventId] = initialPrizeDrafts(event.candidates, event.winnerCount)
          }
        })
        return next
      })
      setError(null)
    } catch {
      setError(t('prizeEvents.loadError'))
    } finally {
      setLoading(false)
    }
  }, [groupId])

  useEffect(() => {
    void load()
  }, [load])

  const handleCreate = async (formEvent: FormEvent<HTMLFormElement>) => {
    formEvent.preventDefault()
    setBusy(true)
    setMessage(null)
    try {
      await apiClient.createPrizeEvent(groupId, {
        title: title.trim(),
        periodStart,
        periodEnd,
        winnerCount: Number(winnerCount),
      })
      setTitle('')
      await load()
    } catch (createError) {
      setError(describeError(createError, t('prizeEvents.saveError')))
    } finally {
      setBusy(false)
    }
  }

  const updateDraft = (eventId: number, accountId: number, change: Partial<PrizeDraft>) => {
    setDrafts((previous) => ({
      ...previous,
      [eventId]: {
        ...previous[eventId],
        [accountId]: { ...(previous[eventId]?.[accountId] ?? { selected: false, prize: '', amount: '' }), ...change },
      },
    }))
  }

  const handleConfirm = async (event: PrizeEvent) => {
    const winners = buildPrizeWinners(event.candidates, drafts[event.eventId] ?? {})
    if (winners === null) {
      setError(t('prizeEvents.amountInvalid'))
      return
    }
    if (winners.length === 0) {
      setError(t('prizeEvents.pickWinners'))
      return
    }
    const total = winners.reduce((sum, winner) => sum + winner.amount, 0)
    if (!window.confirm(t('prizeEvents.confirmPrompt', { count: winners.length, total: total.toLocaleString('ko-KR') }))) {
      return
    }
    setBusy(true)
    setMessage(null)
    try {
      await apiClient.confirmPrizeEvent(groupId, event.eventId, {
        paidOn: paidOn[event.eventId] || todayInKorea(),
        winners,
      })
      setMessage(t('prizeEvents.confirmed', { title: event.title }))
      await load()
    } catch (confirmError) {
      setError(describeError(confirmError, t('prizeEvents.saveError')))
    } finally {
      setBusy(false)
    }
  }

  const handleCancel = async (event: PrizeEvent) => {
    if (!window.confirm(t('prizeEvents.cancelPrompt', { title: event.title }))) {
      return
    }
    setBusy(true)
    try {
      await apiClient.cancelPrizeEvent(groupId, event.eventId)
      await load()
    } catch (cancelError) {
      setError(describeError(cancelError, t('prizeEvents.saveError')))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className={`${CARD_CLASS} space-y-4`}>
      <div className="space-y-1">
        <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('prizeEvents.title')}</h2>
        <p className="text-xs text-slate-500 dark:text-slate-400">{t('prizeEvents.description')}</p>
      </div>

      {error && <p className="text-xs text-rose-600 dark:text-rose-300">{error}</p>}
      {message && <p className="text-xs text-emerald-700 dark:text-emerald-300">{message}</p>}

      {canManage && (
        <form onSubmit={handleCreate} className="grid gap-2 rounded-lg bg-slate-50 p-3 dark:bg-slate-800/60 sm:grid-cols-[2fr_1fr_1fr_auto_auto] sm:items-end">
          <label className="space-y-1 text-xs text-slate-600 dark:text-slate-300">
            <span>{t('prizeEvents.form.title')}</span>
            <input value={title} onChange={(event) => setTitle(event.target.value)} maxLength={100} required className={`${INPUT_CLASS} w-full`} placeholder={t('prizeEvents.form.titlePlaceholder')} />
          </label>
          <label className="space-y-1 text-xs text-slate-600 dark:text-slate-300">
            <span>{t('prizeEvents.form.start')}</span>
            <input type="date" value={periodStart} onChange={(event) => setPeriodStart(event.target.value)} required className={`${INPUT_CLASS} w-full`} />
          </label>
          <label className="space-y-1 text-xs text-slate-600 dark:text-slate-300">
            <span>{t('prizeEvents.form.end')}</span>
            <input type="date" value={periodEnd} onChange={(event) => setPeriodEnd(event.target.value)} required className={`${INPUT_CLASS} w-full`} />
          </label>
          <label className="space-y-1 text-xs text-slate-600 dark:text-slate-300">
            <span>{t('prizeEvents.form.winners')}</span>
            <input type="number" min={1} max={20} value={winnerCount} onChange={(event) => setWinnerCount(event.target.value)} required className={`${INPUT_CLASS} w-20`} />
          </label>
          <button type="submit" disabled={busy} className={PRIMARY_BUTTON_CLASS}>
            {t('prizeEvents.form.create')}
          </button>
        </form>
      )}

      {loading && <LoadingIndicator label={t('common.loading')} />}
      {!loading && events.length === 0 && <p className="text-sm text-slate-500 dark:text-slate-400">{t('prizeEvents.empty')}</p>}

      <ul className="space-y-3">
        {events.map((event) => (
          <li key={event.eventId} className="space-y-2 rounded-lg border border-slate-200 p-3 dark:border-slate-700">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div>
                <p className="text-sm font-semibold text-slate-900 dark:text-slate-100">{event.title}</p>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  {t('prizeEvents.period', { start: event.periodStart, end: event.periodEnd, count: event.winnerCount })}
                </p>
              </div>
              <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-medium text-slate-700 dark:bg-slate-800 dark:text-slate-200">
                {t(`prizeEvents.status.${event.status}`)}
              </span>
            </div>

            {event.status === 'OPEN' && (
              <div className="space-y-2">
                {event.candidates.length === 0 ? (
                  <p className="text-xs text-slate-500 dark:text-slate-400">{t('prizeEvents.noCandidates')}</p>
                ) : (
                  <table className="min-w-full text-left text-sm">
                    <thead className="text-xs text-slate-500 dark:text-slate-400">
                      <tr>
                        {canManage && <th className="py-1 pr-2">{t('prizeEvents.table.pick')}</th>}
                        <th className="py-1 pr-2">{t('prizeEvents.table.rank')}</th>
                        <th className="py-1 pr-2">{t('prizeEvents.table.nickname')}</th>
                        <th className="py-1 pr-2">{t('prizeEvents.table.points')}</th>
                        {canManage && <th className="py-1 pr-2">{t('prizeEvents.table.prize')}</th>}
                        {canManage && <th className="py-1 pr-2">{t('prizeEvents.table.amount')}</th>}
                      </tr>
                    </thead>
                    <tbody>
                      {event.candidates.map((candidate) => {
                        const draft = drafts[event.eventId]?.[candidate.pointAccountId]
                        return (
                          <tr key={candidate.pointAccountId} className="border-t border-slate-100 dark:border-slate-800">
                            {canManage && (
                              <td className="py-1 pr-2">
                                <input
                                  type="checkbox"
                                  checked={draft?.selected ?? false}
                                  onChange={(change) => updateDraft(event.eventId, candidate.pointAccountId, { selected: change.target.checked })}
                                  aria-label={t('prizeEvents.table.pick')}
                                />
                              </td>
                            )}
                            <td className="py-1 pr-2 font-medium">{candidate.rank}</td>
                            <td className="py-1 pr-2">{candidate.nickname ?? t('prizeEvents.unknownNickname')}</td>
                            <td className="py-1 pr-2">{`${candidate.points.toLocaleString('ko-KR')}p`}</td>
                            {canManage && (
                              <td className="py-1 pr-2">
                                <input
                                  value={draft?.prize ?? ''}
                                  maxLength={100}
                                  onChange={(change) => updateDraft(event.eventId, candidate.pointAccountId, { prize: change.target.value })}
                                  placeholder={t('prizeEvents.table.prizePlaceholder')}
                                  className={`${INPUT_CLASS} w-36`}
                                />
                              </td>
                            )}
                            {canManage && (
                              <td className="py-1 pr-2">
                                <input
                                  inputMode="numeric"
                                  value={draft?.amount ?? ''}
                                  onChange={(change) => updateDraft(event.eventId, candidate.pointAccountId, { amount: change.target.value })}
                                  placeholder="0"
                                  className={`${INPUT_CLASS} w-24`}
                                />
                              </td>
                            )}
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                )}
                {canManage && (
                  <div className="flex flex-wrap items-center gap-2">
                    <label className="flex items-center gap-1 text-xs text-slate-600 dark:text-slate-300">
                      {t('prizeEvents.paidOn')}
                      <input
                        type="date"
                        value={paidOn[event.eventId] ?? ''}
                        onChange={(change) => setPaidOn((previous) => ({ ...previous, [event.eventId]: change.target.value }))}
                        className={INPUT_CLASS}
                      />
                    </label>
                    <button type="button" disabled={busy || event.candidates.length === 0} onClick={() => void handleConfirm(event)} className={PRIMARY_BUTTON_CLASS}>
                      {t('prizeEvents.confirm')}
                    </button>
                    <button type="button" disabled={busy} onClick={() => void handleCancel(event)} className={SECONDARY_BUTTON_CLASS}>
                      {t('prizeEvents.cancel')}
                    </button>
                  </div>
                )}
              </div>
            )}

            {event.status === 'CONFIRMED' && (
              <ol className="space-y-1 text-sm">
                {event.winners.map((winner) => (
                  <li key={winner.place} className="flex flex-wrap items-baseline gap-2">
                    <span className="font-semibold text-slate-900 dark:text-slate-100">{t('prizeEvents.place', { place: winner.place })}</span>
                    <span className="text-slate-800 dark:text-slate-200">{winner.nickname ?? t('prizeEvents.leftMember')}</span>
                    <span className="text-xs text-slate-500 dark:text-slate-400">{`${winner.points.toLocaleString('ko-KR')}p`}</span>
                    {winner.prize && <span className="text-xs text-slate-700 dark:text-slate-300">{winner.prize}</span>}
                    {winner.amount > 0 && (
                      <span className="text-xs text-slate-500 dark:text-slate-400">
                        {t('prizeEvents.amount', { amount: winner.amount.toLocaleString('ko-KR') })}
                        {winner.ledgerLinked && ` · ${t('prizeEvents.ledgerLinked')}`}
                      </span>
                    )}
                  </li>
                ))}
              </ol>
            )}
          </li>
        ))}
      </ul>
    </section>
  )
}
