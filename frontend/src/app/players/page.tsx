'use client'

import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react'
import { useAdminAuth } from '@/lib/admin-auth'
import { apiClient, isApiConflictError, isApiForbiddenError, isApiNotFoundError, isApiUnauthorizedError } from '@/lib/api'
import { Alert, AlertContent, AlertDescription, AlertIcon, AlertTitle } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { t } from '@/lib/i18n'
import { useMmrVisibility } from '@/lib/mmr-visibility'
import { toTierOrder } from '@/lib/player-tier'
import {
  buildPlayerProfileUpdateRequest,
  resolveDefaultMmrForTier,
  resolveEditableMmrValue,
} from '@/lib/player-edit'
import type { PlayerRace, PlayerRosterItem, PlayerTierStatus } from '@/types/api'

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
const PLAYER_RACE_OPTIONS: PlayerRace[] = ['P', 'T', 'Z', 'PT', 'PZ', 'TZ', 'PTZ']
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
const PLAYER_DORMANCY_FLOOR_TIER_OPTIONS: PlayerTierStatus[] = [
  'UNASSIGNED',
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
]
const REASSIGNMENT_IMPORT_TIER = '\uC7AC\uBC30\uC815\uB300\uC0C1'

function comparePlayersByTierThenNickname(a: PlayerRosterItem, b: PlayerRosterItem): number {
  const tierDiff = toTierOrder(a.tier) - toTierOrder(b.tier)
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

function formatMmrValue(value: number | undefined): string {
  if (typeof value !== 'number') {
    return '-'
  }
  return value === 0 ? 'None' : String(value)
}

function formatTierOption(tier: PlayerTierStatus | undefined): string {
  if (tier === undefined || tier === 'UNASSIGNED') {
    return t('players.dormancyFloor.defaultPolicy')
  }
  return tier
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
      return 'bg-amber-100 text-amber-800'
    case 'A+':
    case 'A':
    case 'A-':
      return 'bg-indigo-100 text-indigo-800'
    case 'B+':
    case 'B':
    case 'B-':
      return 'bg-blue-100 text-blue-800'
    case 'C+':
    case 'C':
    case 'C-':
    case 'D':
      return 'bg-emerald-100 text-emerald-800'
    case 'UNASSIGNED':
      return 'bg-rose-100 text-rose-800'
    default:
      return 'bg-slate-100 text-slate-700'
  }
}

