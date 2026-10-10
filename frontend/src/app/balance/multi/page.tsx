'use client'

import { useEffect, useMemo, useState } from 'react'
import { useAdminAuth } from '@/lib/admin-auth'
import { apiClient } from '@/lib/api'
import { BalanceSeriesBoard } from '@/components/balance-series-board'
import { TierParticipantBoard } from '@/components/tier-participant-board'
import { Alert, AlertContent, AlertDescription, AlertIcon, AlertTitle } from '@/components/ui/alert'
import {
  buildSeriesLineups,
  canChooseSeriesFormat,
  matchBalanceMetrics,
  offRaceAssignments,
  previewSeriesGames,
} from '@/lib/balance-series'
import { copyTextWithFallback } from '@/lib/clipboard'
import { formatMultiBalanceChatText, formatMultiBalanceMatchChatText } from '@/lib/multi-balance-chat'
import { formatPercent } from '@/lib/percent'
import { t } from '@/lib/i18n'
import { useMmrVisibility } from '@/lib/mmr-visibility'
import { recallPageState, rememberPageState } from '@/lib/page-memory'
import {
  buildMultiBalanceRequestPayload,
  DEFAULT_MULTI_BALANCE_MODE,
  getMultiBalanceModeLabelKey,
  MULTI_BALANCE_MODE_OPTIONS,
} from '@/lib/multi-balance-mode'
import { useParticipantSelection } from '@/lib/use-participant-selection'
import { unassignedNicknames } from '@/lib/unassigned-players'
import type {
  BalancePlayerInput,
  MultiBalanceMatch,
  MultiBalanceMode,
  MultiBalanceResponse,
  TournamentSeriesFormat,
} from '@/types/api'

const TEMP_GROUP_ID = 1
const MODE_OPTIONS: MultiBalanceMode[] = [...MULTI_BALANCE_MODE_OPTIONS]
const MINIMUM_SELECTION_SLOTS = 4
const TEAM_CARD_THEMES = [
  {
    card: 'border-emerald-200 dark:border-emerald-800',
    header: 'border-emerald-200 bg-emerald-50 dark:border-emerald-800 dark:bg-emerald-950/40',
    label: 'text-emerald-700 dark:text-emerald-300',
    metric: 'border-emerald-100 bg-emerald-50/70 dark:border-emerald-900 dark:bg-emerald-950/30',
    player: 'border-emerald-100 bg-emerald-50 text-slate-900 dark:border-emerald-900 dark:bg-emerald-950/40 dark:text-slate-100',
  },
  {
    card: 'border-amber-200 dark:border-amber-800',
    header: 'border-amber-200 bg-amber-50 dark:border-amber-800 dark:bg-amber-950/40',
    label: 'text-amber-700 dark:text-amber-300',
    metric: 'border-amber-100 bg-amber-50/70 dark:border-amber-900 dark:bg-amber-950/30',
    player: 'border-amber-100 bg-amber-50 text-slate-900 dark:border-amber-900 dark:bg-amber-950/40 dark:text-slate-100',
  },
  {
    card: 'border-rose-200 dark:border-rose-800',
    header: 'border-rose-200 bg-rose-50 dark:border-rose-800 dark:bg-rose-950/40',
    label: 'text-rose-700 dark:text-rose-300',
    metric: 'border-rose-100 bg-rose-50/70 dark:border-rose-900 dark:bg-rose-950/30',
    player: 'border-rose-100 bg-rose-50 text-slate-900 dark:border-rose-900 dark:bg-rose-950/40 dark:text-slate-100',
  },
  {
    card: 'border-teal-200 dark:border-teal-800',
    header: 'border-teal-200 bg-teal-50 dark:border-teal-800 dark:bg-teal-950/40',
    label: 'text-teal-700 dark:text-teal-300',
    metric: 'border-teal-100 bg-teal-50/70 dark:border-teal-900 dark:bg-teal-950/30',
    player: 'border-teal-100 bg-teal-50 text-slate-900 dark:border-teal-900 dark:bg-teal-950/40 dark:text-slate-100',
  },
]

