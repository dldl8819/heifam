'use client'

import { useEffect, useMemo, useState } from 'react'
import { useAdminAuth } from '@/lib/admin-auth'
import { apiClient } from '@/lib/api'
import { Alert, AlertContent, AlertDescription, AlertIcon, AlertTitle } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { t } from '@/lib/i18n'
import {
  buildMultiBalanceRequestPayload,
  DEFAULT_MULTI_BALANCE_MODE,
  getMultiBalanceModeLabelKey,
  MULTI_BALANCE_MODE_OPTIONS,
} from '@/lib/multi-balance-mode'
import type {
  BalancePlayerInput,
  BalancePlayerOption,
  MultiBalanceMode,
  MultiBalanceResponse,
} from '@/types/api'

const TEMP_GROUP_ID = 1
const MODE_OPTIONS: MultiBalanceMode[] = [...MULTI_BALANCE_MODE_OPTIONS]

function formatPercent(value: number): string {
  const percent = value <= 1 ? value * 100 : value
  return `${percent.toFixed(2)}%`
}

function buildPlayerLine(player: BalancePlayerInput, showMmr: boolean): string {
  return showMmr ? `${player.name} (${player.mmr} MMR)` : player.name
}

export default function MultiBalancePage() {
  const { isAdmin } = useAdminAuth()
  const [players, setPlayers] = useState<BalancePlayerOption[]>([])
  const [playersLoading, setPlayersLoading] = useState<boolean>(true)
  const [playersError, setPlayersError] = useState<string | null>(null)
  const [search, setSearch] = useState<string>('')
  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const [submitting, setSubmitting] = useState<boolean>(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [result, setResult] = useState<MultiBalanceResponse | null>(null)
  const [balanceMode, setBalanceMode] = useState<MultiBalanceMode>(DEFAULT_MULTI_BALANCE_MODE)

  useEffect(() => {
    let active = true

    const loadPlayers = async () => {
      setPlayersLoading(true)
      setPlayersError(null)

      try {
        const response = await apiClient.getGroupPlayers(TEMP_GROUP_ID)
        if (!active) {
          return
        }

        const mappedPlayers: BalancePlayerOption[] = response
          .map((player) => ({
            id: player.id,
            nickname: player.nickname,
            race: player.race,
            currentMmr: player.currentMmr,
            tier: player.tier,
          }))
          .sort((a, b) =>
            isAdmin
              ? b.currentMmr - a.currentMmr
              : a.nickname.localeCompare(b.nickname, 'ko-KR')
          )

        setPlayers(mappedPlayers)
      } catch {
        if (!active) {
          return
        }
        setPlayers([])
        setPlayersError(t('multiBalance.loadError'))
      } finally {
        if (active) {
          setPlayersLoading(false)
        }
      }
    }

    void loadPlayers()

    return () => {
      active = false
    }
  }, [isAdmin])

  const filteredPlayers = useMemo(() => {
    const normalizedSearch = search.trim().toLowerCase()
    if (normalizedSearch.length === 0) {
      return players
    }

    return players.filter((player) =>
      player.nickname.toLowerCase().includes(normalizedSearch),
    )
  }, [players, search])

  const selectedPlayers = useMemo(
    () => players.filter((player) => selectedIds.includes(player.id)),
    [players, selectedIds],
  )

  const selectedTotalMmr = selectedPlayers.reduce((sum, player) => sum + player.currentMmr, 0)
  const canSubmit = selectedIds.length >= 4 && !submitting && !playersLoading

  const validationMessage =
    selectedIds.length === 0
      ? t('multiBalance.validation.selectPlayers')
      : selectedIds.length < 4
        ? t('multiBalance.validation.minimumFour')
        : null

  const handleTogglePlayer = (playerId: number) => {
    setSubmitError(null)
    setResult(null)
    setSelectedIds((prev) =>
      prev.includes(playerId)
        ? prev.filter((id) => id !== playerId)
        : [...prev, playerId],
    )
  }

  const handleResetSelection = () => {
    setSelectedIds([])
    setBalanceMode(DEFAULT_MULTI_BALANCE_MODE)
    setSubmitError(null)
    setResult(null)
  }

  const handleGenerateMultiBalance = async () => {
    setSubmitError(null)
    setResult(null)

    if (selectedIds.length < 4) {
      setSubmitError(t('multiBalance.validation.minimumFour'))
      return
    }

    setSubmitting(true)
    try {
      const response = await apiClient.balanceMatchMulti(
        buildMultiBalanceRequestPayload(TEMP_GROUP_ID, selectedIds, balanceMode)
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

  return (
    <section className="space-y-6">
      <header className="space-y-1 rounded-xl border border-slate-200 bg-white px-5 py-4 shadow-sm">
        <h2 className="text-2xl font-semibold tracking-tight">{t('multiBalance.title')}</h2>
        <p className="text-sm text-slate-600">{t('multiBalance.description')}</p>
        <p className="text-xs text-slate-500">{t('multiBalance.helper.defaultPriority')}</p>
        <p className="text-xs text-slate-500">{t('multiBalance.helper.addTwoVsTwo')}</p>
        <p className="text-xs text-slate-500">{t('multiBalance.helper.waiting')}</p>
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
        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm xl:col-span-2">
          <div className="flex items-center justify-between gap-3">
            <h3 className="text-sm font-semibold text-slate-900">{t('multiBalance.selection.title')}</h3>
            <button
              type="button"
              onClick={handleResetSelection}
              className="rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-600 transition-colors hover:bg-slate-50 hover:text-slate-900"
            >
              {t('multiBalance.selection.reset')}
            </button>
          </div>

          <label className="mt-3 block space-y-1 text-xs font-medium text-slate-500">
            {t('multiBalance.selection.searchLabel')}
            <input
              type="text"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder={t('multiBalance.selection.searchPlaceholder')}
              className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
            />
          </label>

          {playersLoading ? (
            <LoadingIndicator className="mt-4" label={t('common.loading')} />
          ) : (
            <div className="mt-4 grid gap-2 sm:grid-cols-2">
              {filteredPlayers.map((player) => {
                const selected = selectedIds.includes(player.id)
                return (
                  <button
                    key={player.id}
                    type="button"
                    onClick={() => handleTogglePlayer(player.id)}
                    className={`flex items-center justify-between rounded-lg border px-3 py-2 text-left text-sm transition-colors ${
                      selected
                        ? 'border-indigo-500 bg-indigo-50 text-indigo-900'
                        : 'border-slate-200 bg-white text-slate-800 hover:bg-slate-50'
                    }`}
                  >
                    <span className="font-medium">
                      {player.nickname} ({player.race})
                    </span>
                    <span className="text-xs">{isAdmin ? player.currentMmr : '-'}</span>
                  </button>
                )
              })}
              {filteredPlayers.length === 0 && (
                <div className="rounded-lg border border-dashed border-slate-200 px-3 py-6 text-center text-sm text-slate-500 sm:col-span-2">
                  {t('multiBalance.selection.empty')}
                </div>
              )}
            </div>
          )}
        </article>

        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <h3 className="text-sm font-semibold text-slate-900">{t('multiBalance.summary.title')}</h3>
          <p className="mt-1 text-xs text-slate-500">
            {t('multiBalance.summary.selectedCount', { count: selectedIds.length })}
          </p>

          <div className="mt-3 rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700">
            {t('multiBalance.summary.totalMmr')}:{' '}
            <span className="font-semibold">{isAdmin ? selectedTotalMmr : '-'}</span>
          </div>

          <div className="mt-4 space-y-2">
            <p className="text-xs font-semibold text-slate-700">{t('multiBalance.mode.title')}</p>
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
                        ? 'border-indigo-500 bg-indigo-50 text-indigo-900'
                        : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50'
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
            <p className="mt-3 text-xs text-amber-700">{validationMessage}</p>
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
            className="mt-4 w-full rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300"
          >
            {submitting ? t('multiBalance.summary.submitting') : t('multiBalance.summary.submit')}
          </button>
        </article>
      </div>

      {result && (
        <section className="space-y-4">
          <header className="rounded-xl border border-slate-200 bg-white px-4 py-3 shadow-sm">
            <h3 className="text-sm font-semibold text-slate-900">{t('multiBalance.result.title')}</h3>
            <p className="mt-1 text-xs text-slate-600">
              {t('multiBalance.result.summary', {
                totalPlayers: result.totalPlayers,
                matchCount: result.matchCount,
              })}
            </p>
            <p className="mt-2 text-xs text-slate-700">
              {t('multiBalance.result.selectedMode')}:{' '}
              <span className="font-semibold">
                {t(getMultiBalanceModeLabelKey(result.balanceMode))}
              </span>
            </p>
            <div className="mt-3 grid gap-2 sm:grid-cols-3">
              <div className="rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-700">
                {t('multiBalance.result.totalSelected')}:{' '}
                <span className="font-semibold">{result.totalPlayers}</span>
              </div>
              <div className="rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-700">
                {t('multiBalance.result.assignedPlayers')}:{' '}
                <span className="font-semibold">{result.assignedPlayers}</span>
              </div>
              <div className="rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-700">
                {t('multiBalance.result.waitingPlayers')}:{' '}
                <span className="font-semibold">{result.waitingPlayers.length}</span>
              </div>
            </div>
          </header>

          <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <h4 className="text-sm font-semibold text-slate-900">{t('multiBalance.result.waitingTitle')}</h4>
            {result.waitingPlayers.length > 0 ? (
              <ul className="mt-3 flex flex-wrap gap-2">
                {result.waitingPlayers.map((player) => (
                  <li
                    key={`waiting-${player.id}`}
                    className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-1.5 text-xs font-medium text-amber-900"
                  >
                    {player.nickname}
                  </li>
                ))}
              </ul>
            ) : (
              <p className="mt-2 text-xs text-slate-500">{t('multiBalance.result.noWaiting')}</p>
            )}
          </article>

          <div className="grid gap-4">
            {result.matches.map((match) => (
              <article
                key={`multi-match-${match.matchNumber}`}
                className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm"
              >
                <h4 className="text-sm font-semibold text-slate-900">
                  {t('multiBalance.result.matchNumber', { number: match.matchNumber })}
                </h4>
                <p className="mt-1 text-xs text-slate-600">
                  {t('multiBalance.result.matchType')}:{' '}
                  <span className="font-semibold">
                    {match.matchType === '3v3'
                      ? t('multiBalance.result.matchType3v3')
                      : t('multiBalance.result.matchType2v2')}
                  </span>
                </p>

                <div className="mt-3 grid gap-4 lg:grid-cols-2">
                  <div>
                    <p className="text-xs font-semibold text-slate-500">{t('multiBalance.result.homeTeam')}</p>
                    <ul className="mt-2 space-y-2">
                      {match.homeTeam.map((player) => (
                        <li
                          key={`multi-home-${match.matchNumber}-${player.name}`}
                          className="rounded-lg border border-slate-200 px-3 py-2 text-sm text-slate-800"
                        >
                          {buildPlayerLine(player, isAdmin)}
                        </li>
                      ))}
                    </ul>
                  </div>

                  <div>
                    <p className="text-xs font-semibold text-slate-500">{t('multiBalance.result.awayTeam')}</p>
                    <ul className="mt-2 space-y-2">
                      {match.awayTeam.map((player) => (
                        <li
                          key={`multi-away-${match.matchNumber}-${player.name}`}
                          className="rounded-lg border border-slate-200 px-3 py-2 text-sm text-slate-800"
                        >
                          {buildPlayerLine(player, isAdmin)}
                        </li>
                      ))}
                    </ul>
                  </div>
                </div>

                <div className="mt-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
                  <div className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700">
                    {t('multiBalance.result.homeMmr')}: <span className="font-semibold">{isAdmin ? match.homeMmr : '-'}</span>
                  </div>
                  <div className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700">
                    {t('multiBalance.result.awayMmr')}: <span className="font-semibold">{isAdmin ? match.awayMmr : '-'}</span>
                  </div>
                  <div className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700">
                    {t('multiBalance.result.mmrDiff')}: <span className="font-semibold">{isAdmin ? match.mmrDiff : '-'}</span>
                  </div>
                  <div className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700">
                    {t('multiBalance.result.expectedHomeWinRate')}:{' '}
                    <span className="font-semibold">{isAdmin ? formatPercent(match.expectedHomeWinRate) : '-'}</span>
                  </div>
                </div>
                <div className="mt-3 grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
                  <div className="rounded-lg bg-indigo-50 px-3 py-2 text-xs text-indigo-900">
                    {t('multiBalance.result.raceHome')}:{' '}
                    <span className="font-semibold">{match.raceSummary.home || '-'}</span>
                  </div>
                  <div className="rounded-lg bg-indigo-50 px-3 py-2 text-xs text-indigo-900">
                    {t('multiBalance.result.raceAway')}:{' '}
                    <span className="font-semibold">{match.raceSummary.away || '-'}</span>
                  </div>
                  <div className="rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-700">
                    {t('multiBalance.result.repeatTeammatePenalty')}:{' '}
                    <span className="font-semibold">{match.penaltySummary.repeatTeammatePenalty}</span>
                  </div>
                  <div className="rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-700">
                    {t('multiBalance.result.repeatMatchupPenalty')}:{' '}
                    <span className="font-semibold">{match.penaltySummary.repeatMatchupPenalty}</span>
                  </div>
                </div>
                <div className="mt-2 rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-700">
                  {t('multiBalance.result.racePenalty')}:{' '}
                  <span className="font-semibold">{match.penaltySummary.racePenalty}</span>
                </div>
              </article>
            ))}
          </div>
        </section>
      )}
    </section>
  )
}
