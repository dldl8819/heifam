'use client'

import { useEffect, useMemo, useRef, useState } from 'react'
import { apiClient, isApiNotFoundError } from '@/lib/api'
import { useAdminAuth } from '@/lib/admin-auth'
import { TierParticipantBoard } from '@/components/tier-participant-board'
import { Alert, AlertContent, AlertDescription, AlertIcon, AlertTitle } from '@/components/ui/alert'
import { t } from '@/lib/i18n'
import { useMmrVisibility } from '@/lib/mmr-visibility'
import {
  autocompleteParticipantSlot,
  compactParticipantIds,
  createParticipantSlots,
  createParticipantSlotsFromIds,
  fillParticipantSlotLabels,
  type ParticipantSlotState,
  updateParticipantSlotInput,
} from '@/lib/participant-slots'
import type {
  CaptainDraftResponse,
  CaptainDraftTeam,
  PlayerRosterItem,
} from '@/types/api'

const TEMP_GROUP_ID = 1
const MINIMUM_PARTICIPANT_SLOTS = 8
const winnerTeamOptions = [
  { value: 'HOME' as const, label: 'captainDraft.entry.homeWin' },
  { value: 'AWAY' as const, label: 'captainDraft.entry.awayWin' },
]
type DraftWinnerSelection = '' | 'HOME' | 'AWAY'

function teamLabel(team: CaptainDraftTeam): string {
  if (team === 'HOME') {
    return t('captainDraft.teams.home')
  }
  if (team === 'AWAY') {
    return t('captainDraft.teams.away')
  }
  return t('captainDraft.teams.unassigned')
}

function normalizeSelectionValue(value: string): number | null {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
}

