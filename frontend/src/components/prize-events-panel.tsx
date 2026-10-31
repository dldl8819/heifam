'use client'

import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react'
import { apiClient } from '@/lib/api'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { t } from '@/lib/i18n'
import {
  buildPrizeWinners,
  initialPrizeDrafts,
  parseManualEntries,
  prizeCandidateKey,
  prizeEventMetric,
  type PrizeDraft,
} from '@/lib/prize-events'
import type { PlayerRosterItem, PrizeEvent, PrizeEventMetric } from '@/types/api'

type PrizeEventsPanelProps = {
  groupId: number
  // Admins start an event, as they post a notice.
  canCreate: boolean
  // Super admins pick the winners and confirm or cancel: confirming writes the donation ledger.
  canManage: boolean
}

const CARD_CLASS = 'rounded-xl border border-slate-200 bg-white p-4 shadow-xs dark:border-slate-700 dark:bg-slate-900'
const INPUT_CLASS = 'rounded-md border border-slate-300 px-2 py-1 text-sm dark:border-slate-600 dark:bg-slate-900'
const PRIMARY_BUTTON_CLASS =
  'rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-medium text-white hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white'
const SECONDARY_BUTTON_CLASS =
  'rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800'

function describeError(error: unknown, fallback: string): string {
  return error instanceof Error && error.message.trim().length > 0 ? `${fallback} (${error.message})` : fallback
}

// Points earned show as "12p", matches played as "12경기", an entered number with its unit ("427 채팅").
function formatScore(event: PrizeEvent, value: number): string {
  const metric = prizeEventMetric(event)
  const shown = value.toLocaleString('ko-KR')
  if (metric === 'MANUAL') {
    return t('prizeEvents.manualScore', { value: shown, unit: event.unitLabel ?? '' })
  }
  return t(metric === 'GAMES' ? 'prizeEvents.games' : 'prizeEvents.points', { value: shown })
}

function metricLabel(event: PrizeEvent): string {
  const metric = prizeEventMetric(event)
  return metric === 'MANUAL'
    ? t('prizeEvents.metricManual', { unit: event.unitLabel ?? '' })
    : t(`prizeEvents.metric.${metric}`)
}

function todayInKorea(): string {
  return new Date(Date.now() + 9 * 60 * 60 * 1000).toISOString().slice(0, 10)
}

