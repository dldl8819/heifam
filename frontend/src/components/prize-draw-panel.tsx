'use client'

import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react'
import { apiClient, isApiNotFoundError } from '@/lib/api'
import { PinballStage } from '@/components/pinball-stage'
import { winnersOf, type DrawMode } from '@/lib/pinball/engine'
import {
  PRIZE_DRAW_MAX_ENTRANTS,
  PRIZE_DRAW_MAX_WINNERS,
  PRIZE_DRAW_PRIZE_MAX_LENGTH,
  PRIZE_DRAW_TITLE_MAX_LENGTH,
  buildEntrants,
  draftEntrantIds,
  validatePrizeDraw,
  type DrawEntrant,
} from '@/lib/prize-draw'
import { t } from '@/lib/i18n'
import type { PlayerRosterItem, PrizeDrawList } from '@/types/api'

type PrizeDrawPanelProps = {
  groupId: number
  // A draw was saved: the records as they now are.
  onSaved: (list: PrizeDrawList) => void
}

type Step = 'compose' | 'stage'

const fieldClass = 'w-full rounded-md border border-slate-300 px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-900'
const primaryButtonClass =
  'rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-medium text-white hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white'
const plainButtonClass =
  'rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800'
const errorClass =
  'rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700 dark:border-rose-800 dark:bg-rose-950/40 dark:text-rose-300'

/**
 * Where an admin sets a prize draw up and runs it: who takes part (ticked on the roster, or typed
 * in), how many win and what, and whether the first or the last balls win. The draw runs on this
 * screen only; when it is over the admin saves its outcome to the records, or runs it again.
 */