type MultiBalanceDisplayTeam = {
  key: string
  teamNumber: number
  players: BalancePlayerInput[]
  totalMmr?: number
}

function getTeamCardTheme(teamNumber: number) {
  return TEAM_CARD_THEMES[(teamNumber - 1) % TEAM_CARD_THEMES.length]
}

function buildPlayerLine(player: BalancePlayerInput, showMmr: boolean): string {
  const assignedRaceText = player.assignedRace
    ? ` · ${player.assignedRace}`
    : ''
  if (!showMmr) {
    return `${player.name}${assignedRaceText}`
  }

  return typeof player.mmr === 'number'
    ? `${player.name}${assignedRaceText} (${player.mmr} MMR)`
    : `${player.name}${assignedRaceText}`
}

function buildDisplayTeams(result: MultiBalanceResponse): MultiBalanceDisplayTeam[] {
  return result.matches.flatMap((match, matchIndex) => [
    {
      key: `multi-team-${match.matchNumber}-home`,
      teamNumber: matchIndex * 2 + 1,
      players: match.homeTeam,
      totalMmr: match.homeMmr,
    },
    {
      key: `multi-team-${match.matchNumber}-away`,
      teamNumber: matchIndex * 2 + 2,
      players: match.awayTeam,
      totalMmr: match.awayMmr,
    },
  ])
}

const MULTI_BALANCE_MEMORY_KEY = 'balance.multi'
const MULTI_BALANCE_SLOTS_MEMORY_KEY = 'balance.multi.slots'

// What the page holds while the member visits another menu (lib/page-memory: in memory only).
type MultiBalancePageMemory = {
  result: MultiBalanceResponse | null
  balanceMode: MultiBalanceMode
  seriesStarted: boolean
  seriesFormats: Record<number, TournamentSeriesFormat>
}