export default function PlayersPage() {
  const { isAdmin, isSuperAdmin, canViewMmr } = useAdminAuth()
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
  const [editingDormancyFloorTier, setEditingDormancyFloorTier] =
    useState<PlayerTierStatus>('UNASSIGNED')
  const [savingPlayerId, setSavingPlayerId] = useState<number | null>(null)
  const [deletingPlayerId, setDeletingPlayerId] = useState<number | null>(null)
  const [togglingPlayerId, setTogglingPlayerId] = useState<number | null>(null)
  const [activityForm, setActivityForm] = useState<ActivityFormState | null>(null)
  const [showInactive, setShowInactive] = useState<boolean>(false)
  const [playerActionError, setPlayerActionError] = useState<string | null>(null)
  const [playerActionSuccess, setPlayerActionSuccess] = useState<string | null>(null)

  const fetchRoster = useCallback(async () => {
    setLoading(true)
    setError(null)

    try {
      const response = await apiClient.getGroupPlayers(TEMP_GROUP_ID, {
        includeInactive: isAdmin && showInactive,
      })
      setRows(sortRosterRows(response, showMmrColumn))
    } catch {
      setRows([])
      setError(t('players.loadError'))
    } finally {
      setLoading(false)
    }
  }, [isAdmin, showInactive, showMmrColumn])

  useEffect(() => {
    void fetchRoster()
  }, [fetchRoster])

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
      setShowInactive(false)
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

  const handleStartEdit = (player: PlayerRosterItem) => {
    if (!isAdmin) {
      return
    }

    setEditingPlayerId(player.id)
    setEditingNickname(player.nickname)
    setEditingRace(player.race)
    setEditingTier(player.tier)
    setEditingInlineMmrValue(resolveEditableMmrValue(player))
    setEditingDormancyFloorTier(player.dormancyMmrFloorTier ?? 'UNASSIGNED')
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
    setEditingDormancyFloorTier('UNASSIGNED')
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
        dormancyMmrFloorTier: isSuperAdmin ? editingDormancyFloorTier : undefined,
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
      setEditingDormancyFloorTier('UNASSIGNED')
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
        setEditingDormancyFloorTier('UNASSIGNED')
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
      if (chatLeftReason.length > 500) {
        setPlayerActionError(t('players.actions.chatLeftReasonTooLong'))
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
            ? {
                ...row,
                active: nextActive,
                chatLeftAt: nextActive ? row.chatLeftAt : chatLeftAt ?? undefined,
                chatLeftReason: nextActive ? row.chatLeftReason : chatLeftReason ?? undefined,
                chatRejoinedAt: nextActive ? chatRejoinedAt ?? undefined : undefined,
              }
            : row
        )
        if (!showInactive && !nextActive) {
          return nextRows.filter((row) => row.id !== player.id)
        }

        return sortRosterRows(nextRows, showMmrColumn)
      })
      if (editingPlayerId === player.id) {
        setEditingPlayerId(null)
        setEditingNickname('')
        setEditingRace('P')
        setEditingTier('UNASSIGNED')
        setEditingInlineMmrValue('')
        setEditingDormancyFloorTier('UNASSIGNED')
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

  const filteredRows = useMemo(() => {
    const searchText = search.trim().toLowerCase()
    return rows.filter((row) => {
      const matchesActivity = !showInactive || row.active === false
      const matchesRace = raceFilter === 'ALL' || row.race === raceFilter
      const matchesSearch =
        searchText.length === 0 || row.nickname.toLowerCase().includes(searchText)
      return matchesActivity && matchesRace && matchesSearch
    })
  }, [raceFilter, rows, search, showInactive])
  const activePlayerCount = useMemo(
    () => rows.filter((row) => row.active !== false).length,
    [rows]
  )
  const inactivePlayerCount = useMemo(
    () => rows.filter((row) => row.active === false).length,
    [rows]
  )

  const showStatusColumn = isAdmin
  const showDormancyFloorColumn = isSuperAdmin
  const showActionsColumn = isAdmin
  const tableColumnCount =
    6 +
    (showStatusColumn ? 1 : 0) +
    (showDormancyFloorColumn ? 1 : 0) +
    (showMmrColumn ? 1 : 0) +
    (showActionsColumn ? 1 : 0)
  const downloadableRows = useMemo(
    () => [...rows].sort(comparePlayersByTierThenNickname),
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
          row.tier === 'UNASSIGNED' ? t('players.table.unassigned') : row.tier,
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
    const downloadUrl = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = downloadUrl
    anchor.download = fileName
    document.body.append(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(downloadUrl)

    setPlayerActionError(null)
    setPlayerActionSuccess(t('players.download.success', { count: downloadableRows.length }))
  }, [downloadableRows, isSuperAdmin])

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
        <article id="player-import" className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <h3 className="text-sm font-semibold text-slate-900">{t('players.import.title')}</h3>
          <p className="mt-1 text-xs text-slate-500">{t('players.import.description')}</p>
          <form className="mt-3 space-y-3" onSubmit={handleRegisterPlayer}>
            <div className="grid gap-3 md:grid-cols-[minmax(0,1.5fr)_minmax(9rem,0.75fr)_minmax(9rem,0.75fr)]">
              <label className="space-y-1 text-xs font-medium text-slate-500">
                {t('players.import.nicknameLabel')}
                <input
                  type="text"
                  value={registrationNickname}
                  onChange={(event) => setRegistrationNickname(event.target.value)}
                  placeholder={t('players.import.nicknamePlaceholder')}
                  className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
                />
              </label>

              <label className="space-y-1 text-xs font-medium text-slate-500">
                {t('players.import.tierLabel')}
                <select
                  value={registrationTier}
                  onChange={(event) =>
                    setRegistrationTier(event.target.value as PlayerRegistrationTier | '')
                  }
                  className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
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

              <label className="space-y-1 text-xs font-medium text-slate-500">
                {t('players.import.raceLabel')}
                <select
                  value={registrationRace}
                  onChange={(event) => setRegistrationRace(event.target.value as PlayerRace | '')}
                  className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
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
              <p className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs text-emerald-700">
                {importSuccess}
              </p>
            )}

            <button
              type="submit"
              disabled={importing}
              className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300"
            >
              {importing ? t('players.import.loading') : t('players.import.button')}
            </button>
          </form>
        </article>
      )}

      {(playerActionError || playerActionSuccess) && (
        <div className="space-y-2">
          {playerActionError && (
            <Alert variant="destructive" appearance="light" size="sm">
              <AlertIcon icon="destructive">!</AlertIcon>
              <AlertContent>
                <AlertDescription>{playerActionError}</AlertDescription>
              </AlertContent>
            </Alert>
          )}
          {playerActionSuccess && (
            <p className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs text-emerald-700">
              {playerActionSuccess}
            </p>
          )}
        </div>
      )}

      {activityForm !== null && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/30 px-4">
          <form
            className="w-full max-w-md rounded-xl border border-slate-200 bg-white p-5 shadow-xl"
            onSubmit={handleSubmitActivityForm}
          >
            <div className="space-y-1">
              <h3 className="text-base font-semibold text-slate-900">
                {activityForm.mode === 'deactivate'
                  ? t('players.activityForm.deactivateTitle')
                  : t('players.activityForm.reactivateTitle')}
              </h3>
              <p className="text-xs text-slate-500">
                {t('players.activityForm.target', { nickname: activityForm.player.nickname })}
              </p>
            </div>

            {playerActionError && (
              <p className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">
                {playerActionError}
              </p>
            )}

            <div className="mt-4 space-y-3">
              {activityForm.mode === 'deactivate' ? (
                <>
                  <label className="block space-y-1 text-xs font-medium text-slate-600">
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
                      className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
                    />
                  </label>
                  <label className="block space-y-1 text-xs font-medium text-slate-600">
                    {t('players.activityForm.chatLeftReasonLabel')}
                    <textarea
                      required
                      maxLength={500}
                      value={activityForm.chatLeftReason}
                      onChange={(event) =>
                        setActivityForm((current) =>
                          current === null ? current : { ...current, chatLeftReason: event.target.value }
                        )
                      }
                      className="mt-1 h-24 w-full resize-none rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
                    />
                    <span className="block text-right text-[11px] text-slate-400">
                      {activityForm.chatLeftReason.length}/500
                    </span>
                  </label>
                </>
              ) : (
                <label className="block space-y-1 text-xs font-medium text-slate-600">
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
                    className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
                  />
                </label>
              )}
            </div>

            <div className="mt-5 flex justify-end gap-2">
              <button
                type="button"
                disabled={togglingPlayerId !== null}
                onClick={handleCancelActivityForm}
                className="rounded-md border border-slate-200 px-3 py-2 text-xs font-medium text-slate-700 transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
              >
                {t('players.actions.cancel')}
              </button>
              <button
                type="submit"
                disabled={togglingPlayerId !== null}
                className="rounded-md bg-slate-900 px-3 py-2 text-xs font-medium text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300"
              >
                {togglingPlayerId !== null
                  ? t('players.actions.toggling')
                  : t('players.activityForm.save')}
              </button>
            </div>
          </form>
        </div>
      )}

      <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
        {isAdmin && (
          <div className="mb-3 flex flex-wrap items-center gap-2">
            {showInactive ? (
              <span className="rounded-md border border-slate-300 bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-700">
                {t('players.filters.inactiveCount', { count: inactivePlayerCount })}
              </span>
            ) : (
              <span className="rounded-md border border-sky-200 bg-sky-50 px-2.5 py-1 text-xs font-semibold text-sky-800">
                {t('players.filters.activeCount', { count: activePlayerCount })}
              </span>
            )}
          </div>
        )}
        <div className="grid gap-3 md:grid-cols-3">
          <label className="space-y-1 text-xs font-medium text-slate-500 md:col-span-2">
            {t('players.filters.searchLabel')}
            <input
              type="text"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder={t('players.filters.searchPlaceholder')}
              className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
            />
          </label>

          <label className="space-y-1 text-xs font-medium text-slate-500">
            {t('players.filters.raceLabel')}
            <select
              value={raceFilter}
              onChange={(event) => setRaceFilter(event.target.value as RaceFilter)}
              className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
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
          <label className="mt-3 inline-flex items-center gap-2 text-xs font-medium text-slate-600">
            <input
              type="checkbox"
              checked={showInactive}
              onChange={(event) => setShowInactive(event.target.checked)}
              className="h-4 w-4 rounded border-slate-300 text-slate-900 focus:ring-slate-400"
            />
            <span>{t('players.filters.includeInactive')}</span>
          </label>
        )}
        {isSuperAdmin && (
          <div className="mt-3 flex justify-end">
            <button
              type="button"
              onClick={handleDownloadTierSortedRoster}
              className="rounded-lg border border-slate-300 px-3 py-2 text-xs font-medium text-slate-700 transition-colors hover:border-slate-900 hover:bg-slate-900 hover:text-white"
            >
              {t('players.download.button')}
            </button>
          </div>
        )}
      </div>

      <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-sm">
        <table className="min-w-full text-left text-sm">
          <thead className="bg-slate-50 text-xs tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3">{t('players.table.nickname')}</th>
              <th className="px-4 py-3">{t('players.table.race')}</th>
              <th className="px-4 py-3">{t('players.table.tier')}</th>
              {showStatusColumn && <th className="px-4 py-3">{t('players.table.status')}</th>}
              {showDormancyFloorColumn && (
                <th className="px-4 py-3">{t('players.dormancyFloor.inlineLabel')}</th>
              )}
              {showMmrColumn && <th className="px-4 py-3">{t('players.table.currentMmr')}</th>}
              <th className="px-4 py-3">{t('players.table.wins')}</th>
              <th className="px-4 py-3">{t('players.table.losses')}</th>
              <th className="px-4 py-3">{t('players.table.games')}</th>
              {showActionsColumn && <th className="px-4 py-3">{t('players.table.actions')}</th>}
            </tr>
          </thead>
          <tbody>
            {loading &&
              (
                <tr className="border-t border-slate-100">
                  <td
                    className="px-4 py-3"
                    colSpan={tableColumnCount}
                  >
                    <LoadingIndicator label={t('common.loading')} />
                  </td>
                </tr>
              )}

            {!loading && filteredRows.length === 0 && (
              <tr className="border-t border-slate-100">
                  <td
                    className="px-4 py-8 text-center text-sm text-slate-500"
                    colSpan={tableColumnCount}
                  >
                  {t('players.table.empty')}
                </td>
              </tr>
            )}

            {!loading &&
              filteredRows.map((row) => {
                const isEditing = editingPlayerId === row.id
                const isSaving = savingPlayerId === row.id
                const isDeleting = deletingPlayerId === row.id
                const isToggling = togglingPlayerId === row.id
                const busy = isSaving || isDeleting || isToggling
                const isActive = row.active !== false

                return (
                  <tr
                    key={row.id}
                    className={`border-t border-slate-100 transition-colors ${
                      isActive
                        ? 'hover:bg-slate-50/70'
                        : 'bg-slate-100/90 hover:bg-slate-200/70'
                    }`}
                  >
                    <td className={`px-4 py-3 font-medium ${isActive ? 'text-slate-900' : 'text-slate-700'}`}>
                      {isEditing ? (
                        <input
                          type="text"
                          value={editingNickname}
                          onChange={(event) => setEditingNickname(event.target.value)}
                          className="w-full rounded-md border border-slate-200 px-2 py-1 text-sm outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
                        />
                      ) : (
                        <div className="flex flex-wrap items-center gap-2">
                          <span>{row.nickname}</span>
                          {!isActive && (
                            <span className="rounded-md border border-slate-300 bg-slate-200 px-2 py-0.5 text-[11px] font-semibold text-slate-700">
                              {t('players.table.inactive')}
                            </span>
                          )}
                        </div>
                      )}
                    </td>
                    <td className={`px-4 py-3 ${isActive ? 'text-slate-700' : 'text-slate-600'}`}>
                      {isEditing ? (
                        <select
                          value={editingRace}
                          onChange={(event) => setEditingRace(event.target.value as PlayerRace)}
                          className="w-full rounded-md border border-slate-200 px-2 py-1 text-sm outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
                        >
                          {PLAYER_RACE_OPTIONS.map((raceOption) => (
                            <option key={raceOption} value={raceOption}>
                              {raceOption}
                            </option>
                          ))}
                        </select>
                      ) : (
                        row.race
                      )}
                    </td>
                    <td className="px-4 py-3">
                      {isEditing ? (
                        <select
                          value={editingTier}
                          onChange={(event) => {
                            const nextTier = event.target.value as PlayerTierStatus
                            setEditingTier(nextTier)
                            if (isSuperAdmin) {
                              setEditingInlineMmrValue(String(resolveDefaultMmrForTier(nextTier)))
                            }
                          }}
                          className="w-full rounded-md border border-slate-200 px-2 py-1 text-sm outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
                        >
                          {PLAYER_EDIT_TIER_OPTIONS.map((tierOption) => (
                            <option key={tierOption} value={tierOption}>
                              {tierOption === 'UNASSIGNED' ? t('players.table.unassigned') : tierOption}
                            </option>
                          ))}
                        </select>
                      ) : (
                        <span
                          className={`rounded-md px-2 py-1 text-xs font-semibold ${getTierBadgeClass(
                            row.tier
                          )}`}
                        >
                          {row.tier === 'UNASSIGNED' ? t('players.table.unassigned') : row.tier}
                        </span>
                      )}
                    </td>
                    {showStatusColumn && (
                      <td className="px-4 py-3">
                        <div className="space-y-1">
                          <span
                            className={`rounded-md px-2 py-1 text-xs font-semibold ${
                              isActive
                                ? 'border border-emerald-200 bg-emerald-50 text-emerald-700'
                                : 'border border-slate-300 bg-slate-200 text-slate-700'
                            }`}
                          >
                            {isActive ? t('players.table.active') : t('players.table.inactive')}
                          </span>
                          {!isActive && row.chatLeftAt && (
                            <div className="text-[11px] text-slate-500">
                              {t('players.table.chatLeftAt', {
                                value: formatChatRecordDisplay(row.chatLeftAt),
                              })}
                            </div>
                          )}
                          {!isActive && row.chatLeftReason && (
                            <div className="max-w-52 text-[11px] leading-4 text-slate-500">
                              {t('players.table.chatLeftReason', { value: row.chatLeftReason })}
                            </div>
                          )}
                          {isActive && row.chatRejoinedAt && (
                            <div className="text-[11px] text-emerald-700">
                              {t('players.table.chatRejoinedAt', {
                                value: formatChatRecordDisplay(row.chatRejoinedAt),
                              })}
                            </div>
                          )}
                        </div>
                      </td>
                    )}
                    {showDormancyFloorColumn && (
                      <td className="px-4 py-3">
                        {isEditing ? (
                          <select
                            value={editingDormancyFloorTier}
                            onChange={(event) =>
                              setEditingDormancyFloorTier(event.target.value as PlayerTierStatus)
                            }
                            className="w-32 rounded-md border border-slate-200 px-2 py-1 text-sm outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
                          >
                            {PLAYER_DORMANCY_FLOOR_TIER_OPTIONS.map((tierOption) => (
                              <option key={`dormancy-floor-option-${tierOption}`} value={tierOption}>
                                {formatTierOption(tierOption)}
                              </option>
                            ))}
                          </select>
                        ) : (
                          <span className="inline-flex rounded-md border border-slate-200 bg-slate-50 px-2 py-1 text-xs font-semibold text-slate-700">
                            {formatTierOption(row.dormancyMmrFloorTier)}
                          </span>
                        )}
                      </td>
                    )}
                    {showMmrColumn && (
                      <td className={`px-4 py-3 ${isActive ? 'text-slate-700' : 'text-slate-600'}`}>
                        {isEditing && isSuperAdmin ? (
                          <input
                            type="number"
                            min={0}
                            max={5000}
                            step={1}
                            value={editingInlineMmrValue}
                            onChange={(event) => setEditingInlineMmrValue(event.target.value)}
                            className="w-24 rounded-md border border-slate-200 px-2 py-1 text-sm outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
                          />
                        ) : (
                          formatMmrValue(row.currentMmr)
                        )}
                      </td>
                    )}
                    <td className={`px-4 py-3 ${isActive ? 'text-slate-700' : 'text-slate-600'}`}>{row.wins}</td>
                    <td className={`px-4 py-3 ${isActive ? 'text-slate-700' : 'text-slate-600'}`}>{row.losses}</td>
                    <td className={`px-4 py-3 ${isActive ? 'text-slate-700' : 'text-slate-600'}`}>{row.games}</td>
                    {showActionsColumn && (
                      <td className="px-4 py-3">
                        <div className="flex flex-wrap gap-2">
                          {isEditing ? (
                            <>
                              <button
                                type="button"
                                disabled={busy}
                                onClick={() => handleSaveEdit(row.id)}
                                className="rounded-md bg-slate-900 px-2.5 py-1 text-xs font-medium text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300"
                              >
                                {isSaving ? t('players.actions.saving') : t('players.actions.save')}
                              </button>
                              <button
                                type="button"
                                disabled={busy}
                                onClick={handleCancelEdit}
                                className="rounded-md border border-slate-200 px-2.5 py-1 text-xs font-medium text-slate-700 transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
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
                              className="rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-700 transition-colors hover:border-indigo-600 hover:bg-indigo-600 hover:text-white disabled:cursor-not-allowed disabled:opacity-60"
                            >
                              {t('players.actions.edit')}
                            </button>
                          )}

                          <button
                            type="button"
                            disabled={busy || editingPlayerId !== null || activityForm !== null}
                            onClick={() => handleTogglePlayerActive(row)}
                            className={`rounded-md px-2.5 py-1 text-xs font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-60 ${
                              isActive
                                ? 'border border-slate-300 text-slate-700 hover:border-slate-900 hover:bg-slate-900 hover:text-white'
                                : 'border border-emerald-300 text-emerald-700 hover:border-emerald-600 hover:bg-emerald-600 hover:text-white'
                            }`}
                          >
                            {isToggling
                              ? t('players.actions.toggling')
                              : isActive
                                ? t('players.actions.deactivate')
                                : t('players.actions.reactivate')}
                          </button>

                          <button
                            type="button"
                            disabled={busy || editingPlayerId !== null || activityForm !== null}
                            onClick={() => handleDeletePlayer(row)}
                            className="rounded-md border border-rose-300 px-2.5 py-1 text-xs font-medium text-rose-700 transition-colors hover:border-rose-600 hover:bg-rose-600 hover:text-white disabled:cursor-not-allowed disabled:opacity-60"
                          >
                            {isDeleting ? t('players.actions.deleting') : t('players.actions.delete')}
                          </button>
                        </div>
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
