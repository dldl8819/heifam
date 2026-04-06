'use client'

import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'next/navigation'
import { useAdminAuth } from '@/lib/admin-auth'
import { apiClient, isApiForbiddenError, isApiNotFoundError, isApiUnauthorizedError } from '@/lib/api'
import { Alert, AlertContent, AlertDescription, AlertIcon } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { t } from '@/lib/i18n'
import { useMmrVisibility } from '@/lib/mmr-visibility'
import type { BalancePlayerOption, MatchResultResponse, RecentMatchItem, TeamSide } from '@/types/api'

const TEMP_GROUP_ID = 1
const winnerTeamOptions: TeamSide[] = ['HOME', 'AWAY']
const manualTeamSizeOptions = [3, 2] as const
type OperatorEntryMode = 'existing' | 'manual'
type SupportedTeamSize = (typeof manualTeamSizeOptions)[number]
type ManualSlotValue = number | ''

function formatTeamLabel(team: TeamSide | string | null): string {
  if (team === null) {
    return t('results.recent.noWinner')
  }

  return team === 'HOME'
    ? t('results.team.home')
    : team === 'AWAY'
      ? t('results.team.away')
      : team
}

function formatPlayedAt(value: string): string {
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return value
  }

  return parsed.toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function formatOptionalDate(value: string | null): string {
  if (!value) {
    return '-'
  }
  return formatPlayedAt(value)
}

function formatRecordedBy(value: string | null): string {
  if (!value || value.trim().length === 0) {
    return '-'
  }
  return value
}

function formatTeamPlayers(match: RecentMatchItem, team: TeamSide): string {
  const players = team === 'HOME' ? match.homeTeam : match.awayTeam
  if (players.length === 0) {
    return '-'
  }

  return players.map((player) => player.nickname).join(', ')
}

function isWinningTeam(team: TeamSide, winningTeam: TeamSide | null): boolean {
  return winningTeam === team
}

function createManualSlots(teamSize: SupportedTeamSize): ManualSlotValue[] {
  return Array.from({ length: teamSize }, () => '')
}

function resizeManualSlots(
  slots: ManualSlotValue[],
  teamSize: SupportedTeamSize,
): ManualSlotValue[] {
  const next = slots.slice(0, teamSize)
  while (next.length < teamSize) {
    next.push('')
  }
  return next
}

function formatManualPlayerLabel(player: BalancePlayerOption, showMmr: boolean): string {
  const mmrText =
    showMmr && typeof player.currentMmr === 'number' ? ` · ${player.currentMmr} MMR` : ''
  const tierText = player.tier ? ` [${player.tier}]` : ''
  return `${player.nickname} (${player.race})${tierText}${mmrText}`
}

