'use client'

import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react'
import { useAdminAuth } from '@/lib/admin-auth'
import { apiClient, isApiForbiddenError, isApiNotFoundError, isApiUnauthorizedError } from '@/lib/api'
import { Alert, AlertContent, AlertDescription, AlertIcon, AlertTitle } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { t } from '@/lib/i18n'
import type { PlayerRace, PlayerRosterItem, PlayerTierStatus } from '@/types/api'

const TEMP_GROUP_ID = 1

type RaceFilter = PlayerRace | 'ALL'
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
type TierChangeTarget = {
  playerId: number
  nickname: string
  baseTier: PlayerTierStatus
  currentTier: PlayerTierStatus
  baseMmr: number
  currentMmr: number
}
const PLAYER_RACE_OPTIONS: PlayerRace[] = ['P', 'T', 'Z', 'PT', 'PZ', 'TZ', 'R']
const TIER_DOWNLOAD_ORDER: Record<PlayerTierStatus, number> = {
  S: 0,
  'A+': 1,
  A: 2,
  'A-': 3,
  'B+': 4,
  B: 5,
  'B-': 6,
  'C+': 7,
  C: 8,
  'C-': 9,
  UNASSIGNED: 10,
}

function toTierOrder(tier: PlayerTierStatus): number {
  return TIER_DOWNLOAD_ORDER[tier] ?? Number.MAX_SAFE_INTEGER
}

function comparePlayersByTierThenNickname(a: PlayerRosterItem, b: PlayerRosterItem): number {
  const tierDiff = toTierOrder(a.tier) - toTierOrder(b.tier)
  if (tierDiff !== 0) {
    return tierDiff
  }

  return a.nickname.localeCompare(b.nickname, 'ko-KR')
}

function escapeCsvCell(value: string): string {
  return `"${value.replace(/"/g, '""')}"`
}

function formatMmrValue(value: number): string {
  return value === 0 ? 'None' : String(value)
}

function normalizeImportPayload(payload: unknown): PlayerImportPayload | null {
  if (Array.isArray(payload)) {
    const rows = payload
      .map((row) => normalizeImportRow(row))
      .filter((row): row is PlayerImportRow => row !== null)
    return rows.length > 0 ? { players: rows } : null
  }

  if (payload !== null && typeof payload === 'object') {
    const source = payload as Record<string, unknown>
    if (!Array.isArray(source.players)) {
      return null
    }

    const rows = source.players
      .map((row) => normalizeImportRow(row))
      .filter((row): row is PlayerImportRow => row !== null)
    return rows.length > 0 ? { players: rows } : null
  }

  return null
}

function normalizeImportRow(value: unknown): PlayerImportRow | null {
  if (value === null || typeof value !== 'object') {
    return null
  }

  const source = value as Record<string, unknown>
  const nickname = typeof source.nickname === 'string' ? source.nickname.trim() : ''
  const tier = typeof source.tier === 'string' ? source.tier.trim() : ''

  if (nickname.length === 0 || tier.length === 0) {
    return null
  }

  const row: PlayerImportRow = { nickname, tier }
  if (typeof source.race === 'string') {
    const normalizedRace = source.race.trim().toUpperCase()
    if (PLAYER_RACE_OPTIONS.includes(normalizedRace as PlayerRace)) {
      row.race = normalizedRace as PlayerRace
    }
  }

  if (typeof source.baseMmr === 'number' && Number.isFinite(source.baseMmr)) {
    row.baseMmr = source.baseMmr
  }
  if (typeof source.currentMmr === 'number' && Number.isFinite(source.currentMmr)) {
    row.currentMmr = source.currentMmr
  }
  if (typeof source.note === 'string') {
    row.note = source.note
  }

  return row
}