export default function CaptainDraftPage() {
  const { isAdmin } = useAdminAuth()
  const { mmrVisible } = useMmrVisibility()
  const showMmr = isAdmin && mmrVisible
  const [players, setPlayers] = useState<PlayerRosterItem[]>([])
  const [playersLoading, setPlayersLoading] = useState<boolean>(true)
  const [playersError, setPlayersError] = useState<string | null>(null)
  const [participantSlots, setParticipantSlots] = useState<ParticipantSlotState[]>(() =>
    createParticipantSlots(MINIMUM_PARTICIPANT_SLOTS),
  )
  const [homeCaptainId, setHomeCaptainId] = useState<number | null>(null)
  const [awayCaptainId, setAwayCaptainId] = useState<number | null>(null)
  const [actingCaptainId, setActingCaptainId] = useState<number | null>(null)
  const [setsPerRound, setSetsPerRound] = useState<number>(4)

  const [draft, setDraft] = useState<CaptainDraftResponse | null>(null)
  const [draftLoading, setDraftLoading] = useState<boolean>(true)

  const [selectedPickPlayerId, setSelectedPickPlayerId] = useState<number | null>(null)
  const [entrySelections, setEntrySelections] = useState<Record<string, number | ''>>({})
  const [entryWinnerSelections, setEntryWinnerSelections] = useState<Record<string, DraftWinnerSelection>>({})

  const [isCreatingDraft, setIsCreatingDraft] = useState<boolean>(false)
  const [isPicking, setIsPicking] = useState<boolean>(false)
  const [isSavingEntries, setIsSavingEntries] = useState<boolean>(false)

  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const participantInputRefs = useRef<Array<HTMLInputElement | null>>([])

  const playerMap = useMemo(() => {
    const map = new Map<number, PlayerRosterItem>()
    for (const player of players) {
      map.set(player.id, player)
    }
    return map
  }, [players])

  const selectedParticipantIds = useMemo(
    () => compactParticipantIds(participantSlots),
    [participantSlots],
  )

  const selectedParticipants = useMemo(
    () =>
      selectedParticipantIds
        .map((playerId) => playerMap.get(playerId))
        .filter((player): player is PlayerRosterItem => Boolean(player)),
    [playerMap, selectedParticipantIds],
  )

  const homeCaptainOptions = useMemo(
    () =>
      selectedParticipants.filter(
        (participant) => awayCaptainId == null || participant.id !== awayCaptainId,
      ),
    [awayCaptainId, selectedParticipants],
  )

  const awayCaptainOptions = useMemo(
    () =>
      selectedParticipants.filter(
        (participant) => homeCaptainId == null || participant.id !== homeCaptainId,
      ),
    [homeCaptainId, selectedParticipants],
  )

  const actingCaptainTeam: CaptainDraftTeam = useMemo(() => {
    if (!draft || !actingCaptainId) {
      return 'UNASSIGNED'
    }
    const participant = draft.participants.find((item) => item.playerId === actingCaptainId)
    return participant?.team ?? 'UNASSIGNED'
  }, [draft, actingCaptainId])

  const homeTeamParticipants = useMemo(
    () => (draft?.participants ?? []).filter((participant) => participant.team === 'HOME'),
    [draft],
  )

  const awayTeamParticipants = useMemo(
    () => (draft?.participants ?? []).filter((participant) => participant.team === 'AWAY'),
    [draft],
  )

  const unassignedParticipants = useMemo(
    () => (draft?.participants ?? []).filter((participant) => participant.team === 'UNASSIGNED'),
    [draft],
  )

  const availablePickParticipants = useMemo(
    () => unassignedParticipants.filter((participant) => !participant.captain),
    [unassignedParticipants],
  )

  const actingTeamParticipants = useMemo(
    () =>
      (draft?.participants ?? []).filter(
        (participant) => participant.team === actingCaptainTeam,
      ),
    [draft, actingCaptainTeam],
  )

  const canPick =
    Boolean(draft) &&
    draft?.status === 'DRAFTING' &&
    actingCaptainTeam !== 'UNASSIGNED' &&
    actingCaptainTeam === draft?.currentTurnTeam &&
    Boolean(selectedPickPlayerId) &&
    !isPicking

  const roundGroups = useMemo(() => {
    const groups = new Map<string, CaptainDraftResponse['entries']>()
    for (const entry of draft?.entries ?? []) {
      const key = `${entry.roundNumber}-${entry.roundCode}`
      if (!groups.has(key)) {
        groups.set(key, [])
      }
      groups.get(key)?.push(entry)
    }
    return Array.from(groups.entries()).map(([key, entries]) => ({
      key,
      roundNumber: entries[0]?.roundNumber ?? 0,
      roundCode: entries[0]?.roundCode ?? '',
      entries: entries.sort((a, b) => a.setNumber - b.setNumber),
    }))
  }, [draft])

  const homePlayerSetWins = useMemo(() => {
    const wins = new Map<number, number>()
    for (const entry of draft?.entries ?? []) {
      const key = `${entry.roundNumber}-${entry.setNumber}`
      const winnerTeam = entryWinnerSelections[key] === ''
        ? entry.winnerTeam
        : entryWinnerSelections[key]
      if (winnerTeam === 'HOME' && typeof entry.homePlayerId === 'number') {
        wins.set(entry.homePlayerId, (wins.get(entry.homePlayerId) ?? 0) + 1)
      }
    }
    return wins
  }, [draft, entryWinnerSelections])

  const awayPlayerSetWins = useMemo(() => {
    const wins = new Map<number, number>()
    for (const entry of draft?.entries ?? []) {
      const key = `${entry.roundNumber}-${entry.setNumber}`
      const winnerTeam = entryWinnerSelections[key] === ''
        ? entry.winnerTeam
        : entryWinnerSelections[key]
      if (winnerTeam === 'AWAY' && typeof entry.awayPlayerId === 'number') {
        wins.set(entry.awayPlayerId, (wins.get(entry.awayPlayerId) ?? 0) + 1)
      }
    }
    return wins
  }, [draft, entryWinnerSelections])

  const homeTeamSetWins = useMemo(
    () =>
      (draft?.entries ?? []).filter((entry) => {
        const key = `${entry.roundNumber}-${entry.setNumber}`
        const winnerTeam = entryWinnerSelections[key] === ''
          ? entry.winnerTeam
          : entryWinnerSelections[key]
        return winnerTeam === 'HOME'
      }).length,
    [draft, entryWinnerSelections],
  )

  const awayTeamSetWins = useMemo(
    () =>
      (draft?.entries ?? []).filter((entry) => {
        const key = `${entry.roundNumber}-${entry.setNumber}`
        const winnerTeam = entryWinnerSelections[key] === ''
          ? entry.winnerTeam
          : entryWinnerSelections[key]
        return winnerTeam === 'AWAY'
      }).length,
    [draft, entryWinnerSelections],
  )

  const compactTeamRows = useMemo(() => {
    const rowCount = Math.max(homeTeamParticipants.length, awayTeamParticipants.length)
    return Array.from({ length: rowCount }, (_, index) => ({
      home: homeTeamParticipants[index] ?? null,
      away: awayTeamParticipants[index] ?? null,
    }))
  }, [awayTeamParticipants, homeTeamParticipants])

  useEffect(() => {
    let mounted = true

    const loadPlayers = async () => {
      setPlayersLoading(true)
      setPlayersError(null)
      try {
        const response = await apiClient.getGroupPlayers(TEMP_GROUP_ID)
        if (!mounted) {
          return
        }
        setPlayers(response)
      } catch {
        if (!mounted) {
          return
        }
        setPlayers([])
        setPlayersError(t('captainDraft.validation.loadFailed'))
      } finally {
        if (mounted) {
          setPlayersLoading(false)
        }
      }
    }

    const loadLatestDraft = async () => {
      setDraftLoading(true)
      try {
        const response = await apiClient.getLatestCaptainDraft(TEMP_GROUP_ID)
        if (!mounted) {
          return
        }
        setDraft(response)
        setParticipantSlots(
          createParticipantSlotsFromIds(
            response.participants.map((participant) => ({
              id: participant.playerId,
              nickname: participant.nickname,
              race: participant.race,
            })),
            response.participants.map((participant) => participant.playerId),
            MINIMUM_PARTICIPANT_SLOTS,
          ),
        )
        setHomeCaptainId(response.homeCaptainPlayerId)
        setAwayCaptainId(response.awayCaptainPlayerId)
        setActingCaptainId((current) => current ?? response.homeCaptainPlayerId)
        setSetsPerRound(response.setsPerRound)
      } catch (latestDraftError) {
        if (!mounted) {
          return
        }
        if (!isApiNotFoundError(latestDraftError)) {
          setError(t('captainDraft.validation.loadFailed'))
        }
      } finally {
        if (mounted) {
          setDraftLoading(false)
        }
      }
    }

    void loadPlayers()
    void loadLatestDraft()

    return () => {
      mounted = false
    }
  }, [])

  useEffect(() => {
    if (players.length === 0) {
      return
    }

    setParticipantSlots((previous) =>
      fillParticipantSlotLabels(previous, players, MINIMUM_PARTICIPANT_SLOTS),
    )
  }, [players])

  useEffect(() => {
    if (!draft?.draftId) {
      return
    }

    const intervalId = window.setInterval(async () => {
      try {
        const refreshed = await apiClient.getCaptainDraft(TEMP_GROUP_ID, draft.draftId)
        setDraft(refreshed)
      } catch {
        // Keep current snapshot on polling failure.
      }
    }, 3000)

    return () => window.clearInterval(intervalId)
  }, [draft?.draftId])

  useEffect(() => {
    if (!draft || actingCaptainTeam === 'UNASSIGNED') {
      return
    }

    const nextSelections: Record<string, number | ''> = {}
    const nextWinnerSelections: Record<string, DraftWinnerSelection> = {}
    for (const entry of draft.entries) {
      const key = `${entry.roundNumber}-${entry.setNumber}`
      nextSelections[key] =
        actingCaptainTeam === 'HOME'
          ? (entry.homePlayerId ?? '')
          : (entry.awayPlayerId ?? '')
      nextWinnerSelections[key] =
        entry.winnerTeam === 'HOME' || entry.winnerTeam === 'AWAY'
          ? entry.winnerTeam
          : ''
    }
    setEntrySelections(nextSelections)
    setEntryWinnerSelections(nextWinnerSelections)
  }, [draft, actingCaptainTeam])

  useEffect(() => {
    if (draft) {
      return
    }
    const participantCount = selectedParticipantIds.length
    if (participantCount < 8) {
      setSetsPerRound(4)
      return
    }
    const calculatedSets = Math.min(
      8,
      4 + Math.max(0, Math.floor((participantCount - 8) / 4)),
    )
    setSetsPerRound(calculatedSets)
  }, [selectedParticipantIds.length, draft])

  useEffect(() => {
    if (homeCaptainId != null && awayCaptainId != null && homeCaptainId === awayCaptainId) {
      setAwayCaptainId(null)
    }
  }, [homeCaptainId, awayCaptainId])

  useEffect(() => {
    if (homeCaptainId != null && !selectedParticipantIds.includes(homeCaptainId)) {
      setHomeCaptainId(null)
    }
    if (awayCaptainId != null && !selectedParticipantIds.includes(awayCaptainId)) {
      setAwayCaptainId(null)
    }
    if (actingCaptainId != null && !selectedParticipantIds.includes(actingCaptainId)) {
      setActingCaptainId(null)
    }
  }, [actingCaptainId, awayCaptainId, homeCaptainId, selectedParticipantIds])

  const handleParticipantSlotInputChange = (index: number, value: string) => {
    setMessage(null)
    setError(null)
    setParticipantSlots((previous) =>
      updateParticipantSlotInput({
        slots: previous,
        index,
        inputValue: value,
        players,
        showMmr,
        minimumSlots: MINIMUM_PARTICIPANT_SLOTS,
      }),
    )
  }

  const handleParticipantSlotAutocomplete = (index: number): boolean => {
    const nextSlots = autocompleteParticipantSlot({
      slots: participantSlots,
      index,
      players,
      minimumSlots: MINIMUM_PARTICIPANT_SLOTS,
    })
    if (!nextSlots) {
      return false
    }

    setMessage(null)
    setError(null)
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

  const handleHomeCaptainChange = (value: string) => {
    setMessage(null)
    setError(null)
    setHomeCaptainId(normalizeSelectionValue(value))
  }

  const handleAwayCaptainChange = (value: string) => {
    setMessage(null)
    setError(null)
    setAwayCaptainId(normalizeSelectionValue(value))
  }

  const handleCreateDraft = async () => {
    setMessage(null)
    setError(null)

    if (selectedParticipantIds.length < 8) {
      setError(t('captainDraft.validation.needMinimumEight'))
      return
    }
    if (selectedParticipantIds.length % 2 !== 0) {
      setError(t('captainDraft.validation.needEvenCount'))
      return
    }
    if (!homeCaptainId || !awayCaptainId || homeCaptainId === awayCaptainId) {
      setError(t('captainDraft.validation.needTwoCaptains'))
      return
    }
    if (
      !selectedParticipantIds.includes(homeCaptainId) ||
      !selectedParticipantIds.includes(awayCaptainId)
    ) {
      setError(t('captainDraft.validation.captainMustBeParticipant'))
      return
    }

    setIsCreatingDraft(true)
    try {
      const created = await apiClient.createCaptainDraft(TEMP_GROUP_ID, {
        title: '토요 정기 감전',
        participantPlayerIds: selectedParticipantIds,
        captainPlayerIds: [homeCaptainId, awayCaptainId],
        setsPerRound,
      })
      setDraft(created)
      setActingCaptainId(created.homeCaptainPlayerId)
      setMessage(t('captainDraft.messages.createSuccess'))
    } catch (createError) {
      if (createError instanceof Error && createError.message.trim().length > 0) {
        setError(`${t('captainDraft.validation.createFailed')} (${createError.message})`)
      } else {
        setError(t('captainDraft.validation.createFailed'))
      }
    } finally {
      setIsCreatingDraft(false)
    }
  }

  const handleResetDraftSetup = () => {
    setParticipantSlots(createParticipantSlots(MINIMUM_PARTICIPANT_SLOTS))
    setHomeCaptainId(null)
    setAwayCaptainId(null)
    setActingCaptainId(null)
    setSetsPerRound(4)
    setDraft(null)
    setDraftLoading(false)
    setSelectedPickPlayerId(null)
    setEntrySelections({})
    setEntryWinnerSelections({})
    setError(null)
    setMessage(null)
  }

  const handleRefreshDraft = async () => {
    setError(null)
    setMessage(null)
    setDraftLoading(true)
    try {
      const refreshed = await apiClient.getLatestCaptainDraft(TEMP_GROUP_ID)
      setDraft(refreshed)
      setParticipantSlots(
        createParticipantSlotsFromIds(
          refreshed.participants.map((participant) => ({
            id: participant.playerId,
            nickname: participant.nickname,
            race: participant.race,
          })),
          refreshed.participants.map((participant) => participant.playerId),
          MINIMUM_PARTICIPANT_SLOTS,
        ),
      )
      setHomeCaptainId(refreshed.homeCaptainPlayerId)
      setAwayCaptainId(refreshed.awayCaptainPlayerId)
      setActingCaptainId(refreshed.homeCaptainPlayerId)
      setSelectedPickPlayerId(null)
      setSetsPerRound(refreshed.setsPerRound)
      setEntrySelections({})
      setEntryWinnerSelections({})
    } catch (refreshError) {
      if (isApiNotFoundError(refreshError)) {
        setDraft(null)
        setParticipantSlots(createParticipantSlots(MINIMUM_PARTICIPANT_SLOTS))
        setHomeCaptainId(null)
        setAwayCaptainId(null)
        setActingCaptainId(null)
        setSelectedPickPlayerId(null)
        setEntrySelections({})
        setEntryWinnerSelections({})
        setSetsPerRound(4)
      } else {
        setError(t('captainDraft.validation.loadFailed'))
      }
    } finally {
      setDraftLoading(false)
    }
  }

  const handlePickPlayer = async () => {
    setMessage(null)
    setError(null)

    if (!draft?.draftId) {
      setError(t('captainDraft.validation.selectDraftFirst'))
      return
    }
    if (!actingCaptainId) {
      setError(t('captainDraft.validation.selectActingCaptain'))
      return
    }
    if (!selectedPickPlayerId) {
      setError(t('captainDraft.validation.selectPlayerToPick'))
      return
    }
    if (draft.currentTurnTeam !== actingCaptainTeam) {
      setError(t('captainDraft.validation.notYourTurn'))
      return
    }

    setIsPicking(true)
    try {
      const next = await apiClient.pickCaptainDraftPlayer(TEMP_GROUP_ID, draft.draftId, {
        captainPlayerId: actingCaptainId,
        pickedPlayerId: selectedPickPlayerId,
      })
      setDraft(next)
      setSelectedPickPlayerId(null)
    } catch (pickError) {
      if (pickError instanceof Error && pickError.message.trim().length > 0) {
        setError(`${t('captainDraft.validation.pickFailed')} (${pickError.message})`)
      } else {
        setError(t('captainDraft.validation.pickFailed'))
      }
    } finally {
      setIsPicking(false)
    }
  }

  const handleEntrySelectionChange = (key: string, playerId: number | null) => {
    setEntrySelections((previous) => ({
      ...previous,
      [key]: playerId ?? '',
    }))
  }

  const handleEntryWinnerChange = (key: string, winnerTeam: DraftWinnerSelection) => {
    setEntryWinnerSelections((previous) => ({
      ...previous,
      [key]: winnerTeam,
    }))
  }

  const handleSaveEntries = async () => {
    setMessage(null)
    setError(null)

    if (!draft?.draftId) {
      setError(t('captainDraft.validation.selectDraftFirst'))
      return
    }
    if (!actingCaptainId || actingCaptainTeam === 'UNASSIGNED') {
      setError(t('captainDraft.validation.selectActingCaptain'))
      return
    }

    const payloadEntries = draft.entries.map((entry) => {
      const key = `${entry.roundNumber}-${entry.setNumber}`
      const selected = entrySelections[key]
      return {
        roundNumber: entry.roundNumber,
        setNumber: entry.setNumber,
        playerId: typeof selected === 'number' ? selected : null,
        winnerTeam: entryWinnerSelections[key] === '' ? null : entryWinnerSelections[key],
      }
    })

    setIsSavingEntries(true)
    try {
      const next = await apiClient.updateCaptainDraftEntries(TEMP_GROUP_ID, draft.draftId, {
        captainPlayerId: actingCaptainId,
        entries: payloadEntries,
      })
      setDraft(next)
      setMessage(t('captainDraft.messages.entriesSaved'))
    } catch (saveError) {
      if (saveError instanceof Error && saveError.message.trim().length > 0) {
        setError(`${t('captainDraft.validation.saveEntryFailed')} (${saveError.message})`)
      } else {
        setError(t('captainDraft.validation.saveEntryFailed'))
      }
    } finally {
      setIsSavingEntries(false)
    }
  }

  return (
    <section className="space-y-6">
      <header className="space-y-1 rounded-xl border border-slate-200 bg-white px-5 py-4 shadow-sm">
        <h2 className="text-2xl font-semibold tracking-tight">{t('captainDraft.title')}</h2>
        <p className="text-sm text-slate-600">{t('captainDraft.description')}</p>
      </header>

      {(playersError || error) && (
        <Alert variant="destructive" appearance="light">
          <AlertIcon icon="destructive">!</AlertIcon>
          <AlertContent>
            <AlertTitle>{t('common.errorPrefix')}</AlertTitle>
            <AlertDescription>{playersError ?? error}</AlertDescription>
          </AlertContent>
        </Alert>
      )}
      {message && (
        <div className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
          {message}
        </div>
      )}

      <div className="grid gap-4 xl:grid-cols-3">
        <div className="xl:col-span-2">
          <TierParticipantBoard
            title={t('captainDraft.attendance.title')}
            helper={t('captainDraft.attendance.helper')}
            players={players}
            slots={participantSlots}
            showMmr={showMmr}
            loading={playersLoading}
            selectedCountLabel={t('captainDraft.attendance.selectedCount', { count: selectedParticipantIds.length })}
            emptyMessage={t('captainDraft.attendance.empty')}
            duplicateMessage={t('balance.validation.duplicate')}
            resetLabel={t('captainDraft.actions.reset')}
            inputRefs={participantInputRefs}
            onReset={handleResetDraftSetup}
            onSlotInputChange={handleParticipantSlotInputChange}
            onSlotAutocomplete={handleParticipantSlotAutocomplete}
          />
        </div>

        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <h3 className="text-sm font-semibold text-slate-900">{t('captainDraft.captain.title')}</h3>

          <div className="mt-3 space-y-2">
            <label className="block text-xs font-medium text-slate-500">
              {t('captainDraft.captain.home')}
              <select
                value={homeCaptainId ?? ''}
                onChange={(event) => handleHomeCaptainChange(event.target.value)}
                className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
              >
                <option value="">-</option>
                {homeCaptainOptions.map((participant) => (
                  <option key={`home-cap-${participant.id}`} value={participant.id}>
                    {participant.nickname} ({participant.race})
                  </option>
                ))}
              </select>
            </label>

            <label className="block text-xs font-medium text-slate-500">
              {t('captainDraft.captain.away')}
              <select
                value={awayCaptainId ?? ''}
                onChange={(event) => handleAwayCaptainChange(event.target.value)}
                className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
              >
                <option value="">-</option>
                {awayCaptainOptions.map((participant) => (
                  <option key={`away-cap-${participant.id}`} value={participant.id}>
                    {participant.nickname} ({participant.race})
                  </option>
                ))}
              </select>
            </label>
          </div>

          <div className="mt-4">
            <h4 className="text-xs font-semibold text-slate-700">{t('captainDraft.settings.title')}</h4>
            <label className="mt-2 block text-xs font-medium text-slate-500">
              {t('captainDraft.settings.setsPerRound')}
              <input
                type="number"
                min={1}
                max={8}
                value={setsPerRound}
                onChange={(event) => {
                  const value = Number(event.target.value)
                  if (!Number.isFinite(value)) {
                    return
                  }
                  setSetsPerRound(Math.max(1, Math.min(8, value)))
                }}
                className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
              />
            </label>
            <p className="mt-1 text-xs text-slate-500">{t('captainDraft.settings.helper')}</p>
          </div>

          <div className="mt-4 flex gap-2">
            <button
              type="button"
              onClick={handleResetDraftSetup}
              className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50"
            >
              {t('captainDraft.actions.reset')}
            </button>
            <button
              type="button"
              onClick={handleCreateDraft}
              disabled={isCreatingDraft}
              className="flex-1 rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300"
            >
              {isCreatingDraft ? t('captainDraft.actions.creating') : t('captainDraft.actions.create')}
            </button>
            <button
              type="button"
              onClick={handleRefreshDraft}
              disabled={draftLoading}
              className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-100 disabled:cursor-not-allowed disabled:border-slate-200 disabled:text-slate-400"
            >
              {t('captainDraft.actions.refresh')}
            </button>
          </div>

          {draft && (
            <div className="mt-4 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-700">
              <p>
                {t('captainDraft.status.label')}:{' '}
                {draft.status === 'READY'
                  ? t('captainDraft.status.ready')
                  : t('captainDraft.status.drafting')}
              </p>
              <p className="mt-1">
                {t('captainDraft.status.turn')}:{' '}
                {draft.currentTurnTeam === 'UNASSIGNED'
                  ? t('captainDraft.status.none')
                  : teamLabel(draft.currentTurnTeam)}
              </p>
            </div>
          )}
        </article>
      </div>

      {draft && (
        <>
          <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <div className="flex flex-wrap items-end justify-between gap-3">
              <div>
                <h3 className="text-sm font-semibold text-slate-900">{t('captainDraft.captain.acting')}</h3>
                <p className="mt-1 text-xs text-slate-500">{t('captainDraft.captain.actingHelper')}</p>
              </div>
              <select
                value={actingCaptainId ?? ''}
                onChange={(event) => setActingCaptainId(normalizeSelectionValue(event.target.value))}
                className="w-full max-w-sm rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
              >
                <option value="">-</option>
                <option value={draft.homeCaptainPlayerId}>
                  {draft.homeCaptainNickname} ({t('captainDraft.teams.home')})
                </option>
                <option value={draft.awayCaptainPlayerId}>
                  {draft.awayCaptainNickname} ({t('captainDraft.teams.away')})
                </option>
              </select>
            </div>
          </article>

          <div className="grid gap-4 xl:grid-cols-3">
            <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm xl:col-span-2">
              <h3 className="text-sm font-semibold text-slate-900">{t('captainDraft.pick.title')}</h3>
              <p className="mt-1 text-xs text-slate-500">{t('captainDraft.pick.helper')}</p>

              <div className="mt-3 grid gap-3 sm:grid-cols-2">
                <label className="block text-xs font-medium text-slate-500">
                  {t('captainDraft.pick.turnTeam')}
                  <input
                    type="text"
                    value={teamLabel(draft.currentTurnTeam)}
                    disabled
                    className="mt-1 w-full rounded-lg border border-slate-200 bg-slate-100 px-3 py-2 text-sm text-slate-700"
                  />
                </label>
                <label className="block text-xs font-medium text-slate-500">
                  {t('captainDraft.pick.selectPlayer')}
                  <select
                    value={selectedPickPlayerId ?? ''}
                    onChange={(event) =>
                      setSelectedPickPlayerId(normalizeSelectionValue(event.target.value))
                    }
                    className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
                  >
                    <option value="">-</option>
                    {availablePickParticipants.map((participant) => (
                      <option key={`pick-${participant.playerId}`} value={participant.playerId}>
                        {participant.nickname} ({participant.race})
                      </option>
                    ))}
                  </select>
                </label>
              </div>

              <div className="mt-3">
                <button
                  type="button"
                  onClick={handlePickPlayer}
                  disabled={!canPick}
                  className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300"
                >
                  {isPicking ? t('captainDraft.actions.picking') : t('captainDraft.actions.pick')}
                </button>
              </div>
            </article>

            <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <h3 className="text-sm font-semibold text-slate-900">{t('captainDraft.pickLog.title')}</h3>
              <div className="mt-3 space-y-2">
                {draft.picks.map((pick) => (
                  <div
                    key={`pick-log-${pick.pickOrder}`}
                    className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700"
                  >
                    <p className="font-medium">#{pick.pickOrder}</p>
                    <p className="mt-1 text-xs">
                      {pick.captainNickname} -&gt; {pick.pickedPlayerNickname} ({teamLabel(pick.team)})
                    </p>
                  </div>
                ))}
                {draft.picks.length === 0 && (
                  <div className="rounded-lg border border-dashed border-slate-200 px-3 py-6 text-center text-xs text-slate-500">
                    {t('captainDraft.pickLog.empty')}
                  </div>
                )}
              </div>
            </article>
          </div>

          <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <h3 className="text-sm font-semibold text-slate-900">{t('captainDraft.scoreboard.title')}</h3>
            <p className="mt-1 text-xs text-slate-500">{t('captainDraft.scoreboard.helper')}</p>

            <div className="mt-4 overflow-x-auto rounded-xl border border-slate-300 bg-white">
              <table className="min-w-[520px] table-fixed border-collapse text-sm">
                <thead>
                  <tr className="bg-white">
                    <th className="border border-slate-400 px-3 py-2 text-center text-lg font-semibold text-sky-700">
                      {t('captainDraft.scoreboard.homeHeader')}
                    </th>
                    <th className="w-16 border border-slate-400 px-2 py-2 text-center text-xs font-semibold text-slate-500">
                      {t('captainDraft.scoreboard.setWinsShort')}
                    </th>
                    <th className="border border-slate-400 px-3 py-2 text-center text-lg font-semibold text-rose-700">
                      {t('captainDraft.scoreboard.awayHeader')}
                    </th>
                    <th className="w-16 border border-slate-400 px-2 py-2 text-center text-xs font-semibold text-slate-500">
                      {t('captainDraft.scoreboard.setWinsShort')}
                    </th>
                  </tr>
                  <tr className="bg-slate-50">
                    <th className="border border-slate-400 px-3 py-3 text-center text-4xl font-bold text-slate-900">
                      {homeTeamSetWins}
                    </th>
                    <th className="border border-slate-400 px-2 py-3 text-center text-xs font-semibold text-slate-500">
                      &nbsp;
                    </th>
                    <th className="border border-slate-400 px-3 py-3 text-center text-4xl font-bold text-slate-900">
                      {awayTeamSetWins}
                    </th>
                    <th className="border border-slate-400 px-2 py-3 text-center text-xs font-semibold text-slate-500">
                      &nbsp;
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {compactTeamRows.map((row, index) => (
                    <tr key={`scoreboard-row-${index}`} className="bg-white">
                      <td className="border border-slate-300 px-3 py-2 text-center font-medium text-slate-800">
                        {row.home ? (
                          <span>
                            {row.home.nickname}
                            {row.home.captain ? ` ${t('captainDraft.teams.captainBadge')}` : ''}
                          </span>
                        ) : (
                          <span className="text-slate-400">-</span>
                        )}
                      </td>
                      <td className="border border-slate-300 px-2 py-2 text-center font-semibold text-slate-700">
                        {row.home ? (homePlayerSetWins.get(row.home.playerId) ?? 0) : 0}
                      </td>
                      <td className="border border-slate-300 px-3 py-2 text-center font-medium text-slate-800">
                        {row.away ? (
                          <span>
                            {row.away.nickname}
                            {row.away.captain ? ` ${t('captainDraft.teams.captainBadge')}` : ''}
                          </span>
                        ) : (
                          <span className="text-slate-400">-</span>
                        )}
                      </td>
                      <td className="border border-slate-300 px-2 py-2 text-center font-semibold text-slate-700">
                        {row.away ? (awayPlayerSetWins.get(row.away.playerId) ?? 0) : 0}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {unassignedParticipants.length > 0 && (
              <div className="mt-4 rounded-xl border border-slate-200 bg-slate-50 px-4 py-3">
                <h4 className="text-sm font-semibold text-slate-900">{t('captainDraft.teams.unassigned')}</h4>
                <div className="mt-2 flex flex-wrap gap-2">
                  {unassignedParticipants.map((participant) => (
                    <span
                      key={`unassigned-chip-${participant.playerId}`}
                      className="rounded-md border border-slate-200 bg-white px-2 py-1 text-xs text-slate-700"
                    >
                      {participant.nickname} ({participant.race})
                    </span>
                  ))}
                </div>
              </div>
            )}
          </article>

          <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <h3 className="text-sm font-semibold text-slate-900">{t('captainDraft.entry.title')}</h3>
            <p className="mt-1 text-xs text-slate-500">{t('captainDraft.entry.helper')}</p>

            <div className="mt-4 space-y-4">
              <div className="overflow-x-auto rounded-xl border border-slate-300 bg-white">
                <table className="min-w-[980px] table-fixed border-collapse text-sm">
                  <thead>
                    <tr className="bg-[#f4eadf] text-slate-900">
                      <th className="w-24 border border-slate-400 px-3 py-2 text-center font-semibold">
                        {t('captainDraft.entry.roundLabel')}
                      </th>
                      <th className="w-24 border border-slate-400 px-3 py-2 text-center font-semibold">
                        {t('captainDraft.entry.raceLabel')}
                      </th>
                      <th className="w-24 border border-slate-400 px-3 py-2 text-center font-semibold">
                        {t('captainDraft.entry.requiredTierLabel')}
                      </th>
                      <th className="w-56 border border-slate-400 px-3 py-2 text-center font-semibold text-sky-700">
                        {t('captainDraft.entry.homeSheetLabel')}
                      </th>
                      <th className="w-56 border border-slate-400 px-3 py-2 text-center font-semibold text-rose-700">
                        {t('captainDraft.entry.awaySheetLabel')}
                      </th>
                      <th className="w-24 border border-slate-400 px-3 py-2 text-center font-semibold">
                        {t('captainDraft.entry.resultLabel')}
                      </th>
                      <th className="w-24 border border-slate-400 px-3 py-2 text-center font-semibold">
                        {t('captainDraft.entry.finalWinLabel')}
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {roundGroups.map((roundGroup, roundIndex) =>
                      roundGroup.entries.map((entry, entryIndex) => {
                        const key = `${entry.roundNumber}-${entry.setNumber}`
                        const selectedPlayerId = entrySelections[key]
                        const selectedWinnerTeam = entryWinnerSelections[key] ?? ''
                        const dividerClass =
                          roundIndex > 0 && entryIndex === 0
                            ? 'border-t-[3px] border-t-slate-800'
                            : ''
                        const roundHomeWins = roundGroup.entries.filter(
                          (roundEntry) => {
                            const roundKey = `${roundEntry.roundNumber}-${roundEntry.setNumber}`
                            const winnerTeam = entryWinnerSelections[roundKey] === ''
                              ? roundEntry.winnerTeam
                              : entryWinnerSelections[roundKey]
                            return winnerTeam === 'HOME'
                          },
                        ).length
                        const roundAwayWins = roundGroup.entries.filter(
                          (roundEntry) => {
                            const roundKey = `${roundEntry.roundNumber}-${roundEntry.setNumber}`
                            const winnerTeam = entryWinnerSelections[roundKey] === ''
                              ? roundEntry.winnerTeam
                              : entryWinnerSelections[roundKey]
                            return winnerTeam === 'AWAY'
                          },
                        ).length

                        return (
                          <tr
                            key={`entry-${key}`}
                            className={`bg-white ${dividerClass}`.trim()}
                          >
                            {entryIndex === 0 && (
                              <th
                                rowSpan={roundGroup.entries.length}
                                className={`border border-slate-400 bg-white px-3 py-2 text-center align-middle text-base font-semibold text-slate-900 ${dividerClass}`.trim()}
                              >
                                {t('captainDraft.entry.roundOnly', { round: roundGroup.roundNumber })}
                              </th>
                            )}
                            <td className={`border border-slate-300 bg-slate-50 px-3 py-2 text-center font-medium text-slate-800 ${dividerClass}`.trim()}>
                              {entry.roundCode}
                            </td>
                            <td className={`border border-slate-300 px-3 py-2 text-center text-slate-500 ${dividerClass}`.trim()}>
                              &nbsp;
                            </td>
                            <td className={`border border-slate-300 px-2 py-1 ${dividerClass}`.trim()}>
                              {actingCaptainTeam === 'HOME' ? (
                                <select
                                  value={selectedPlayerId === '' ? '' : selectedPlayerId ?? ''}
                                  onChange={(event) =>
                                    handleEntrySelectionChange(
                                      key,
                                      normalizeSelectionValue(event.target.value),
                                    )
                                  }
                                  className="w-full border-0 bg-transparent px-2 py-2 text-sm font-medium text-sky-700 outline-none focus:bg-sky-50"
                                >
                                  <option value="">{t('captainDraft.entry.notSet')}</option>
                                  {actingTeamParticipants.map((participant) => (
                                    <option
                                      key={`home-entry-${key}-${participant.playerId}`}
                                      value={participant.playerId}
                                    >
                                      {participant.nickname}
                                    </option>
                                  ))}
                                </select>
                              ) : (
                                <div className="px-2 py-2 text-sm font-medium text-sky-700">
                                  {entry.homePlayerNickname ?? t('captainDraft.entry.notSet')}
                                </div>
                              )}
                            </td>
                            <td className={`border border-slate-300 px-2 py-1 ${dividerClass}`.trim()}>
                              {actingCaptainTeam === 'AWAY' ? (
                                <select
                                  value={selectedPlayerId === '' ? '' : selectedPlayerId ?? ''}
                                  onChange={(event) =>
                                    handleEntrySelectionChange(
                                      key,
                                      normalizeSelectionValue(event.target.value),
                                    )
                                  }
                                  className="w-full border-0 bg-transparent px-2 py-2 text-sm font-medium text-rose-700 outline-none focus:bg-rose-50"
                                >
                                  <option value="">{t('captainDraft.entry.notSet')}</option>
                                  {actingTeamParticipants.map((participant) => (
                                    <option
                                      key={`away-entry-${key}-${participant.playerId}`}
                                      value={participant.playerId}
                                    >
                                      {participant.nickname}
                                    </option>
                                  ))}
                                </select>
                              ) : (
                                <div className="px-2 py-2 text-sm font-medium text-rose-700">
                                  {entry.awayPlayerNickname ?? t('captainDraft.entry.notSet')}
                                </div>
                              )}
                            </td>
                            <td className={`border border-slate-300 px-2 py-1 text-center ${dividerClass}`.trim()}>
                              <select
                                value={selectedWinnerTeam}
                                onChange={(event) =>
                                  handleEntryWinnerChange(
                                    key,
                                    event.target.value as DraftWinnerSelection,
                                  )
                                }
                                className="w-full border-0 bg-transparent px-2 py-2 text-sm font-medium text-slate-700 outline-none focus:bg-slate-50"
                              >
                                <option value="">{t('captainDraft.entry.notSet')}</option>
                                {winnerTeamOptions.map((option) => (
                                  <option key={`${key}-${option.value}`} value={option.value}>
                                    {t(option.label)}
                                  </option>
                                ))}
                              </select>
                            </td>
                            <td className={`border border-slate-300 px-3 py-2 text-center text-slate-700 ${dividerClass}`.trim()}>
                              {entryIndex === 0
                                ? `${roundHomeWins} : ${roundAwayWins}`
                                : ''}
                            </td>
                          </tr>
                        )
                      }),
                    )}
                  </tbody>
                </table>
              </div>

              <div className="flex justify-end">
                <button
                  type="button"
                  onClick={handleSaveEntries}
                  disabled={isSavingEntries || !actingCaptainId || actingCaptainTeam === 'UNASSIGNED'}
                  className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300"
                >
                  {isSavingEntries
                    ? t('captainDraft.actions.savingEntries')
                    : t('captainDraft.actions.saveEntries')}
                </button>
              </div>
            </div>
          </article>
        </>
      )}

      {!draftLoading && !draft && (
        <article className="rounded-xl border border-dashed border-slate-300 bg-white px-4 py-10 text-center text-sm text-slate-600">
          {t('captainDraft.emptyDraft')}
        </article>
      )}
    </section>
  )
}