export function PrizeDrawPanel({ groupId, onSaved }: PrizeDrawPanelProps) {
  const [step, setStep] = useState<Step>('compose')
  const [players, setPlayers] = useState<PlayerRosterItem[]>([])
  const [rosterError, setRosterError] = useState<boolean>(false)

  const [title, setTitle] = useState<string>('')
  const [winnerCount, setWinnerCount] = useState<number>(1)
  const [mode, setMode] = useState<DrawMode>('FIRST')
  const [prizes, setPrizes] = useState<string[]>([''])
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [search, setSearch] = useState<string>('')
  const [manual, setManual] = useState<string>('')
  const [formError, setFormError] = useState<string | null>(null)
  const [draftLoading, setDraftLoading] = useState<boolean>(false)
  const [draftNote, setDraftNote] = useState<{ ok: boolean; text: string } | null>(null)

  // What the stage runs on: fixed when the stage is opened, so typing elsewhere cannot restart a draw.
  const [stageEntrants, setStageEntrants] = useState<DrawEntrant[]>([])
  const [run, setRun] = useState<number>(0)
  const [finishedOrder, setFinishedOrder] = useState<number[] | null>(null)
  const [saving, setSaving] = useState<boolean>(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [savedMessage, setSavedMessage] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    apiClient
      .getGroupPlayers(groupId)
      .then((roster) => {
        if (!cancelled) {
          // Whoever is on the roster now, by name: members who left are not drawn for.
          setPlayers(
            roster
              .filter((player) => player.active !== false && !player.identityHidden)
              .sort((left, right) => left.nickname.localeCompare(right.nickname, 'ko'))
          )
        }
      })
      .catch(() => {
        if (!cancelled) {
          setRosterError(true)
        }
      })
    return () => {
      cancelled = true
    }
  }, [groupId])

  const entrants = useMemo(() => buildEntrants(players, selected, manual), [manual, players, selected])
  const shownPlayers = useMemo(() => {
    const word = search.trim().toLowerCase()
    return word.length === 0 ? players : players.filter((player) => player.nickname.toLowerCase().includes(word))
  }, [players, search])

  const changeWinnerCount = (next: number) => {
    const count = Math.max(1, Math.min(PRIZE_DRAW_MAX_WINNERS, Number.isFinite(next) ? Math.floor(next) : 1))
    setWinnerCount(count)
    setPrizes((previous) => Array.from({ length: count }, (_, index) => previous[index] ?? ''))
  }

  const toggle = (playerId: number) =>
    setSelected((previous) => {
      const next = new Set(previous)
      if (next.has(playerId)) {
        next.delete(playerId)
      } else {
        next.add(playerId)
      }
      return next
    })

  // Prizes are drawn mostly among the players of the latest regular draft (정기 감전): tick them all.
  const loadDraftPlayers = async () => {
    setDraftLoading(true)
    setDraftNote(null)
    try {
      const draft = await apiClient.getLatestCaptainDraft(groupId)
      const { ids, missing } = draftEntrantIds(draft.participants, players)
      setSelected(new Set(ids))
      setSearch('')
      setDraftNote({
        ok: true,
        text:
          t('prizeDraw.draftPlayersLoaded', { title: draft.title, count: ids.length }) +
          (missing > 0 ? t('prizeDraw.draftPlayersMissing', { count: missing }) : ''),
      })
    } catch (error) {
      setDraftNote({ ok: false, text: t(isApiNotFoundError(error) ? 'prizeDraw.draftNone' : 'prizeDraw.draftLoadError') })
    } finally {
      setDraftLoading(false)
    }
  }

  const openStage = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const problem = validatePrizeDraw(title, entrants, winnerCount, prizes)
    if (problem) {
      setFormError(t(`prizeDraw.${problem.key}`, { min: problem.min, max: problem.max }))
      return
    }
    setFormError(null)
    setSavedMessage(null)
    setSaveError(null)
    setStageEntrants(entrants)
    setFinishedOrder(null)
    setRun((previous) => previous + 1)
    setStep('stage')
  }

  const handleFinished = useCallback((order: number[]) => setFinishedOrder(order), [])

  const runAgain = () => {
    setFinishedOrder(null)
    setSaveError(null)
    setRun((previous) => previous + 1)
  }

  const winners = finishedOrder ? winnersOf(finishedOrder, winnerCount, mode) : []

  const save = async () => {
    if (!finishedOrder) {
      return
    }
    setSaving(true)
    setSaveError(null)
    try {
      const list = await apiClient.savePrizeDraw(groupId, {
        title: title.trim(),
        mode,
        entrantCount: stageEntrants.length,
        winners: winners.map((ballIndex, index) => ({
          place: index + 1,
          name: stageEntrants[ballIndex].name,
          playerId: stageEntrants[ballIndex].playerId,
          prize: (prizes[index] ?? '').trim() || undefined,
        })),
      })
      onSaved(list)
      setSavedMessage(t('prizeDraw.saved', { title: title.trim() }))
      setStep('compose')
      setFinishedOrder(null)
      // The next draw is another draw: its title and prizes are its own. Who takes part is kept,
      // since a second prize is often drawn among the same people.
      setTitle('')
      setPrizes((previous) => previous.map(() => ''))
    } catch {
      setSaveError(t('prizeDraw.saveError'))
    } finally {
      setSaving(false)
    }
  }

  if (step === 'stage') {
    return (
      <section className="space-y-4 rounded-xl border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-800/60">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="break-all text-base font-semibold text-slate-900 dark:text-slate-100">{title.trim()}</h2>
          <button type="button" onClick={() => setStep('compose')} disabled={saving} className={plainButtonClass}>
            {t('prizeDraw.backToSetup')}
          </button>
        </div>

        <PinballStage key={run} entrants={stageEntrants} winnerCount={winnerCount} mode={mode} onFinished={handleFinished} />

        {finishedOrder && (
          <div className="space-y-3 rounded-xl border border-amber-300 bg-amber-50 p-4 dark:border-amber-700 dark:bg-amber-950/30">
            <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('prizeDraw.resultTitle')}</h3>
            <ol className="space-y-1">
              {winners.map((ballIndex, index) => (
                <li key={ballIndex} className="flex flex-wrap items-baseline gap-x-2 text-sm">
                  <span className="w-10 shrink-0 text-xs font-semibold text-amber-700 dark:text-amber-300">
                    {t('prizeDraw.place', { place: index + 1 })}
                  </span>
                  <span className="break-all font-semibold text-slate-900 dark:text-slate-100">{stageEntrants[ballIndex]?.name}</span>
                  {(prizes[index] ?? '').trim() && (
                    <span className="break-all text-xs text-slate-600 dark:text-slate-300">{(prizes[index] ?? '').trim()}</span>
                  )}
                </li>
              ))}
            </ol>
            {saveError && <p className={errorClass}>{saveError}</p>}
            <div className="flex flex-wrap gap-2">
              <button type="button" onClick={() => void save()} disabled={saving} className={primaryButtonClass}>
                {saving ? t('prizeDraw.saving') : t('prizeDraw.save')}
              </button>
              <button type="button" onClick={runAgain} disabled={saving} className={plainButtonClass}>
                {t('prizeDraw.runAgain')}
              </button>
            </div>
            <p className="text-xs text-slate-500 dark:text-slate-400">{t('prizeDraw.saveHint')}</p>
          </div>
        )}
      </section>
    )
  }

  return (
    <form
      onSubmit={openStage}
      className="space-y-4 rounded-xl border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-800/60"
    >
      <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('prizeDraw.setupTitle')}</h2>
      {savedMessage && (
        <p className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300">
          {savedMessage}
        </p>
      )}
      {formError && <p className={errorClass}>{formError}</p>}

      <input
        type="text"
        value={title}
        onChange={(event) => setTitle(event.target.value)}
        maxLength={PRIZE_DRAW_TITLE_MAX_LENGTH}
        placeholder={t('prizeDraw.titlePlaceholder')}
        className={fieldClass}
      />

      <div className="flex flex-wrap items-center gap-x-6 gap-y-2 text-xs text-slate-700 dark:text-slate-200">
        <label className="flex items-center gap-2">
          {t('prizeDraw.winnerCountLabel')}
          <input
            type="number"
            min={1}
            max={PRIZE_DRAW_MAX_WINNERS}
            value={winnerCount}
            onChange={(event) => changeWinnerCount(Number(event.target.value))}
            className="w-16 rounded-md border border-slate-300 px-2 py-1 text-sm dark:border-slate-600 dark:bg-slate-900"
          />
        </label>
        <div className="flex items-center gap-3" role="radiogroup" aria-label={t('prizeDraw.modeLabel')}>
          <span>{t('prizeDraw.modeLabel')}</span>
          {(['FIRST', 'LAST'] as const).map((option) => (
            <label key={option} className="flex items-center gap-1.5">
              <input type="radio" name="prize-draw-mode" checked={mode === option} onChange={() => setMode(option)} />
              {t(option === 'FIRST' ? 'prizeDraw.modeFirst' : 'prizeDraw.modeLast')}
            </label>
          ))}
        </div>
      </div>

      <div className="space-y-2">
        <p className="text-xs font-semibold text-slate-900 dark:text-slate-100">{t('prizeDraw.prizesLabel')}</p>
        <ul className="grid gap-2 sm:grid-cols-2">
          {prizes.map((prize, index) => (
            <li key={index} className="flex items-center gap-2">
              <span className="w-10 shrink-0 text-xs font-semibold text-slate-500 dark:text-slate-400">
                {t('prizeDraw.place', { place: index + 1 })}
              </span>
              <input
                type="text"
                value={prize}
                onChange={(event) => setPrizes((previous) => previous.map((value, position) => (position === index ? event.target.value : value)))}
                maxLength={PRIZE_DRAW_PRIZE_MAX_LENGTH}
                placeholder={t('prizeDraw.prizePlaceholder')}
                className="min-w-0 flex-1 rounded-md border border-slate-300 px-3 py-1.5 text-sm dark:border-slate-600 dark:bg-slate-900"
              />
            </li>
          ))}
        </ul>
      </div>

      <div className="space-y-2">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="text-xs font-semibold text-slate-900 dark:text-slate-100">
            {t('prizeDraw.rosterLabel', { selected: players.filter((player) => selected.has(player.id)).length, total: players.length })}
          </p>
          <div className="flex flex-wrap items-center gap-2">
            <input
              type="search"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder={t('prizeDraw.rosterSearch')}
              aria-label={t('prizeDraw.rosterSearch')}
              className="w-36 rounded-md border border-slate-300 px-2 py-1 text-xs dark:border-slate-600 dark:bg-slate-900"
            />
            <button
              type="button"
              onClick={() => setSelected((previous) => new Set([...previous, ...shownPlayers.map((player) => player.id)]))}
              className={plainButtonClass}
            >
              {t('prizeDraw.selectAll')}
            </button>
            <button type="button" onClick={() => setSelected(new Set())} className={plainButtonClass}>
              {t('prizeDraw.selectNone')}
            </button>
            <button
              type="button"
              onClick={() => void loadDraftPlayers()}
              disabled={draftLoading || players.length === 0}
              className={plainButtonClass}
            >
              {draftLoading ? t('prizeDraw.draftLoading') : t('prizeDraw.loadDraftPlayers')}
            </button>
          </div>
        </div>
        {draftNote && (
          <p
            role="status"
            className={
              draftNote.ok
                ? 'rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs text-emerald-800 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-200'
                : errorClass
            }
          >
            {draftNote.text}
          </p>
        )}
        {rosterError && <p className={errorClass}>{t('prizeDraw.rosterError')}</p>}
        <ul className="flex max-h-56 flex-wrap gap-1.5 overflow-y-auto rounded-lg border border-slate-200 bg-white p-2 dark:border-slate-700 dark:bg-slate-900">
          {shownPlayers.length === 0 && (
            <li className="px-2 py-1 text-xs text-slate-500 dark:text-slate-400">{t('prizeDraw.rosterEmpty')}</li>
          )}
          {shownPlayers.map((player) => {
            const checked = selected.has(player.id)
            return (
              <li key={player.id}>
                <label
                  className={`flex cursor-pointer items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs ${
                    checked
                      ? 'border-amber-400 bg-amber-50 text-slate-900 dark:border-amber-500 dark:bg-amber-950/40 dark:text-slate-100'
                      : 'border-slate-200 text-slate-700 dark:border-slate-700 dark:text-slate-200'
                  }`}
                >
                  <input type="checkbox" checked={checked} onChange={() => toggle(player.id)} />
                  {player.nickname}
                </label>
              </li>
            )
          })}
        </ul>
      </div>

      <div className="space-y-1">
        <p className="text-xs font-semibold text-slate-900 dark:text-slate-100">{t('prizeDraw.manualLabel')}</p>
        <textarea
          value={manual}
          onChange={(event) => setManual(event.target.value)}
          rows={3}
          placeholder={t('prizeDraw.manualPlaceholder')}
          className={fieldClass}
        />
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <button type="submit" className={primaryButtonClass}>
          {t('prizeDraw.openStage')}
        </button>
        <p className="text-xs text-slate-600 dark:text-slate-300">
          {t('prizeDraw.entrantCount', { count: entrants.length, max: PRIZE_DRAW_MAX_ENTRANTS })}
        </p>
      </div>
      <p className="text-xs text-slate-500 dark:text-slate-400">{t('prizeDraw.setupHint')}</p>
    </form>
  )
}