function parseTextImportPayload(raw: string): PlayerImportPayload | null {
  const lines = raw
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0)

  if (lines.length === 0) {
    return null
  }

  const rows: PlayerImportRow[] = []
  for (const line of lines) {
    let parts = line.split('\t').map((part) => part.trim()).filter((part) => part.length > 0)
    if (parts.length < 2) {
      parts = line.split(',').map((part) => part.trim()).filter((part) => part.length > 0)
    }
    if (parts.length < 2) {
      parts = line.split(/\s+/).map((part) => part.trim()).filter((part) => part.length > 0)
    }

    if (parts.length < 2) {
      return null
    }

    const nickname = parts[0]
    const tier = parts[1]
    const race =
      parts.length >= 3 ? parts[2].toUpperCase() : undefined
    if (nickname.length === 0 || tier.length === 0) {
      return null
    }

    if (race && !PLAYER_RACE_OPTIONS.includes(race as PlayerRace)) {
      return null
    }

    rows.push({ nickname, tier, race: race as PlayerRace | undefined })
  }

  return rows.length > 0 ? { players: rows } : null
}

function parseImportPayload(raw: string): PlayerImportPayload | null {
  const trimmed = raw.trim()
  if (trimmed.length === 0) {
    return null
  }

  try {
    const parsedJson = JSON.parse(trimmed) as unknown
    const normalizedJsonPayload = normalizeImportPayload(parsedJson)
    if (normalizedJsonPayload) {
      return normalizedJsonPayload
    }
  } catch {
    // no-op
  }

  return parseTextImportPayload(trimmed)
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
      return 'bg-emerald-100 text-emerald-800'
    case 'UNASSIGNED':
      return 'bg-rose-100 text-rose-800'
    default:
      return 'bg-slate-100 text-slate-700'
  }
}

