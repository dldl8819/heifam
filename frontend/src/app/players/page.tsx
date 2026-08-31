'use client'

import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useAdminAuth } from '@/lib/admin-auth'
import { apiClient, isApiConflictError, isApiForbiddenError, isApiNotFoundError, isApiUnauthorizedError } from '@/lib/api'
import { Alert, AlertContent, AlertDescription, AlertIcon, AlertTitle } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { PlayerGameTypeStatsModal } from '@/components/player-game-type-stats-modal'
import { t } from '@/lib/i18n'
import { useMmrVisibility } from '@/lib/mmr-visibility'
import { toTierOrder } from '@/lib/player-tier'
import {
  buildOwnPlayerRaceUpdateRequest,
  buildPlayerProfileUpdateRequest,
  resolveDefaultMmrForTier,
  resolveEditableMmrValue,
} from '@/lib/player-edit'
import {
  filterPlayerRosterByView,
  type PlayerRosterView,
} from '@/lib/player-roster-filter'
import { applyPlayerActivityTransition } from '@/lib/player-activity'
import {
  createMonthlyTierBoardPng,
  selectMonthlyTierBoardPlayers,
} from '@/lib/monthly-tier-board-image'
import type { GroupPlayerRaceStatsItem, PlayerRace, PlayerRosterItem, PlayerTierStatus } from '@/types/api'

const TEMP_GROUP_ID = 1

type RaceFilter = PlayerRace | 'ALL'
type PlayerRegistrationTier = Exclude<PlayerTierStatus, 'S'>
type PlayerImportRow = {
  nickname: string
  tier: string
  race?: PlayerRace
  baseMmr?: number
  currentMmr?: number
  note?: string
}
type PlayerImportPayload = {
  players: PlayerImportRow[]
}
type PlayerImportResult = {
  createdCount: number
  updatedCount: number
  failedCount: number
  failedRows: PlayerImportFailedRow[]
}
type PlayerImportFailedRow = {
  reason?: string
}
type ActivityFormMode = 'deactivate' | 'reactivate'
type ActivityFormState = {
  mode: ActivityFormMode
  player: PlayerRosterItem
  chatLeftAt: string
  chatLeftReason: string
  chatRejoinedAt: string
}
type LastParticipationState = {
  playerId: number
  status: 'loading' | 'success' | 'error'
  lastPlayedAt: string | null
}
const PLAYER_RACE_OPTIONS: PlayerRace[] = ['P', 'T', 'Z', 'PT', 'PZ', 'TZ', 'PTZ']
const PLAYER_INACTIVE_REASON_OPTIONS = ['장기 미참여', '본인 요청', '운영 정책', '기타'] as const
const PLAYER_EDIT_TIER_OPTIONS: PlayerTierStatus[] = [
  'S',
  'A+',
  'A',
  'A-',
  'B+',
  'B',
  'B-',
  'C+',
  'C',
  'C-',
  'D',
  'UNASSIGNED',
]
const PLAYER_REGISTRATION_TIER_OPTIONS: PlayerRegistrationTier[] = [
  'A+',
  'A',
  'A-',
  'B+',
  'B',
  'B-',
  'C+',
  'C',
  'C-',
  'D',
  'UNASSIGNED',
]
const REASSIGNMENT_IMPORT_TIER = '\uC7AC\uBC30\uC815\uB300\uC0C1'

function displayTier(row: Pick<PlayerRosterItem, 'tier' | 'liveTier'>): PlayerTierStatus {
  return row.liveTier ?? row.tier
}

function comparePlayersByTierThenNickname(a: PlayerRosterItem, b: PlayerRosterItem): number {
  const tierDiff = toTierOrder(displayTier(a)) - toTierOrder(displayTier(b))
  if (tierDiff !== 0) {
    return tierDiff
  }

  return a.nickname.localeCompare(b.nickname, 'ko-KR')
}

function sortRosterRows(rows: PlayerRosterItem[], showMmrColumn: boolean): PlayerRosterItem[] {
  return [...rows].sort((a, b) => {
    const aActive = a.active !== false
    const bActive = b.active !== false
    if (aActive !== bActive) {
      return aActive ? -1 : 1
    }
    if (b.games !== a.games) {
      return b.games - a.games
    }
    if (showMmrColumn) {
      const aMmr = typeof a.currentMmr === 'number' ? a.currentMmr : -1
      const bMmr = typeof b.currentMmr === 'number' ? b.currentMmr : -1
      if (bMmr !== aMmr) {
        return bMmr - aMmr
      }
    }
    return a.nickname.localeCompare(b.nickname, 'ko-KR')
  })
}

function escapeCsvCell(value: string): string {
  return `"${value.replace(/"/g, '""')}"`
}

function triggerBlobDownload(blob: Blob, fileName: string): void {
  const downloadUrl = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = downloadUrl
  anchor.download = fileName

  try {
    document.body.append(anchor)
    anchor.click()
  } finally {
    anchor.remove()
    window.setTimeout(() => URL.revokeObjectURL(downloadUrl), 1000)
  }
}

function formatMmrValue(value: number | undefined): string {
  if (typeof value !== 'number') {
    return '-'
  }
  return value === 0 ? 'None' : String(value)
}

function formatDateTimeLocalInputValue(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day}T${hour}:${minute}`
}

function parseDateTimeLocalInput(value: string): string | null {
  const match = value
    .trim()
    .match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/)
  if (!match) {
    return null
  }

  const [, yearValue, monthValue, dayValue, hourValue, minuteValue] = match
  const year = Number(yearValue)
  const month = Number(monthValue)
  const day = Number(dayValue)
  const hour = Number(hourValue)
  const minute = Number(minuteValue)
  const date = new Date(year, month - 1, day, hour, minute, 0, 0)

  if (
    date.getFullYear() !== year ||
    date.getMonth() !== month - 1 ||
    date.getDate() !== day ||
    date.getHours() !== hour ||
    date.getMinutes() !== minute
  ) {
    return null
  }

  return date.toISOString()
}

function formatChatRecordDisplay(value: string | undefined): string {
  if (!value) {
    return '-'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${year}.${month}.${day} ${hour}:${minute}`
}

function resolveLifecycleStatus(row: PlayerRosterItem) {
  if (row.lifecycleStatus) {
    return row.lifecycleStatus
  }
  if (row.active !== false) {
    return 'ACTIVE'
  }
  return row.identityHidden ? 'ANONYMIZED' : 'INACTIVE'
}

function formatLastParticipationDate(value: string): string {
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    timeZone: 'Asia/Seoul',
  }).format(new Date(value))
}

function toImportResultNumber(value: unknown): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : 0
}

function normalizeImportResult(value: unknown): PlayerImportResult {
  if (value === null || typeof value !== 'object') {
    return {
      createdCount: 0,
      updatedCount: 0,
      failedCount: 0,
      failedRows: [],
    }
  }

  const source = value as Record<string, unknown>
  const failedRows = Array.isArray(source.failedRows)
    ? source.failedRows
        .flatMap((row): PlayerImportFailedRow[] => {
          if (row === null || typeof row !== 'object') {
            return []
          }
          const rowSource = row as Record<string, unknown>
          const reason = typeof rowSource.reason === 'string' ? rowSource.reason : undefined
          return reason ? [{ reason }] : [{}]
        })
    : []

  return {
    createdCount: toImportResultNumber(source.createdCount),
    updatedCount: toImportResultNumber(source.updatedCount),
    failedCount: toImportResultNumber(source.failedCount) || failedRows.length,
    failedRows,
  }
}

function formatImportFailureMessage(result: PlayerImportResult): string {
  const firstReason = result.failedRows
    .map((row) => row.reason?.trim() ?? '')
    .find((reason) => reason.length > 0)

  if (firstReason) {
    return t('players.import.failureWithReason', { reason: firstReason })
  }

  return t('players.import.failure')
}