export function PrizeEventsPanel({ groupId, canCreate, canManage }: PrizeEventsPanelProps) {
  const [events, setEvents] = useState<PrizeEvent[]>([])
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [busy, setBusy] = useState<boolean>(false)
  const [title, setTitle] = useState<string>('')
  const [periodStart, setPeriodStart] = useState<string>('')
  const [periodEnd, setPeriodEnd] = useState<string>('')
  const [winnerCount, setWinnerCount] = useState<string>('3')
  const [metric, setMetric] = useState<PrizeEventMetric>('POINTS')
  const [unitLabel, setUnitLabel] = useState<string>('채팅')
  const [manualText, setManualText] = useState<string>('')
  const [roster, setRoster] = useState<PlayerRosterItem[] | null>(null)
  const [drafts, setDrafts] = useState<Record<number, Record<string, PrizeDraft>>>({})
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

  // The roster names what is pasted for a MANUAL event; read once, when that kind is picked.
  useEffect(() => {
    if (metric !== 'MANUAL' || roster !== null) {
      return
    }
    let cancelled = false
    apiClient
      .getGroupPlayers(groupId)
      .then((players) => {
        if (!cancelled) {
          setRoster(players)
        }
      })
      .catch(() => {
        if (!cancelled) {
          setError(t('prizeEvents.form.rosterError'))
        }
      })
    return () => {
      cancelled = true
    }
  }, [groupId, metric, roster])

  const manual = useMemo(() => parseManualEntries(manualText, roster ?? []), [manualText, roster])
  const manualProblems = [
    manual.unknown.length > 0 ? t('prizeEvents.form.manualUnknown', { names: manual.unknown.join(', ') }) : null,
    manual.repeated.length > 0 ? t('prizeEvents.form.manualRepeated', { names: manual.repeated.join(', ') }) : null,
    manual.unreadable.length > 0 ? t('prizeEvents.form.manualUnreadable', { lines: manual.unreadable.join(' / ') }) : null,
  ].filter((problem): problem is string => problem !== null)

  const handleCreate = async (formEvent: FormEvent<HTMLFormElement>) => {
    formEvent.preventDefault()
    if (metric === 'MANUAL' && (manual.entries.length === 0 || manualProblems.length > 0)) {
      setError(manualProblems[0] ?? t('prizeEvents.form.manualEmpty'))
      return
    }
    setBusy(true)
    setMessage(null)
    try {
      await apiClient.createPrizeEvent(groupId, {
        title: title.trim(),
        periodStart,
        periodEnd,
        winnerCount: Number(winnerCount),
        metric,
        ...(metric === 'MANUAL' ? { unitLabel: unitLabel.trim(), entries: manual.entries } : {}),
      })
      setTitle('')
      setManualText('')
      await load()
    } catch (createError) {
      setError(describeError(createError, t('prizeEvents.saveError')))
    } finally {
      setBusy(false)
    }
  }

  const updateDraft = (eventId: number, candidateKey: string, change: Partial<PrizeDraft>) => {
    setDrafts((previous) => ({
      ...previous,
      [eventId]: {
        ...previous[eventId],
        [candidateKey]: { ...(previous[eventId]?.[candidateKey] ?? { selected: false, prize: '', amount: '' }), ...change },
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

      {error && <p role="alert" className="text-xs text-rose-600 dark:text-rose-300">{error}</p>}
      {message && <p className="text-xs text-emerald-700 dark:text-emerald-300">{message}</p>}

      {canCreate && (
        <form onSubmit={handleCreate} className="grid gap-2 rounded-lg bg-slate-50 p-3 dark:bg-slate-800/60 sm:grid-cols-[2fr_1fr_1fr_auto_auto_auto] sm:items-end">
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
            <span>{t('prizeEvents.form.metric')}</span>
            <select value={metric} onChange={(event) => setMetric(event.target.value as PrizeEventMetric)} className={`${INPUT_CLASS} w-full`}>
              <option value="POINTS">{t('prizeEvents.form.metricPoints')}</option>
              <option value="GAMES">{t('prizeEvents.form.metricGames')}</option>
              <option value="MANUAL">{t('prizeEvents.form.metricManual')}</option>
            </select>
          </label>
          <label className="space-y-1 text-xs text-slate-600 dark:text-slate-300">
            <span>{t('prizeEvents.form.winners')}</span>
            <input type="number" min={1} max={20} value={winnerCount} onChange={(event) => setWinnerCount(event.target.value)} required className={`${INPUT_CLASS} w-20`} />
          </label>
          <button type="submit" disabled={busy} className={PRIMARY_BUTTON_CLASS}>
            {t('prizeEvents.form.create')}
          </button>
          {metric === 'MANUAL' && (
            <div className="space-y-2 sm:col-span-full">
              <label className="block space-y-1 text-xs text-slate-600 dark:text-slate-300">
                <span>{t('prizeEvents.form.unit')}</span>
                <input value={unitLabel} onChange={(event) => setUnitLabel(event.target.value)} maxLength={20} required className={`${INPUT_CLASS} w-32`} />
              </label>
              <label className="block space-y-1 text-xs text-slate-600 dark:text-slate-300">
                <span>{t('prizeEvents.form.manualEntries')}</span>
                <textarea
                  value={manualText}
                  onChange={(event) => setManualText(event.target.value)}
                  rows={6}
                  placeholder={t('prizeEvents.form.manualPlaceholder')}
                  className={`${INPUT_CLASS} w-full font-mono`}
                />
              </label>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                {roster === null ? t('common.loading') : t('prizeEvents.form.manualCount', { count: manual.entries.length })}
              </p>
              {manualProblems.map((problem) => (
                <p key={problem} className="text-xs text-rose-600 dark:text-rose-300">{problem}</p>
              ))}
            </div>
          )}
        </form>
      )}

      {loading && <LoadingIndicator label={t('common.loading')} />}
      {!loading && events.length === 0 && <p className="text-sm text-slate-500 dark:text-slate-400">{t('prizeEvents.empty')}</p>}

      <ul className="space-y-3">
        {events.map((event) => {
          const eventMetric = prizeEventMetric(event)
          return (
          <li key={event.eventId} className="space-y-2 rounded-lg border border-slate-200 p-3 dark:border-slate-700">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div>
                <p className="text-sm font-semibold text-slate-900 dark:text-slate-100">{event.title}</p>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  {t('prizeEvents.period', {
                    start: event.periodStart,
                    end: event.periodEnd,
                    count: event.winnerCount,
                    metric: metricLabel(event),
                  })}
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
                        <th className="py-1 pr-2">
                          {eventMetric === 'MANUAL'
                            ? event.unitLabel
                            : t(eventMetric === 'GAMES' ? 'prizeEvents.table.games' : 'prizeEvents.table.points')}
                        </th>
                        {canManage && <th className="py-1 pr-2">{t('prizeEvents.table.prize')}</th>}
                        {canManage && <th className="py-1 pr-2">{t('prizeEvents.table.amount')}</th>}
                      </tr>
                    </thead>
                    <tbody>
                      {event.candidates.map((candidate) => {
                        const candidateKey = prizeCandidateKey(candidate)
                        const draft = drafts[event.eventId]?.[candidateKey]
                        return (
                          <tr key={candidateKey} className="border-t border-slate-100 dark:border-slate-800">
                            {canManage && (
                              <td className="py-1 pr-2">
                                <input
                                  type="checkbox"
                                  checked={draft?.selected ?? false}
                                  onChange={(change) => updateDraft(event.eventId, candidateKey, { selected: change.target.checked })}
                                  aria-label={t('prizeEvents.table.pick')}
                                />
                              </td>
                            )}
                            <td className="py-1 pr-2 font-medium">{candidate.rank}</td>
                            <td className="py-1 pr-2">{candidate.nickname ?? t('prizeEvents.unknownNickname')}</td>
                            <td className="py-1 pr-2">{formatScore(event, candidate.points)}</td>
                            {canManage && (
                              <td className="py-1 pr-2">
                                <input
                                  value={draft?.prize ?? ''}
                                  aria-label={t('a11y.fields.ofRow', { row: candidate.nickname ?? t('prizeEvents.unknownNickname'), field: t('prizeEvents.table.prize') })}
                                  maxLength={100}
                                  onChange={(change) => updateDraft(event.eventId, candidateKey, { prize: change.target.value })}
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
                                  aria-label={t('a11y.fields.ofRow', { row: candidate.nickname ?? t('prizeEvents.unknownNickname'), field: t('prizeEvents.table.amount') })}
                                  onChange={(change) => updateDraft(event.eventId, candidateKey, { amount: change.target.value })}
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
                    <span className="text-xs text-slate-500 dark:text-slate-400">{formatScore(event, winner.points)}</span>
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
          )
        })}
      </ul>
    </section>
  )
}