export default function PlayersPage() {
  const { isAdmin, isSuperAdmin } = useAdminAuth()
  const [rows, setRows] = useState<PlayerRosterItem[]>([])
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState<string>('')
  const [raceFilter, setRaceFilter] = useState<RaceFilter>('ALL')
  const [importPayload, setImportPayload] = useState<string>('')
  const [importing, setImporting] = useState<boolean>(false)
  const [importError, setImportError] = useState<string | null>(null)
  const [importSuccess, setImportSuccess] = useState<string | null>(null)
  const [editingPlayerId, setEditingPlayerId] = useState<number | null>(null)
  const [editingNickname, setEditingNickname] = useState<string>('')
  const [editingRace, setEditingRace] = useState<PlayerRace>('P')
  const [savingPlayerId, setSavingPlayerId] = useState<number | null>(null)
  const [deletingPlayerId, setDeletingPlayerId] = useState<number | null>(null)
  const [playerActionError, setPlayerActionError] = useState<string | null>(null)
  const [playerActionSuccess, setPlayerActionSuccess] = useState<string | null>(null)

  const fetchRoster = useCallback(async () => {
    setLoading(true)
    setError(null)

    try {
      const response = await apiClient.getGroupPlayers(TEMP_GROUP_ID)
      setRows(
        [...response].sort((a, b) => {
          if (b.games !== a.games) {
            return b.games - a.games
          }
          if (b.currentMmr !== a.currentMmr) {
            return b.currentMmr - a.currentMmr
          }
          return a.nickname.localeCompare(b.nickname, 'ko-KR')
        })
      )
    } catch {
      setRows([])
      setError(t('players.loadError'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void fetchRoster()
  }, [fetchRoster])

  const handleImportPlayers = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setImportError(null)
    setImportSuccess(null)
    setPlayerActionError(null)
    setPlayerActionSuccess(null)

    if (!isSuperAdmin) {
      setImportError(t('common.adminOnlyAction'))
      return
    }

    const normalizedPayload = parseImportPayload(importPayload)
    if (!normalizedPayload) {
      setImportError(t('players.import.invalidFormat'))
      return
    }

    setImporting(true)
    try {
      await apiClient.importGroupPlayers(TEMP_GROUP_ID, normalizedPayload)
      setImportSuccess(t('players.import.success'))
      setImportPayload('')
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
    setPlayerActionError(null)
    setPlayerActionSuccess(null)
  }

  const handleCancelEdit = () => {
    setEditingPlayerId(null)
    setEditingNickname('')
    setEditingRace('P')
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

    setSavingPlayerId(playerId)
    setPlayerActionError(null)
    setPlayerActionSuccess(null)
    try {
      await apiClient.updateGroupPlayer(TEMP_GROUP_ID, playerId, {
        nickname: nextNickname,
        race: editingRace,
      })
      setEditingPlayerId(null)
      setEditingNickname('')
      setEditingRace('P')
      setPlayerActionSuccess(t('players.actions.updateSuccess'))
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
      }
      setPlayerActionSuccess(t('players.actions.deleteSuccess'))
      await fetchRoster()
    } catch (actionError) {
      if (isApiForbiddenError(actionError)) {
        setPlayerActionError(t('common.permissionDenied'))
      } else if (isApiNotFoundError(actionError)) {
        setPlayerActionError(t('players.actions.deleteNotFound'))
      } else {
        setPlayerActionError(t('players.actions.deleteFailure'))
      }
    } finally {
      setDeletingPlayerId(null)
    }
  }

  const filteredRows = useMemo(() => {
    const searchText = search.trim().toLowerCase()
    return rows.filter((row) => {
      const matchesRace = raceFilter === 'ALL' || row.race === raceFilter
      const matchesSearch =
        searchText.length === 0 || row.nickname.toLowerCase().includes(searchText)
      return matchesRace && matchesSearch
    })
  }, [raceFilter, rows, search])

  const showMmrColumn = isAdmin
  const showActionsColumn = isAdmin
  const tierChangeTargets = useMemo<TierChangeTarget[]>(
    () =>
      rows
        .flatMap((row) => {
          if (row.baseTier === undefined || row.baseMmr === undefined) {
            return []
          }
          if (row.baseTier === row.tier) {
            return []
          }

          return [{
            playerId: row.id,
            nickname: row.nickname,
            baseTier: row.baseTier,
            currentTier: row.tier,
            baseMmr: row.baseMmr,
            currentMmr: row.currentMmr,
          }]
        })
        .sort((a, b) => {
          const currentTierDiff = toTierOrder(a.currentTier) - toTierOrder(b.currentTier)
          if (currentTierDiff !== 0) {
            return currentTierDiff
          }

          if (b.currentMmr !== a.currentMmr) {
            return b.currentMmr - a.currentMmr
          }

          return a.nickname.localeCompare(b.nickname, 'ko-KR')
        }),
    [rows]
  )
  const tierChangeTargetById = useMemo(
    () => new Map(tierChangeTargets.map((target) => [target.playerId, target])),
    [tierChangeTargets]
  )
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
      t('players.download.headers.tier'),
      t('players.download.headers.nickname'),
      t('players.download.headers.race'),
      t('players.download.headers.currentMmr'),
      t('players.download.headers.games'),
    ]

    const lines = [
      header.map(escapeCsvCell).join(','),
      ...downloadableRows.map((row) =>
        [
          row.tier === 'UNASSIGNED' ? t('players.table.unassigned') : row.tier,
          row.nickname,
          row.race,
          formatMmrValue(row.currentMmr),
          String(row.games),
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
      <header className="space-y-1 rounded-xl border border-slate-200 bg-white px-5 py-4 shadow-sm">
        <h2 className="text-2xl font-semibold tracking-tight">{t('players.title')}</h2>
        <p className="text-sm text-slate-600">{t('players.description')}</p>
      </header>

      {error && (
        <Alert variant="destructive" appearance="light">
          <AlertIcon icon="destructive">!</AlertIcon>
          <AlertContent>
            <AlertTitle>{t('common.errorPrefix')}</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </AlertContent>
        </Alert>
      )}

      {isSuperAdmin && (
        <article id="player-import" className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <h3 className="text-sm font-semibold text-slate-900">{t('players.import.title')}</h3>
          <p className="mt-1 text-xs text-slate-500">{t('players.import.description')}</p>
          <form className="mt-3 space-y-3" onSubmit={handleImportPlayers}>
            <textarea
              value={importPayload}
              onChange={(event) => setImportPayload(event.target.value)}
              className="h-32 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 font-mono text-xs text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
              placeholder={t('players.import.placeholder')}
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

      {isAdmin && (
        <article className="rounded-xl border border-amber-200 bg-amber-50/60 p-4 shadow-sm">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h3 className="text-sm font-semibold text-amber-950">{t('players.tierChange.title')}</h3>
            <span className="rounded-md border border-amber-300 bg-white px-2 py-0.5 text-xs font-medium text-amber-800">
              {t('players.tierChange.count', { count: tierChangeTargets.length })}
            </span>
          </div>
          <p className="mt-1 text-xs text-amber-900">{t('players.tierChange.description')}</p>
          {tierChangeTargets.length === 0 ? (
            <p className="mt-3 rounded-md border border-amber-200 bg-white/70 px-3 py-2 text-xs text-amber-800">
              {t('players.tierChange.empty')}
            </p>
          ) : (
            <ul className="mt-3 space-y-2">
              {tierChangeTargets.map((target) => {
                const fromTierLabel =
                  target.baseTier === 'UNASSIGNED' ? t('players.table.unassigned') : target.baseTier
                const toTierLabel =
                  target.currentTier === 'UNASSIGNED'
                    ? t('players.table.unassigned')
                    : target.currentTier

                return (
                  <li
                    key={`tier-change-${target.playerId}`}
                    className="rounded-md border border-amber-200 bg-white/80 px-3 py-2 text-sm text-slate-800"
                  >
                    <div className="font-medium text-slate-900">{target.nickname}</div>
                    <div className="mt-0.5 text-xs text-slate-700">
                      {t('players.tierChange.fromTo', { from: fromTierLabel, to: toTierLabel })}
                    </div>
                    <div className="mt-0.5 text-xs text-slate-600">
                      {t('players.tierChange.mmrFlow', {
                        from: formatMmrValue(target.baseMmr),
                        to: formatMmrValue(target.currentMmr),
                      })}
                    </div>
                  </li>
                )
              })}
            </ul>
          )}
        </article>
      )}

      <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
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
                    colSpan={showMmrColumn && showActionsColumn ? 8 : 6}
                  >
                    <LoadingIndicator label={t('common.loading')} />
                  </td>
                </tr>
              )}

            {!loading && filteredRows.length === 0 && (
              <tr className="border-t border-slate-100">
                <td
                  className="px-4 py-8 text-center text-sm text-slate-500"
                  colSpan={showMmrColumn && showActionsColumn ? 8 : 6}
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
                const busy = isSaving || isDeleting
                const tierChangeTarget = tierChangeTargetById.get(row.id)

                return (
                  <tr key={row.id} className="border-t border-slate-100 transition-colors hover:bg-slate-50/70">
                    <td className="px-4 py-3 font-medium text-slate-900">
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
                          {isAdmin && tierChangeTarget && (
                            <span className="rounded-md border border-amber-300 bg-amber-100 px-2 py-0.5 text-[11px] font-semibold text-amber-900">
                              {t('players.tierChange.badge')}
                            </span>
                          )}
                        </div>
                      )}
                    </td>
                    <td className="px-4 py-3 text-slate-700">
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
                      <span
                        className={`rounded-md px-2 py-1 text-xs font-semibold ${getTierBadgeClass(
                          row.tier
                        )}`}
                      >
                        {row.tier === 'UNASSIGNED' ? t('players.table.unassigned') : row.tier}
                      </span>
                    </td>
                    {showMmrColumn && (
                      <td className="px-4 py-3 text-slate-700">{formatMmrValue(row.currentMmr)}</td>
                    )}
                    <td className="px-4 py-3 text-slate-700">{row.wins}</td>
                    <td className="px-4 py-3 text-slate-700">{row.losses}</td>
                    <td className="px-4 py-3 text-slate-700">{row.games}</td>
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
                              disabled={editingPlayerId !== null || deletingPlayerId !== null}
                              onClick={() => handleStartEdit(row)}
                              className="rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-700 transition-colors hover:border-indigo-600 hover:bg-indigo-600 hover:text-white disabled:cursor-not-allowed disabled:opacity-60"
                            >
                              {t('players.actions.edit')}
                            </button>
                          )}

                          <button
                            type="button"
                            disabled={busy || editingPlayerId !== null}
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