export default function MultiBalancePage() {
  const { canViewMmr } = useAdminAuth()
  const { mmrVisible } = useMmrVisibility()
  const showMmr = canViewMmr && mmrVisible
  // Coming back from another menu, the page picks up where it was left.
  const [recalled] = useState<MultiBalancePageMemory | null>(() =>
    recallPageState<MultiBalancePageMemory>(MULTI_BALANCE_MEMORY_KEY),
  )
  const [submitting, setSubmitting] = useState<boolean>(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [result, setResult] = useState<MultiBalanceResponse | null>(recalled?.result ?? null)
  const [balanceMode, setBalanceMode] = useState<MultiBalanceMode>(recalled?.balanceMode ?? DEFAULT_MULTI_BALANCE_MODE)
  const [startingSeries, setStartingSeries] = useState<boolean>(false)
  const [seriesStarted, setSeriesStarted] = useState<boolean>(recalled?.seriesStarted ?? false)
  const [seriesError, setSeriesError] = useState<string | null>(null)
  const [seriesRefreshSignal, setSeriesRefreshSignal] = useState<number>(0)
  // The format picked per match number; a match left out plays its plan.
  const [seriesFormats, setSeriesFormats] = useState<Record<number, TournamentSeriesFormat>>(
    () => recalled?.seriesFormats ?? {},
  )

  useEffect(() => {
    rememberPageState<MultiBalancePageMemory>(MULTI_BALANCE_MEMORY_KEY, {
      result,
      balanceMode,
      seriesStarted,
      seriesFormats,
    })
  }, [result, balanceMode, seriesStarted, seriesFormats])
  // What was copied last: the whole result, or one match by its number.
  const [copied, setCopied] = useState<'all' | number | null>(null)
  const [copyFailed, setCopyFailed] = useState<boolean>(false)
  const clearResult = () => {
    setSubmitError(null)
    setResult(null)
    setCopied(null)
    setCopyFailed(false)
    setSeriesStarted(false)
    setSeriesError(null)
    setSeriesFormats({})
  }
  const {
    players,
    playersLoading,
    playersError,
    participantSlots,
    participantInputRefs,
    selectedIds,
    resetSelection,
    handleSlotInputChange,
    handleSlotAutocomplete,
  } = useParticipantSelection({
    groupId: TEMP_GROUP_ID,
    showMmr,
    minimumSlots: MINIMUM_SELECTION_SLOTS,
    onSelectionChange: clearResult,
    memoryKey: MULTI_BALANCE_SLOTS_MEMORY_KEY,
  })

  const selectedPlayers = useMemo(
    () => players.filter((player) => selectedIds.includes(player.id)),
    [players, selectedIds],
  )
  const selectedTotalMmr = selectedPlayers.reduce(
    (sum, player) => sum + (typeof player.currentMmr === 'number' ? player.currentMmr : 0),
    0
  )
  const canSubmit = selectedIds.length >= 4 && !submitting && !playersLoading

  const validationMessage =
    selectedIds.length === 0
      ? t('multiBalance.validation.selectPlayers')
      : selectedIds.length < 4
        ? t('multiBalance.validation.minimumFour')
        : null
  const displayTeams = useMemo(() => (result ? buildDisplayTeams(result) : []), [result])

  const handleResetSelection = () => {
    resetSelection()
    setBalanceMode(DEFAULT_MULTI_BALANCE_MODE)
    clearResult()
  }

  const handleGenerateMultiBalance = async () => {
    clearResult()

    if (selectedIds.length < 4) {
      setSubmitError(t('multiBalance.validation.minimumFour'))
      return
    }

    setSubmitting(true)
    try {
      const response = await apiClient.balanceMatchMulti(
        // Each match plays a series whose games set the races, so no single composition is asked for.
        buildMultiBalanceRequestPayload(TEMP_GROUP_ID, selectedIds, balanceMode, null)
      )
      setResult(response)
    } catch (error) {
      if (error instanceof Error && error.message.trim().length > 0) {
        setSubmitError(`${t('multiBalance.generateError')} (${error.message})`)
      } else {
        setSubmitError(t('multiBalance.generateError'))
      }
    } finally {
      setSubmitting(false)
    }
  }

  useEffect(() => {
    if (copied === null) {
      return
    }
    const timeoutId = window.setTimeout(() => setCopied(null), 2000)
    return () => window.clearTimeout(timeoutId)
  }, [copied])

  const handleCopy = async (target: 'all' | number, text: string) => {
    try {
      await copyTextWithFallback(text)
      setCopied(target)
      setCopyFailed(false)
    } catch {
      setCopied(null)
      setCopyFailed(true)
    }
  }

  const seriesLineups = result ? buildSeriesLineups(result.matches, seriesFormats) : null
  // Every game of a series needs its result, which a player whose tier is still to be set holds up.
  const unassignedInSeries = useMemo(
    () =>
      result
        ? unassignedNicknames(
            result.matches.flatMap((match) => [...match.homeTeam, ...match.awayTeam]).map((player) => player.playerId),
            players
          )
        : [],
    [players, result]
  )
  const canStartSeries =
    result !== null &&
    result.matches.every((match) => match.seriesPlan) &&
    unassignedInSeries.length === 0 &&
    !startingSeries &&
    !seriesStarted

  const handleStartSeries = async () => {
    if (!result) {
      return
    }
    if (!seriesLineups) {
      setSeriesError(t('balanceSeries.plan.missingPlayers'))
      return
    }
    if (!window.confirm(t('balanceSeries.plan.startConfirm', { count: seriesLineups.length }))) {
      return
    }
    setStartingSeries(true)
    setSeriesError(null)
    try {
      await apiClient.startBalanceSeries(TEMP_GROUP_ID, seriesLineups)
      setSeriesStarted(true)
      setSeriesRefreshSignal((previous) => previous + 1)
    } catch (error) {
      setSeriesError(
        error instanceof Error && error.message.trim().length > 0
          ? `${t('balanceSeries.plan.startError')} (${error.message})`
          : t('balanceSeries.plan.startError'),
      )
    } finally {
      setStartingSeries(false)
    }
  }

  const renderSeriesPlan = (match: MultiBalanceMatch, matchIndex: number) => {
    const plan = match.seriesPlan
    const format = seriesFormats[match.matchNumber] ?? plan?.format ?? 'BEST_OF_THREE'
    const homeTeamNumber = matchIndex * 2 + 1
    const metrics = matchBalanceMetrics(match)
    return (
      <article
        key={`series-plan-${match.matchNumber}`}
        className="space-y-2 rounded-lg border border-indigo-200 bg-white p-4 shadow-sm dark:border-indigo-800 dark:bg-slate-900"
      >
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
            {t('balanceSeries.plan.matchTitle', {
              number: match.matchNumber,
              home: homeTeamNumber,
              away: homeTeamNumber + 1,
            })}
          </h4>
          <div className="flex flex-wrap items-center gap-2">
            {plan && (
              <span className="text-xs font-medium text-indigo-700 dark:text-indigo-300">
                {t(`balanceSeries.formats.${format}`)}
              </span>
            )}
            {/* One line for this match's own lobby chat. */}
            <button
              type="button"
              onClick={() => void handleCopy(match.matchNumber, formatMultiBalanceMatchChatText(match, matchIndex))}
              aria-label={t('multiBalance.copy.matchAriaLabel', { number: match.matchNumber })}
              className="rounded-md border border-slate-300 px-2 py-1 text-[11px] font-medium text-slate-700 hover:bg-slate-50 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
            >
              {copied === match.matchNumber ? t('balance.copy.copiedButton') : t('balance.copy.button')}
            </button>
          </div>
        </div>
        {/* The balance page's metrics; the MMR ones only for those who may see MMR, as the server sends them. */}
        <div className="space-y-1">
          <p className="text-[11px] font-semibold text-slate-500 dark:text-slate-400">{t('balanceSeries.plan.metricsTitle')}</p>
          <div className={`grid gap-2 ${showMmr ? 'sm:grid-cols-3' : 'sm:grid-cols-1'}`}>
            {showMmr && (
              <div className="rounded-md bg-slate-50 px-2.5 py-1.5 text-xs text-slate-700 dark:bg-slate-800 dark:text-slate-200">
                {t('balanceSeries.plan.mmrDiff')}: <span className="font-semibold">{metrics.mmrDiff ?? '-'}</span>
              </div>
            )}
            <div className="rounded-md bg-slate-50 px-2.5 py-1.5 text-xs text-slate-700 dark:bg-slate-800 dark:text-slate-200">
              {t('balanceSeries.plan.expectedWinRate', { team: homeTeamNumber })}:{' '}
              <span className="font-semibold">
                {metrics.expectedHomeWinRate === null ? '-' : formatPercent(metrics.expectedHomeWinRate)}
              </span>
            </div>
            {showMmr && (
              <div className="rounded-md bg-slate-50 px-2.5 py-1.5 text-xs text-slate-700 dark:bg-slate-800 dark:text-slate-200">
                {t('balanceSeries.plan.averageTeamMmr')}: <span className="font-semibold">{metrics.averageTeamMmr ?? '-'}</span>
              </div>
            )}
          </div>
        </div>
        {plan && canChooseSeriesFormat(match) && (
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-[11px] text-slate-500 dark:text-slate-400">{t('balanceSeries.plan.formatChoice')}</span>
            {(['MIXED_THREE', 'BEST_OF_THREE'] as const).map((option) => (
              <button
                key={option}
                type="button"
                disabled={seriesStarted}
                onClick={() => setSeriesFormats((previous) => ({ ...previous, [match.matchNumber]: option }))}
                className={`rounded-md border px-2 py-1 text-[11px] font-medium disabled:cursor-not-allowed disabled:opacity-60 ${
                  format === option
                    ? 'border-indigo-500 bg-indigo-50 text-indigo-900 dark:border-indigo-400 dark:bg-indigo-950/40 dark:text-indigo-200'
                    : 'border-slate-200 text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800'
                }`}
              >
                {t(`balanceSeries.formats.${option}`)}
              </button>
            ))}
          </div>
        )}
        {plan ? (
          <ol className="space-y-1 text-xs text-slate-700 dark:text-slate-200">
            {previewSeriesGames(match, format).map((game) => {
              const offRaces = offRaceAssignments(game, match.homeTeam, match.awayTeam)
              return (
                <li key={`series-plan-${match.matchNumber}-${game.gameNumber}`} className="flex flex-wrap gap-x-2">
                  <span className="font-semibold">
                    {t('teamTournament.games.label', { number: game.gameNumber })} {game.raceComposition}
                  </span>
                  <span className="text-slate-500 dark:text-slate-400">
                    {offRaces.length === 0
                      ? t('balanceSeries.plan.protossOnly')
                      : offRaces
                          .map((assignment) =>
                            t('balanceSeries.plan.offRace', {
                              race: assignment.race,
                              players: assignment.players.join(', '),
                            }),
                          )
                          .join(' · ')}
                  </span>
                </li>
              )
            })}
          </ol>
        ) : (
          <p className="text-xs text-amber-700 dark:text-amber-300">{t('balanceSeries.plan.unavailable')}</p>
        )}
      </article>
    )
  }

  return (
    <section className="space-y-6">
      <header className="space-y-1 rounded-xl border border-slate-200 bg-white px-5 py-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
        <h2 className="text-2xl font-semibold tracking-tight">{t('multiBalance.title')}</h2>
        <p className="text-sm text-slate-600 dark:text-slate-300">{t('multiBalance.description')}</p>
        <p className="text-xs text-slate-500 dark:text-slate-400">{t('multiBalance.helper.defaultPriority')}</p>
        <p className="text-xs text-slate-500 dark:text-slate-400">{t('multiBalance.helper.addTwoVsTwo')}</p>
        <p className="text-xs text-slate-500 dark:text-slate-400">{t('multiBalance.helper.waiting')}</p>
        <p className="text-xs text-slate-500 dark:text-slate-400">{t('multiBalance.helper.series')}</p>
      </header>

      {playersError && (
        <Alert variant="destructive" appearance="light">
          <AlertIcon icon="destructive">!</AlertIcon>
          <AlertContent>
            <AlertTitle>{t('common.errorPrefix')}</AlertTitle>
            <AlertDescription>{playersError}</AlertDescription>
          </AlertContent>
        </Alert>
      )}

      <div className="grid gap-4 xl:grid-cols-3">
        <div className="xl:col-span-2">
          <TierParticipantBoard
            title={t('multiBalance.selection.title')}
            helper={t('multiBalance.selection.helper')}
            players={players}
            slots={participantSlots}
            showMmr={showMmr}
            loading={playersLoading}
            selectedCountLabel={t('multiBalance.summary.selectedCount', { count: selectedIds.length })}
            emptyMessage={t('multiBalance.selection.empty')}
            duplicateMessage={t('balance.validation.duplicate')}
            resetLabel={t('multiBalance.selection.reset')}
            inputRefs={participantInputRefs}
            onReset={handleResetSelection}
            onSlotInputChange={handleSlotInputChange}
            onSlotAutocomplete={handleSlotAutocomplete}
          />
        </div>

        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
          <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('multiBalance.summary.title')}</h3>
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
            {t('multiBalance.summary.selectedCount', { count: selectedIds.length })}
          </p>

          {showMmr && (
            <div className="mt-3 rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700 dark:bg-slate-800 dark:text-slate-200">
              {t('multiBalance.summary.totalMmr')}:{' '}
              <span className="font-semibold">{selectedTotalMmr}</span>
            </div>
          )}

          <div className="mt-4 space-y-2">
            <p className="text-xs font-semibold text-slate-700 dark:text-slate-300">{t('multiBalance.mode.title')}</p>
            <div className="grid gap-2">
              {MODE_OPTIONS.map((mode) => {
                const selected = balanceMode === mode
                return (
                  <button
                    key={mode}
                    type="button"
                    onClick={() => setBalanceMode(mode)}
                    className={`rounded-lg border px-3 py-2 text-left transition-colors ${
                      selected
                      ? 'border-indigo-500 bg-indigo-50 text-indigo-900 dark:border-indigo-400 dark:bg-indigo-950/40 dark:text-indigo-200'
                      : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-300 dark:hover:bg-slate-800'
                    }`}
                  >
                    <p className="text-sm font-semibold">{t(getMultiBalanceModeLabelKey(mode))}</p>
                    <p className="mt-0.5 text-xs">{t(`multiBalance.mode.options.${mode}.helper`)}</p>
                  </button>
                )
              })}
            </div>
          </div>

          {validationMessage && (
            <p className="mt-3 text-xs text-amber-700 dark:text-amber-300">{validationMessage}</p>
          )}
          {submitError && (
            <Alert variant="destructive" appearance="light" size="sm" className="mt-2">
              <AlertIcon icon="destructive">!</AlertIcon>
              <AlertContent>
                <AlertDescription>{submitError}</AlertDescription>
              </AlertContent>
            </Alert>
          )}

          <button
            type="button"
            onClick={handleGenerateMultiBalance}
            disabled={!canSubmit}
            className="mt-4 w-full rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white dark:disabled:bg-slate-700 dark:disabled:text-slate-400"
          >
            {submitting ? t('multiBalance.summary.submitting') : t('multiBalance.summary.submit')}
          </button>
        </article>
      </div>

      {result && (
        <section className="space-y-4">
          <div className="flex flex-wrap items-center justify-end gap-2">
            {copyFailed && <p className="text-xs text-rose-700 dark:text-rose-300">{t('balance.copy.failed')}</p>}
            <button
              type="button"
              onClick={() => void handleCopy('all', formatMultiBalanceChatText(result))}
              aria-label={t('multiBalance.copy.allAriaLabel')}
              className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white"
            >
              {copied === 'all' ? t('balance.copy.copiedButton') : t('multiBalance.copy.allButton')}
            </button>
          </div>
          <header className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-900">
            <div className="border-b border-slate-200 px-4 py-3 dark:border-slate-700">
              <p className="text-xs font-semibold text-emerald-700 dark:text-emerald-300">{t('multiBalance.result.title')}</p>
              <h3 className="mt-1 text-lg font-semibold text-slate-950 dark:text-slate-100">
                {t('multiBalance.result.summary', {
                  totalPlayers: result.totalPlayers,
                  teamCount: displayTeams.length,
                })}
              </h3>
              <p className="mt-2 text-xs text-slate-600 dark:text-slate-300">
                {t('multiBalance.result.selectedMode')}:{' '}
                <span className="font-semibold text-emerald-700 dark:text-emerald-300">
                  {t(getMultiBalanceModeLabelKey(result.balanceMode))}
                </span>
              </p>
            </div>
            <div className="grid divide-y divide-slate-200 bg-slate-50 dark:divide-slate-700 dark:bg-slate-800 sm:grid-cols-3 sm:divide-x sm:divide-y-0">
              <div className="px-4 py-3 text-xs text-slate-600 dark:text-slate-300">
                <p>{t('multiBalance.result.teamCount')}</p>
                <p className="mt-1 text-lg font-semibold text-slate-950 dark:text-slate-100">{displayTeams.length}</p>
              </div>
              <div className="px-4 py-3 text-xs text-slate-600 dark:text-slate-300">
                <p>{t('multiBalance.result.assignedPlayers')}</p>
                <p className="mt-1 text-lg font-semibold text-slate-950 dark:text-slate-100">{result.assignedPlayers}</p>
              </div>
              <div className="px-4 py-3 text-xs text-slate-600 dark:text-slate-300">
                <p>{t('multiBalance.result.waitingPlayers')}</p>
                <p className="mt-1 text-lg font-semibold text-slate-950 dark:text-slate-100">{result.waitingPlayers.length}</p>
              </div>
            </div>
          </header>

          {result.waitingPlayers.length > 0 && (
            <article className="rounded-lg border border-amber-200 bg-white p-4 shadow-sm dark:border-amber-800 dark:bg-slate-900">
              <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('multiBalance.result.waitingTitle')}</h4>
              <ul className="mt-3 flex flex-wrap gap-2">
                {result.waitingPlayers.map((player) => (
                  <li
                    key={`waiting-${player.id}`}
                    className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-1.5 text-xs font-medium text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200"
                  >
                    {player.nickname}
                  </li>
                ))}
              </ul>
            </article>
          )}

          <div className="grid gap-4 md:grid-cols-2">
            {displayTeams.map((team) => {
              const theme = getTeamCardTheme(team.teamNumber)

              return (
                <article
                  key={team.key}
                className={`overflow-hidden rounded-lg border bg-white shadow-sm dark:bg-slate-900 ${theme.card}`}
                >
                  <header className={`border-b px-4 py-3 ${theme.header}`}>
                    <div>
                      <p className={`text-xs font-semibold ${theme.label}`}>
                        {t('multiBalance.result.teamLabel', { number: team.teamNumber })}
                      </p>
                    <h4 className="mt-1 text-base font-semibold text-slate-950 dark:text-slate-100">
                        {t('multiBalance.result.teamMemberCount', { count: team.players.length })}
                      </h4>
                    </div>
                  </header>

                  {showMmr && (
                  <div className={`border-b px-4 py-2 text-xs text-slate-700 dark:text-slate-300 ${theme.metric}`}>
                      {t('multiBalance.result.teamTotalMmr')}:{' '}
                      <span className="font-semibold text-slate-950 dark:text-slate-100">
                        {typeof team.totalMmr === 'number' ? team.totalMmr : '-'}
                      </span>
                    </div>
                  )}

                  <ul className="space-y-2 p-4">
                    {team.players.map((player) => (
                      <li
                        key={`${team.key}-${player.name}`}
                        className={`rounded-md border px-3 py-2 text-sm font-medium ${theme.player}`}
                      >
                        {buildPlayerLine(player, showMmr)}
                      </li>
                    ))}
                  </ul>
                </article>
              )
            })}
          </div>

          <section className="space-y-3">
            <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('balanceSeries.plan.title')}</h4>
            <div className="grid gap-3 md:grid-cols-2">{result.matches.map(renderSeriesPlan)}</div>
            <div className="flex flex-wrap items-center gap-3">
              <button
                type="button"
                onClick={() => void handleStartSeries()}
                disabled={!canStartSeries}
                className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-indigo-500 disabled:cursor-not-allowed disabled:bg-slate-300 dark:disabled:bg-slate-700 dark:disabled:text-slate-400"
              >
                {startingSeries ? t('balanceSeries.plan.starting') : t('balanceSeries.plan.startButton')}
              </button>
              {seriesError && <p className="text-xs text-rose-600 dark:text-rose-300">{seriesError}</p>}
            </div>
            {unassignedInSeries.length > 0 && (
              <p className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200">
                {t('balanceSeries.plan.unassignedBlocked', { players: unassignedInSeries.join(', ') })}
              </p>
            )}
          </section>
        </section>
      )}

      <BalanceSeriesBoard groupId={TEMP_GROUP_ID} refreshSignal={seriesRefreshSignal} />
    </section>
  )
}