function getTierBadgeClass(tier: PlayerTierStatus): string {
  switch (tier) {
    case 'S':
      return 'bg-amber-100 text-amber-800 dark:bg-amber-950/50 dark:text-amber-200'
    case 'A+':
    case 'A':
    case 'A-':
      return 'bg-indigo-100 text-indigo-800 dark:bg-indigo-950/50 dark:text-indigo-200'
    case 'B+':
    case 'B':
    case 'B-':
      return 'bg-blue-100 text-blue-800 dark:bg-blue-950/50 dark:text-blue-200'
    case 'C+':
    case 'C':
    case 'C-':
    case 'D':
      return 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950/50 dark:text-emerald-200'
    case 'UNASSIGNED':
      return 'bg-rose-100 text-rose-800 dark:bg-rose-950/50 dark:text-rose-200'
    default:
      return 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-200'
  }
}

export default function PlayersPage() {
  const { isAdmin, isSuperAdmin, canViewMmr, isLoading: isAdminAuthLoading } = useAdminAuth()
  const { mmrVisible } = useMmrVisibility()
  const showMmrColumn = canViewMmr && mmrVisible
  const [rows, setRows] = useState<PlayerRosterItem[]>([])
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState<string>('')
  const [raceFilter, setRaceFilter] = useState<RaceFilter>('ALL')
  const [registrationNickname, setRegistrationNickname] = useState<string>('')
  const [registrationTier, setRegistrationTier] = useState<PlayerRegistrationTier | ''>('')
  const [registrationRace, setRegistrationRace] = useState<PlayerRace | ''>('')
  const [importing, setImporting] = useState<boolean>(false)
  const [importError, setImportError] = useState<string | null>(null)
  const [importSuccess, setImportSuccess] = useState<string | null>(null)
  const [editingPlayerId, setEditingPlayerId] = useState<number | null>(null)
  const [editingNickname, setEditingNickname] = useState<string>('')
  const [editingRace, setEditingRace] = useState<PlayerRace>('P')
  const [editingTier, setEditingTier] = useState<PlayerTierStatus>('UNASSIGNED')
  const [editingInlineMmrValue, setEditingInlineMmrValue] = useState<string>('')
  const [savingPlayerId, setSavingPlayerId] = useState<number | null>(null)
  const [deletingPlayerId, setDeletingPlayerId] = useState<number | null>(null)
  const [togglingPlayerId, setTogglingPlayerId] = useState<number | null>(null)
  const [activityForm, setActivityForm] = useState<ActivityFormState | null>(null)
  const [rosterView, setRosterView] = useState<PlayerRosterView>('active')
  const [dormantPlayerIds, setDormantPlayerIds] = useState<ReadonlySet<number>>(
    () => new Set<number>()
  )
  const [playerActionError, setPlayerActionError] = useState<string | null>(null)
  const [playerActionSuccess, setPlayerActionSuccess] = useState<string | null>(null)
  const [tierBoardDownloading, setTierBoardDownloading] = useState<boolean>(false)
  const [gameTypeStatsPlayer, setGameTypeStatsPlayer] =
    useState<{ id: number; nickname: string } | null>(null)
  const [gameTypeStats, setGameTypeStats] = useState<GroupPlayerRaceStatsItem | null>(null)
  const [gameTypeStatsLoading, setGameTypeStatsLoading] = useState<boolean>(false)
  const [gameTypeStatsError, setGameTypeStatsError] = useState<string | null>(null)
  const [lastParticipation, setLastParticipation] = useState<LastParticipationState | null>(null)
  const [ownRaceEditingPlayerId, setOwnRaceEditingPlayerId] = useState<number | null>(null)
  const [ownRaceEditingValue, setOwnRaceEditingValue] = useState<PlayerRace>('P')
  const [ownRaceSavingPlayerId, setOwnRaceSavingPlayerId] = useState<number | null>(null)
  const [ownRaceActionError, setOwnRaceActionError] = useState<string | null>(null)
  const [ownRaceActionSuccess, setOwnRaceActionSuccess] = useState<string | null>(null)
  const rosterRequestId = useRef(0)
  const lastParticipationRequestId = useRef(0)
  const effectiveRosterView: PlayerRosterView = isAdmin ? rosterView : 'active'

  const fetchRoster = useCallback(async () => {
    const requestId = rosterRequestId.current + 1
    rosterRequestId.current = requestId
    setLoading(true)
    setError(null)

    try {
      const [response, dormantPlayers] = await Promise.all([
        apiClient.getGroupPlayers(TEMP_GROUP_ID, {
          includeInactive: isAdmin && effectiveRosterView === 'inactive',
        }),
        isAdmin && effectiveRosterView === 'dormant'
          ? apiClient.getGroupDormantPlayers(TEMP_GROUP_ID)
          : Promise.resolve([]),
      ])
      if (rosterRequestId.current !== requestId) {
        return
      }
      setRows(response)
      setDormantPlayerIds(new Set(dormantPlayers.map((player) => player.playerId)))
    } catch {
      if (rosterRequestId.current !== requestId) {
        return
      }
      setRows([])
      setDormantPlayerIds(new Set<number>())
      setError(t('players.loadError'))
    } finally {
      if (rosterRequestId.current === requestId) {
        setLoading(false)
      }
    }
  }, [effectiveRosterView, isAdmin])

  useEffect(() => {
    // Wait until admin-auth has fully resolved (isAdmin/effectiveRosterView settled) so the
    // roster is fetched once, not once per intermediate loading state.
    if (isAdminAuthLoading) {
      return
    }
    void fetchRoster()
    return () => {
      rosterRequestId.current += 1
    }
  }, [fetchRoster, isAdminAuthLoading])

  useEffect(() => {
    if (isAdmin) {
      return
    }
    lastParticipationRequestId.current += 1
    setRosterView('active')
    setDormantPlayerIds(new Set<number>())
    setLastParticipation(null)
  }, [isAdmin])

  const handleRegisterPlayer = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setImportError(null)
    setImportSuccess(null)
    setPlayerActionError(null)
    setPlayerActionSuccess(null)

    if (!isAdmin) {
      setImportError(t('common.adminOnlyAction'))
      return
    }

    const nickname = registrationNickname.trim()
    if (nickname.length === 0) {
      setImportError(t('players.import.nicknameRequired'))
      return
    }

    if (!registrationTier) {
      setImportError(t('players.import.tierRequired'))
      return
    }

    if (!registrationRace) {
      setImportError(t('players.import.raceRequired'))
      return
    }

    const payload: PlayerImportPayload = {
      players: [
        {
          nickname,
          tier: registrationTier === 'UNASSIGNED' ? REASSIGNMENT_IMPORT_TIER : registrationTier,
          race: registrationRace,
        },
      ],
    }

    setImporting(true)
    try {
      const response = await apiClient.importGroupPlayers(TEMP_GROUP_ID, payload)
      const importResult = normalizeImportResult(response)
      if (
        importResult.failedCount > 0 ||
        importResult.createdCount + importResult.updatedCount === 0
      ) {
        setImportError(formatImportFailureMessage(importResult))
        return
      }

      setImportSuccess(t('players.import.success'))
      setRegistrationNickname('')
      setRegistrationTier('')
      setRegistrationRace('')
      setSearch('')
      setRaceFilter('ALL')
      setRosterView('active')
      await fetchRoster()
    } catch (importRequestError) {
      if (isApiUnauthorizedError(importRequestError)) {
        setImportError(t('common.adminLoginRequired'))
      } else if (isApiForbiddenError(importRequestError)) {
        setImportError(t('common.permissionDenied'))
      } else {
        setImportError(t('players.import.failure'))
      }
    } finally {
      setImporting(false)
    }
  }

  const handleStartOwnRaceEdit = (player: PlayerRosterItem) => {
    if (isAdmin || player.isOwnPlayer !== true) {
      return
    }

    setOwnRaceEditingPlayerId(player.id)
    setOwnRaceEditingValue(player.race)
    setOwnRaceActionError(null)
    setOwnRaceActionSuccess(null)
  }

  const handleCancelOwnRaceEdit = () => {
    setOwnRaceEditingPlayerId(null)
  }

  const handleSaveOwnRace = async (player: PlayerRosterItem) => {
    if (isAdmin || player.isOwnPlayer !== true) {
      return
    }

    const payload = buildOwnPlayerRaceUpdateRequest(player, ownRaceEditingValue)
    if (payload === null) {
      setOwnRaceEditingPlayerId(null)
      return
    }

    setOwnRaceSavingPlayerId(player.id)
    setOwnRaceActionError(null)
    setOwnRaceActionSuccess(null)
    try {
      await apiClient.updateGroupPlayer(TEMP_GROUP_ID, player.id, payload)
      setOwnRaceEditingPlayerId(null)
      setOwnRaceActionSuccess(t('players.ownRace.success'))
      await fetchRoster()
    } catch (actionError) {
      if (isApiForbiddenError(actionError)) {
        setOwnRaceActionError(t('common.permissionDenied'))
      } else {
        setOwnRaceActionError(t('players.ownRace.failure'))
      }
    } finally {
      setOwnRaceSavingPlayerId(null)
    }
  }

  const handleStartEdit = (player: PlayerRosterItem) => {
    if (!isAdmin) {
      return
    }

    setEditingPlayerId(player.id)
    setEditingNickname(player.nickname)
    setEditingRace(player.race)
    setEditingTier(player.liveTier ?? player.tier)
    setEditingInlineMmrValue(resolveEditableMmrValue(player))
    setActivityForm(null)
    setPlayerActionError(null)
    setPlayerActionSuccess(null)
  }

  const handleCancelEdit = () => {
    setEditingPlayerId(null)
    setEditingNickname('')
    setEditingRace('P')
    setEditingTier('UNASSIGNED')
    setEditingInlineMmrValue('')
  }

  const handleSaveEdit = async (playerId: number) => {
    if (!isAdmin) {
      setPlayerActionError(t('common.adminOnlyAction'))
      return
    }

    const nextNickname = editingNickname.trim()
    if (nextNickname.length === 0) {
      setPlayerActionError(t('players.actions.nicknameRequired'))
      return
    }

    const targetRow = rows.find((row) => row.id === playerId)
    if (!targetRow) {
      setPlayerActionError(t('players.actions.updateNotFound'))
      return
    }

    let nextMmr: number | null = null
    if (isSuperAdmin) {
      const rawMmr = editingInlineMmrValue.trim()
      if (rawMmr.length === 0) {
        setPlayerActionError(t('players.actions.mmrRequired'))
        return
      }

      const parsedMmr = Number(rawMmr)
      if (!Number.isInteger(parsedMmr) || parsedMmr < 0 || parsedMmr > 5000) {
        setPlayerActionError(t('players.actions.mmrInvalid'))
        return
      }
      nextMmr = parsedMmr
    }

    setSavingPlayerId(playerId)
    setPlayerActionError(null)
    setPlayerActionSuccess(null)
    try {
      const profilePayload = buildPlayerProfileUpdateRequest(targetRow, {
        nickname: nextNickname,
        race: editingRace,
        tier: editingTier,
      })
      if (Object.keys(profilePayload).length > 0) {
        await apiClient.updateGroupPlayer(TEMP_GROUP_ID, playerId, profilePayload)
      }

      const targetMmr = typeof targetRow.currentMmr === 'number' ? targetRow.currentMmr : null
      const didUpdateMmr = isSuperAdmin && nextMmr !== null && nextMmr !== targetMmr
      if (didUpdateMmr && nextMmr !== null) {
        await apiClient.updateGroupPlayerMmr(TEMP_GROUP_ID, playerId, {
          mmr: nextMmr,
        })
      }

      setEditingPlayerId(null)
      setEditingNickname('')
      setEditingRace('P')
      setEditingTier('UNASSIGNED')
      setEditingInlineMmrValue('')
      setPlayerActionSuccess(
        didUpdateMmr ? t('players.actions.updateAndMmrSuccess') : t('players.actions.updateSuccess')
      )
      await fetchRoster()
    } catch (actionError) {
      if (isApiForbiddenError(actionError)) {
        setPlayerActionError(t('common.permissionDenied'))
      } else if (isApiNotFoundError(actionError)) {
        setPlayerActionError(t('players.actions.updateNotFound'))
      } else {
        setPlayerActionError(t('players.actions.updateFailure'))
      }
    } finally {
      setSavingPlayerId(null)
    }
  }

  const handleDeletePlayer = async (player: PlayerRosterItem) => {
    if (!isAdmin) {
      setPlayerActionError(t('common.adminOnlyAction'))
      return
    }

    if (!window.confirm(t('players.actions.deleteConfirm', { nickname: player.nickname }))) {
      return
    }

    setDeletingPlayerId(player.id)
    setPlayerActionError(null)
    setPlayerActionSuccess(null)
    try {
      await apiClient.deleteGroupPlayer(TEMP_GROUP_ID, player.id)
      if (editingPlayerId === player.id) {
        setEditingPlayerId(null)
        setEditingNickname('')
        setEditingRace('P')
        setEditingTier('UNASSIGNED')
        setEditingInlineMmrValue('')
      }
      if (activityForm?.player.id === player.id) {
        setActivityForm(null)
      }
      setPlayerActionSuccess(t('players.actions.deleteSuccess'))
      await fetchRoster()
    } catch (actionError) {
      if (isApiForbiddenError(actionError)) {
        setPlayerActionError(t('common.permissionDenied'))
      } else if (isApiConflictError(actionError)) {
        if (actionError instanceof Error && actionError.message.trim().length > 0) {
          setPlayerActionError(actionError.message)
        } else {
          setPlayerActionError(t('players.actions.deleteConflict'))
        }
      } else if (isApiNotFoundError(actionError)) {
        setPlayerActionError(t('players.actions.deleteNotFound'))
      } else {
        setPlayerActionError(t('players.actions.deleteFailure'))
      }
    } finally {
      setDeletingPlayerId(null)
    }
  }

  const handleTogglePlayerActive = (player: PlayerRosterItem) => {
    if (!isAdmin) {
      setPlayerActionError(t('common.adminOnlyAction'))
      return
    }

    const nextActive = player.active === false
    setActivityForm({
      mode: nextActive ? 'reactivate' : 'deactivate',
      player,
      chatLeftAt: formatDateTimeLocalInputValue(new Date()),
      chatLeftReason: '',
      chatRejoinedAt: formatDateTimeLocalInputValue(new Date()),
    })
    setPlayerActionError(null)
    setPlayerActionSuccess(null)
  }

  const handleCancelActivityForm = () => {
    setActivityForm(null)
  }

  const handleSubmitActivityForm = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!isAdmin || activityForm === null) {
      setPlayerActionError(t('common.adminOnlyAction'))
      return
    }

    const { mode, player } = activityForm
    const nextActive = mode === 'reactivate'
    let chatLeftAt: string | null = null
    let chatLeftReason: string | null = null
    let chatRejoinedAt: string | null = null

    if (mode === 'deactivate') {
      chatLeftAt = parseDateTimeLocalInput(activityForm.chatLeftAt)
      chatLeftReason = activityForm.chatLeftReason.trim()
      if (chatLeftAt === null) {
        setPlayerActionError(t('players.actions.chatLeftAtRequired'))
        setPlayerActionSuccess(null)
        return
      }
      if (chatLeftReason.length === 0) {
        setPlayerActionError(t('players.actions.chatLeftReasonRequired'))
        setPlayerActionSuccess(null)
        return
      }
    } else {
      chatRejoinedAt = parseDateTimeLocalInput(activityForm.chatRejoinedAt)
      if (chatRejoinedAt === null) {
        setPlayerActionError(t('players.actions.chatRejoinedAtRequired'))
        setPlayerActionSuccess(null)
        return
      }
    }

    setTogglingPlayerId(player.id)
    setPlayerActionError(null)
    setPlayerActionSuccess(null)
    try {
      await apiClient.updateGroupPlayer(TEMP_GROUP_ID, player.id, {
        active: nextActive,
        chatLeftAt,
        chatLeftReason,
        chatRejoinedAt,
      })
      setRows((currentRows) => {
        const nextRows = currentRows.map((row) =>
          row.id === player.id
            ? applyPlayerActivityTransition(row, {
                nextActive,
                chatLeftAt: chatLeftAt ?? undefined,
                chatLeftReason: chatLeftReason ?? undefined,
                chatRejoinedAt: chatRejoinedAt ?? undefined,
              })
            : row
        )
        if (rosterView !== 'inactive' && !nextActive) {
          return nextRows.filter((row) => row.id !== player.id)
        }

        return nextRows
      })
      if (editingPlayerId === player.id) {
        setEditingPlayerId(null)
        setEditingNickname('')
        setEditingRace('P')
        setEditingTier('UNASSIGNED')
        setEditingInlineMmrValue('')
      }
      setPlayerActionSuccess(
        nextActive ? t('players.actions.reactivateSuccess') : t('players.actions.deactivateSuccess')
      )
      setActivityForm(null)
    } catch (actionError) {
      if (isApiForbiddenError(actionError)) {
        setPlayerActionError(t('common.permissionDenied'))
      } else if (isApiNotFoundError(actionError)) {
        setPlayerActionError(t('players.actions.updateNotFound'))
      } else {
        setPlayerActionError(
          nextActive ? t('players.actions.reactivateFailure') : t('players.actions.deactivateFailure')
        )
      }
    } finally {
      setTogglingPlayerId(null)
    }
  }

  const handleOpenGameTypeStats = useCallback(async (player: PlayerRosterItem) => {
    setGameTypeStatsPlayer({ id: player.id, nickname: player.nickname })
    setGameTypeStats(null)
    setGameTypeStatsError(null)
    setGameTypeStatsLoading(true)

    try {
      const response = await apiClient.getGroupPlayerRaceStatsForPlayer(TEMP_GROUP_ID, player.id)
      setGameTypeStats(response)
    } catch {
      setGameTypeStatsError(t('statsModal.loadError'))
    } finally {
      setGameTypeStatsLoading(false)
    }
  }, [])

  const handleCloseGameTypeStats = useCallback(() => {
    setGameTypeStatsPlayer(null)
    setGameTypeStats(null)
    setGameTypeStatsError(null)
    setGameTypeStatsLoading(false)
  }, [])

  const handleRosterViewChange = useCallback(
    (view: Exclude<PlayerRosterView, 'active'>, checked: boolean) => {
      rosterRequestId.current += 1
      lastParticipationRequestId.current += 1
      setLastParticipation(null)
      setRosterView(checked ? view : 'active')
    },
    []
  )

  const handleToggleLastParticipation = useCallback(
    async (player: PlayerRosterItem) => {
      if (!isAdmin || rosterView !== 'dormant') {
        return
      }

      if (lastParticipation?.playerId === player.id) {
        lastParticipationRequestId.current += 1
        setLastParticipation(null)
        return
      }

      const requestId = lastParticipationRequestId.current + 1
      lastParticipationRequestId.current = requestId
      setLastParticipation({
        playerId: player.id,
        status: 'loading',
        lastPlayedAt: null,
      })

      try {
        const response = await apiClient.getGroupPlayerLastParticipation(
          TEMP_GROUP_ID,
          player.id
        )
        if (lastParticipationRequestId.current !== requestId) {
          return
        }
        setLastParticipation({
          playerId: player.id,
          status: 'success',
          lastPlayedAt: response.lastPlayedAt,
        })
      } catch {
        if (lastParticipationRequestId.current !== requestId) {
          return
        }
        setLastParticipation({
          playerId: player.id,
          status: 'error',
          lastPlayedAt: null,
        })
      }
    },
    [isAdmin, lastParticipation?.playerId, rosterView]
  )

  const sortedRows = useMemo(
    () => sortRosterRows(rows, showMmrColumn),
    [rows, showMmrColumn]
  )
  const activityRows = useMemo(
    () => filterPlayerRosterByView(sortedRows, effectiveRosterView, dormantPlayerIds),
    [dormantPlayerIds, effectiveRosterView, sortedRows]
  )
  const filteredRows = useMemo(() => {
    const searchText = search.trim().toLowerCase()
    return activityRows.filter((row) => {
      const matchesRace = row.identityHidden || raceFilter === 'ALL' || row.race === raceFilter
      const matchesSearch =
        searchText.length === 0 || row.nickname.toLowerCase().includes(searchText)
      return matchesRace && matchesSearch
    })
  }, [activityRows, raceFilter, search])
  const activePlayerCount = useMemo(
    () => filterPlayerRosterByView(rows, 'active').length,
    [rows]
  )
  const inactivePlayerCount = useMemo(
    () => filterPlayerRosterByView(rows, 'inactive').length,
    [rows]
  )
  const dormantPlayerCount = useMemo(
    () => filterPlayerRosterByView(rows, 'dormant', dormantPlayerIds).length,
    [dormantPlayerIds, rows]
  )

  const showInactiveRetentionColumns = rosterView === 'inactive'
  const showTierColumn = !showInactiveRetentionColumns
  const showStatusColumn = isAdmin
  const showRosterMmrColumn = showMmrColumn && !showInactiveRetentionColumns
  const showGameTypeStatsColumn = !showInactiveRetentionColumns
  const showActionsColumn = isAdmin
  const tableColumnCount =
    5 +
    (showTierColumn ? 1 : 0) +
    (showStatusColumn ? 1 : 0) +
    (showRosterMmrColumn ? 1 : 0) +
    (showGameTypeStatsColumn ? 1 : 0) +
    (showActionsColumn ? 1 : 0)
  const downloadableRows = useMemo(
    () =>
      rows
        .filter((row) => row.active !== false && !row.identityHidden)
        .sort(comparePlayersByTierThenNickname),
    [rows]
  )

  const handleDownloadTierSortedRoster = useCallback(() => {
    if (!isSuperAdmin) {
      setPlayerActionError(t('common.adminOnlyAction'))
      return
    }

    if (downloadableRows.length === 0) {
      setPlayerActionError(t('players.download.empty'))
      return
    }

    const header = [
      t('players.download.headers.index'),
      t('players.download.headers.tier'),
      t('players.download.headers.nickname'),
    ]

    const lines = [
      header.map(escapeCsvCell).join(','),
      ...downloadableRows.map((row, index) =>
        [
          String(index + 1),
          displayTier(row) === 'UNASSIGNED' ? t('players.table.unassigned') : displayTier(row),
          row.nickname,
        ]
          .map(escapeCsvCell)
          .join(',')
      ),
    ]

    const now = new Date()
    const fileDate = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(
      now.getDate()
    ).padStart(2, '0')}`
    const fileName = `players-tier-name-${fileDate}.csv`
    const blob = new Blob([`\uFEFF${lines.join('\n')}`], { type: 'text/csv;charset=utf-8;' })
    triggerBlobDownload(blob, fileName)

    setPlayerActionError(null)
    setPlayerActionSuccess(t('players.download.success', { count: downloadableRows.length }))
  }, [downloadableRows, isSuperAdmin])

  const handleDownloadMonthlyTierBoard = useCallback(async () => {
    if (!isSuperAdmin) {
      setPlayerActionError(t('common.adminOnlyAction'))
      return
    }

    setTierBoardDownloading(true)
    setPlayerActionError(null)
    setPlayerActionSuccess(null)
    try {
      const tierBoardItems = await apiClient.getGroupPlayerTierBoard(TEMP_GROUP_ID)
      const tierBoardPlayers = selectMonthlyTierBoardPlayers(tierBoardItems)
      if (tierBoardPlayers.length === 0) {
        setPlayerActionError(t('players.monthlyTierBoard.empty'))
        return
      }

      const image = await createMonthlyTierBoardPng(tierBoardPlayers, {
        title: t('common.tierBoard.title'),
        index: t('common.tierBoard.index'),
        unassigned: t('common.tierBoard.unassigned'),
        tierSuffix: t('players.monthlyTierBoard.tierSuffix'),
        totalSuffix: t('players.monthlyTierBoard.totalSuffix'),
      })
      triggerBlobDownload(image.blob, image.fileName)
      setPlayerActionSuccess(
        t('players.monthlyTierBoard.success', {
          period: image.periodLabel,
          count: image.totalCount,
        }),
      )
    } catch (downloadError) {
      if (isApiUnauthorizedError(downloadError)) {
        setPlayerActionError(t('common.adminLoginRequired'))
      } else if (isApiForbiddenError(downloadError)) {
        setPlayerActionError(t('common.permissionDenied'))
      } else {
        setPlayerActionError(t('players.monthlyTierBoard.failure'))
      }
    } finally {
      setTierBoardDownloading(false)
    }
  }, [isSuperAdmin])

  return (
    <section className="space-y-6">
      {error && (
        <Alert variant="destructive" appearance="light">
          <AlertIcon icon="destructive">!</AlertIcon>
          <AlertContent>
            <AlertTitle>{t('common.errorPrefix')}</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </AlertContent>
        </Alert>
      )}

      {isAdmin && (
        <article id="player-import" className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
          <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('players.import.title')}</h3>
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{t('players.import.description')}</p>
          <form className="mt-3 space-y-3" onSubmit={handleRegisterPlayer}>
            <div className="grid gap-3 md:grid-cols-[minmax(0,1.5fr)_minmax(9rem,0.75fr)_minmax(9rem,0.75fr)]">
              <label className="space-y-1 text-xs font-medium text-slate-500 dark:text-slate-400">
                {t('players.import.nicknameLabel')}
                <input
                  type="text"
                  value={registrationNickname}
                  onChange={(event) => setRegistrationNickname(event.target.value)}
                  placeholder={t('players.import.nicknamePlaceholder')}
                  className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-slate-500 dark:focus:ring-slate-700"
                />
              </label>

              <label className="space-y-1 text-xs font-medium text-slate-500 dark:text-slate-400">
                {t('players.import.tierLabel')}
                <select
                  value={registrationTier}
                  onChange={(event) =>
                    setRegistrationTier(event.target.value as PlayerRegistrationTier | '')
                  }
                  className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-slate-500 dark:focus:ring-slate-700"
                >
                  <option value="">{t('players.import.tierPlaceholder')}</option>
                  {PLAYER_REGISTRATION_TIER_OPTIONS.map((tierOption) => (
                    <option key={tierOption} value={tierOption}>
                      {tierOption === 'UNASSIGNED'
                        ? t('players.import.unassignedTierOption')
                        : tierOption}
                    </option>
                  ))}
                </select>
              </label>

              <label className="space-y-1 text-xs font-medium text-slate-500 dark:text-slate-400">
                {t('players.import.raceLabel')}
                <select
                  value={registrationRace}
                  onChange={(event) => setRegistrationRace(event.target.value as PlayerRace | '')}
                  className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-slate-500 dark:focus:ring-slate-700"
                >
                  <option value="">{t('players.import.racePlaceholder')}</option>
                  {PLAYER_RACE_OPTIONS.map((raceOption) => (
                    <option key={raceOption} value={raceOption}>
                      {raceOption}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            {importError && (
              <Alert variant="destructive" appearance="light" size="sm">
                <AlertIcon icon="destructive">!</AlertIcon>
                <AlertContent>
                  <AlertDescription>{importError}</AlertDescription>
                </AlertContent>
              </Alert>
            )}
            {importSuccess && (
              <p className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300">
                {importSuccess}
              </p>
            )}

            <button
              type="submit"
              disabled={importing}
              className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white dark:disabled:bg-slate-700 dark:disabled:text-slate-400"
            >
              {importing ? t('players.import.loading') : t('players.import.button')}
            </button>
          </form>
        </article>
      )}

      {(playerActionError || playerActionSuccess || ownRaceActionError || ownRaceActionSuccess) && (
        <div className="space-y-2">
          {(playerActionError || ownRaceActionError) && (
            <Alert variant="destructive" appearance="light" size="sm">
              <AlertIcon icon="destructive">!</AlertIcon>
              <AlertContent>
                <AlertDescription>{playerActionError ?? ownRaceActionError}</AlertDescription>
              </AlertContent>
            </Alert>
          )}
          {(playerActionSuccess || ownRaceActionSuccess) && (
          <p className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300">
              {playerActionSuccess ?? ownRaceActionSuccess}
            </p>
          )}
        </div>
      )}

      {!isAdmin && !loading && !rows.some((row) => row.isOwnPlayer) && (
        <p className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs leading-5 text-slate-600 dark:border-slate-700 dark:bg-slate-800/60 dark:text-slate-300">
          {t('players.ownRace.notLinkedNotice')}
        </p>
      )}

      {activityForm !== null && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/30 px-4 dark:bg-black/70">
          <form
            className="w-full max-w-md rounded-xl border border-slate-200 bg-white p-5 shadow-xl dark:border-slate-700 dark:bg-slate-900"
            onSubmit={handleSubmitActivityForm}
          >
            <div className="space-y-1">
                <h3 className="text-base font-semibold text-slate-900 dark:text-slate-100">
                {activityForm.mode === 'deactivate'
                  ? t('players.activityForm.deactivateTitle')
                  : t('players.activityForm.reactivateTitle')}
              </h3>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                {t('players.activityForm.target', { nickname: activityForm.player.nickname })}
              </p>
            </div>

            {playerActionError && (
              <p className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700 dark:border-rose-800 dark:bg-rose-950/40 dark:text-rose-300">
                {playerActionError}
              </p>
            )}

            <div className="mt-4 space-y-3">
              {activityForm.mode === 'deactivate' ? (
                <>
                <label className="block space-y-1 text-xs font-medium text-slate-600 dark:text-slate-300">
                    {t('players.activityForm.chatLeftAtLabel')}
                    <input
                      type="datetime-local"
                      required
                      value={activityForm.chatLeftAt}
                      onChange={(event) =>
                        setActivityForm((current) =>
                          current === null ? current : { ...current, chatLeftAt: event.target.value }
                        )
                      }
                    className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-slate-500 dark:focus:ring-slate-700"
                    />
                  </label>
                <label className="block space-y-1 text-xs font-medium text-slate-600 dark:text-slate-300">
                    {t('players.activityForm.chatLeftReasonLabel')}
                    <select
                      required
                      value={activityForm.chatLeftReason}
                      onChange={(event) =>
                        setActivityForm((current) =>
                          current === null ? current : { ...current, chatLeftReason: event.target.value }
                        )
                      }
                      className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-slate-500 dark:focus:ring-slate-700"
                    >
                      <option value="" disabled>
                        {t('players.activityForm.chatLeftReasonPlaceholder')}
                      </option>
                      {PLAYER_INACTIVE_REASON_OPTIONS.map((reason) => (
                        <option key={reason} value={reason}>
                          {reason}
                        </option>
                      ))}
                    </select>
                    <span className="block text-[11px] font-normal leading-4 text-amber-700 dark:text-amber-300">
                      {t('players.activityForm.reasonPrivacyNotice')}
                    </span>
                  </label>
                </>
              ) : (
                <label className="block space-y-1 text-xs font-medium text-slate-600 dark:text-slate-300">
                  {t('players.activityForm.chatRejoinedAtLabel')}
                  <input
                    type="datetime-local"
                    required
                    value={activityForm.chatRejoinedAt}
                    onChange={(event) =>
                      setActivityForm((current) =>
                        current === null ? current : { ...current, chatRejoinedAt: event.target.value }
                      )
                    }
                    className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-slate-500 dark:focus:ring-slate-700"
                  />
                </label>
              )}
            </div>

            <div className="mt-5 flex justify-end gap-2">
              <button
                type="button"
                disabled={togglingPlayerId !== null}
                onClick={handleCancelActivityForm}
                  className="rounded-md border border-slate-200 px-3 py-2 text-xs font-medium text-slate-700 transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
              >
                {t('players.actions.cancel')}
              </button>
              <button
                type="submit"
                disabled={togglingPlayerId !== null}
                  className="rounded-md bg-slate-900 px-3 py-2 text-xs font-medium text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white dark:disabled:bg-slate-700 dark:disabled:text-slate-400"
              >
                {togglingPlayerId !== null
                  ? t('players.actions.toggling')
                  : t('players.activityForm.save')}
              </button>
            </div>
          </form>
        </div>
      )}

      <PlayerGameTypeStatsModal
        open={gameTypeStatsPlayer !== null}
        playerName={gameTypeStatsPlayer?.nickname ?? ''}
        stats={gameTypeStats}
        loading={gameTypeStatsLoading}
        error={gameTypeStatsError}
        onClose={handleCloseGameTypeStats}
      />

      <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
        {isAdmin && (
          <div className="mb-3 flex flex-wrap items-center gap-2">
            {rosterView === 'inactive' ? (
              <span className="rounded-md border border-slate-300 bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-700 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-200">
                {t('players.filters.inactiveCount', { count: inactivePlayerCount })}
              </span>
            ) : rosterView === 'dormant' ? (
              <span className="rounded-md border border-amber-200 bg-amber-50 px-2.5 py-1 text-xs font-semibold text-amber-800 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200">
                {t('players.filters.dormantCount', { count: dormantPlayerCount })}
              </span>
            ) : (
              <span className="rounded-md border border-sky-200 bg-sky-50 px-2.5 py-1 text-xs font-semibold text-sky-800 dark:border-sky-800 dark:bg-sky-950/40 dark:text-sky-200">
                {t('players.filters.activeCount', { count: activePlayerCount })}
              </span>
            )}
          </div>
        )}
        <div className="grid gap-3 md:grid-cols-3">
          <label className="space-y-1 text-xs font-medium text-slate-500 dark:text-slate-400 md:col-span-2">
            {t('players.filters.searchLabel')}
            <input
              type="text"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder={t('players.filters.searchPlaceholder')}
              className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-slate-500 dark:focus:ring-slate-700"
            />
          </label>

          <label className="space-y-1 text-xs font-medium text-slate-500 dark:text-slate-400">
            {t('players.filters.raceLabel')}
            <select
              value={raceFilter}
              onChange={(event) => setRaceFilter(event.target.value as RaceFilter)}
              className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-slate-500 dark:focus:ring-slate-700"
            >
              <option value="ALL">{t('common.all')}</option>
              {PLAYER_RACE_OPTIONS.map((raceOption) => (
                <option key={raceOption} value={raceOption}>
                  {raceOption}
                </option>
              ))}
            </select>
          </label>
        </div>
        {isAdmin && (
          <div className="mt-3 flex flex-wrap items-center gap-x-5 gap-y-2">
            <label className="inline-flex min-h-8 items-center gap-2 text-xs font-medium text-slate-600 dark:text-slate-300">
              <input
                type="checkbox"
                checked={rosterView === 'inactive'}
                onChange={(event) =>
                  handleRosterViewChange('inactive', event.target.checked)
                }
                className="h-4 w-4 rounded border-slate-300 text-slate-900 focus:ring-slate-400 dark:border-slate-600 dark:bg-slate-950 dark:text-slate-100"
              />
              <span>{t('players.filters.includeInactive')}</span>
            </label>
            <label className="inline-flex min-h-8 items-center gap-2 text-xs font-medium text-slate-600 dark:text-slate-300">
              <input
                type="checkbox"
                checked={rosterView === 'dormant'}
                onChange={(event) =>
                  handleRosterViewChange('dormant', event.target.checked)
                }
                className="h-4 w-4 rounded border-slate-300 text-amber-700 focus:ring-amber-400 dark:border-slate-600 dark:bg-slate-950 dark:text-amber-300"
              />
              <span>{t('players.filters.includeDormant')}</span>
            </label>
          </div>
        )}
        {isSuperAdmin && rosterView !== 'inactive' && (
          <div className="mt-3 flex flex-wrap justify-end gap-2">
            <button
              type="button"
              onClick={() => void handleDownloadMonthlyTierBoard()}
              disabled={tierBoardDownloading || loading}
              className="rounded-lg border border-slate-900 bg-slate-900 px-3 py-2 text-xs font-medium text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:border-slate-300 disabled:bg-slate-300 dark:border-slate-100 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white dark:disabled:border-slate-700 dark:disabled:bg-slate-700 dark:disabled:text-slate-400"
            >
              {tierBoardDownloading
                ? t('players.monthlyTierBoard.downloading')
                : t('players.monthlyTierBoard.button')}
            </button>
            <button
              type="button"
              onClick={handleDownloadTierSortedRoster}
              disabled={tierBoardDownloading}
              className="rounded-lg border border-slate-300 px-3 py-2 text-xs font-medium text-slate-700 transition-colors hover:border-slate-900 hover:bg-slate-900 hover:text-white disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-200 dark:hover:border-slate-300 dark:hover:bg-slate-100 dark:hover:text-slate-900"
            >
              {t('players.download.button')}
            </button>
          </div>
        )}
      </div>

      {isAdmin && rosterView === 'inactive' && (
        <p className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs leading-5 text-amber-800 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200">
          {t('players.table.inactiveRetentionNotice')}
        </p>
      )}

      <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-900">
        <table
          className={`${rosterView === 'inactive' ? 'min-w-[48rem]' : 'min-w-full'} text-left text-sm`}
        >
          <thead className="bg-slate-50 text-xs tracking-wide text-slate-500 dark:bg-slate-800/80 dark:text-slate-300">
            <tr>
              <th className="whitespace-nowrap px-4 py-3">{t('players.table.nickname')}</th>
              <th className="whitespace-nowrap px-4 py-3">{t('players.table.race')}</th>
              {showTierColumn && <th className="whitespace-nowrap px-4 py-3">{t('players.table.tier')}</th>}
              {showStatusColumn && <th className="min-w-[11rem] whitespace-nowrap px-4 py-3">{t('players.table.status')}</th>}
              {showRosterMmrColumn && <th className="whitespace-nowrap px-4 py-3">{t('players.table.currentMmr')}</th>}
              <th className="whitespace-nowrap px-4 py-3">{t('players.table.wins')}</th>
              <th className="whitespace-nowrap px-4 py-3">{t('players.table.losses')}</th>
              <th className="whitespace-nowrap px-4 py-3">{t('players.table.games')}</th>
              {showGameTypeStatsColumn && (
                <th className="whitespace-nowrap px-4 py-3">{t('players.table.gameTypeStats')}</th>
              )}
              {showActionsColumn && <th className="whitespace-nowrap px-4 py-3">{t('players.table.actions')}</th>}
            </tr>
          </thead>
          <tbody>
            {loading &&
              (
              <tr className="border-t border-slate-100 dark:border-slate-800">
                  <td
                    className="px-4 py-3"
                    colSpan={tableColumnCount}
                  >
                    <LoadingIndicator label={t('common.loading')} />
                  </td>
                </tr>
              )}

            {!loading && filteredRows.length === 0 && (
              <tr className="border-t border-slate-100 dark:border-slate-800">
                  <td
                    className="px-4 py-8 text-center text-sm text-slate-500 dark:text-slate-400"
                    colSpan={tableColumnCount}
                  >
                  {t('players.table.empty')}
                </td>
              </tr>
            )}

            {!loading &&
              filteredRows.map((row) => {
                const identityHidden = row.identityHidden === true
                const lifecycleStatus = resolveLifecycleStatus(row)
                const isActive = lifecycleStatus === 'ACTIVE' && row.active !== false
                const isOperationallyInactive = lifecycleStatus === 'INACTIVE'
                const isWithdrawn = lifecycleStatus === 'WITHDRAWN'
                const lifecycleLabel = isActive
                  ? t('players.table.active')
                  : isOperationallyInactive
                    ? t('players.table.operationallyInactive')
                    : isWithdrawn
                      ? t('players.table.withdrawn')
                      : t('players.table.anonymized')
                const canReactivate = isOperationallyInactive && !identityHidden && row.id > 0
                const isEditing = isActive && !identityHidden && editingPlayerId === row.id
                const isSaving = savingPlayerId === row.id
                const isDeleting = deletingPlayerId === row.id
                const isToggling = togglingPlayerId === row.id
                const busy = isSaving || isDeleting || isToggling
                const canInspectLastParticipation = isAdmin && rosterView === 'dormant'
                const lastParticipationExpanded =
                  canInspectLastParticipation && lastParticipation?.playerId === row.id
                const lastParticipationDetailsId = `player-${row.id}-last-participation`

                return (
                  <tr
                    key={row.id}
                    className={`border-t border-slate-100 transition-colors dark:border-slate-800 ${
                      isActive
                        ? 'hover:bg-slate-50/70 dark:hover:bg-slate-800/60'
                        : 'bg-slate-100/90 hover:bg-slate-200/70 dark:bg-slate-800/80 dark:hover:bg-slate-700/80'
                    }`}
                  >
                    <td className={`px-4 py-3 font-medium ${isActive ? 'text-slate-900 dark:text-slate-100' : 'text-slate-700 dark:text-slate-200'}`}>
                      {isEditing ? (
                        <input
                          type="text"
                          value={editingNickname}
                          onChange={(event) => setEditingNickname(event.target.value)}
                          className="w-full rounded-md border border-slate-200 bg-white px-2 py-1 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-slate-500 dark:focus:ring-slate-700"
                        />
                      ) : (
                        <div className="min-w-[8rem] space-y-1.5">
                          <div className="flex flex-wrap items-center gap-2">
                            {canInspectLastParticipation ? (
                              <button
                                type="button"
                                aria-expanded={lastParticipationExpanded}
                                aria-controls={
                                  lastParticipationExpanded
                                    ? lastParticipationDetailsId
                                    : undefined
                                }
                                aria-label={t('players.table.lastParticipationToggleAria', {
                                  nickname: row.nickname,
                                })}
                                onClick={() => handleToggleLastParticipation(row)}
                                className="rounded-sm text-left font-semibold text-slate-900 underline decoration-slate-300 underline-offset-4 transition-colors hover:text-amber-700 hover:decoration-amber-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500 focus-visible:ring-offset-2 dark:text-slate-100 dark:decoration-slate-600 dark:hover:text-amber-300 dark:focus-visible:ring-offset-slate-900"
                              >
                                {row.nickname}
                              </button>
                            ) : (
                              <span>{row.nickname}</span>
                            )}
                            {canInspectLastParticipation && (
                              <span className="rounded-md border border-amber-200 bg-amber-50 px-2 py-0.5 text-[11px] font-semibold text-amber-800 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200">
                                {t('players.table.dormant')}
                              </span>
                            )}
                            {!isActive && (
                              <span className="inline-flex whitespace-nowrap rounded-md border border-slate-300 bg-slate-200 px-2 py-0.5 text-[11px] font-semibold text-slate-700 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-100">
                                {lifecycleLabel}
                              </span>
                            )}
                          </div>
                          {lastParticipationExpanded && lastParticipation && (
                            <div
                              id={lastParticipationDetailsId}
                              aria-live="polite"
                              role={lastParticipation.status === 'error' ? 'alert' : 'status'}
                              className={`max-w-56 text-[11px] font-normal leading-4 ${
                                lastParticipation.status === 'error'
                                  ? 'text-rose-700 dark:text-rose-300'
                                  : 'text-slate-500 dark:text-slate-400'
                              }`}
                            >
                              {lastParticipation.status === 'loading'
                                ? t('players.table.lastParticipationLoading')
                                : lastParticipation.status === 'error'
                                  ? t('players.table.lastParticipationError')
                                  : lastParticipation.lastPlayedAt === null
                                    ? t('players.table.lastParticipationNone')
                                    : t('players.table.lastParticipationLabel', {
                                        value: formatLastParticipationDate(
                                          lastParticipation.lastPlayedAt
                                        ),
                                      })}
                            </div>
                          )}
                        </div>
                      )}
                    </td>
                    <td className={`whitespace-nowrap px-4 py-3 ${isActive ? 'text-slate-700 dark:text-slate-300' : 'text-slate-600 dark:text-slate-300'}`}>
                      {identityHidden ? (
                        <span aria-hidden="true">—</span>
                      ) : isEditing ? (
                        <select
                          value={editingRace}
                          onChange={(event) => setEditingRace(event.target.value as PlayerRace)}
                          className="w-full rounded-md border border-slate-200 bg-white px-2 py-1 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-slate-500 dark:focus:ring-slate-700"
                        >
                          {PLAYER_RACE_OPTIONS.map((raceOption) => (
                            <option key={raceOption} value={raceOption}>
                              {raceOption}
                            </option>
                          ))}
                        </select>
                      ) : !isAdmin && isActive && row.isOwnPlayer === true && ownRaceEditingPlayerId === row.id ? (
                        <div className="flex items-center gap-1.5 whitespace-nowrap">
                          <select
                            value={ownRaceEditingValue}
                            onChange={(event) => setOwnRaceEditingValue(event.target.value as PlayerRace)}
                            disabled={ownRaceSavingPlayerId === row.id}
                            className="rounded-md border border-slate-200 bg-white px-2 py-1 text-xs text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-slate-500 dark:focus:ring-slate-700"
                          >
                            {PLAYER_RACE_OPTIONS.map((raceOption) => (
                              <option key={raceOption} value={raceOption}>
                                {raceOption}
                              </option>
                            ))}
                          </select>
                          <button
                            type="button"
                            onClick={() => void handleSaveOwnRace(row)}
                            disabled={ownRaceSavingPlayerId === row.id}
                            className="rounded-md border border-slate-900 bg-slate-900 px-2 py-1 text-xs font-medium text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300 dark:border-slate-100 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white dark:disabled:bg-slate-700"
                          >
                            {ownRaceSavingPlayerId === row.id
                              ? t('players.ownRace.saving')
                              : t('players.ownRace.save')}
                          </button>
                          <button
                            type="button"
                            onClick={handleCancelOwnRaceEdit}
                            disabled={ownRaceSavingPlayerId === row.id}
                            className="rounded-md border border-slate-200 px-2 py-1 text-xs font-medium text-slate-700 transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
                          >
                            {t('players.ownRace.cancel')}
                          </button>
                        </div>
                      ) : !isAdmin && isActive && row.isOwnPlayer === true ? (
                        <button
                          type="button"
                          onClick={() => handleStartOwnRaceEdit(row)}
                          className="inline-flex items-center gap-1 rounded-md border border-transparent px-1 py-0.5 text-left underline decoration-slate-300 underline-offset-4 transition-colors hover:decoration-amber-500 dark:decoration-slate-600 dark:hover:decoration-amber-400"
                          title={t('players.ownRace.edit')}
                        >
                          {row.race}
                        </button>
                      ) : (
                        row.race
                      )}
                    </td>
                    {showTierColumn && (
                      <td className="min-w-[11rem] px-4 py-3">
                        {identityHidden ? (
                          <span className="text-slate-500 dark:text-slate-400" aria-hidden="true">—</span>
                        ) : isEditing ? (
                          <select
                            value={editingTier}
                            onChange={(event) => {
                              const nextTier = event.target.value as PlayerTierStatus
                              setEditingTier(nextTier)
                              if (isSuperAdmin) {
                                setEditingInlineMmrValue(String(resolveDefaultMmrForTier(nextTier)))
                              }
                            }}
                            className="w-full rounded-md border border-slate-200 bg-white px-2 py-1 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-slate-500 dark:focus:ring-slate-700"
                          >
                            {PLAYER_EDIT_TIER_OPTIONS.map((tierOption) => (
                              <option key={tierOption} value={tierOption}>
                                {tierOption === 'UNASSIGNED'
                                  ? t('players.table.unassigned')
                                  : tierOption}
                              </option>
                            ))}
                          </select>
                        ) : (
                          <span
                            className={`rounded-md px-2 py-1 text-xs font-semibold ${getTierBadgeClass(
                              displayTier(row)
                            )}`}
                          >
                            {displayTier(row) === 'UNASSIGNED'
                              ? t('players.table.unassigned')
                              : displayTier(row)}
                          </span>
                        )}
                      </td>
                    )}
                    {showStatusColumn && (
                      <td className="px-4 py-3">
                        <div className="space-y-1">
                          <span
                            className={`inline-flex whitespace-nowrap rounded-md px-2 py-1 text-xs font-semibold ${
                              isActive
                                ? 'border border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300'
                                : isWithdrawn
                                  ? 'border border-rose-200 bg-rose-50 text-rose-700 dark:border-rose-800 dark:bg-rose-950/40 dark:text-rose-300'
                                  : isOperationallyInactive
                                    ? 'border border-amber-200 bg-amber-50 text-amber-800 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200'
                                    : 'border border-slate-300 bg-slate-200 text-slate-700 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-100'
                            }`}
                          >
                            {lifecycleLabel}
                          </span>
                          {!isActive && row.chatLeftAt && (
                              <div className="text-[11px] text-slate-500 dark:text-slate-400">
                              {t(isWithdrawn ? 'players.table.withdrawnAt' : 'players.table.inactiveAt', {
                                value: formatChatRecordDisplay(row.chatLeftAt),
                              })}
                            </div>
                          )}
                          {!isActive && row.chatLeftReason && (
                              <div className="max-w-52 text-[11px] leading-4 text-slate-500 dark:text-slate-400">
                              {t(
                                isWithdrawn
                                  ? 'players.table.withdrawnReason'
                                  : 'players.table.inactiveReason',
                                { value: row.chatLeftReason }
                              )}
                            </div>
                          )}
                          {!isActive && row.identityRetainedUntil && (
                            <div className="text-[11px] text-slate-500 dark:text-slate-400">
                              {t('players.table.identityRetainedUntil', {
                                value: formatChatRecordDisplay(row.identityRetainedUntil),
                              })}
                            </div>
                          )}
                          {isActive && row.chatRejoinedAt && (
                              <div className="text-[11px] text-emerald-700 dark:text-emerald-300">
                              {t('players.table.chatRejoinedAt', {
                                value: formatChatRecordDisplay(row.chatRejoinedAt),
                              })}
                            </div>
                          )}
                        </div>
                      </td>
                    )}
                    {showRosterMmrColumn && (
                      <td className={`px-4 py-3 ${isActive ? 'text-slate-700 dark:text-slate-300' : 'text-slate-600 dark:text-slate-300'}`}>
                        {identityHidden ? (
                          <span aria-hidden="true">—</span>
                        ) : isEditing && isSuperAdmin ? (
                          <input
                            type="number"
                            min={0}
                            max={5000}
                            step={1}
                            value={editingInlineMmrValue}
                            onChange={(event) => setEditingInlineMmrValue(event.target.value)}
                            className="w-24 rounded-md border border-slate-200 bg-white px-2 py-1 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-slate-500 dark:focus:ring-slate-700"
                          />
                        ) : (
                          formatMmrValue(row.currentMmr)
                        )}
                      </td>
                    )}
                    <td className={`whitespace-nowrap px-4 py-3 ${isActive ? 'text-slate-700 dark:text-slate-300' : 'text-slate-600 dark:text-slate-300'}`}>{identityHidden ? '—' : row.wins}</td>
                    <td className={`whitespace-nowrap px-4 py-3 ${isActive ? 'text-slate-700 dark:text-slate-300' : 'text-slate-600 dark:text-slate-300'}`}>{identityHidden ? '—' : row.losses}</td>
                    <td className={`whitespace-nowrap px-4 py-3 ${isActive ? 'text-slate-700 dark:text-slate-300' : 'text-slate-600 dark:text-slate-300'}`}>{identityHidden ? '—' : row.games}</td>
                    {showGameTypeStatsColumn && (
                      <td className="whitespace-nowrap px-4 py-3">
                        {identityHidden ? (
                          <span className="text-slate-500 dark:text-slate-400" aria-hidden="true">—</span>
                        ) : (
                          <button
                            type="button"
                            disabled={gameTypeStatsLoading && gameTypeStatsPlayer?.id === row.id}
                            onClick={() => handleOpenGameTypeStats(row)}
                            className="rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-700 transition-colors hover:border-emerald-600 hover:bg-emerald-600 hover:text-white disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-200 dark:hover:border-emerald-400 dark:hover:bg-emerald-700"
                          >
                            {gameTypeStatsLoading && gameTypeStatsPlayer?.id === row.id
                              ? t('statsModal.buttonLoading')
                              : t('statsModal.button')}
                          </button>
                        )}
                      </td>
                    )}
                    {showActionsColumn && (
                      <td className="px-4 py-3">
                        {identityHidden ? (
                          <span className="text-slate-500 dark:text-slate-400" aria-hidden="true">—</span>
                        ) : !isActive ? (
                          canReactivate ? (
                            <button
                              type="button"
                              disabled={busy || editingPlayerId !== null || activityForm !== null}
                              onClick={() => handleTogglePlayerActive(row)}
                              className="rounded-md border border-emerald-300 px-2.5 py-1 text-xs font-medium text-emerald-700 transition-colors hover:border-emerald-600 hover:bg-emerald-600 hover:text-white disabled:cursor-not-allowed disabled:opacity-60 dark:border-emerald-700 dark:text-emerald-300 dark:hover:border-emerald-400 dark:hover:bg-emerald-700"
                            >
                              {isToggling
                                ? t('players.actions.toggling')
                                : t('players.actions.reactivate')}
                            </button>
                          ) : (
                            <span className="text-slate-500 dark:text-slate-400" aria-hidden="true">—</span>
                          )
                        ) : (
                          <div className="flex flex-wrap gap-2">
                          {isEditing ? (
                            <>
                              <button
                                type="button"
                                disabled={busy}
                                onClick={() => handleSaveEdit(row.id)}
                            className="rounded-md bg-slate-900 px-2.5 py-1 text-xs font-medium text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white dark:disabled:bg-slate-700 dark:disabled:text-slate-400"
                              >
                                {isSaving ? t('players.actions.saving') : t('players.actions.save')}
                              </button>
                              <button
                                type="button"
                                disabled={busy}
                                onClick={handleCancelEdit}
                            className="rounded-md border border-slate-200 px-2.5 py-1 text-xs font-medium text-slate-700 transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
                              >
                                {t('players.actions.cancel')}
                              </button>
                            </>
                          ) : (
                            <button
                              type="button"
                              disabled={
                                editingPlayerId !== null ||
                                deletingPlayerId !== null ||
                                activityForm !== null
                              }
                              onClick={() => handleStartEdit(row)}
                          className="rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-700 transition-colors hover:border-indigo-600 hover:bg-indigo-600 hover:text-white disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-200 dark:hover:border-indigo-400 dark:hover:bg-indigo-700"
                            >
                              {t('players.actions.edit')}
                            </button>
                          )}

                          <button
                            type="button"
                            disabled={busy || editingPlayerId !== null || activityForm !== null}
                            onClick={() => handleTogglePlayerActive(row)}
                            className="rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-700 transition-colors hover:border-slate-900 hover:bg-slate-900 hover:text-white disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-200 dark:hover:border-slate-300 dark:hover:bg-slate-100 dark:hover:text-slate-900"
                          >
                            {isToggling
                              ? t('players.actions.toggling')
                              : t('players.actions.deactivate')}
                          </button>

                          <button
                            type="button"
                            disabled={busy || editingPlayerId !== null || activityForm !== null}
                            onClick={() => handleDeletePlayer(row)}
                            className="rounded-md border border-rose-300 px-2.5 py-1 text-xs font-medium text-rose-700 transition-colors hover:border-rose-600 hover:bg-rose-600 hover:text-white disabled:cursor-not-allowed disabled:opacity-60 dark:border-rose-700 dark:text-rose-300 dark:hover:border-rose-400 dark:hover:bg-rose-700"
                          >
                            {isDeleting ? t('players.actions.deleting') : t('players.actions.delete')}
                          </button>
                          </div>
                        )}
                      </td>
                    )}
                  </tr>
                )
              })}
          </tbody>
        </table>
      </div>
    </section>
  )
}
