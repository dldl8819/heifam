'use client'

import { useEffect, useMemo, useRef, useState } from 'react'
import { useAdminAuth } from '@/lib/admin-auth'
import { apiClient } from '@/lib/api'
import { TierParticipantBoard } from '@/components/tier-participant-board'
import { Alert, AlertContent, AlertDescription, AlertIcon, AlertTitle } from '@/components/ui/alert'
import { t } from '@/lib/i18n'
import { useMmrVisibility } from '@/lib/mmr-visibility'
import {
  buildMultiBalanceRequestPayload,
  DEFAULT_MULTI_BALANCE_MODE,
  getMultiBalanceModeLabelKey,
  MULTI_BALANCE_MODE_OPTIONS,
} from '@/lib/multi-balance-mode'
import {
  autocompleteParticipantSlot,
  compactParticipantIds,
  createParticipantSlots,
  fillParticipantSlotLabels,
  type ParticipantSlotState,
  updateParticipantSlotInput,
} from '@/lib/participant-slots'
import { getRaceCompositionOptions, normalizeRaceComposition } from '@/lib/race-composition'
import type {
  BalancePlayerInput,
  BalancePlayerOption,
  MultiBalanceMode,
  MultiBalanceResponse,
  RaceComposition,
} from '@/types/api'

const TEMP_GROUP_ID = 1
const MODE_OPTIONS: MultiBalanceMode[] = [...MULTI_BALANCE_MODE_OPTIONS]
const MINIMUM_SELECTION_SLOTS = 4

