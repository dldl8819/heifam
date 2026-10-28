'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { apiClient, isApiConflictError, isApiForbiddenError, isApiNotFoundError } from '@/lib/api'
import { useAdminAuth } from '@/lib/admin-auth'
import { TierParticipantBoard } from '@/components/tier-participant-board'
import { CaptainDraftEntrySheet } from '@/components/captain-draft-entry-sheet'
import { CaptainDraftGameCounts } from '@/components/captain-draft-game-counts'
import { CaptainDraftRecords } from '@/components/captain-draft-records'
import { CaptainDraftRoundSettings } from '@/components/captain-draft-round-settings'
import { CaptainDraftTeamBoard } from '@/components/captain-draft-team-board'
import { Alert, AlertContent, AlertDescription, AlertIcon, AlertTitle } from '@/components/ui/alert'
import { t } from '@/lib/i18n'
import { useMmrVisibility } from '@/lib/mmr-visibility'
import {
  DEFAULT_ROUND_COUNT,
  countSetWins,
  defaultDraftTitle,
  defaultSetSizes,
  draftLayout,
  draftSheet,
  dropEditsNaming,
  aceDeciderOffer,
  aceSizeChoices,
  dropLineupEdits,
  hasAnyLineup,
  hasLoneLineup,
  hasAnyResult,
  isRunByCaptains,
  lineupSubmission,
  repeatedSetGroups,
  roundComposition,
  setComposition,
  setupDiffersFromDraft,
  stageLineups,
  rosterRanking,
  stageOf,
  stagesWithLineupEdits,
  teamSubmitted,
  teamsByPlayer,
  type DraftViewer,
  type EffectiveEntry,
  type EntryEdit,
  type EntryEdits,
  type LineupEdits,
  type SetControls,
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
import { formatKstDateTime } from '@/lib/kst-time'
import type {
  CaptainDraftEntryMode,
  CaptainDraftLineupStage,
  CaptainDraftParticipant,
  CaptainDraftResponse,
  CaptainDraftSummary,
  PlayerRosterItem,
  TeamSide,
} from '@/types/api'

const TEAM_LABEL_KEYS: Record<TeamSide, string> = {
  HOME: 'captainDraft.scoreboard.homeHeader',
  AWAY: 'captainDraft.scoreboard.awayHeader',
}

const TEMP_GROUP_ID = 1
const MINIMUM_PARTICIPANT_SLOTS = 8

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

function normalizeSelectionValue(value: string): number | null {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
}

function errorWithReason(key: string, caught: unknown): string {
  return caught instanceof Error && caught.message.trim().length > 0 ? `${t(key)} (${caught.message})` : t(key)
}

/**
 * The regular draft (정기 감전), run by the host: attendance and two captains, then the players each
 * captain calls out are put into their team, in any order, and both teams' round entries are set,
 * by the host or, for a draft run by its captains, by each captain for their own team unseen by the
 * other until both are in.
 */
export default function CaptainDraftPage() {
  const { canViewMmr, isAdmin, isMatchResultEditor } = useAdminAuth()
  const { mmrVisible } = useMmrVisibility()
  const showMmr = canViewMmr && mmrVisible
  // Members run the draft; a result already in is changed only by admins and match result editors,
  // and a record removed only by admins. The backend holds to both; this only shapes the screen.
  const canEditResults = isAdmin || isMatchResultEditor
  const [players, setPlayers] = useState<PlayerRosterItem[]>([])
  const [playersLoading, setPlayersLoading] = useState<boolean>(true)
  const [playersError, setPlayersError] = useState<string | null>(null)
  const [participantSlots, setParticipantSlots] = useState<ParticipantSlotState[]>(() =>
    createParticipantSlots(MINIMUM_PARTICIPANT_SLOTS),
  )
  const [homeCaptainId, setHomeCaptainId] = useState<number | null>(null)
  const [awayCaptainId, setAwayCaptainId] = useState<number | null>(null)
  // Each set's team size in play order, and how many rounds, for the next draft started.
  const [setSizes, setSetSizes] = useState<number[]>([])
  const [roundCount, setRoundCount] = useState<number>(DEFAULT_ROUND_COUNT)
  const [entryMode, setEntryMode] = useState<CaptainDraftEntryMode>('HOST')

  const [draftTitle, setDraftTitle] = useState<string>(() => defaultDraftTitle())
  const [draft, setDraft] = useState<CaptainDraftResponse | null>(null)
  const [records, setRecords] = useState<CaptainDraftSummary[] | null>(null)
  const [recordsLoading, setRecordsLoading] = useState<boolean>(true)
  const [recordsError, setRecordsError] = useState<string | null>(null)
  const [deletingDraftId, setDeletingDraftId] = useState<number | null>(null)
  const [draftLoading, setDraftLoading] = useState<boolean>(true)

  const [entryEdits, setEntryEdits] = useState<EntryEdits>({})
  // A captain's line-up for their own team not handed in yet (a draft run by its captains).
  const [lineupEdits, setLineupEdits] = useState<LineupEdits>({})

  const [isCreatingDraft, setIsCreatingDraft] = useState<boolean>(false)
  const [isChangingTeams, setIsChangingTeams] = useState<boolean>(false)
  const [isSavingEntries, setIsSavingEntries] = useState<boolean>(false)
  const [submittingStage, setSubmittingStage] = useState<CaptainDraftLineupStage | null>(null)
  const [isChangingMode, setIsChangingMode] = useState<boolean>(false)
  const [aceSize, setAceSize] = useState<number>(1)
  const [isAddingAce, setIsAddingAce] = useState<boolean>(false)

  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const participantInputRefs = useRef<Array<HTMLInputElement | null>>([])
  // Which draft the screen is on. It moves on whenever the host resets the screen or another draft
  // is shown, and a reply sent for the screen before (a poll, a pick, a load) is then dropped:
  // otherwise a poll answered after 초기화 put the old teams and entries back.
  const draftViewRef = useRef<number>(0)
  const moveToNewView = () => {
    draftViewRef.current += 1
  }

  const playerMap = useMemo(() => {
    const map = new Map<number, PlayerRosterItem>()
    for (const player of players) {
      map.set(player.id, player)
    }
    return map
  }, [players])
  // The ranking order the team board and the game counts list players by.
  const ranking = useMemo(() => rosterRanking(players), [players])

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
  // The tier board colours each picked player by team once a draft is under way.
  const tierBoardTeams = useMemo(() => (draft ? teamsByPlayer(draft.participants) : undefined), [draft])

  // Who reads the sheet. The backend tells the reader's team from their account, and keeps a team's
  // line-up from everyone else until both are in; this only shapes the screen.
  const byCaptains = isRunByCaptains(draft)
  const viewer = useMemo<DraftViewer>(
    () => ({
      byCaptains,
      viewerTeam: draft?.viewerTeam ?? null,
      viewerCaptain: Boolean(draft?.viewerCaptain),
      canEditResults,
    }),
    [byCaptains, draft?.viewerTeam, draft?.viewerCaptain, canEditResults],
  )
  const lineups = useMemo(() => stageLineups(draft), [draft])
  // The team this viewer hands in: their own, when they captain it.
  const submitTeam = byCaptains && viewer.viewerCaptain ? viewer.viewerTeam : null
  const canTakeBack = !hasAnyResult(draft?.entries ?? []) && !hasAnyLineup(lineups)

  // The sheet as shown: saved sets with the changes made here laid over them, only those this viewer
  // may make. A set that got its result meanwhile (someone else saved it) shows as saved.
  const sheetRows = useMemo(
    () => draftSheet(draft?.entries ?? [], entryEdits, lineupEdits, lineups, viewer),
    [draft, entryEdits, lineupEdits, lineups, viewer],
  )
  const sheet = useMemo(() => sheetRows.map((row) => row.set), [sheetRows])
  const controls = useMemo(() => {
    const byKey = new Map<string, SetControls>()
    for (const row of sheetRows) {
      byKey.set(row.set.key, row.controls)
    }
    return byKey
  }, [sheetRows])
  const setWins = useMemo(() => countSetWins(sheet), [sheet])
  // Groups that play a set together again in a later round, team by team: said on screen, to ask
  // for groups that mix everyone.
  const repeatedGroups = useMemo(() => repeatedSetGroups(sheet), [sheet])
  const nicknameOf = useMemo(() => {
    const names = new Map<number, string>()
    for (const participant of draft?.participants ?? []) {
      if (participant.playerId !== null) {
        names.set(participant.playerId, participant.nickname)
      }
    }
    return names
  }, [draft])
  const unsavedCount = sheetRows.filter((row) => row.edited).length
  // Stages this captain has a line-up on screen for that is not handed in yet.
  const unsubmittedStages = useMemo(() => {
    if (!submitTeam) {
      return new Set<CaptainDraftLineupStage>()
    }
    const stages = stagesWithLineupEdits(lineupEdits, draft?.entries ?? [])
    return new Set([...stages].filter((stage) => !teamSubmitted(lineups.get(stage), submitTeam)))
  }, [submitTeam, lineupEdits, draft, lineups])
  // The rounds ended level with every set played: whoever runs the draft may play an ace decider.
  const aceOffer = useMemo(() => aceDeciderOffer(draft?.entries ?? []), [draft])

  // The rounds in order, each with its sets and the composition of its biggest set.
  const roundGroups = useMemo(() => {
    const groups = new Map<number, EffectiveEntry[]>()
    for (const set of sheet) {
      groups.set(set.roundNumber, [...(groups.get(set.roundNumber) ?? []), set])
    }
    return Array.from(groups.entries())
      .sort(([left], [right]) => left - right)
      .map(([roundNumber, sets]) => {
        const ordered = [...sets].sort((left, right) => left.setNumber - right.setNumber)
        return { roundNumber, roundCode: roundComposition(ordered), sets: ordered, aceDecider: ordered.some((set) => set.aceDecider) }
      })
  }, [sheet])

  const playersPerTeam = selectedParticipantIds.length >= 8 && selectedParticipantIds.length % 2 === 0
    ? selectedParticipantIds.length / 2
    : null

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
      const view = draftViewRef.current
      try {
        const response = await apiClient.getLatestCaptainDraft(TEMP_GROUP_ID)
        // The host may have reset the screen or opened a record while this loaded.
        if (!mounted || view !== draftViewRef.current) {
          return
        }
        setDraft(response)
        setParticipantSlots(createParticipantSlotsFromDraft(response))
        setHomeCaptainId(response.homeCaptainPlayerId)
        setAwayCaptainId(response.awayCaptainPlayerId)
        const layout = draftLayout(response.entries)
        setSetSizes(layout.setSizes)
        setRoundCount(layout.roundCount || DEFAULT_ROUND_COUNT)
        setEntryMode(response.entryMode ?? 'HOST')
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

  const loadRecords = useCallback(async () => {
    setRecordsLoading(true)
    setRecordsError(null)
    try {
      setRecords((await apiClient.listCaptainDrafts(TEMP_GROUP_ID)).drafts)
    } catch {
      setRecordsError(t('captainDraft.records.loadError'))
    } finally {
      setRecordsLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadRecords()
  }, [loadRecords])

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
    let active = true
    // The tag of the copy this polling last got: an unchanged draft then comes back as "not
    // modified", without the backend reading it again. A change made on this screen sets the draft
    // itself, and the backend no longer matches the old tag, so the next poll reads it afresh.
    let etag: string | null = null
    const stop = startVisiblePolling(async () => {
      const view = draftViewRef.current
      try {
        const refreshed = await apiClient.getCaptainDraftIfChanged(TEMP_GROUP_ID, draftId, etag)
        // A reply landing after polling stopped or the screen moved on is for a draft no longer shown.
        if (active && view === draftViewRef.current) {
          etag = refreshed.etag
          if (refreshed.draft) {
            setDraft(refreshed.draft)
          }
        }
      } catch {
        // Keep current snapshot on polling failure.
      }
    }, 3000)
    return () => {
      active = false
      stop()
    }
  }, [draft?.draftId])

  // Until a draft is started, the sets follow the attendance: enough for everyone to play a round.
  useEffect(() => {
    if (draft || playersPerTeam === null) {
      return
    }
    setSetSizes(defaultSetSizes(playersPerTeam))
  }, [playersPerTeam, draft])

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
        title: draftTitle.trim() || defaultDraftTitle(),
        participantPlayerIds: selectedParticipantIds,
        captainPlayerIds: [homeCaptainId, awayCaptainId],
        roundCount,
        setSizes,
        entryMode,
      })
      moveToNewView()
      setDraft(created)
      setEntryEdits({})
      setLineupEdits({})
      setMessage(t('captainDraft.messages.createSuccess'))
      void loadRecords()
    } catch (createError) {
      setError(errorWithReason('captainDraft.validation.createFailed', createError))
    } finally {
      setIsCreatingDraft(false)
    }
  }

  // A clean screen for the next draft: attendance, captains, sets, the team status and the round
  // entries all go. The draft shown stays saved and can be opened again from the records.
  const clearScreen = () => {
    moveToNewView()
    setParticipantSlots(createParticipantSlots(MINIMUM_PARTICIPANT_SLOTS))
    setHomeCaptainId(null)
    setAwayCaptainId(null)
    setSetSizes([])
    setRoundCount(DEFAULT_ROUND_COUNT)
    setEntryMode('HOST')
    setDraftTitle(defaultDraftTitle())
    setDraft(null)
    setDraftLoading(false)
    setEntryEdits({})
    setLineupEdits({})
  }

  // Changes on screen that 초기화 or loading another draft would lose, a captain's line-up included.
  const pendingCount = unsavedCount + unsubmittedStages.size

  const handleResetDraftSetup = () => {
    if (pendingCount > 0 && !window.confirm(t('captainDraft.actions.resetConfirm', { count: pendingCount }))) {
      return
    }
    clearScreen()
    setError(null)
    setMessage(null)
  }

  // A draft made by mistake or to try the page out goes from the records with everything in it.
  // When it is the one on screen, the screen is cleared as by 초기화.
  const handleDeleteRecord = async (record: CaptainDraftSummary) => {
    if (!window.confirm(t('captainDraft.records.deleteConfirm', { title: record.title }))) {
      return
    }
    setError(null)
    setMessage(null)
    setDeletingDraftId(record.draftId)
    const shownDraftId = draft?.draftId ?? null
    const view = draftViewRef.current
    try {
      const remaining = await apiClient.deleteCaptainDraft(TEMP_GROUP_ID, record.draftId)
      setRecords(remaining.drafts)
      if (shownDraftId === record.draftId && view === draftViewRef.current) {
        clearScreen()
      }
      setMessage(t('captainDraft.records.deleted', { title: record.title }))
    } catch (deleteError) {
      setError(errorWithReason('captainDraft.records.deleteError', deleteError))
    } finally {
      setDeletingDraftId(null)
    }
  }

  const showDraft = (shown: CaptainDraftResponse) => {
    moveToNewView()
    setDraft(shown)
    setParticipantSlots(createParticipantSlotsFromDraft(shown))
    setHomeCaptainId(shown.homeCaptainPlayerId)
    setAwayCaptainId(shown.awayCaptainPlayerId)
    const layout = draftLayout(shown.entries)
    setSetSizes(layout.setSizes)
    setRoundCount(layout.roundCount || DEFAULT_ROUND_COUNT)
    setEntryMode(shown.entryMode ?? 'HOST')
    setEntryEdits({})
    setLineupEdits({})
  }

  const handleOpenRecord = async (draftId: number) => {
    setError(null)
    setMessage(null)
    const view = draftViewRef.current
    try {
      const opened = await apiClient.getCaptainDraft(TEMP_GROUP_ID, draftId)
      if (view !== draftViewRef.current) {
        return
      }
      showDraft(opened)
      window.scrollTo({ top: 0, behavior: 'smooth' })
    } catch {
      setError(t('captainDraft.validation.loadFailed'))
    }
  }

  // 최근 감전 불러오기: the latest draft saved, over whatever the screen shows. Attendance or
  // captains typed for the next draft and unsaved entry changes would be lost, so ask first.
  const handleRefreshDraft = async () => {
    const wouldOverwrite =
      pendingCount > 0 || setupDiffersFromDraft(draft, selectedParticipantIds, homeCaptainId, awayCaptainId)
    if (wouldOverwrite && !window.confirm(t('captainDraft.actions.loadLatestConfirm'))) {
      return
    }
    setError(null)
    setMessage(null)
    setDraftLoading(true)
    const view = draftViewRef.current
    try {
      const latest = await apiClient.getLatestCaptainDraft(TEMP_GROUP_ID)
      if (view !== draftViewRef.current) {
        return
      }
      showDraft(latest)
      void loadRecords()
    } catch (refreshError) {
      if (view !== draftViewRef.current) {
        return
      }
      if (isApiNotFoundError(refreshError)) {
        moveToNewView()
        setDraft(null)
        setParticipantSlots(createParticipantSlots(MINIMUM_PARTICIPANT_SLOTS))
        setHomeCaptainId(null)
        setAwayCaptainId(null)
        setEntryEdits({})
        setLineupEdits({})
        setSetSizes([])
        setRoundCount(DEFAULT_ROUND_COUNT)
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
    const view = draftViewRef.current
    try {
      const picked = await apiClient.pickCaptainDraftPlayer(TEMP_GROUP_ID, draft.draftId, { captainPlayerId, pickedPlayerId: playerId })
      if (view === draftViewRef.current) {
        setDraft(picked)
      }
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
    const view = draftViewRef.current
    try {
      const unpicked = await apiClient.unpickCaptainDraftPlayer(TEMP_GROUP_ID, draft.draftId, { playerId })
      if (view === draftViewRef.current) {
        setDraft(unpicked)
        setEntryEdits((previous) => dropEditsNaming(previous, playerId))
      }
    } catch (unpickError) {
      setError(
        isApiConflictError(unpickError)
          ? t('captainDraft.teams.unpickAfterResults')
          : errorWithReason('captainDraft.validation.unpickFailed', unpickError),
      )
    } finally {
      setIsChangingTeams(false)
    }
  }

  // A captain's own side of a stage not shown to everyone yet is their line-up, handed in once;
  // everything else is saved with 엔트리 저장.
  const editEntry = (key: string, change: EntryEdit) => {
    const set = sheet.find((candidate) => candidate.key === key)
    const { homePlayerIds, awayPlayerIds, ...rest } = change
    const ownSide = submitTeam === 'HOME' ? homePlayerIds : submitTeam === 'AWAY' ? awayPlayerIds : undefined
    if (set && submitTeam && !lineups.get(stageOf(set))?.revealed && ownSide) {
      setLineupEdits((previous) => ({ ...previous, [key]: ownSide }))
      if (Object.keys(rest).length === 0) {
        return
      }
      setEntryEdits((previous) => ({ ...previous, [key]: { ...previous[key], ...rest } }))
      return
    }
    setEntryEdits((previous) => ({ ...previous, [key]: { ...previous[key], ...change } }))
  }

  // A captain hands in every round of their team at once (or the ace decider), and only once.
  const handleSubmitLineup = async (stage: CaptainDraftLineupStage) => {
    if (!draft?.draftId || !submitTeam) {
      return
    }
    setMessage(null)
    setError(null)
    const { request, complete } = lineupSubmission(sheet, stage, submitTeam)
    if (!complete) {
      setError(t(stage === 'ACE' ? 'captainDraft.lineup.incompleteAce' : 'captainDraft.lineup.incomplete'))
      return
    }
    if (!window.confirm(t(stage === 'ACE' ? 'captainDraft.lineup.confirmAce' : 'captainDraft.lineup.confirm'))) {
      return
    }
    setSubmittingStage(stage)
    const view = draftViewRef.current
    try {
      const next = await apiClient.submitCaptainDraftLineup(TEMP_GROUP_ID, draft.draftId, request)
      if (view !== draftViewRef.current) {
        return
      }
      setDraft(next)
      setLineupEdits((previous) => dropLineupEdits(previous, next.entries, stage))
      const revealed = next.lineups?.find((lineup) => lineup.stage === stage)?.revealed
      setMessage(t(revealed ? 'captainDraft.lineup.revealedNow' : 'captainDraft.lineup.submitted'))
    } catch (submitError) {
      setError(
        isApiForbiddenError(submitError)
          ? t('captainDraft.lineup.notCaptain')
          : isApiConflictError(submitError)
            ? t('captainDraft.lineup.alreadySubmitted')
            : errorWithReason('captainDraft.lineup.submitFailed', submitError),
      )
    } finally {
      setSubmittingStage(null)
    }
  }

  // Whoever runs the draft switches between the host filling both teams in and the captains handing
  // theirs in; the backend refuses a switch that would show or hide what it must not.
  const handleChangeEntryMode = async (mode: CaptainDraftEntryMode) => {
    if (!draft?.draftId || mode === (draft.entryMode ?? 'HOST')) {
      return
    }
    if (!window.confirm(t(mode === 'CAPTAINS' ? 'captainDraft.entryMode.toCaptainsConfirm' : 'captainDraft.entryMode.toHostConfirm'))) {
      return
    }
    setMessage(null)
    setError(null)
    setIsChangingMode(true)
    const view = draftViewRef.current
    try {
      const next = await apiClient.changeCaptainDraftEntryMode(TEMP_GROUP_ID, draft.draftId, { entryMode: mode })
      if (view !== draftViewRef.current) {
        return
      }
      setDraft(next)
      setEntryMode(next.entryMode ?? 'HOST')
      setLineupEdits({})
      setMessage(t('captainDraft.entryMode.changed', {
        mode: t(mode === 'CAPTAINS' ? 'captainDraft.entryMode.captains' : 'captainDraft.entryMode.host'),
      }))
    } catch (changeError) {
      setError(errorWithReason('captainDraft.entryMode.changeFailed', changeError))
    } finally {
      setIsChangingMode(false)
    }
  }

  // The rounds ended level: one more round, the ace decider, if whoever runs the draft wants it.
  const handleAddAce = async () => {
    if (!draft?.draftId || !aceOffer) {
      return
    }
    if (!window.confirm(t('captainDraft.ace.confirm', { size: aceSize }))) {
      return
    }
    setMessage(null)
    setError(null)
    setIsAddingAce(true)
    const view = draftViewRef.current
    try {
      const next = await apiClient.addCaptainDraftAceDecider(TEMP_GROUP_ID, draft.draftId, { teamSize: aceSize })
      void loadRecords()
      if (view !== draftViewRef.current) {
        return
      }
      setDraft(next)
      setMessage(t('captainDraft.ace.added'))
    } catch (aceError) {
      setError(errorWithReason('captainDraft.ace.addFailed', aceError))
    } finally {
      setIsAddingAce(false)
    }
  }

  const handleSaveEntries = async () => {
    setMessage(null)
    setError(null)

    if (!draft?.draftId) {
      setError(t('captainDraft.validation.selectDraftFirst'))
      return
    }

    setIsSavingEntries(true)
    const view = draftViewRef.current
    try {
      const next = await apiClient.updateCaptainDraftEntries(TEMP_GROUP_ID, draft.draftId, {
        entries: sheet.map((set) => ({
          roundNumber: set.roundNumber,
          setNumber: set.setNumber,
          raceCode: set.raceCode,
          homePlayerIds: set.homePlayerIds,
          awayPlayerIds: set.awayPlayerIds,
          requiredTier: set.requiredTier.trim() || null,
          winnerTeam: set.winnerTeam ? set.winnerTeam : null,
        })),
      })
      void loadRecords()
      if (view !== draftViewRef.current) {
        return
      }
      setDraft(next)
      setEntryEdits({})
      setMessage(t('captainDraft.messages.entriesSaved'))
    } catch (saveError) {
      setError(
        isApiForbiddenError(saveError)
          ? t('captainDraft.validation.resultLocked')
          : errorWithReason('captainDraft.validation.saveEntryFailed', saveError),
      )
    } finally {
      setIsSavingEntries(false)
    }
  }

  // A round's composition sets every set of it: the biggest plays it, smaller ones a cut-down version.
  // Sets whose races this viewer may not change keep theirs.
  const changeRoundCode = (roundNumber: number, code: string) => {
    setEntryEdits((previous) => {
      const next = { ...previous }
      for (const set of sheet.filter((candidate) => candidate.roundNumber === roundNumber && controls.get(candidate.key)?.settings)) {
        next[set.key] = { ...next[set.key], raceCode: setComposition(code, set.teamSize) }
      }
      return next
    })
  }

  return (
    <section className="space-y-6">
      <header className="space-y-1 rounded-xl border border-slate-200 bg-white px-5 py-4 shadow-xs dark:border-slate-700 dark:bg-slate-900">
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
            playerTeams={tierBoardTeams}
          />
        </div>

        <div className="space-y-4">
          <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-xs dark:border-slate-700 dark:bg-slate-900">
            <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('captainDraft.captain.title')}</h3>

            <div className="mt-3 space-y-2">
              <label className="block text-xs font-medium text-slate-500 dark:text-slate-400">
                {t('captainDraft.records.titleLabel')}
                <input
                  value={draftTitle}
                  onChange={(event) => setDraftTitle(event.target.value)}
                  maxLength={120}
                  className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-hidden transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-slate-500 dark:focus:ring-slate-700"
                />
              </label>

              <label className="block text-xs font-medium text-slate-500 dark:text-slate-400">
                {t('captainDraft.captain.home')}
                <select
                  value={homeCaptainId ?? ''}
                  onChange={(event) => handleHomeCaptainChange(event.target.value)}
                  className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-hidden transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-slate-500 dark:focus:ring-slate-700"
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
                  className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-hidden transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-slate-500 dark:focus:ring-slate-700"
                >
                  <option value="">-</option>
                  {awayCaptainOptions.map((participant) => (
                    <option key={`away-cap-${participant.id}`} value={participant.id}>
                      {participant.nickname} ({participant.race})
                    </option>
                  ))}
                </select>
              </label>

              <label className="block text-xs font-medium text-slate-500 dark:text-slate-400">
                {t('captainDraft.entryMode.label')}
                <select
                  value={entryMode}
                  onChange={(event) => setEntryMode(event.target.value === 'CAPTAINS' ? 'CAPTAINS' : 'HOST')}
                  className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-hidden transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-slate-500 dark:focus:ring-slate-700"
                >
                  <option value="HOST">{t('captainDraft.entryMode.host')}</option>
                  <option value="CAPTAINS">{t('captainDraft.entryMode.captains')}</option>
                </select>
                <span className="mt-1 block font-normal text-slate-500 dark:text-slate-400">
                  {t(entryMode === 'CAPTAINS' ? 'captainDraft.entryMode.captainsHelp' : 'captainDraft.entryMode.hostHelp')}
                </span>
              </label>
            </div>

            <CaptainDraftRoundSettings
              playersPerTeam={playersPerTeam}
              setSizes={setSizes}
              roundCount={roundCount}
              onSetSizesChange={setSetSizes}
              onRoundCountChange={setRoundCount}
            />

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
                {t('captainDraft.actions.loadLatest')}
              </button>
            </div>

            {draft && (
              <div className="mt-4 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-700 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200">
                <p className="font-semibold">
                  {draft.title}
                  <span className="ml-2 font-normal text-slate-500 dark:text-slate-400">
                    {formatKstDateTime(draft.createdAt)}
                    {draft.createdByNickname && ` · ${t('captainDraft.records.startedBy', { nickname: draft.createdByNickname })}`}
                  </span>
                </p>
                <p className="mt-1">
                  {t('captainDraft.status.label')}:{' '}
                  {draft.status === 'READY' ? t('captainDraft.status.ready') : t('captainDraft.status.drafting')}
                </p>
                <label className="mt-1 flex flex-wrap items-center gap-2">
                  <span>{t('captainDraft.entryMode.label')}:</span>
                  <select
                    value={byCaptains ? 'CAPTAINS' : 'HOST'}
                    onChange={(event) => void handleChangeEntryMode(event.target.value === 'CAPTAINS' ? 'CAPTAINS' : 'HOST')}
                    disabled={isChangingMode}
                    aria-label={t('captainDraft.entryMode.changeLabel')}
                    className="rounded-md border border-slate-300 bg-white px-2 py-1 text-xs text-slate-800 outline-hidden focus:border-slate-500 disabled:opacity-60 dark:border-slate-600 dark:bg-slate-950 dark:text-slate-100"
                  >
                    <option value="HOST">{t('captainDraft.entryMode.host')}</option>
                    <option value="CAPTAINS">{t('captainDraft.entryMode.captains')}</option>
                  </select>
                </label>
                {byCaptains && hasLoneLineup(lineups) && (
                  <p className="mt-1 text-[11px] text-slate-500 dark:text-slate-400">{t('captainDraft.entryMode.loneLineupHint')}</p>
                )}
                {byCaptains && viewer.viewerTeam && (
                  <p className="mt-1 font-semibold">
                    {t(viewer.viewerCaptain ? 'captainDraft.entryMode.viewerCaptain' : 'captainDraft.entryMode.viewerMember', {
                      team: t(TEAM_LABEL_KEYS[viewer.viewerTeam]),
                    })}
                  </p>
                )}
              </div>
            )}
          </article>

          {draft && (
            <CaptainDraftTeamBoard
              draft={draft}
              playerMap={playerMap}
              ranking={ranking}
              setWins={setWins}
              busy={isChangingTeams}
              canTakeBack={canTakeBack}
              onPick={(team, playerId) => void handlePick(team, playerId)}
              onUnpick={(playerId) => void handleUnpick(playerId)}
            />
          )}
        </div>
      </div>

      {draft && (
        <>
          <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-xs dark:border-slate-700 dark:bg-slate-900">
            <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('captainDraft.entry.title')}</h3>
            <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
              {t(byCaptains ? 'captainDraft.lineup.helper' : 'captainDraft.entry.helper')}
            </p>

            <div className="mt-4 space-y-4">
              {byCaptains &&
                [...lineups.entries()].map(([stage, lineup]) => (
                  <div
                    key={`lineup-${stage}`}
                    className="flex flex-wrap items-center gap-x-4 gap-y-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs dark:border-slate-700 dark:bg-slate-800"
                  >
                    <span className="font-semibold text-slate-800 dark:text-slate-100">
                      {t(stage === 'ACE' ? 'captainDraft.lineup.aceStage' : 'captainDraft.lineup.mainStage')}
                    </span>
                    {lineup.revealed ? (
                      <span className="rounded-sm bg-emerald-100 px-1.5 py-0.5 font-semibold text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-300">
                        {t('captainDraft.lineup.revealed')}
                      </span>
                    ) : (
                      (['HOME', 'AWAY'] as const).map((team) => (
                        <span
                          key={`lineup-${stage}-${team}`}
                          className={
                            teamSubmitted(lineup, team)
                              ? team === 'HOME'
                                ? 'font-medium text-sky-700 dark:text-sky-300'
                                : 'font-medium text-rose-700 dark:text-rose-300'
                              : 'text-slate-500 dark:text-slate-400'
                          }
                        >
                          {t(teamSubmitted(lineup, team) ? 'captainDraft.lineup.teamIn' : 'captainDraft.lineup.teamPending', {
                            team: t(TEAM_LABEL_KEYS[team]),
                          })}
                        </span>
                      ))
                    )}
                    {submitTeam && !lineup.revealed && !teamSubmitted(lineup, submitTeam) && (
                      <span className="ml-auto flex items-center gap-2">
                        {unsubmittedStages.has(stage) && (
                          <span className="text-amber-700 dark:text-amber-300">{t('captainDraft.lineup.unsubmitted')}</span>
                        )}
                        <button
                          type="button"
                          onClick={() => void handleSubmitLineup(stage)}
                          disabled={submittingStage !== null}
                          className="rounded-md bg-slate-900 px-3 py-1.5 font-semibold text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white dark:disabled:bg-slate-700 dark:disabled:text-slate-400"
                        >
                          {submittingStage === stage
                            ? t('captainDraft.lineup.submitting')
                            : t(stage === 'ACE' ? 'captainDraft.lineup.submitAce' : 'captainDraft.lineup.submit')}
                        </button>
                      </span>
                    )}
                  </div>
                ))}
              {repeatedGroups.length > 0 && (
                <div
                  role="status"
                  className="space-y-1 rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200"
                >
                  {repeatedGroups.map((repeat) => (
                    <p key={`repeated-${repeat.team}-${repeat.again.round}-${repeat.again.set}`} className="font-medium">
                      {t('captainDraft.entry.repeatedGroup', {
                        team: t(TEAM_LABEL_KEYS[repeat.team]),
                        players: repeat.playerIds.map((playerId) => nicknameOf.get(playerId) ?? `#${playerId}`).join(', '),
                        first: t('captainDraft.entry.roundSet', { round: repeat.first.round, set: repeat.first.set }),
                        again: t('captainDraft.entry.roundSet', { round: repeat.again.round, set: repeat.again.set }),
                      })}
                    </p>
                  ))}
                  <p className="text-xs">{t('captainDraft.entry.repeatedLineupAdvice')}</p>
                </div>
              )}
              <CaptainDraftGameCounts sheet={sheet} teamMembers={teamMembers} ranking={ranking} controls={controls} />
              <CaptainDraftEntrySheet
                rounds={roundGroups}
                sheet={sheet}
                teamMembers={teamMembers}
                playerMap={playerMap}
                controls={controls}
                onEditEntry={editEntry}
                onChangeRoundCode={changeRoundCode}
                lineups={byCaptains ? lineups : undefined}
              />
              {sheetRows.some((row) => row.controls.locked) && (
                <p className="text-xs text-slate-500 dark:text-slate-400">{t('captainDraft.entry.lockedHint')}</p>
              )}
              {aceOffer && (
                <div className="flex flex-wrap items-center gap-3 rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200">
                  <span className="font-medium">{t('captainDraft.ace.offer', { home: aceOffer.home, away: aceOffer.away })}</span>
                  <label className="flex items-center gap-1 text-xs">
                    {t('captainDraft.ace.sizeLabel')}
                    <select
                      value={aceSize}
                      onChange={(event) => setAceSize(Number(event.target.value))}
                      className="rounded-md border border-amber-300 bg-white px-1.5 py-1 text-xs text-slate-800 outline-hidden dark:border-amber-800 dark:bg-slate-950 dark:text-slate-100"
                    >
                      {aceSizeChoices(teamMembers.HOME.length, teamMembers.AWAY.length).map((size) => (
                        <option key={`ace-size-${size}`} value={size}>
                          {t('captainDraft.entry.teamSize', { size })}
                        </option>
                      ))}
                    </select>
                  </label>
                  <button
                    type="button"
                    onClick={() => void handleAddAce()}
                    disabled={isAddingAce}
                    className="ml-auto rounded-md bg-amber-600 px-3 py-1.5 text-xs font-semibold text-white transition-colors hover:bg-amber-700 disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    {isAddingAce ? t('captainDraft.ace.adding') : t('captainDraft.ace.add')}
                  </button>
                </div>
              )}

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

        </>
      )}

      {!draftLoading && !draft && (
        <article className="rounded-xl border border-dashed border-slate-300 bg-white px-4 py-10 text-center text-sm text-slate-600 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-300">
          {t('captainDraft.emptyDraft')}
        </article>
      )}

      <CaptainDraftRecords
        records={records}
        loading={recordsLoading}
        error={recordsError}
        currentDraftId={draft?.draftId ?? null}
        canDelete={isAdmin}
        deletingDraftId={deletingDraftId}
        onOpen={(draftId) => void handleOpenRecord(draftId)}
        onDelete={(record) => void handleDeleteRecord(record)}
      />
    </section>
  )
}
