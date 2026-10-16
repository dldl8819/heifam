'use client'

import { useEffect, useMemo, useRef, useState } from 'react'
import { apiClient, isApiNotFoundError } from '@/lib/api'
import { useAdminAuth } from '@/lib/admin-auth'
import { TierParticipantBoard } from '@/components/tier-participant-board'
import { CaptainDraftTeamBoard } from '@/components/captain-draft-team-board'
import { Alert, AlertContent, AlertDescription, AlertIcon, AlertTitle } from '@/components/ui/alert'
import { t } from '@/lib/i18n'
import { useMmrVisibility } from '@/lib/mmr-visibility'
import {
  countSetWins,
  draftPlayerOptionLabel,
  draftPlayerTags,
  dropEditsNaming,
  effectiveEntry,
  type DraftWinnerSelection,
  type EntryEdit,
  type EntryEdits,
} from '@/lib/captain-draft'
import { startVisiblePolling } from '@/lib/visible-polling'
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
  CaptainDraftParticipant,
  CaptainDraftResponse,
  CaptainDraftTeam,
  PlayerRosterItem,
  TeamSide,
} from '@/types/api'

const TEMP_GROUP_ID = 1
const MINIMUM_PARTICIPANT_SLOTS = 8
const winnerTeamOptions = [
  { value: 'HOME' as const, label: 'captainDraft.entry.homeWin' },
  { value: 'AWAY' as const, label: 'captainDraft.entry.awayWin' },
]

type IdentifiedCaptainDraftParticipant = CaptainDraftParticipant & { playerId: number }

function hasPlayerId(
  participant: CaptainDraftParticipant,
): participant is IdentifiedCaptainDraftParticipant {
  return typeof participant.playerId === 'number' && Number.isFinite(participant.playerId)
}

function createParticipantSlotsFromDraft(draft: CaptainDraftResponse): ParticipantSlotState[] {
  const participants = draft.participants.filter(hasPlayerId)
  return createParticipantSlotsFromIds(
    participants.map((participant) => ({
      id: participant.playerId,
      nickname: participant.nickname,
      race: participant.race,
    })),
    participants.map((participant) => participant.playerId),
    MINIMUM_PARTICIPANT_SLOTS,
  )
}

function teamLabel(team: CaptainDraftTeam): string {
  if (team === 'HOME') {
    return t('captainDraft.scoreboard.homeHeader')
  }
  if (team === 'AWAY') {
    return t('captainDraft.scoreboard.awayHeader')
  }
  return t('captainDraft.teams.unassigned')
}

function normalizeSelectionValue(value: string): number | null {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
}

function errorWithReason(key: string, caught: unknown): string {
  return caught instanceof Error && caught.message.trim().length > 0 ? `${t(key)} (${caught.message})` : t(key)
}

/**
 * The regular draft (정기 감전), run by the host: attendance and two captains, then the players each
 * captain calls out are put into their team, in any order, and both teams' round entries are set.
 */