export default function ResultsPage() {
  const searchParams = useSearchParams()
  const { isAdmin, isSuperAdmin } = useAdminAuth()
  const { mmrVisible } = useMmrVisibility()
  const showMmr = isAdmin && mmrVisible
  const [operatorEntryMode, setOperatorEntryMode] = useState<OperatorEntryMode>('existing')
  const [manualGroupId, setManualGroupId] = useState<number>(TEMP_GROUP_ID)
  const [manualTeamSize, setManualTeamSize] = useState<SupportedTeamSize>(3)
  const [manualHomeSlots, setManualHomeSlots] = useState<ManualSlotValue[]>(() => createManualSlots(3))
  const [manualAwaySlots, setManualAwaySlots] = useState<ManualSlotValue[]>(() => createManualSlots(3))
  const [manualWinnerTeam, setManualWinnerTeam] = useState<TeamSide>('HOME')
  const [manualNote, setManualNote] = useState<string>('')
  const [manualPlayers, setManualPlayers] = useState<BalancePlayerOption[]>([])
  const [manualPlayersLoading, setManualPlayersLoading] = useState<boolean>(false)
  const [manualPlayersError, setManualPlayersError] = useState<string | null>(null)
  const [manualSubmitting, setManualSubmitting] = useState<boolean>(false)
  const [manualSubmitError, setManualSubmitError] = useState<string | null>(null)
  const [manualSubmitSuccess, setManualSubmitSuccess] = useState<string | null>(null)
  const [selectedRecentMatchId, setSelectedRecentMatchId] = useState<number | null>(null)
  const [selectedRecentWinnerTeam, setSelectedRecentWinnerTeam] = useState<TeamSide>('HOME')
  const [isRecentSaving, setIsRecentSaving] = useState<boolean>(false)
  const [isRecentDeleting, setIsRecentDeleting] = useState<boolean>(false)
  const [recentActionMessage, setRecentActionMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<MatchResultResponse | null>(null)
  const [matchImportPayload, setMatchImportPayload] = useState<string>('[]')
  const [isImporting, setIsImporting] = useState<boolean>(false)
  const [importError, setImportError] = useState<string | null>(null)
  const [importSuccess, setImportSuccess] = useState<string | null>(null)
  const [recentMatches, setRecentMatches] = useState<RecentMatchItem[]>([])
  const [recentMatchesLoading, setRecentMatchesLoading] = useState<boolean>(true)
  const [recentMatchesError, setRecentMatchesError] = useState<string | null>(null)
  const [appliedSearchSelection, setAppliedSearchSelection] = useState<boolean>(false)

  const requestedMatchId = useMemo(() => {
    const raw = searchParams.get('matchId')
    if (!raw) {
      return null
    }
    const parsed = Number(raw)
    return Number.isFinite(parsed) && parsed > 0 ? parsed : null
  }, [searchParams])

  const requestedWinnerTeam = useMemo<TeamSide | null>(() => {
    const raw = searchParams.get('winnerTeam')
    if (raw === 'HOME' || raw === 'AWAY') {
      return raw
    }
    return null
  }, [searchParams])

  const requestedFromBalance = useMemo(
    () => searchParams.get('from') === 'balance',
    [searchParams]
  )

  const recentMatchDisplayOrderMap = useMemo(
    () =>
      new Map(
        recentMatches.map((recentMatch, index) => [recentMatch.matchId, index + 1]),
      ),
    [recentMatches],
  )

  const formatMatchReference = useCallback(
    (matchId: number): string => {
      if (isSuperAdmin) {
        return t('results.recent.reference.matchId', { matchId })
      }

      const displayOrder = recentMatchDisplayOrderMap.get(matchId)
      if (typeof displayOrder === 'number') {
        return t('results.recent.reference.displayOrder', { order: displayOrder })
      }

      return t('results.recent.reference.generic')
    },
    [isSuperAdmin, recentMatchDisplayOrderMap],
  )

  const loadRecentMatches = async () => {
    setRecentMatchesLoading(true)
    setRecentMatchesError(null)

    try {
      const response = await apiClient.getRecentMatches(TEMP_GROUP_ID, 20)
      const completedMatches = response.filter(
        (match) => match.winningTeam === 'HOME' || match.winningTeam === 'AWAY',
      )
      setRecentMatches(completedMatches)
      if (selectedRecentMatchId !== null) {
        const selectedMatch = completedMatches.find((match) => match.matchId === selectedRecentMatchId)
        if (!selectedMatch || (selectedMatch.winningTeam !== 'HOME' && selectedMatch.winningTeam !== 'AWAY')) {
          setSelectedRecentMatchId(null)
          setRecentActionMessage(null)
        } else {
          setSelectedRecentWinnerTeam(selectedMatch.winningTeam)
        }
      }
    } catch {
      setRecentMatches([])
      setRecentMatchesError(t('results.recent.loadError'))
    } finally {
      setRecentMatchesLoading(false)
    }
  }

  useEffect(() => {
    void loadRecentMatches()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    setAppliedSearchSelection(false)
  }, [requestedFromBalance, requestedMatchId, requestedWinnerTeam])

  useEffect(() => {
    if (appliedSearchSelection || !isAdmin || requestedMatchId === null || recentMatches.length === 0) {
      return
    }

    const matched = recentMatches.find((match) => match.matchId === requestedMatchId)
    if (!matched) {
      return
    }

    if (requestedFromBalance) {
      setSelectedRecentMatchId(null)
      setSelectedRecentWinnerTeam(
        requestedWinnerTeam ??
          (matched.winningTeam === 'HOME' || matched.winningTeam === 'AWAY'
            ? matched.winningTeam
            : 'HOME')
      )
      setRecentActionMessage(
        t('results.recent.submittedFromBalance', {
          matchReference: formatMatchReference(matched.matchId),
        })
      )
      setAppliedSearchSelection(true)
      return
    }

    setSelectedRecentMatchId(matched.matchId)
    setSelectedRecentWinnerTeam(
      requestedWinnerTeam ??
        (matched.winningTeam === 'HOME' || matched.winningTeam === 'AWAY'
          ? matched.winningTeam
          : 'HOME')
    )

    setAppliedSearchSelection(true)
  }, [
    appliedSearchSelection,
    isAdmin,
    recentMatches,
    requestedFromBalance,
    requestedMatchId,
    requestedWinnerTeam,
    formatMatchReference,
  ])

  useEffect(() => {
    if (!requestedFromBalance || requestedMatchId !== null || appliedSearchSelection) {
      return
    }

    setSelectedRecentMatchId(null)
    setRecentActionMessage(t('results.recent.submittedFromBalanceGeneric'))
    setAppliedSearchSelection(true)
  }, [appliedSearchSelection, requestedFromBalance, requestedMatchId])

  useEffect(() => {
    if (isAdmin) {
      return
    }

    setSelectedRecentMatchId(null)
  }, [isAdmin])

  useEffect(() => {
    setManualHomeSlots((previous) => resizeManualSlots(previous, manualTeamSize))
    setManualAwaySlots((previous) => resizeManualSlots(previous, manualTeamSize))
  }, [manualTeamSize])

  useEffect(() => {
    if (!isAdmin) {
      setManualPlayers([])
      setManualPlayersError(null)
      setManualPlayersLoading(false)
      return
    }

    let active = true

    const loadManualPlayers = async () => {
      setManualPlayersLoading(true)
      setManualPlayersError(null)

      try {
        const roster = await apiClient.getGroupPlayers(manualGroupId)
        if (!active) {
          return
        }

        const nextPlayers = roster
          .map((player) => ({
            id: player.id,
            nickname: player.nickname,
            race: player.race,
            currentMmr: player.currentMmr,
            tier: player.tier,
          }))
          .sort((left, right) => {
            if (!showMmr) {
              return left.nickname.localeCompare(right.nickname, 'ko-KR')
            }

            const leftMmr = typeof left.currentMmr === 'number' ? left.currentMmr : -1
            const rightMmr = typeof right.currentMmr === 'number' ? right.currentMmr : -1
            if (rightMmr !== leftMmr) {
              return rightMmr - leftMmr
            }

            return left.nickname.localeCompare(right.nickname, 'ko-KR')
          })

        setManualPlayers(nextPlayers)
      } catch {
        if (!active) {
          return
        }
        setManualPlayers([])
        setManualPlayersError(t('results.manual.loadPlayersError'))
      } finally {
        if (active) {
          setManualPlayersLoading(false)
        }
      }
    }

    void loadManualPlayers()

    return () => {
      active = false
    }
  }, [isAdmin, manualGroupId, showMmr])

  const selectedManualPlayerIds = useMemo(
    () =>
      [...manualHomeSlots, ...manualAwaySlots].filter(
        (playerId): playerId is number => typeof playerId === 'number' && Number.isFinite(playerId),
      ),
    [manualAwaySlots, manualHomeSlots],
  )

  const handleManualSlotChange = (
    team: TeamSide,
    slotIndex: number,
    value: string,
  ) => {
    const normalizedValue = value.trim()
    const parsedValue = normalizedValue.length === 0 ? NaN : Number.parseInt(normalizedValue, 10)
    const nextValue: ManualSlotValue =
      normalizedValue.length === 0 || !Number.isFinite(parsedValue) ? '' : parsedValue

    if (team === 'HOME') {
      setManualHomeSlots((previous) =>
        previous.map((playerId, index) => (index === slotIndex ? nextValue : playerId)),
      )
      return
    }

    setManualAwaySlots((previous) =>
      previous.map((playerId, index) => (index === slotIndex ? nextValue : playerId)),
    )
  }

  const handleManualSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()

    if (!isAdmin) {
      setManualSubmitError(t('common.adminOnlyAction'))
      return
    }

    const homePlayerIds = manualHomeSlots.filter(
      (playerId): playerId is number => typeof playerId === 'number' && Number.isFinite(playerId),
    )
    const awayPlayerIds = manualAwaySlots.filter(
      (playerId): playerId is number => typeof playerId === 'number' && Number.isFinite(playerId),
    )

    if (homePlayerIds.length !== manualTeamSize || awayPlayerIds.length !== manualTeamSize) {
      setManualSubmitError(t('results.manual.validation.playersRequired'))
      return
    }

    const allPlayerIds = [...homePlayerIds, ...awayPlayerIds]
    if (new Set(allPlayerIds).size !== allPlayerIds.length) {
      setManualSubmitError(t('results.manual.validation.duplicatePlayers'))
      return
    }

    setManualSubmitError(null)
    setManualSubmitSuccess(null)
    setError(null)
    setRecentActionMessage(null)
    setManualSubmitting(true)

    try {
      const response = await apiClient.createManualMatch({
        groupId: manualGroupId,
        teamSize: manualTeamSize,
        homePlayerIds,
        awayPlayerIds,
        winnerTeam: manualWinnerTeam,
        note: manualNote.trim().length > 0 ? manualNote.trim() : undefined,
      })

      setResult(response)
      setSelectedRecentMatchId(null)
      setSelectedRecentWinnerTeam(response.winnerTeam)
      setManualSubmitSuccess(
        isSuperAdmin
          ? t('results.manual.successWithMatchId', { matchId: response.matchId })
          : t('results.manual.success'),
      )
      await loadRecentMatches()
    } catch (manualError) {
      if (isApiUnauthorizedError(manualError)) {
        setManualSubmitError(t('common.adminLoginRequired'))
      } else if (isApiForbiddenError(manualError)) {
        setManualSubmitError(t('common.permissionDenied'))
      } else if (manualError instanceof Error && manualError.message.trim().length > 0) {
        setManualSubmitError(manualError.message)
      } else {
        setManualSubmitError(t('results.manual.failure'))
      }
    } finally {
      setManualSubmitting(false)
    }
  }

  const handlePickRecentMatch = (recentMatch: RecentMatchItem) => {
    if (!isAdmin) {
      return
    }

    setSelectedRecentMatchId(recentMatch.matchId)
    const pickedWinnerTeam =
      recentMatch.winningTeam === 'HOME' || recentMatch.winningTeam === 'AWAY'
        ? recentMatch.winningTeam
        : 'HOME'
    setSelectedRecentWinnerTeam(pickedWinnerTeam)
    setError(null)
    setRecentActionMessage(null)
  }

  const handleUpdateRecentMatch = async () => {
    if (!isAdmin) {
      setError(t('common.adminOnlyAction'))
      return
    }

    if (selectedRecentMatchId === null) {
      return
    }

    setError(null)
    setRecentActionMessage(null)
    setIsRecentSaving(true)
    try {
      const response = await apiClient.updateMatchResult(selectedRecentMatchId, {
        winnerTeam: selectedRecentWinnerTeam,
      })
      setResult(response)
      setRecentActionMessage(
        t('results.recent.updated', {
          matchReference: formatMatchReference(selectedRecentMatchId),
          winner: formatTeamLabel(selectedRecentWinnerTeam),
        }),
      )
      await loadRecentMatches()
    } catch (updateError) {
      if (isApiForbiddenError(updateError)) {
        setError(t('common.permissionDenied'))
      } else {
        setError(t('results.recent.updateFailure'))
      }
    } finally {
      setIsRecentSaving(false)
    }
  }

  const handleDeleteRecentMatch = async () => {
    if (!isAdmin) {
      setError(t('common.adminOnlyAction'))
      return
    }

    if (selectedRecentMatchId === null) {
      return
    }

    const targetMatchId = selectedRecentMatchId
    const confirmed = window.confirm(
      t('results.recent.deleteConfirm', {
        matchReference: formatMatchReference(targetMatchId),
      }),
    )
    if (!confirmed) {
      return
    }

    setError(null)
    setRecentActionMessage(null)
    setIsRecentDeleting(true)
    try {
      await apiClient.deleteMatch(targetMatchId)
      setSelectedRecentMatchId(null)
      setResult((previousResult) =>
        previousResult && previousResult.matchId === targetMatchId ? null : previousResult,
      )
      setRecentActionMessage(
        t('results.recent.deleted', {
          matchReference: formatMatchReference(targetMatchId),
        }),
      )
      await loadRecentMatches()
    } catch (deleteError) {
      if (isApiForbiddenError(deleteError)) {
        setError(t('common.permissionDenied'))
      } else if (isApiNotFoundError(deleteError)) {
        setError(t('results.recent.deleteNotFound'))
      } else {
        setError(t('results.recent.deleteFailure'))
      }
    } finally {
      setIsRecentDeleting(false)
    }
  }

  const handleImportMatches = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setImportError(null)
    setImportSuccess(null)

    if (!isSuperAdmin) {
      setImportError(t('common.adminOnlyAction'))
      return
    }

    let payload: unknown
    try {
      payload = JSON.parse(matchImportPayload)
    } catch {
      setImportError(t('common.invalidJson'))
      return
    }

    setIsImporting(true)
    try {
      await apiClient.importMatches(payload)
      setImportSuccess(t('results.import.success'))
      await loadRecentMatches()
    } catch (matchImportError) {
      if (isApiUnauthorizedError(matchImportError)) {
        setImportError(t('common.adminLoginRequired'))
      } else if (isApiForbiddenError(matchImportError)) {
        setImportError(t('common.permissionDenied'))
      } else if (matchImportError instanceof Error && matchImportError.message.trim().length > 0) {
        setImportError(matchImportError.message)
      } else {
        setImportError(t('results.import.failure'))
      }
    } finally {
      setIsImporting(false)
    }
  }

  const resultsHeaderDescription = isAdmin
    ? t('results.descriptionAdmin')
    : t('results.descriptionViewer')

  return (
    <section className="space-y-6">
      <header className="space-y-1 rounded-xl border border-slate-200 bg-white px-5 py-4 shadow-sm">
        <h2 className="text-2xl font-semibold tracking-tight">{t('results.title')}</h2>
        <p className="text-sm text-slate-600">{resultsHeaderDescription}</p>
      </header>

      {isAdmin && (
        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div className="space-y-1">
              <h3 className="text-sm font-semibold text-slate-900">{t('results.operator.title')}</h3>
              <p className="text-xs text-slate-500">{t('results.operator.description')}</p>
            </div>
            <div className="inline-flex rounded-lg border border-slate-200 bg-slate-50 p-1">
              {(['existing', 'manual'] as const).map((mode) => {
                const selected = operatorEntryMode === mode
                return (
                  <button
                    key={mode}
                    type="button"
                    onClick={() => setOperatorEntryMode(mode)}
                    className={`rounded-md px-3 py-1.5 text-xs font-medium transition-colors ${
                      selected
                        ? 'bg-slate-900 text-white'
                        : 'text-slate-600 hover:bg-white hover:text-slate-900'
                    }`}
                  >
                    {mode === 'existing'
                      ? t('results.operator.mode.existing')
                      : t('results.operator.mode.manual')}
                  </button>
                )
              })}
            </div>
          </div>

          {operatorEntryMode === 'existing' ? (
            <p className="mt-4 rounded-lg border border-slate-200 bg-slate-50 px-3 py-3 text-sm text-slate-600">
              {t('results.operator.existingHelper')}
            </p>
          ) : (
            <form className="mt-4 space-y-4" onSubmit={handleManualSubmit}>
              <div className="grid gap-4 lg:grid-cols-3">
                <label className="space-y-1 text-sm">
                  <span className="font-medium text-slate-700">{t('results.manual.groupLabel')}</span>
                  <select
                    value={manualGroupId}
                    onChange={(event) => setManualGroupId(Number.parseInt(event.target.value, 10))}
                    className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
                  >
                    <option value={TEMP_GROUP_ID}>{t('results.manual.groupOptionDefault')}</option>
                  </select>
                </label>

                <label className="space-y-1 text-sm">
                  <span className="font-medium text-slate-700">{t('results.manual.teamSizeLabel')}</span>
                  <select
                    value={manualTeamSize}
                    onChange={(event) => setManualTeamSize(event.target.value === '2' ? 2 : 3)}
                    className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
                  >
                    {manualTeamSizeOptions.map((teamSizeOption) => (
                      <option key={teamSizeOption} value={teamSizeOption}>
                        {teamSizeOption === 3
                          ? t('balance.mode.threeVsThree')
                          : t('balance.mode.twoVsTwo')}
                      </option>
                    ))}
                  </select>
                </label>

                <label className="space-y-1 text-sm">
                  <span className="font-medium text-slate-700">{t('results.manual.winnerLabel')}</span>
                  <select
                    value={manualWinnerTeam}
                    onChange={(event) => setManualWinnerTeam(event.target.value as TeamSide)}
                    className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
                  >
                    {winnerTeamOptions.map((team) => (
                      <option key={`manual-${team}`} value={team}>
                        {formatTeamLabel(team)}
                      </option>
                    ))}
                  </select>
                </label>
              </div>

              {manualPlayersLoading ? (
                <LoadingIndicator label={t('common.loading')} />
              ) : manualPlayersError ? (
                <Alert variant="destructive" appearance="light" size="sm">
                  <AlertIcon icon="destructive">!</AlertIcon>
                  <AlertContent>
                    <AlertDescription>{manualPlayersError}</AlertDescription>
                  </AlertContent>
                </Alert>
              ) : (
                <div className="grid gap-4 lg:grid-cols-2">
                  {([
                    ['HOME', manualHomeSlots, t('results.manual.homeTitle')],
                    ['AWAY', manualAwaySlots, t('results.manual.awayTitle')],
                  ] as const).map(([team, slots, title]) => (
                    <div key={team} className="space-y-3 rounded-xl border border-slate-200 bg-slate-50/60 p-4">
                      <h4 className="text-sm font-semibold text-slate-900">{title}</h4>
                      <div className="space-y-2">
                        {slots.map((selectedPlayerId, slotIndex) => (
                          <select
                            key={`${team}-slot-${slotIndex}`}
                            value={selectedPlayerId}
                            onChange={(event) => handleManualSlotChange(team, slotIndex, event.target.value)}
                            className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
                          >
                            <option value="">
                              {t('results.manual.playerPlaceholder', { slot: slotIndex + 1 })}
                            </option>
                            {manualPlayers.map((player) => {
                              const isSelectedElsewhere =
                                selectedManualPlayerIds.includes(player.id) && selectedPlayerId !== player.id
                              return (
                                <option
                                  key={`${team}-${slotIndex}-${player.id}`}
                                  value={player.id}
                                  disabled={isSelectedElsewhere}
                                >
                                  {formatManualPlayerLabel(player, showMmr)}
                                </option>
                              )
                            })}
                          </select>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              )}

              <label className="block space-y-1 text-sm">
                <span className="font-medium text-slate-700">{t('results.manual.noteLabel')}</span>
                <input
                  type="text"
                  value={manualNote}
                  onChange={(event) => setManualNote(event.target.value)}
                  maxLength={255}
                  placeholder={t('results.manual.notePlaceholder')}
                  className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
                />
              </label>

              {manualSubmitError && (
                <Alert variant="destructive" appearance="light" size="sm">
                  <AlertIcon icon="destructive">!</AlertIcon>
                  <AlertContent>
                    <AlertDescription>{manualSubmitError}</AlertDescription>
                  </AlertContent>
                </Alert>
              )}
              {manualSubmitSuccess && (
                <p className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">
                  {manualSubmitSuccess}
                </p>
              )}

              <button
                type="submit"
                disabled={manualSubmitting || manualPlayersLoading}
                className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300"
              >
                {manualSubmitting ? t('results.manual.submitting') : t('results.manual.submit')}
              </button>
            </form>
          )}
        </article>
      )}

      <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
        <h3 className="text-sm font-semibold text-slate-900">{t('results.recent.title')}</h3>
        <p className="mt-1 text-xs text-slate-500">{t('results.recent.description')}</p>
        {recentActionMessage && (
          <p className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs text-emerald-700">
            {recentActionMessage}
          </p>
        )}
        {error && (
          <Alert variant="destructive" appearance="light" size="sm" className="mt-3">
            <AlertIcon icon="destructive">!</AlertIcon>
            <AlertContent>
              <AlertDescription>{error}</AlertDescription>
            </AlertContent>
          </Alert>
        )}

        {recentMatchesLoading && (
          <LoadingIndicator className="mt-3" label={t('common.loading')} />
        )}
        {recentMatchesError && (
          <Alert variant="destructive" appearance="light" size="sm" className="mt-3">
            <AlertIcon icon="destructive">!</AlertIcon>
            <AlertContent>
              <AlertDescription>{recentMatchesError}</AlertDescription>
            </AlertContent>
          </Alert>
        )}

        {!recentMatchesLoading && !recentMatchesError && recentMatches.length === 0 && (
          <div className="mt-3 rounded-lg border border-dashed border-slate-200 px-3 py-8 text-center text-sm text-slate-500">
            {t('results.recent.empty')}
          </div>
        )}

        {!recentMatchesLoading && !recentMatchesError && recentMatches.length > 0 && (
          <div className="mt-3 overflow-x-auto rounded-lg border border-slate-200">
            <table className="min-w-full text-left text-sm">
              <thead className="bg-slate-50 text-xs tracking-wide text-slate-500">
                <tr>
                  <th className="px-3 py-2">{t('results.recent.table.displayOrder')}</th>
                  {isSuperAdmin && <th className="px-3 py-2">{t('results.recent.table.matchId')}</th>}
                  <th className="px-3 py-2">{t('results.recent.table.playedAt')}</th>
                  <th className="px-3 py-2">{t('results.recent.table.recordedAt')}</th>
                  <th className="px-3 py-2">{t('results.recent.table.recordedBy')}</th>
                  <th className="px-3 py-2">{t('results.recent.table.winner')}</th>
                  <th className="px-3 py-2">{t('results.recent.table.homeTeam')}</th>
                  <th className="px-3 py-2">{t('results.recent.table.awayTeam')}</th>
                  {showMmr && <th className="px-3 py-2">{t('results.recent.table.mmrDiff')}</th>}
                  {isAdmin && <th className="px-3 py-2">{t('results.recent.table.action')}</th>}
                </tr>
              </thead>
              <tbody>
                {recentMatches.map((recentMatch) => (
                  <tr
                    key={`recent-match-${recentMatch.matchId}`}
                    className={`border-t border-slate-100 transition-colors hover:bg-slate-50 ${
                      selectedRecentMatchId === recentMatch.matchId ? 'bg-amber-50/60' : ''
                    }`}
                  >
                    <td className="px-3 py-2 font-medium text-slate-900">
                      {recentMatchDisplayOrderMap.get(recentMatch.matchId) ?? '-'}
                    </td>
                    {isSuperAdmin && (
                      <td className="px-3 py-2 font-medium text-slate-900">{recentMatch.matchId}</td>
                    )}
                    <td className="px-3 py-2 text-slate-700">{formatPlayedAt(recentMatch.playedAt)}</td>
                    <td className="px-3 py-2 text-slate-700">{formatOptionalDate(recentMatch.resultRecordedAt)}</td>
                    <td className="px-3 py-2 text-slate-700">{formatRecordedBy(recentMatch.resultRecordedByNickname)}</td>
                    <td className="px-3 py-2 text-slate-700">
                      {selectedRecentMatchId === recentMatch.matchId && isAdmin ? (
                        <select
                          value={selectedRecentWinnerTeam}
                          onChange={(event) => setSelectedRecentWinnerTeam(event.target.value as TeamSide)}
                          className="rounded-md border border-slate-300 bg-white px-2 py-1 text-xs text-slate-800 outline-none focus:border-slate-400 focus:ring-1 focus:ring-slate-200"
                        >
                          {winnerTeamOptions.map((team) => (
                            <option key={`${recentMatch.matchId}-${team}`} value={team}>
                              {formatTeamLabel(team)}
                            </option>
                          ))}
                        </select>
                      ) : (
                        formatTeamLabel(recentMatch.winningTeam)
                      )}
                    </td>
                    <td
                      className={`px-3 py-2 ${
                        isWinningTeam('HOME', recentMatch.winningTeam)
                          ? 'font-medium text-emerald-700'
                          : 'text-slate-700'
                      }`}
                    >
                      {formatTeamPlayers(recentMatch, 'HOME')}
                    </td>
                    <td
                      className={`px-3 py-2 ${
                        isWinningTeam('AWAY', recentMatch.winningTeam)
                          ? 'font-medium text-emerald-700'
                          : 'text-slate-700'
                      }`}
                    >
                      {formatTeamPlayers(recentMatch, 'AWAY')}
                    </td>
                    {showMmr && (
                      <td className="px-3 py-2 text-slate-700">
                        {typeof recentMatch.mmrDiff === 'number' ? recentMatch.mmrDiff : '-'}
                      </td>
                    )}
                    {isAdmin && (
                      <td className="px-3 py-2">
                        {selectedRecentMatchId === recentMatch.matchId ? (
                        <div className="flex items-center gap-1.5">
                          <button
                            type="button"
                            onClick={handleUpdateRecentMatch}
                            disabled={isRecentSaving || isRecentDeleting}
                            className="rounded-md border border-slate-900 bg-slate-900 px-2.5 py-1 text-xs font-medium text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:border-slate-300 disabled:bg-slate-300"
                          >
                            {isRecentSaving ? t('results.recent.saving') : t('results.recent.save')}
                          </button>
                          <button
                            type="button"
                            onClick={handleDeleteRecentMatch}
                            disabled={isRecentSaving || isRecentDeleting}
                            className="rounded-md border border-rose-300 bg-white px-2.5 py-1 text-xs font-medium text-rose-700 transition-colors hover:border-rose-500 hover:bg-rose-50 disabled:cursor-not-allowed disabled:border-slate-200 disabled:text-slate-400"
                          >
                            {isRecentDeleting ? t('results.recent.deleting') : t('results.recent.delete')}
                          </button>
                        </div>
                        ) : (
                          <button
                            type="button"
                            onClick={() => handlePickRecentMatch(recentMatch)}
                            className="rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-700 transition-colors hover:border-slate-900 hover:bg-slate-900 hover:text-white"
                          >
                            {t('results.recent.pick')}
                          </button>
                        )}
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </article>

      {result && (
        <article className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-sm">
          <h3 className="border-b border-slate-200 px-4 py-3 text-sm font-semibold text-slate-900">
            {t('results.participants.title')}
          </h3>
          <table className="min-w-full text-left text-sm">
            <thead className="bg-slate-50 text-xs tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-3">{t('results.participants.nickname')}</th>
                <th className="px-4 py-3">{t('results.participants.team')}</th>
                {showMmr && <th className="px-4 py-3">{t('results.participants.beforeMmr')}</th>}
                {showMmr && <th className="px-4 py-3">{t('results.participants.afterMmr')}</th>}
                {showMmr && <th className="px-4 py-3">{t('results.participants.delta')}</th>}
              </tr>
            </thead>
            <tbody>
              {result.participants.map((participant) => {
                const participantMmrBefore =
                  typeof participant.mmrBefore === 'number' ? participant.mmrBefore : null
                const participantMmrAfter =
                  typeof participant.mmrAfter === 'number' ? participant.mmrAfter : null
                const participantMmrDelta =
                  typeof participant.mmrDelta === 'number' ? participant.mmrDelta : null

                return (
                  <tr
                    key={`${participant.playerId}-${participant.nickname}`}
                    className="border-t border-slate-100"
                  >
                    <td
                      className={`px-4 py-3 font-medium ${
                        participant.team === result.winnerTeam
                          ? 'text-emerald-700'
                          : 'text-slate-900'
                      }`}
                    >
                      {participant.nickname}
                    </td>
                    <td
                      className={`px-4 py-3 ${
                        participant.team === result.winnerTeam
                          ? 'font-medium text-emerald-700'
                          : 'text-slate-700'
                      }`}
                    >
                      {formatTeamLabel(participant.team)}
                    </td>
                    {showMmr && (
                      <td className="px-4 py-3 text-slate-700">
                        {participantMmrBefore !== null ? participantMmrBefore : '-'}
                      </td>
                    )}
                    {showMmr && (
                      <td className="px-4 py-3 text-slate-700">
                        {participantMmrAfter !== null ? participantMmrAfter : '-'}
                      </td>
                    )}
                    {showMmr && participantMmrDelta !== null ? (
                      <td
                        className={`px-4 py-3 font-medium ${
                          participantMmrDelta >= 0 ? 'text-emerald-600' : 'text-rose-600'
                        }`}
                      >
                        {participantMmrDelta >= 0 ? `+${participantMmrDelta}` : participantMmrDelta}
                      </td>
                    ) : showMmr ? (
                      <td className="px-4 py-3 text-slate-700">-</td>
                    ) : null}
                  </tr>
                )
              })}
            </tbody>
          </table>
        </article>
      )}

      {isSuperAdmin && (
        <article id="match-import" className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <h3 className="text-sm font-semibold text-slate-900">{t('results.import.title')}</h3>
          <p className="mt-1 text-xs text-slate-500">{t('results.import.description')}</p>
          <form className="mt-3 space-y-3" onSubmit={handleImportMatches}>
            <textarea
              value={matchImportPayload}
              onChange={(event) => setMatchImportPayload(event.target.value)}
              className="h-32 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 font-mono text-xs text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
              placeholder={t('results.import.placeholder')}
            />

            {importError && (
              <Alert variant="destructive" appearance="light" size="sm">
                <AlertIcon icon="destructive">!</AlertIcon>
                <AlertContent>
                  <AlertDescription>{importError}</AlertDescription>
                </AlertContent>
              </Alert>
            )}
            {importSuccess && (
              <p className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs text-emerald-700">
                {importSuccess}
              </p>
            )}

            <button
              type="submit"
              disabled={isImporting}
              className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300"
            >
              {isImporting ? t('results.import.loading') : t('results.import.button')}
            </button>
          </form>
        </article>
      )}
    </section>
  )
}