type MultiBalanceDisplayTeam = {
  key: string
  teamNumber: number
  players: BalancePlayerInput[]
  totalMmr?: number
  raceSummary: string
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

function buildTeamRaceSummary(players: BalancePlayerInput[], responseSummary: string): string {
  const assignedRaceSummary = players
    .map((player) => player.assignedRace)
    .filter(Boolean)
    .join('')

  if (assignedRaceSummary.length === players.length) {
    return assignedRaceSummary
  }

  const trimmedResponseSummary = responseSummary.trim()
  if (trimmedResponseSummary.length > 0 && trimmedResponseSummary.length <= players.length) {
    return trimmedResponseSummary
  }

  return '-'
}

function buildDisplayTeams(result: MultiBalanceResponse): MultiBalanceDisplayTeam[] {
  return result.matches.flatMap((match, matchIndex) => [
    {
      key: `multi-team-${match.matchNumber}-home`,
      teamNumber: matchIndex * 2 + 1,
      players: match.homeTeam,
      totalMmr: match.homeMmr,
      raceSummary: buildTeamRaceSummary(match.homeTeam, match.raceSummary.home),
    },
    {
      key: `multi-team-${match.matchNumber}-away`,
      teamNumber: matchIndex * 2 + 2,
      players: match.awayTeam,
      totalMmr: match.awayMmr,
      raceSummary: buildTeamRaceSummary(match.awayTeam, match.raceSummary.away),
    },
  ])
}

function deriveMultiBalanceTeamSizes(totalPlayers: number): number[] {
  if (totalPlayers < 4) {
    return []
  }

  let match3Count = Math.floor(totalPlayers / 6)
  let remaining = totalPlayers - match3Count * 6

  if (remaining === 2 && match3Count > 0 && totalPlayers < 18) {
    match3Count -= 1
    remaining += 6
  }

  const match2Count = Math.floor(remaining / 4)
  const teamSizes: number[] = []

  for (let index = 0; index < match3Count; index += 1) {
    teamSizes.push(3)
  }
  for (let index = 0; index < match2Count; index += 1) {
    teamSizes.push(2)
  }

  return teamSizes
}

function deriveRaceCompositionTeamSize(totalPlayers: number): 2 | 3 | null {
  const teamSizes = deriveMultiBalanceTeamSizes(totalPlayers)
  if (teamSizes.length === 0) {
    return null
  }

  const uniqueTeamSizes = [...new Set(teamSizes)]
  if (uniqueTeamSizes.length !== 1) {
    return null
  }

  return uniqueTeamSizes[0] === 2 || uniqueTeamSizes[0] === 3
    ? (uniqueTeamSizes[0] as 2 | 3)
    : null
}

export default function MultiBalancePage() {
  const { canViewMmr } = useAdminAuth()
  const { mmrVisible } = useMmrVisibility()
  const showMmr = canViewMmr && mmrVisible
  const [players, setPlayers] = useState<BalancePlayerOption[]>([])
  const [playersLoading, setPlayersLoading] = useState<boolean>(true)
  const [playersError, setPlayersError] = useState<string | null>(null)
  const [participantSlots, setParticipantSlots] = useState<ParticipantSlotState[]>(() =>
    createParticipantSlots(MINIMUM_SELECTION_SLOTS),
  )
  const [submitting, setSubmitting] = useState<boolean>(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [result, setResult] = useState<MultiBalanceResponse | null>(null)
  const [balanceMode, setBalanceMode] = useState<MultiBalanceMode>(DEFAULT_MULTI_BALANCE_MODE)
  const [raceComposition, setRaceComposition] = useState<RaceComposition | null>(null)
  const participantInputRefs = useRef<Array<HTMLInputElement | null>>([])

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
          .sort((a, b) => {
            if (!showMmr) {
              return a.nickname.localeCompare(b.nickname, 'ko-KR')
            }

            const aMmr = typeof a.currentMmr === 'number' ? a.currentMmr : -1
            const bMmr = typeof b.currentMmr === 'number' ? b.currentMmr : -1
            if (bMmr !== aMmr) {
              return bMmr - aMmr
            }

            return a.nickname.localeCompare(b.nickname, 'ko-KR')
          })

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
  }, [showMmr])
 
  useEffect(() => {
    if (players.length === 0) {
      return
    }

    setParticipantSlots((previous) =>
      fillParticipantSlotLabels(previous, players, MINIMUM_SELECTION_SLOTS),
    )
  }, [players])

  const selectedIds = useMemo(
    () => compactParticipantIds(participantSlots),
    [participantSlots],
  )

  const selectedPlayers = useMemo(
    () => players.filter((player) => selectedIds.includes(player.id)),
    [players, selectedIds],
  )
  const raceCompositionTeamSize = useMemo(
    () => deriveRaceCompositionTeamSize(selectedIds.length),
    [selectedIds.length],
  )
  const raceCompositionOptions = useMemo(
    () => (raceCompositionTeamSize ? getRaceCompositionOptions(raceCompositionTeamSize) : []),
    [raceCompositionTeamSize],
  )

  useEffect(() => {
    if (!raceCompositionTeamSize) {
      setRaceComposition(null)
      return
    }

    setRaceComposition((previous) => normalizeRaceComposition(raceCompositionTeamSize, previous))
  }, [raceCompositionTeamSize])

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
    setParticipantSlots(createParticipantSlots(MINIMUM_SELECTION_SLOTS))
    setBalanceMode(DEFAULT_MULTI_BALANCE_MODE)
    setRaceComposition(null)
    setSubmitError(null)
    setResult(null)
  }

  const handleParticipantSlotInputChange = (index: number, value: string) => {
    setSubmitError(null)
    setResult(null)
    setParticipantSlots((previous) =>
      updateParticipantSlotInput({
        slots: previous,
        index,
        inputValue: value,
        players,
        showMmr,
        minimumSlots: MINIMUM_SELECTION_SLOTS,
      }),
    )
  }

  const handleParticipantSlotAutocomplete = (index: number): boolean => {
    const nextSlots = autocompleteParticipantSlot({
      slots: participantSlots,
      index,
      players,
      minimumSlots: MINIMUM_SELECTION_SLOTS,
    })
    if (!nextSlots) {
      return false
    }

    setSubmitError(null)
    setResult(null)
    setParticipantSlots(nextSlots)

    window.requestAnimationFrame(() => {
      const nextInput = participantInputRefs.current[index + 1]
      if (!nextInput) {
        return
      }
      nextInput.focus()
      nextInput.select()
    })

    return true
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
        buildMultiBalanceRequestPayload(TEMP_GROUP_ID, selectedIds, balanceMode, raceComposition)
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
            onSlotInputChange={handleParticipantSlotInputChange}
            onSlotAutocomplete={handleParticipantSlotAutocomplete}
          />
        </div>

        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <h3 className="text-sm font-semibold text-slate-900">{t('multiBalance.summary.title')}</h3>
          <p className="mt-1 text-xs text-slate-500">
            {t('multiBalance.summary.selectedCount', { count: selectedIds.length })}
          </p>

          {showMmr && (
            <div className="mt-3 rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700">
              {t('multiBalance.summary.totalMmr')}:{' '}
              <span className="font-semibold">{selectedTotalMmr}</span>
            </div>
          )}

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

          <label className="mt-4 block space-y-1 text-xs font-medium text-slate-500">
            {t('multiBalance.raceComposition.label')}
            <select
              value={raceComposition ?? ''}
              onChange={(event) =>
                setRaceComposition(
                  raceCompositionTeamSize
                    ? normalizeRaceComposition(raceCompositionTeamSize, event.target.value)
                    : null,
                )
              }
              disabled={raceCompositionOptions.length === 0}
              className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 disabled:cursor-not-allowed disabled:bg-slate-100"
            >
              <option value="">{t('multiBalance.raceComposition.placeholder')}</option>
              {raceCompositionOptions.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
            {raceCompositionOptions.length === 0 && selectedIds.length >= 4 && (
              <p className="text-[11px] text-slate-500">
                {t('multiBalance.raceComposition.unavailable')}
              </p>
            )}
          </label>

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
          <header className="overflow-hidden rounded-lg border border-slate-800 bg-slate-950 shadow-sm">
            <div className="border-b border-cyan-500/30 px-4 py-3">
              <p className="text-xs font-semibold text-cyan-300">{t('multiBalance.result.title')}</p>
              <h3 className="mt-1 text-lg font-semibold text-white">
                {t('multiBalance.result.summary', {
                  totalPlayers: result.totalPlayers,
                  teamCount: displayTeams.length,
                })}
              </h3>
              <p className="mt-2 text-xs text-slate-300">
                {t('multiBalance.result.selectedMode')}:{' '}
                <span className="font-semibold text-cyan-200">
                  {t(getMultiBalanceModeLabelKey(result.balanceMode))}
                </span>
              </p>
            </div>
            <div className="grid gap-px bg-slate-800 sm:grid-cols-3">
              <div className="bg-slate-950 px-4 py-3 text-xs text-slate-300">
                <p>{t('multiBalance.result.teamCount')}</p>
                <p className="mt-1 text-lg font-semibold text-white">{displayTeams.length}</p>
              </div>
              <div className="bg-slate-950 px-4 py-3 text-xs text-slate-300">
                <p>{t('multiBalance.result.assignedPlayers')}</p>
                <p className="mt-1 text-lg font-semibold text-white">{result.assignedPlayers}</p>
              </div>
              <div className="bg-slate-950 px-4 py-3 text-xs text-slate-300">
                <p>{t('multiBalance.result.waitingPlayers')}</p>
                <p className="mt-1 text-lg font-semibold text-white">{result.waitingPlayers.length}</p>
              </div>
            </div>
          </header>

          <article className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
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

          <div className="grid gap-4 md:grid-cols-2">
            {displayTeams.map((team) => (
              <article
                key={team.key}
                className="overflow-hidden rounded-lg border border-slate-300 bg-white shadow-sm"
              >
                <header className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 bg-slate-950 px-4 py-3">
                  <div>
                    <p className="text-xs font-semibold text-cyan-300">
                      {t('multiBalance.result.teamLabel', { number: team.teamNumber })}
                    </p>
                    <h4 className="mt-1 text-base font-semibold text-white">
                      {t('multiBalance.result.teamMemberCount', { count: team.players.length })}
                    </h4>
                  </div>
                  <span className="rounded-md border border-cyan-400/40 bg-cyan-400/10 px-2.5 py-1 text-xs font-semibold text-cyan-100">
                    {t('multiBalance.result.teamRaceComposition')}: {team.raceSummary}
                  </span>
                </header>

                {showMmr && (
                  <div className="border-b border-slate-200 bg-slate-50 px-4 py-2 text-xs text-slate-700">
                    {t('multiBalance.result.teamTotalMmr')}:{' '}
                    <span className="font-semibold text-slate-950">
                      {typeof team.totalMmr === 'number' ? team.totalMmr : '-'}
                    </span>
                  </div>
                )}

                <ul className="space-y-2 p-4">
                  {team.players.map((player) => (
                    <li
                      key={`${team.key}-${player.name}`}
                      className="rounded-md border border-cyan-100 bg-cyan-50 px-3 py-2 text-sm font-medium text-slate-900"
                    >
                      {buildPlayerLine(player, showMmr)}
                    </li>
                  ))}
                </ul>
              </article>
            ))}
          </div>
        </section>
      )}
    </section>
  )
}