export default function CaptainDraftPage() {
  const { canViewMmr } = useAdminAuth()
  const { mmrVisible } = useMmrVisibility()
  const showMmr = canViewMmr && mmrVisible
  const [players, setPlayers] = useState<PlayerRosterItem[]>([])
  const [playersLoading, setPlayersLoading] = useState<boolean>(true)
  const [playersError, setPlayersError] = useState<string | null>(null)
  const [participantSlots, setParticipantSlots] = useState<ParticipantSlotState[]>(() =>
    createParticipantSlots(MINIMUM_PARTICIPANT_SLOTS),
  )
  const [homeCaptainId, setHomeCaptainId] = useState<number | null>(null)
  const [awayCaptainId, setAwayCaptainId] = useState<number | null>(null)
  const [setsPerRound, setSetsPerRound] = useState<number>(4)

  const [draft, setDraft] = useState<CaptainDraftResponse | null>(null)
  const [draftLoading, setDraftLoading] = useState<boolean>(true)

  const [entryEdits, setEntryEdits] = useState<EntryEdits>({})

  const [isCreatingDraft, setIsCreatingDraft] = useState<boolean>(false)
  const [isChangingTeams, setIsChangingTeams] = useState<boolean>(false)
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

  const teamMembers = useMemo(() => {
    const members: Record<TeamSide, IdentifiedCaptainDraftParticipant[]> = { HOME: [], AWAY: [] }
    for (const participant of (draft?.participants ?? []).filter(hasPlayerId)) {
      if (participant.team === 'HOME' || participant.team === 'AWAY') {
        members[participant.team].push(participant)
      }
    }
    return members
  }, [draft])

  // The sheet as shown: saved sets with the host's unsaved changes laid over them.
  const sheet = useMemo(
    () => (draft?.entries ?? []).map((entry) => effectiveEntry(entry, entryEdits[`${entry.roundNumber}-${entry.setNumber}`])),
    [draft, entryEdits],
  )
  const setWins = useMemo(() => countSetWins(sheet), [sheet])
  const unsavedCount = Object.keys(entryEdits).length

  const roundGroups = useMemo(() => {
    const groups = new Map<string, { roundNumber: number; roundCode: string; keys: string[] }>()
    for (const entry of draft?.entries ?? []) {
      const groupKey = `${entry.roundNumber}-${entry.roundCode}`
      const group = groups.get(groupKey) ?? { roundNumber: entry.roundNumber, roundCode: entry.roundCode, keys: [] }
      group.keys.push(`${entry.roundNumber}-${entry.setNumber}`)
      groups.set(groupKey, group)
    }
    return Array.from(groups.values())
  }, [draft])

  const sheetByKey = useMemo(() => new Map(sheet.map((entry) => [entry.key, entry])), [sheet])

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
        setParticipantSlots(createParticipantSlotsFromDraft(response))
        setHomeCaptainId(response.homeCaptainPlayerId)
        setAwayCaptainId(response.awayCaptainPlayerId)
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

    const draftId = draft.draftId
    return startVisiblePolling(async () => {
      try {
        const refreshed = await apiClient.getCaptainDraft(TEMP_GROUP_ID, draftId)
        setDraft(refreshed)
      } catch {
        // Keep current snapshot on polling failure.
      }
    }, 3000)
  }, [draft?.draftId])

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
  }, [awayCaptainId, homeCaptainId, selectedParticipantIds])

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
      setEntryEdits({})
      setMessage(t('captainDraft.messages.createSuccess'))
    } catch (createError) {
      setError(errorWithReason('captainDraft.validation.createFailed', createError))
    } finally {
      setIsCreatingDraft(false)
    }
  }

  const handleResetDraftSetup = () => {
    setParticipantSlots(createParticipantSlots(MINIMUM_PARTICIPANT_SLOTS))
    setHomeCaptainId(null)
    setAwayCaptainId(null)
    setSetsPerRound(4)
    setDraft(null)
    setDraftLoading(false)
    setEntryEdits({})
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
      setParticipantSlots(createParticipantSlotsFromDraft(refreshed))
      setHomeCaptainId(refreshed.homeCaptainPlayerId)
      setAwayCaptainId(refreshed.awayCaptainPlayerId)
      setSetsPerRound(refreshed.setsPerRound)
      setEntryEdits({})
    } catch (refreshError) {
      if (isApiNotFoundError(refreshError)) {
        setDraft(null)
        setParticipantSlots(createParticipantSlots(MINIMUM_PARTICIPANT_SLOTS))
        setHomeCaptainId(null)
        setAwayCaptainId(null)
        setEntryEdits({})
        setSetsPerRound(4)
      } else {
        setError(t('captainDraft.validation.loadFailed'))
      }
    } finally {
      setDraftLoading(false)
    }
  }

  // The host puts the player a captain called out into that captain's team.
  const handlePick = async (team: TeamSide, playerId: number) => {
    const captainPlayerId = team === 'HOME' ? draft?.homeCaptainPlayerId : draft?.awayCaptainPlayerId
    if (!draft?.draftId || typeof captainPlayerId !== 'number') {
      setError(t('captainDraft.validation.selectDraftFirst'))
      return
    }
    setMessage(null)
    setError(null)
    setIsChangingTeams(true)
    try {
      setDraft(await apiClient.pickCaptainDraftPlayer(TEMP_GROUP_ID, draft.draftId, { captainPlayerId, pickedPlayerId: playerId }))
    } catch (pickError) {
      setError(errorWithReason('captainDraft.validation.pickFailed', pickError))
    } finally {
      setIsChangingTeams(false)
    }
  }

  // A pick made by mistake goes back to the remaining players, out of any entry that named them.
  const handleUnpick = async (playerId: number) => {
    if (!draft?.draftId) {
      return
    }
    setMessage(null)
    setError(null)
    setIsChangingTeams(true)
    try {
      setDraft(await apiClient.unpickCaptainDraftPlayer(TEMP_GROUP_ID, draft.draftId, { playerId }))
      setEntryEdits((previous) => dropEditsNaming(previous, playerId))
    } catch (unpickError) {
      setError(errorWithReason('captainDraft.validation.unpickFailed', unpickError))
    } finally {
      setIsChangingTeams(false)
    }
  }

  const editEntry = (key: string, change: EntryEdit) => {
    setEntryEdits((previous) => ({ ...previous, [key]: { ...previous[key], ...change } }))
  }

  const handleSaveEntries = async () => {
    setMessage(null)
    setError(null)

    if (!draft?.draftId) {
      setError(t('captainDraft.validation.selectDraftFirst'))
      return
    }

    setIsSavingEntries(true)
    try {
      const next = await apiClient.updateCaptainDraftEntries(TEMP_GROUP_ID, draft.draftId, {
        entries: draft.entries.map((entry) => {
          const shown = sheetByKey.get(`${entry.roundNumber}-${entry.setNumber}`)
          return {
            roundNumber: entry.roundNumber,
            setNumber: entry.setNumber,
            homePlayerId: shown?.homePlayerId ?? null,
            awayPlayerId: shown?.awayPlayerId ?? null,
            winnerTeam: shown?.winnerTeam ? shown.winnerTeam : null,
          }
        }),
      })
      setDraft(next)
      setEntryEdits({})
      setMessage(t('captainDraft.messages.entriesSaved'))
    } catch (saveError) {
      setError(errorWithReason('captainDraft.validation.saveEntryFailed', saveError))
    } finally {
      setIsSavingEntries(false)
    }
  }

  const playerOptions = (team: TeamSide, key: string) =>
    teamMembers[team].map((participant) => (
      <option key={`${team}-entry-${key}-${participant.playerId}`} value={participant.playerId}>
        {draftPlayerOptionLabel(participant.nickname, draftPlayerTags(playerMap.get(participant.playerId), participant.race))}
      </option>
    ))

  return (
    <section className="space-y-6">
      <header className="space-y-1 rounded-xl border border-slate-200 bg-white px-5 py-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
        <h2 className="text-2xl font-semibold tracking-tight">{t('captainDraft.title')}</h2>
        <p className="text-sm text-slate-600 dark:text-slate-300">{t('captainDraft.description')}</p>
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
        <div className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300">
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

        <div className="space-y-4">
          <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
            <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('captainDraft.captain.title')}</h3>

            <div className="mt-3 space-y-2">
              <label className="block text-xs font-medium text-slate-500 dark:text-slate-400">
                {t('captainDraft.captain.home')}
                <select
                  value={homeCaptainId ?? ''}
                  onChange={(event) => handleHomeCaptainChange(event.target.value)}
                  className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-slate-500 dark:focus:ring-slate-700"
                >
                  <option value="">-</option>
                  {homeCaptainOptions.map((participant) => (
                    <option key={`home-cap-${participant.id}`} value={participant.id}>
                      {participant.nickname} ({participant.race})
                    </option>
                  ))}
                </select>
              </label>

              <label className="block text-xs font-medium text-slate-500 dark:text-slate-400">
                {t('captainDraft.captain.away')}
                <select
                  value={awayCaptainId ?? ''}
                  onChange={(event) => handleAwayCaptainChange(event.target.value)}
                  className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-slate-500 dark:focus:ring-slate-700"
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
              <h4 className="text-xs font-semibold text-slate-700 dark:text-slate-300">{t('captainDraft.settings.title')}</h4>
              <label className="mt-2 block text-xs font-medium text-slate-500 dark:text-slate-400">
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
                  className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-slate-500 dark:focus:ring-slate-700"
                />
              </label>
              <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{t('captainDraft.settings.helper')}</p>
            </div>

            <div className="mt-4 flex gap-2">
              <button
                type="button"
                onClick={handleResetDraftSetup}
                className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
              >
                {t('captainDraft.actions.reset')}
              </button>
              <button
                type="button"
                onClick={handleCreateDraft}
                disabled={isCreatingDraft}
                className="flex-1 rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white dark:disabled:bg-slate-700 dark:disabled:text-slate-400"
              >
                {isCreatingDraft ? t('captainDraft.actions.creating') : t('captainDraft.actions.create')}
              </button>
              <button
                type="button"
                onClick={handleRefreshDraft}
                disabled={draftLoading}
                className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-100 disabled:cursor-not-allowed disabled:border-slate-200 disabled:text-slate-400 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800 dark:disabled:border-slate-700 dark:disabled:text-slate-500"
              >
                {t('captainDraft.actions.refresh')}
              </button>
            </div>

            {draft && (
              <p className="mt-4 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-700 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200">
                {t('captainDraft.status.label')}:{' '}
                {draft.status === 'READY' ? t('captainDraft.status.ready') : t('captainDraft.status.drafting')}
              </p>
            )}
          </article>

          {draft && (
            <CaptainDraftTeamBoard
              draft={draft}
              playerMap={playerMap}
              setWins={setWins}
              busy={isChangingTeams}
              onPick={(team, playerId) => void handlePick(team, playerId)}
              onUnpick={(playerId) => void handleUnpick(playerId)}
            />
          )}
        </div>
      </div>

      {draft && (
        <>
          <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
            <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('captainDraft.entry.title')}</h3>
            <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{t('captainDraft.entry.helper')}</p>

            <div className="mt-4 space-y-4">
              <div className="overflow-x-auto rounded-xl border border-slate-300 bg-white dark:border-slate-600 dark:bg-slate-950">
                <table className="min-w-[980px] table-fixed border-collapse text-sm">
                  <thead>
                    <tr className="bg-[#f4eadf] text-slate-900 dark:bg-[#5b4438] dark:text-slate-100">
                      <th className="w-24 border border-slate-400 px-3 py-2 text-center font-semibold dark:border-slate-600">
                        {t('captainDraft.entry.roundLabel')}
                      </th>
                      <th className="w-24 border border-slate-400 px-3 py-2 text-center font-semibold dark:border-slate-600">
                        {t('captainDraft.entry.raceLabel')}
                      </th>
                      <th className="w-24 border border-slate-400 px-3 py-2 text-center font-semibold dark:border-slate-600">
                        {t('captainDraft.entry.requiredTierLabel')}
                      </th>
                      <th className="w-56 border border-slate-400 px-3 py-2 text-center font-semibold text-sky-700 dark:border-slate-600 dark:text-sky-300">
                        {t('captainDraft.entry.homeSheetLabel')}
                      </th>
                      <th className="w-56 border border-slate-400 px-3 py-2 text-center font-semibold text-rose-700 dark:border-slate-600 dark:text-rose-300">
                        {t('captainDraft.entry.awaySheetLabel')}
                      </th>
                      <th className="w-24 border border-slate-400 px-3 py-2 text-center font-semibold dark:border-slate-600">
                        {t('captainDraft.entry.resultLabel')}
                      </th>
                      <th className="w-24 border border-slate-400 px-3 py-2 text-center font-semibold dark:border-slate-600">
                        {t('captainDraft.entry.finalWinLabel')}
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {roundGroups.map((roundGroup, roundIndex) => {
                      const roundEntries = roundGroup.keys
                        .map((key) => sheetByKey.get(key))
                        .filter((entry): entry is NonNullable<typeof entry> => Boolean(entry))
                      const roundHomeWins = roundEntries.filter((entry) => entry.winnerTeam === 'HOME').length
                      const roundAwayWins = roundEntries.filter((entry) => entry.winnerTeam === 'AWAY').length

                      return roundEntries.map((entry, entryIndex) => {
                        const dividerClass = roundIndex > 0 && entryIndex === 0 ? 'border-t-[3px] border-t-slate-800' : ''
                        return (
                          <tr key={`entry-${entry.key}`} className={`bg-white dark:bg-slate-900 ${dividerClass}`.trim()}>
                            {entryIndex === 0 && (
                              <th
                                rowSpan={roundEntries.length}
                                className={`border border-slate-400 bg-white px-3 py-2 text-center align-middle text-base font-semibold text-slate-900 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 ${dividerClass}`.trim()}
                              >
                                {t('captainDraft.entry.roundOnly', { round: roundGroup.roundNumber })}
                              </th>
                            )}
                            <td className={`border border-slate-300 bg-slate-50 px-3 py-2 text-center font-medium text-slate-800 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200 ${dividerClass}`.trim()}>
                              {roundGroup.roundCode}
                            </td>
                            <td className={`border border-slate-300 px-3 py-2 text-center text-slate-500 dark:border-slate-700 dark:text-slate-400 ${dividerClass}`.trim()}>
                              &nbsp;
                            </td>
                            <td className={`border border-slate-300 px-2 py-1 dark:border-slate-700 ${dividerClass}`.trim()}>
                              <select
                                value={entry.homePlayerId ?? ''}
                                onChange={(event) => editEntry(entry.key, { homePlayerId: normalizeSelectionValue(event.target.value) })}
                                aria-label={`${t('captainDraft.entry.homeSheetLabel')} ${entry.key}`}
                                className="w-full border-0 bg-transparent px-2 py-2 text-sm font-medium text-sky-700 outline-none focus:bg-sky-50 dark:text-sky-300 dark:focus:bg-sky-950/50"
                              >
                                <option value="">{t('captainDraft.entry.notSet')}</option>
                                {playerOptions('HOME', entry.key)}
                              </select>
                            </td>
                            <td className={`border border-slate-300 px-2 py-1 dark:border-slate-700 ${dividerClass}`.trim()}>
                              <select
                                value={entry.awayPlayerId ?? ''}
                                onChange={(event) => editEntry(entry.key, { awayPlayerId: normalizeSelectionValue(event.target.value) })}
                                aria-label={`${t('captainDraft.entry.awaySheetLabel')} ${entry.key}`}
                                className="w-full border-0 bg-transparent px-2 py-2 text-sm font-medium text-rose-700 outline-none focus:bg-rose-50 dark:text-rose-300 dark:focus:bg-rose-950/50"
                              >
                                <option value="">{t('captainDraft.entry.notSet')}</option>
                                {playerOptions('AWAY', entry.key)}
                              </select>
                            </td>
                            <td className={`border border-slate-300 px-2 py-1 text-center dark:border-slate-700 ${dividerClass}`.trim()}>
                              <select
                                value={entry.winnerTeam}
                                onChange={(event) => editEntry(entry.key, { winnerTeam: event.target.value as DraftWinnerSelection })}
                                aria-label={`${t('captainDraft.entry.resultLabel')} ${entry.key}`}
                                className="w-full border-0 bg-transparent px-2 py-2 text-sm font-medium text-slate-700 outline-none focus:bg-slate-50 dark:text-slate-200 dark:focus:bg-slate-800"
                              >
                                <option value="">{t('captainDraft.entry.notSet')}</option>
                                {winnerTeamOptions.map((option) => (
                                  <option key={`${entry.key}-${option.value}`} value={option.value}>
                                    {t(option.label)}
                                  </option>
                                ))}
                              </select>
                            </td>
                            <td className={`border border-slate-300 px-3 py-2 text-center text-slate-700 dark:border-slate-700 dark:text-slate-300 ${dividerClass}`.trim()}>
                              {entryIndex === 0 ? `${roundHomeWins} : ${roundAwayWins}` : ''}
                            </td>
                          </tr>
                        )
                      })
                    })}
                  </tbody>
                </table>
              </div>

              <div className="flex flex-wrap items-center justify-end gap-3">
                {unsavedCount > 0 && (
                  <span className="text-xs text-amber-700 dark:text-amber-300">
                    {t('captainDraft.entry.unsaved', { count: unsavedCount })}
                  </span>
                )}
                <button
                  type="button"
                  onClick={handleSaveEntries}
                  disabled={isSavingEntries}
                  className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white dark:disabled:bg-slate-700 dark:disabled:text-slate-400"
                >
                  {isSavingEntries ? t('captainDraft.actions.savingEntries') : t('captainDraft.actions.saveEntries')}
                </button>
              </div>
            </div>
          </article>

          <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
            <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('captainDraft.pickLog.title')}</h3>
            {draft.picks.length === 0 ? (
              <p className="mt-3 rounded-lg border border-dashed border-slate-200 px-3 py-6 text-center text-xs text-slate-500 dark:border-slate-700 dark:text-slate-400">
                {t('captainDraft.pickLog.empty')}
              </p>
            ) : (
              <ol className="mt-3 flex flex-wrap gap-2">
                {draft.picks.map((pick) => (
                  <li
                    key={`pick-log-${pick.pickOrder}`}
                    className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-1.5 text-xs text-slate-700 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200"
                  >
                    <span className="font-semibold">#{pick.pickOrder}</span> {pick.captainNickname} → {pick.pickedPlayerNickname} (
                    {teamLabel(pick.team)})
                  </li>
                ))}
              </ol>
            )}
          </article>
        </>
      )}

      {!draftLoading && !draft && (
        <article className="rounded-xl border border-dashed border-slate-300 bg-white px-4 py-10 text-center text-sm text-slate-600 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-300">
          {t('captainDraft.emptyDraft')}
        </article>
      )}
    </section>
  )
}
