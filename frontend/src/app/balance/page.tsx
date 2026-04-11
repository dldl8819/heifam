'use client'

import {
  type CSSProperties,
  type ClipboardEvent as ReactClipboardEvent,
  type DragEvent as ReactDragEvent,
  type KeyboardEvent as ReactKeyboardEvent,
  type MouseEvent as ReactMouseEvent,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react'
import { useRouter } from 'next/navigation'
import { useAdminAuth } from '@/lib/admin-auth'
import { apiClient, isApiConflictError, isApiForbiddenError, isApiUnauthorizedError } from '@/lib/api'
import { Alert, AlertContent, AlertDescription, AlertIcon, AlertTitle } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { t } from '@/lib/i18n'
import { useMmrVisibility } from '@/lib/mmr-visibility'
import { findUniquePlayerByNicknamePrefix } from '@/lib/player-autocomplete'
import { getRaceCompositionOptions, normalizeRaceComposition } from '@/lib/race-composition'
import type {
  BalancePlayerOption,
  BalanceResponse,
  MatchResultResponse,
  RaceComposition,
  TeamSide,
} from '@/types/api'

const TEMP_GROUP_ID = 1
const MAX_SLOT_COUNT = 6
const BALANCE_STATE_STORAGE_KEY = 'balancify.balance.state.v2'
const winnerTeamOptions: TeamSide[] = ['HOME', 'AWAY']
type WinnerTeamSelection = '' | TeamSide
const teamSizeOptions = [
  { value: 3 as const, labelKey: 'balance.mode.threeVsThree' },
  { value: 2 as const, labelKey: 'balance.mode.twoVsTwo' },
]

type SupportedTeamSize = 2 | 3

type PersistedBalanceState = {
  teamSize: SupportedTeamSize
  raceComposition: RaceComposition | null
  slots: Array<number | null>
  slotInputs: string[]
  result: BalanceResponse | null
  resultMatchId: string
  winnerTeam: WinnerTeamSelection
}

function formatPercent(value: number): string {
  const percent = value <= 1 ? value * 100 : value
  return `${percent.toFixed(2)}%`
}

function formatTeamLabel(team: TeamSide | string): string {
  return team === 'HOME'
    ? t('results.team.home')
    : team === 'AWAY'
      ? t('results.team.away')
      : team
}

function formatAssignedRace(assignedRace?: string): string {
  if (!assignedRace) {
    return ''
  }
  return ` · ${assignedRace}`
}

function resolveReadableApiErrorMessage(error: unknown): string | null {
  if (!(error instanceof Error)) {
    return null
  }

  const message = error.message.trim()
  if (
    message.length === 0 ||
    message.startsWith('{') ||
    message.startsWith('[') ||
    /^API request failed \(\d+\)$/.test(message)
  ) {
    return null
  }

  return message
}

function createEmptySlots(): Array<number | null> {
  return Array.from({ length: MAX_SLOT_COUNT }, () => null)
}

function createEmptySlotInputs(): string[] {
  return Array.from({ length: MAX_SLOT_COUNT }, () => '')
}

function toPlayerLabel(player: BalancePlayerOption, showMmr: boolean): string {
  const mmrText =
    showMmr && typeof player.currentMmr === 'number'
      ? ` - ${player.currentMmr} MMR`
      : ''
  return `${player.nickname} (${player.race})${mmrText}${player.tier ? ` [${player.tier}]` : ''}`
}

function sanitizePersistedTeamSize(teamSize: unknown): SupportedTeamSize {
  return teamSize === 2 ? 2 : 3
}

function sanitizePersistedSlots(slots: unknown): Array<number | null> {
  if (!Array.isArray(slots)) {
    return createEmptySlots()
  }

  const normalized = slots
    .slice(0, MAX_SLOT_COUNT)
    .map((value) => (typeof value === 'number' && Number.isFinite(value) ? value : null))

  while (normalized.length < MAX_SLOT_COUNT) {
    normalized.push(null)
  }

  return normalized
}

function sanitizePersistedSlotInputs(slotInputs: unknown): string[] {
  if (!Array.isArray(slotInputs)) {
    return createEmptySlotInputs()
  }

  const normalized = slotInputs
    .slice(0, MAX_SLOT_COUNT)
    .map((value) => (typeof value === 'string' ? value : ''))

  while (normalized.length < MAX_SLOT_COUNT) {
    normalized.push('')
  }

  return normalized
}

function readPersistedBalanceState(): PersistedBalanceState | null {
  if (typeof window === 'undefined') {
    return null
  }

  try {
    const raw = window.localStorage.getItem(BALANCE_STATE_STORAGE_KEY)
    if (!raw) {
      return null
    }

    const parsed = JSON.parse(raw) as Record<string, unknown>
    return {
      teamSize: sanitizePersistedTeamSize(parsed.teamSize),
      raceComposition: normalizeRaceComposition(
        sanitizePersistedTeamSize(parsed.teamSize),
        typeof parsed.raceComposition === 'string' ? parsed.raceComposition : null
      ),
      slots: sanitizePersistedSlots(parsed.slots),
      slotInputs: sanitizePersistedSlotInputs(parsed.slotInputs),
      result:
        parsed.result !== null &&
        typeof parsed.result === 'object' &&
        parsed.result !== undefined
          ? (parsed.result as BalanceResponse)
          : null,
      resultMatchId: typeof parsed.resultMatchId === 'string' ? parsed.resultMatchId : '',
      winnerTeam:
        parsed.winnerTeam === 'HOME' || parsed.winnerTeam === 'AWAY'
          ? parsed.winnerTeam
          : '',
    }
  } catch {
    return null
  }
}

export default function BalancePage() {
  const router = useRouter()
  const { isAdmin, isSuperAdmin, isLoggedIn } = useAdminAuth()
  const { mmrVisible } = useMmrVisibility()
  const showMmr = isAdmin && mmrVisible
  const [players, setPlayers] = useState<BalancePlayerOption[]>([])
  const [teamSize, setTeamSize] = useState<SupportedTeamSize>(3)
  const [raceComposition, setRaceComposition] = useState<RaceComposition | null>(null)
  const [slots, setSlots] = useState<Array<number | null>>(createEmptySlots)
  const [slotInputs, setSlotInputs] = useState<string[]>(createEmptySlotInputs)
  const [playersLoading, setPlayersLoading] = useState<boolean>(true)
  const [playersError, setPlayersError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState<boolean>(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [result, setResult] = useState<BalanceResponse | null>(null)
  const [resultMatchId, setResultMatchId] = useState<string>('')
  const [resultWinnerTeam, setResultWinnerTeam] = useState<WinnerTeamSelection>('')
  const [resultSubmitting, setResultSubmitting] = useState<boolean>(false)
  const [resultSubmitError, setResultSubmitError] = useState<string | null>(null)
  const [resultSubmitSuccess, setResultSubmitSuccess] = useState<MatchResultResponse | null>(null)
  const [matchCreateMessage, setMatchCreateMessage] = useState<string | null>(null)
  const [persistedReady, setPersistedReady] = useState<boolean>(false)
  const slotInputRefs = useRef<Array<HTMLInputElement | null>>([])

  const requiredPlayerCount = teamSize * 2

  useEffect(() => {
    let active = true

    const loadPlayers = async () => {
      setPlayersLoading(true)
      setPlayersError(null)

      try {
        const apiPlayers = await apiClient.getGroupPlayers(TEMP_GROUP_ID)

        if (!active) {
          return
        }

        const nextPlayers = apiPlayers
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

        setPlayers(nextPlayers)
      } catch {
        if (!active) {
          return
        }

        setPlayersError(t('balance.loadError'))
        setPlayers([])
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
    if (playersLoading || players.length === 0) {
      return
    }

    const validPlayerIds = new Set(players.map((player) => player.id))
    setSlots((prev) => prev.map((id) => (id !== null && validPlayerIds.has(id) ? id : null)))
  }, [players, playersLoading])

  useEffect(() => {
    const persisted = readPersistedBalanceState()
    if (persisted) {
      setSlots(persisted.slots)
      setSlotInputs(persisted.slotInputs)
      setRaceComposition(persisted.raceComposition)
      setResult(persisted.result)
      setResultMatchId(persisted.resultMatchId)
      setResultWinnerTeam(persisted.winnerTeam)
    }
    setPersistedReady(true)
  }, [])

  useEffect(() => {
    if (!persistedReady || typeof window === 'undefined') {
      return
    }

    const payload: PersistedBalanceState = {
      teamSize,
      raceComposition,
      slots,
      slotInputs,
      result,
      resultMatchId,
      winnerTeam: resultWinnerTeam,
    }

    try {
      window.localStorage.setItem(BALANCE_STATE_STORAGE_KEY, JSON.stringify(payload))
    } catch {
      return
    }
  }, [teamSize, raceComposition, slots, slotInputs, result, resultMatchId, resultWinnerTeam, persistedReady])

  const activeSlotIndexes = useMemo(
    () => Array.from({ length: requiredPlayerCount }, (_, index) => index),
    [requiredPlayerCount]
  )

  const selectedPlayers = useMemo(
    () =>
      slots
        .slice(0, requiredPlayerCount)
        .map((selectedId) => players.find((player) => player.id === selectedId))
        .filter((player): player is BalancePlayerOption => player !== undefined),
    [players, requiredPlayerCount, slots]
  )

  const selectedIds = useMemo(
    () => new Set(slots.slice(0, requiredPlayerCount).filter((value): value is number => value !== null)),
    [requiredPlayerCount, slots]
  )

  const allSelected = selectedPlayers.length === requiredPlayerCount
  const hasDuplicates = selectedIds.size !== selectedPlayers.length
  const canSubmit =
    !playersLoading &&
    !submitting &&
    players.length > 0 &&
    allSelected &&
    !hasDuplicates &&
    raceComposition !== null
  const hasGeneratedMatchId = Number.isFinite(Number(resultMatchId)) && Number(resultMatchId) > 0
  const canCreateMatchFromResult = result !== null
  const canSubmitQuickResult =
    isLoggedIn &&
    (hasGeneratedMatchId || canCreateMatchFromResult) &&
    (resultWinnerTeam === 'HOME' || resultWinnerTeam === 'AWAY') &&
    !resultSubmitting
  const protectedMmrStyle: CSSProperties | undefined = showMmr
    ? {
        WebkitTouchCallout: 'none',
        WebkitUserSelect: 'none',
        userSelect: 'none',
      }
    : undefined
  const handleProtectedClipboard = (event: ReactClipboardEvent<HTMLElement>) => {
    if (!showMmr) {
      return
    }
    event.preventDefault()
  }
  const handleProtectedContextMenu = (event: ReactMouseEvent<HTMLElement>) => {
    if (!showMmr) {
      return
    }
    event.preventDefault()
  }
  const handleProtectedDragStart = (event: ReactDragEvent<HTMLElement>) => {
    if (!showMmr) {
      return
    }
    event.preventDefault()
  }
  const handleProtectedKeyDown = (event: ReactKeyboardEvent<HTMLElement>) => {
    if (!showMmr) {
      return
    }
    if ((event.ctrlKey || event.metaKey) && ['c', 'x'].includes(event.key.toLowerCase())) {
      event.preventDefault()
    }
  }

  const totalSelectedMmr = selectedPlayers.reduce(
    (sum, player) => sum + (typeof player.currentMmr === 'number' ? player.currentMmr : 0),
    0
  )
  const clearSelectionAndResult = (nextTeamSize: SupportedTeamSize | null = null) => {
    if (nextTeamSize !== null) {
      setTeamSize(nextTeamSize)
    }
    setRaceComposition(null)
    setSlots(createEmptySlots())
    setSlotInputs(createEmptySlotInputs())
    setSubmitError(null)
    setResult(null)
    setResultMatchId('')
    setResultWinnerTeam('')
    setMatchCreateMessage(null)
    setResultSubmitError(null)
    setResultSubmitSuccess(null)
  }

  const handleTeamSizeChange = (nextTeamSize: SupportedTeamSize) => {
    if (nextTeamSize === teamSize) {
      return
    }
    clearSelectionAndResult(nextTeamSize)
  }

  const handleRaceCompositionChange = (value: string) => {
    setRaceComposition(normalizeRaceComposition(teamSize, value))
    setResult(null)
    setResultMatchId('')
    setResultWinnerTeam('')
    setMatchCreateMessage(null)
    setResultSubmitError(null)
    setResultSubmitSuccess(null)
  }

  const handleSlotInputChange = (index: number, value: string) => {
    const input = value.trim()
    const normalizedInput = input.toLowerCase()

    const matchedPlayer =
      input.length === 0
        ? null
        : players.find((player) => player.nickname.toLowerCase() === normalizedInput) ??
          players.find((player) => toPlayerLabel(player, showMmr).toLowerCase() === normalizedInput) ??
          players.find((player) => toPlayerLabel(player, true).toLowerCase() === normalizedInput) ??
          null

    setSlots((prev) => {
      const next = [...prev]
      next[index] = matchedPlayer?.id ?? null
      return next
    })
    setSlotInputs((prev) => {
      const next = [...prev]
      next[index] = value
      return next
    })
    setSubmitError(null)
  }

  const handleSlotAutocomplete = (index: number): boolean => {
    const matchedPlayer = findUniquePlayerByNicknamePrefix(players, slotInputs[index] ?? '')
    if (!matchedPlayer) {
      return false
    }

    setSlots((prev) => {
      const next = [...prev]
      next[index] = matchedPlayer.id
      return next
    })
    setSlotInputs((prev) => {
      const next = [...prev]
      next[index] = matchedPlayer.nickname
      return next
    })
    setSubmitError(null)

    window.requestAnimationFrame(() => {
      const nextInput = slotInputRefs.current[index + 1]
      if (!nextInput) {
        return
      }
      nextInput.focus()
      nextInput.select()
    })

    return true
  }

  const handleGenerate = async () => {
    setSubmitError(null)
    setResult(null)
    setResultMatchId('')
    setResultWinnerTeam('')
    setMatchCreateMessage(null)
    setResultSubmitError(null)
    setResultSubmitSuccess(null)

    if (!allSelected || hasDuplicates) {
      setSubmitError(t('balance.validation.needExact', { count: requiredPlayerCount }))
      return
    }
    if (!raceComposition) {
      setSubmitError(t('balance.validation.raceCompositionRequired'))
      return
    }

    setSubmitting(true)
    try {
      const selectedPlayerIds = selectedPlayers.map((player) => player.id)
      const response = await apiClient.balanceMatch({
        groupId: TEMP_GROUP_ID,
        playerIds: selectedPlayerIds,
        teamSize,
        raceComposition,
      })
      setResult(response)
    } catch (error) {
      if (error instanceof Error && error.message.trim().length > 0) {
        setSubmitError(error.message)
      } else {
        setSubmitError(t('balance.validation.generateFailed'))
      }
    } finally {
      setSubmitting(false)
    }
  }

  const createMatchFromResult = async (balanceResult: BalanceResponse): Promise<number | null> => {
    const homePlayerIds = balanceResult.homeTeam
      .map((player) => player.playerId)
      .filter((playerId): playerId is number => typeof playerId === 'number' && Number.isFinite(playerId))
    const awayPlayerIds = balanceResult.awayTeam
      .map((player) => player.playerId)
      .filter((playerId): playerId is number => typeof playerId === 'number' && Number.isFinite(playerId))

    if (homePlayerIds.length !== balanceResult.teamSize || awayPlayerIds.length !== balanceResult.teamSize) {
      setResultMatchId('')
      setMatchCreateMessage(t('balance.quickResult.matchCreateMissingPlayers'))
      return null
    }
    if (!raceComposition) {
      setResultMatchId('')
      setMatchCreateMessage(t('balance.validation.raceCompositionRequired'))
      return null
    }

    try {
      const created = await apiClient.createGroupMatch(TEMP_GROUP_ID, {
        homePlayerIds,
        awayPlayerIds,
        teamSize: balanceResult.teamSize,
        raceComposition,
      })

      const confirmationStatus = created.confirmationStatus
      if (confirmationStatus === 'DUPLICATE_REJECTED') {
        setResultMatchId('')
        setMatchCreateMessage(
          created.message && created.message.trim().length > 0
            ? created.message
            : t('balance.quickResult.matchDuplicateRejected')
        )
        return null
      }

      if (
        (confirmationStatus === 'CREATED' || confirmationStatus === 'REUSED_EXISTING') &&
        typeof created.matchId === 'number' &&
        Number.isFinite(created.matchId) &&
        created.matchId > 0
      ) {
        setResultMatchId(String(created.matchId))
        setMatchCreateMessage(
          confirmationStatus === 'REUSED_EXISTING'
            ? isSuperAdmin
              ? t('balance.quickResult.matchReused', { matchId: created.matchId })
              : t('balance.quickResult.matchReusedGeneric')
            : isSuperAdmin
              ? t('balance.quickResult.matchCreated', { matchId: created.matchId })
              : t('balance.quickResult.matchCreatedGeneric')
        )
        return created.matchId
      }

      setResultMatchId('')
      setMatchCreateMessage(t('balance.quickResult.matchCreateFailed'))
      return null
    } catch (createError) {
      setResultMatchId('')
      if (isApiForbiddenError(createError)) {
        setMatchCreateMessage(t('balance.quickResult.matchCreateForbidden'))
      } else {
        const readableMessage = resolveReadableApiErrorMessage(createError)
        setMatchCreateMessage(readableMessage ?? t('balance.quickResult.matchCreateFailed'))
      }
      return null
    }
  }

  const handleSubmitResult = async () => {
    setResultSubmitError(null)
    setResultSubmitSuccess(null)
    setMatchCreateMessage(null)

    if (!isLoggedIn) {
      setResultSubmitError(t('common.adminLoginRequired'))
      return
    }

    if (!result) {
      setResultSubmitError(t('balance.quickResult.matchNotReady'))
      return
    }

    if (resultWinnerTeam !== 'HOME' && resultWinnerTeam !== 'AWAY') {
      setResultSubmitError(t('balance.quickResult.winnerRequired'))
      return
    }
    if (!raceComposition) {
      setResultSubmitError(t('balance.validation.raceCompositionRequired'))
      return
    }

    setResultSubmitting(true)
    try {
      let parsedMatchId = Number(resultMatchId)
      if (!Number.isFinite(parsedMatchId) || parsedMatchId <= 0) {
        const createdMatchId = await createMatchFromResult(result)
        if (!createdMatchId) {
          return
        }
        parsedMatchId = createdMatchId
      }

      const response = await apiClient.submitMatchResult(parsedMatchId, {
        winnerTeam: resultWinnerTeam,
      })
      setResultSubmitSuccess(response)
      if (isSuperAdmin) {
        router.push(
          `/results?matchId=${response.matchId}&winnerTeam=${response.winnerTeam}&from=balance`
        )
      } else {
        router.push('/results?from=balance')
      }
    } catch (error) {
      if (isApiConflictError(error)) {
        setResultSubmitError(t('balance.quickResult.submitConflict'))
      } else if (isApiUnauthorizedError(error)) {
        setResultSubmitError(t('common.adminLoginRequired'))
      } else if (isApiForbiddenError(error)) {
        setResultSubmitError(t('common.permissionDenied'))
      } else {
        setResultSubmitError(t('results.form.submitFailure'))
      }
    } finally {
      setResultSubmitting(false)
    }
  }

  return (
    <section className="space-y-6">
      <header className="space-y-2 rounded-xl border border-slate-200 bg-white px-5 py-4 shadow-sm">
        <h2 className="text-2xl font-semibold tracking-tight">{t('balance.title')}</h2>
        <p className="text-sm text-slate-600">{t('balance.description')}</p>
        <p className="text-xs text-slate-500">
          {teamSize === 2 ? t('balance.mode.helperTwoVsTwo') : t('balance.mode.helperThreeVsThree')}
        </p>
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
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-semibold text-slate-900">
                {t('balance.selection.title', { count: requiredPlayerCount })}
              </h3>
              <button
                type="button"
                onClick={() => clearSelectionAndResult()}
                className="rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-600 transition-colors hover:bg-slate-50 hover:text-slate-900"
              >
                {t('balance.selection.reset')}
              </button>
            </div>

            <div className="space-y-2">
              <p className="text-xs font-semibold text-slate-700">{t('balance.mode.title')}</p>
              <div className="grid gap-2 sm:grid-cols-2">
                {teamSizeOptions.map((option) => {
                  const selected = teamSize === option.value
                  return (
                    <button
                      key={option.value}
                      type="button"
                      onClick={() => handleTeamSizeChange(option.value)}
                      className={`rounded-lg border px-3 py-2 text-left transition-colors ${
                        selected
                          ? 'border-indigo-500 bg-indigo-50 text-indigo-900'
                          : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50'
                      }`}
                    >
                      <span className="text-sm font-semibold">{t(option.labelKey)}</span>
                    </button>
                  )
                })}
              </div>
            </div>

            <label className="space-y-1 text-xs font-medium text-slate-500">
              {t('balance.raceComposition.label')}
              <select
                value={raceComposition ?? ''}
                onChange={(event) => handleRaceCompositionChange(event.target.value)}
                className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
              >
                <option value="">{t('balance.raceComposition.placeholder')}</option>
                {getRaceCompositionOptions(teamSize).map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>
            </label>
          </div>

          {playersLoading ? (
            <LoadingIndicator className="mt-4" label={t('common.loading')} />
          ) : (
            <div className="mt-4 grid gap-3 sm:grid-cols-2">
              {activeSlotIndexes.map((index) => {
                const activeSlots = slots.slice(0, requiredPlayerCount)
                const unavailableIds = new Set(
                  activeSlots.filter(
                    (slotId, slotIndex): slotId is number => slotId !== null && slotIndex !== index
                  )
                )
                const selectedId = slots[index]
                const inputMatchedPlayer =
                  selectedId === null
                    ? null
                    : players.find((player) => player.id === selectedId) ?? null
                const isDuplicateSelection =
                  inputMatchedPlayer !== null && unavailableIds.has(inputMatchedPlayer.id)

                return (
                  <label
                    key={`slot-${index}`}
                    className="space-y-1 text-xs font-medium text-slate-500"
                  >
                    {t('balance.selection.slot', { index: index + 1 })}
                    <input
                      ref={(element) => {
                        slotInputRefs.current[index] = element
                      }}
                      value={slotInputs[index]}
                      onChange={(event) => handleSlotInputChange(index, event.target.value)}
                      onKeyDown={(event) => {
                        if (event.key === 'Tab' && !event.shiftKey && handleSlotAutocomplete(index)) {
                          event.preventDefault()
                        }
                      }}
                      className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
                      placeholder={t('balance.selection.placeholder')}
                    />
                    {inputMatchedPlayer && (
                      <p className="text-[11px] text-slate-500">{toPlayerLabel(inputMatchedPlayer, showMmr)}</p>
                    )}
                    {isDuplicateSelection && (
                      <p className="text-[11px] text-rose-700">{t('balance.validation.duplicate')}</p>
                    )}
                  </label>
                )
              })}
            </div>
          )}
        </article>

        <article
          className={`rounded-xl border border-slate-200 bg-white p-4 shadow-sm ${
            showMmr ? 'select-none' : ''
          }`}
          onCopy={handleProtectedClipboard}
          onCut={handleProtectedClipboard}
          onContextMenu={handleProtectedContextMenu}
          onDragStart={handleProtectedDragStart}
          onKeyDown={handleProtectedKeyDown}
          style={protectedMmrStyle}
        >
          <h3 className="text-sm font-semibold text-slate-900">{t('balance.summary.title')}</h3>
          <p className="mt-1 text-xs text-slate-500">
            {t('balance.summary.selectedCount', {
              count: selectedPlayers.length,
              required: requiredPlayerCount,
            })}
          </p>

          <ul className="mt-3 space-y-2">
            {selectedPlayers.map((player) => (
              <li
                key={`summary-${player.id}`}
                className="flex items-center justify-between rounded-lg border border-slate-200 px-3 py-2 text-sm"
              >
                <span className="font-medium text-slate-800">
                  {player.nickname} ({player.race})
                </span>
                {showMmr && (
                  <span className="text-slate-600">
                    {typeof player.currentMmr === 'number' ? player.currentMmr : '-'}
                  </span>
                )}
              </li>
            ))}
            {selectedPlayers.length === 0 && (
              <li className="rounded-lg border border-dashed border-slate-200 px-3 py-6 text-center text-sm text-slate-500">
                {t('balance.summary.empty')}
              </li>
            )}
          </ul>

          {showMmr && (
            <div className="mt-4 rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700">
              {t('balance.summary.totalMmr')}: <span className="font-semibold">{totalSelectedMmr}</span>
            </div>
          )}

          <button
            type="button"
            onClick={handleGenerate}
            disabled={!canSubmit}
            className="mt-4 w-full rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300"
          >
            {submitting ? t('balance.summary.submitting') : t('balance.summary.submit')}
          </button>

          {!allSelected && !playersLoading && (
            <p className="mt-2 text-xs text-amber-700">
              {t('balance.validation.needExact', { count: requiredPlayerCount })}
            </p>
          )}
          {!playersLoading && players.length === 0 && (
            <p className="mt-2 text-xs text-rose-700">{t('balance.validation.noPlayers')}</p>
          )}
          {hasDuplicates && (
            <p className="mt-2 text-xs text-rose-700">{t('balance.validation.duplicate')}</p>
          )}
          {!raceComposition && (
            <p className="mt-2 text-xs text-rose-700">
              {t('balance.validation.raceCompositionRequired')}
            </p>
          )}
          {submitError && (
            <Alert variant="destructive" appearance="light" size="sm" className="mt-2">
              <AlertIcon icon="destructive">!</AlertIcon>
              <AlertContent>
                <AlertDescription>{submitError}</AlertDescription>
              </AlertContent>
            </Alert>
          )}
        </article>
      </div>

      {result && (
        <section className="grid gap-4 lg:grid-cols-2">
          <article
            className={`rounded-xl border border-slate-200 bg-white p-4 shadow-sm ${
              showMmr ? 'select-none' : ''
            }`}
            onCopy={handleProtectedClipboard}
            onCut={handleProtectedClipboard}
            onContextMenu={handleProtectedContextMenu}
            onDragStart={handleProtectedDragStart}
            onKeyDown={handleProtectedKeyDown}
            style={protectedMmrStyle}
          >
            <h3 className="text-sm font-semibold text-slate-900">{t('balance.result.homeTeam')}</h3>
            <ul className="mt-3 space-y-2">
              {result.homeTeam.map((player) => (
                <li
                  key={`home-${player.name}`}
                  className="flex items-center justify-between rounded-lg border border-slate-200 px-3 py-2 text-sm"
                >
                  <span className="font-medium text-slate-800">
                    {player.name}
                    {formatAssignedRace(player.assignedRace)}
                  </span>
                  {showMmr && (
                    <span className="text-slate-600">
                      {typeof player.mmr === 'number' ? `${player.mmr} MMR` : '-'}
                    </span>
                  )}
                </li>
              ))}
            </ul>
            {showMmr && (
              <p className="mt-3 text-sm text-slate-700">
                {t('balance.result.homeMmr')}:{' '}
                <span className="font-semibold">{result.homeMmr ?? '-'}</span>
              </p>
            )}
          </article>

          <article
            className={`rounded-xl border border-slate-200 bg-white p-4 shadow-sm ${
              showMmr ? 'select-none' : ''
            }`}
            onCopy={handleProtectedClipboard}
            onCut={handleProtectedClipboard}
            onContextMenu={handleProtectedContextMenu}
            onDragStart={handleProtectedDragStart}
            onKeyDown={handleProtectedKeyDown}
            style={protectedMmrStyle}
          >
            <h3 className="text-sm font-semibold text-slate-900">{t('balance.result.awayTeam')}</h3>
            <ul className="mt-3 space-y-2">
              {result.awayTeam.map((player) => (
                <li
                  key={`away-${player.name}`}
                  className="flex items-center justify-between rounded-lg border border-slate-200 px-3 py-2 text-sm"
                >
                  <span className="font-medium text-slate-800">
                    {player.name}
                    {formatAssignedRace(player.assignedRace)}
                  </span>
                  {showMmr && (
                    <span className="text-slate-600">
                      {typeof player.mmr === 'number' ? `${player.mmr} MMR` : '-'}
                    </span>
                  )}
                </li>
              ))}
            </ul>
            {showMmr && (
              <p className="mt-3 text-sm text-slate-700">
                {t('balance.result.awayMmr')}:{' '}
                <span className="font-semibold">{result.awayMmr ?? '-'}</span>
              </p>
            )}
          </article>

          <article
            className={`rounded-xl border border-slate-200 bg-white p-4 shadow-sm lg:col-span-2 ${
              showMmr ? 'select-none' : ''
            }`}
            onCopy={handleProtectedClipboard}
            onCut={handleProtectedClipboard}
            onContextMenu={handleProtectedContextMenu}
            onDragStart={handleProtectedDragStart}
            onKeyDown={handleProtectedKeyDown}
            style={protectedMmrStyle}
          >
            <h3 className="text-sm font-semibold text-slate-900">{t('balance.result.metricsTitle')}</h3>
            <div className={`mt-3 grid gap-3 ${showMmr ? 'sm:grid-cols-3' : 'sm:grid-cols-1'}`}>
              {showMmr && (
                <div className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700">
                  {t('balance.result.mmrDiff')}:{' '}
                  <span className="font-semibold">{result.mmrDiff ?? '-'}</span>
                </div>
              )}
              <div className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700">
                {t('balance.result.expectedHomeWinRate')}:{' '}
                <span className="font-semibold">
                  {typeof result.expectedHomeWinRate === 'number'
                    ? formatPercent(result.expectedHomeWinRate)
                    : '-'}
                </span>
              </div>
              {showMmr && (
                <div className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700">
                  {t('balance.result.averageTeamMmr')}:{' '}
                  <span className="font-semibold">
                    {typeof result.homeMmr === 'number' && typeof result.awayMmr === 'number'
                      ? Math.round((result.homeMmr + result.awayMmr) / 2)
                      : '-'}
                  </span>
                </div>
              )}
            </div>
          </article>
        </section>
      )}

      <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
        <h3 className="text-sm font-semibold text-slate-900">{t('balance.quickResult.title')}</h3>
        <p className="mt-1 text-xs text-slate-500">{t('balance.quickResult.description')}</p>
        <p className="mt-3 text-xs text-slate-600">
          {hasGeneratedMatchId
            ? isSuperAdmin
              ? t('balance.quickResult.autoMatchId', { matchId: resultMatchId })
              : t('balance.quickResult.autoMatchReady')
            : canCreateMatchFromResult
              ? t('balance.quickResult.matchWillBeCreatedOnSubmit')
              : t('balance.quickResult.matchNotReady')}
        </p>
        <div className="mt-3 grid gap-3 md:grid-cols-1">
          <label className="space-y-1 text-xs font-medium text-slate-500">
            {t('results.form.winnerTeam')}
            <select
              value={resultWinnerTeam}
              onChange={(event) => setResultWinnerTeam(event.target.value as WinnerTeamSelection)}
              className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
            >
              <option value="">{t('balance.quickResult.winnerPlaceholder')}</option>
              {winnerTeamOptions.map((team) => (
                <option key={team} value={team}>
                  {formatTeamLabel(team)}
                </option>
              ))}
            </select>
          </label>
        </div>

        <div className="mt-3 flex flex-wrap items-center gap-2">
          <button
            type="button"
            onClick={handleSubmitResult}
            disabled={!canSubmitQuickResult}
            className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300"
          >
            {resultSubmitting ? t('balance.quickResult.submitting') : t('balance.quickResult.submit')}
          </button>
        </div>

        {matchCreateMessage && (
          <p className="mt-3 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-700">
            {matchCreateMessage}
          </p>
        )}

        {resultSubmitError && (
          <Alert variant="destructive" appearance="light" size="sm" className="mt-3">
            <AlertIcon icon="destructive">!</AlertIcon>
            <AlertContent>
              <AlertDescription>{resultSubmitError}</AlertDescription>
            </AlertContent>
          </Alert>
        )}
        {resultSubmitSuccess && (
          <div className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs text-emerald-700">
            {isSuperAdmin
              ? t('balance.quickResult.success', { matchId: resultSubmitSuccess.matchId })
              : t('balance.quickResult.successGeneric')}
          </div>
        )}
      </article>
    </section>
  )
}
