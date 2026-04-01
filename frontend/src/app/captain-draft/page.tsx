'use client'

import { useEffect, useMemo, useState } from 'react'
import { apiClient, isApiNotFoundError } from '@/lib/api'
import { Alert, AlertContent, AlertDescription, AlertIcon, AlertTitle } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { t } from '@/lib/i18n'
import type {
  CaptainDraftResponse,
  CaptainDraftTeam,
  PlayerRosterItem,
} from '@/types/api'

const TEMP_GROUP_ID = 1

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

function resolvePlayerMeta(
  roster: PlayerRosterItem[],
  playerId: number,
): PlayerRosterItem | null {
  for (const player of roster) {
    if (player.id === playerId) {
      return player
    }
  }
  return null
}

export default function CaptainDraftPage() {
  const [players, setPlayers] = useState<PlayerRosterItem[]>([])
  const [playersLoading, setPlayersLoading] = useState<boolean>(true)
  const [playersError, setPlayersError] = useState<string | null>(null)
  const [search, setSearch] = useState<string>('')

  const [selectedParticipantIds, setSelectedParticipantIds] = useState<number[]>([])
  const [homeCaptainId, setHomeCaptainId] = useState<number | null>(null)
  const [awayCaptainId, setAwayCaptainId] = useState<number | null>(null)
  const [actingCaptainId, setActingCaptainId] = useState<number | null>(null)
  const [setsPerRound, setSetsPerRound] = useState<number>(4)

  const [draft, setDraft] = useState<CaptainDraftResponse | null>(null)
  const [draftLoading, setDraftLoading] = useState<boolean>(true)

  const [selectedPickPlayerId, setSelectedPickPlayerId] = useState<number | null>(null)
  const [entrySelections, setEntrySelections] = useState<Record<string, number | ''>>({})

  const [isCreatingDraft, setIsCreatingDraft] = useState<boolean>(false)
  const [isPicking, setIsPicking] = useState<boolean>(false)
  const [isSavingEntries, setIsSavingEntries] = useState<boolean>(false)

  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  const playerMap = useMemo(() => {
    const map = new Map<number, PlayerRosterItem>()
    for (const player of players) {
      map.set(player.id, player)
    }
    return map
  }, [players])

  const draftParticipantIds = useMemo(
    () => new Set((draft?.participants ?? []).map((participant) => participant.playerId)),
    [draft],
  )

  const filteredPlayers = useMemo(() => {
    const keyword = search.trim().toLowerCase()
    const filtered = players.filter((player) =>
      keyword.length === 0 ? true : player.nickname.toLowerCase().includes(keyword),
    )
    return filtered.sort((a, b) => a.nickname.localeCompare(b.nickname, 'ko-KR'))
  }, [players, search])

  const selectedParticipants = useMemo(
    () =>
      selectedParticipantIds
        .map((playerId) => playerMap.get(playerId))
        .filter((player): player is PlayerRosterItem => Boolean(player)),
    [playerMap, selectedParticipantIds],
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

  const opponentTeamParticipants = useMemo(() => {
    const opponentTeam: CaptainDraftTeam =
      actingCaptainTeam === 'HOME'
        ? 'AWAY'
        : actingCaptainTeam === 'AWAY'
          ? 'HOME'
          : 'UNASSIGNED'
    return (draft?.participants ?? []).filter(
      (participant) => participant.team === opponentTeam,
    )
  }, [draft, actingCaptainTeam])

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
        const participantIds = response.participants.map((participant) => participant.playerId)
        setSelectedParticipantIds(participantIds)
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
    for (const entry of draft.entries) {
      const key = `${entry.roundNumber}-${entry.setNumber}`
      nextSelections[key] =
        actingCaptainTeam === 'HOME'
          ? (entry.homePlayerId ?? '')
          : (entry.awayPlayerId ?? '')
    }
    setEntrySelections(nextSelections)
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

  const handleToggleParticipant = (playerId: number) => {
    setMessage(null)
    setError(null)
    setSelectedParticipantIds((previous) => {
      const exists = previous.includes(playerId)
      if (!exists) {
        return [...previous, playerId]
      }

      const next = previous.filter((id) => id !== playerId)
      if (homeCaptainId === playerId) {
        setHomeCaptainId(null)
      }
      if (awayCaptainId === playerId) {
        setAwayCaptainId(null)
      }
      if (actingCaptainId === playerId) {
        setActingCaptainId(null)
      }
      return next
    })
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

  const handleRefreshDraft = async () => {
    if (!draft?.draftId) {
      return
    }
    setError(null)
    try {
      const refreshed = await apiClient.getCaptainDraft(TEMP_GROUP_ID, draft.draftId)
      setDraft(refreshed)
    } catch {
      setError(t('captainDraft.validation.loadFailed'))
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
        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm xl:col-span-2">
          <h3 className="text-sm font-semibold text-slate-900">{t('captainDraft.attendance.title')}</h3>
          <p className="mt-1 text-xs text-slate-500">{t('captainDraft.attendance.helper')}</p>

          <label className="mt-3 block space-y-1 text-xs font-medium text-slate-500">
            {t('captainDraft.attendance.searchLabel')}
            <input
              type="text"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder={t('captainDraft.attendance.searchPlaceholder')}
              className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
            />
          </label>

          <p className="mt-3 text-xs text-slate-500">
            {t('captainDraft.attendance.selectedCount', { count: selectedParticipantIds.length })}
          </p>

          {playersLoading ? (
            <LoadingIndicator className="mt-3" label={t('common.loading')} />
          ) : (
            <div className="mt-3 grid gap-2 sm:grid-cols-2">
              {filteredPlayers.map((player) => {
                const selected = selectedParticipantIds.includes(player.id)
                const inCurrentDraft = draftParticipantIds.has(player.id)
                return (
                  <button
                    key={player.id}
                    type="button"
                    onClick={() => handleToggleParticipant(player.id)}
                    className={`rounded-lg border px-3 py-2 text-left text-sm transition-colors ${
                      selected
                        ? 'border-indigo-500 bg-indigo-50 text-indigo-900'
                        : 'border-slate-200 bg-white text-slate-800 hover:bg-slate-50'
                    }`}
                  >
                    <p className="font-medium">
                      {player.nickname} ({player.race})
                    </p>
                    <p className="mt-1 text-xs text-slate-500">{player.currentMmr} MMR</p>
                    {inCurrentDraft && (
                      <p className="mt-1 text-[11px] text-indigo-700">
                        {t('captainDraft.attendance.inCurrentDraft')}
                      </p>
                    )}
                  </button>
                )
              })}
              {filteredPlayers.length === 0 && (
                <div className="rounded-lg border border-dashed border-slate-200 px-3 py-8 text-center text-sm text-slate-500 sm:col-span-2">
                  {t('captainDraft.attendance.empty')}
                </div>
              )}
            </div>
          )}
        </article>

        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <h3 className="text-sm font-semibold text-slate-900">{t('captainDraft.captain.title')}</h3>

          <div className="mt-3 space-y-2">
            <label className="block text-xs font-medium text-slate-500">
              {t('captainDraft.captain.home')}
              <select
                value={homeCaptainId ?? ''}
                onChange={(event) => setHomeCaptainId(normalizeSelectionValue(event.target.value))}
                className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
              >
                <option value="">-</option>
                {selectedParticipants.map((participant) => (
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
                onChange={(event) => setAwayCaptainId(normalizeSelectionValue(event.target.value))}
                className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
              >
                <option value="">-</option>
                {selectedParticipants.map((participant) => (
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
              onClick={handleCreateDraft}
              disabled={isCreatingDraft}
              className="flex-1 rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300"
            >
              {isCreatingDraft ? t('captainDraft.actions.creating') : t('captainDraft.actions.create')}
            </button>
            <button
              type="button"
              onClick={handleRefreshDraft}
              disabled={!draft || draftLoading}
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
            <article className="rounded-xl border border-indigo-100 bg-indigo-50/40 p-4 shadow-sm">
              <h3 className="text-sm font-semibold text-indigo-900">{t('captainDraft.teams.home')}</h3>
              <div className="mt-3 space-y-2">
                {homeTeamParticipants.map((participant) => {
                  const meta = resolvePlayerMeta(players, participant.playerId)
                  return (
                    <div
                      key={`home-${participant.playerId}`}
                      className="rounded-lg border border-indigo-100 bg-white px-3 py-2 text-sm text-slate-800"
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-medium">
                          {participant.nickname} ({participant.race})
                        </span>
                        {participant.captain && (
                          <span className="rounded-md bg-indigo-600 px-2 py-0.5 text-[11px] font-medium text-white">
                            {t('captainDraft.teams.captainBadge')}
                          </span>
                        )}
                      </div>
                      <p className="mt-1 text-xs text-slate-500">{meta ? `${meta.currentMmr} MMR` : '-'}</p>
                    </div>
                  )
                })}
                {homeTeamParticipants.length === 0 && (
                  <div className="rounded-lg border border-dashed border-indigo-200 bg-white px-3 py-6 text-center text-xs text-slate-500">
                    {t('captainDraft.teams.emptyHome')}
                  </div>
                )}
              </div>
            </article>

            <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <h3 className="text-sm font-semibold text-slate-900">{t('captainDraft.teams.unassigned')}</h3>
              <div className="mt-3 space-y-2">
                {unassignedParticipants.map((participant) => {
                  const meta = resolvePlayerMeta(players, participant.playerId)
                  return (
                    <div
                      key={`unassigned-${participant.playerId}`}
                      className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-800"
                    >
                      <p className="font-medium">
                        {participant.nickname} ({participant.race})
                      </p>
                      <p className="mt-1 text-xs text-slate-500">{meta ? `${meta.currentMmr} MMR` : '-'}</p>
                    </div>
                  )
                })}
                {unassignedParticipants.length === 0 && (
                  <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50 px-3 py-6 text-center text-xs text-slate-500">
                    {t('captainDraft.teams.emptyUnassigned')}
                  </div>
                )}
              </div>
            </article>

            <article className="rounded-xl border border-emerald-100 bg-emerald-50/40 p-4 shadow-sm">
              <h3 className="text-sm font-semibold text-emerald-900">{t('captainDraft.teams.away')}</h3>
              <div className="mt-3 space-y-2">
                {awayTeamParticipants.map((participant) => {
                  const meta = resolvePlayerMeta(players, participant.playerId)
                  return (
                    <div
                      key={`away-${participant.playerId}`}
                      className="rounded-lg border border-emerald-100 bg-white px-3 py-2 text-sm text-slate-800"
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-medium">
                          {participant.nickname} ({participant.race})
                        </span>
                        {participant.captain && (
                          <span className="rounded-md bg-emerald-600 px-2 py-0.5 text-[11px] font-medium text-white">
                            {t('captainDraft.teams.captainBadge')}
                          </span>
                        )}
                      </div>
                      <p className="mt-1 text-xs text-slate-500">{meta ? `${meta.currentMmr} MMR` : '-'}</p>
                    </div>
                  )
                })}
                {awayTeamParticipants.length === 0 && (
                  <div className="rounded-lg border border-dashed border-emerald-200 bg-white px-3 py-6 text-center text-xs text-slate-500">
                    {t('captainDraft.teams.emptyAway')}
                  </div>
                )}
              </div>
            </article>
          </div>

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
            <h3 className="text-sm font-semibold text-slate-900">{t('captainDraft.entry.title')}</h3>
            <p className="mt-1 text-xs text-slate-500">{t('captainDraft.entry.helper')}</p>

            <div className="mt-4 space-y-4">
              {roundGroups.map((roundGroup) => (
                <div
                  key={`round-${roundGroup.key}`}
                  className="rounded-xl border border-slate-200 bg-slate-50/60 p-3"
                >
                  <h4 className="text-sm font-semibold text-slate-900">
                    {t('captainDraft.entry.round', {
                      round: roundGroup.roundNumber,
                      code: roundGroup.roundCode,
                    })}
                  </h4>

                  <div className="mt-3 overflow-x-auto rounded-lg border border-slate-200 bg-white">
                    <table className="min-w-full text-sm">
                      <thead className="bg-slate-50 text-slate-600">
                        <tr>
                          <th className="px-3 py-2 text-left font-medium">{t('captainDraft.entry.setLabel')}</th>
                          <th className="px-3 py-2 text-left font-medium">
                            {t('captainDraft.entry.homePlayer')}
                          </th>
                          <th className="px-3 py-2 text-left font-medium">
                            {t('captainDraft.entry.awayPlayer')}
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        {roundGroup.entries.map((entry) => {
                          const key = `${entry.roundNumber}-${entry.setNumber}`
                          const selectedPlayerId = entrySelections[key]

                          return (
                            <tr key={`entry-${key}`} className="border-t border-slate-100">
                              <td className="px-3 py-2 text-slate-700">
                                {t('captainDraft.entry.set', { set: entry.setNumber })}
                              </td>
                              <td className="px-3 py-2">
                                {actingCaptainTeam === 'HOME' ? (
                                  <select
                                    value={selectedPlayerId === '' ? '' : selectedPlayerId ?? ''}
                                    onChange={(event) =>
                                      handleEntrySelectionChange(
                                        key,
                                        normalizeSelectionValue(event.target.value),
                                      )
                                    }
                                    className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
                                  >
                                    <option value="">{t('captainDraft.entry.notSet')}</option>
                                    {actingTeamParticipants.map((participant) => (
                                      <option
                                        key={`home-entry-${key}-${participant.playerId}`}
                                        value={participant.playerId}
                                      >
                                        {participant.nickname} ({participant.race})
                                      </option>
                                    ))}
                                  </select>
                                ) : (
                                  <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                                    {entry.homePlayerNickname ?? t('captainDraft.entry.notSet')}
                                  </div>
                                )}
                              </td>
                              <td className="px-3 py-2">
                                {actingCaptainTeam === 'AWAY' ? (
                                  <select
                                    value={selectedPlayerId === '' ? '' : selectedPlayerId ?? ''}
                                    onChange={(event) =>
                                      handleEntrySelectionChange(
                                        key,
                                        normalizeSelectionValue(event.target.value),
                                      )
                                    }
                                    className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
                                  >
                                    <option value="">{t('captainDraft.entry.notSet')}</option>
                                    {actingTeamParticipants.map((participant) => (
                                      <option
                                        key={`away-entry-${key}-${participant.playerId}`}
                                        value={participant.playerId}
                                      >
                                        {participant.nickname} ({participant.race})
                                      </option>
                                    ))}
                                  </select>
                                ) : (
                                  <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                                    {entry.awayPlayerNickname ?? t('captainDraft.entry.notSet')}
                                  </div>
                                )}
                              </td>
                            </tr>
                          )
                        })}
                      </tbody>
                    </table>
                  </div>
                </div>
              ))}

              <div className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3">
                <h4 className="text-sm font-semibold text-slate-900">
                  {t('captainDraft.entry.opponentVisibleTitle')}
                </h4>
                <p className="mt-1 text-xs text-slate-500">
                  {t('captainDraft.entry.opponentVisibleHelper')}
                </p>
                <div className="mt-2 flex flex-wrap gap-2">
                  {opponentTeamParticipants.map((participant) => (
                    <span
                      key={`opponent-${participant.playerId}`}
                      className="rounded-md border border-slate-200 bg-white px-2 py-1 text-xs text-slate-700"
                    >
                      {participant.nickname} ({participant.race})
                    </span>
                  ))}
                  {opponentTeamParticipants.length === 0 && (
                    <span className="text-xs text-slate-500">{t('captainDraft.teams.emptyOpponent')}</span>
                  )}
                </div>
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
