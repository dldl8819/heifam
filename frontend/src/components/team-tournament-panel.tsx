'use client'

import { useCallback, useEffect, useState } from 'react'
import { apiClient, isApiConflictError, isApiForbiddenError } from '@/lib/api'
import { Alert, AlertContent, AlertDescription, AlertIcon } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { t } from '@/lib/i18n'
import { ASSIGNED_RACES, normalizeAssignedRace } from '@/lib/participant-races'
import {
  buildParticipantRaces,
  finalWaitingTeamNumber,
  initialRaceDraft,
  orderSeries,
  raceDraftMatchesComposition,
  rankedTeams,
  TEAM_SIDES,
  TEAM_TOURNAMENT_POLL_MS,
  teamTournamentTeamCount,
  teamTournamentWaitingCount,
  type GameRaceDraft,
} from '@/lib/team-tournament'
import { startVisiblePolling } from '@/lib/visible-polling'
import type {
  TeamSide,
  TeamTournament,
  TournamentGame,
  TournamentGamePlayer,
  TournamentPlayer,
  TournamentSeries,
} from '@/types/api'

type TeamTournamentPanelProps = {
  groupId: number
  selectedPlayerIds: number[]
  showMmr: boolean
}

type GameDraft = {
  winner: TeamSide | ''
  races: GameRaceDraft
}

const CARD_CLASS = 'rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900'
const PRIMARY_BUTTON_CLASS =
  'rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white dark:disabled:bg-slate-700 dark:disabled:text-slate-400'
const SECONDARY_BUTTON_CLASS =
  'rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800'
const SELECT_CLASS =
  'rounded-md border border-slate-300 bg-white px-2 py-1 text-xs text-slate-800 outline-none focus:border-slate-400 focus:ring-1 focus:ring-slate-200 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-slate-500 dark:focus:ring-slate-700'

function teamLabel(teamNumber: number): string {
  return t('teamTournament.teams.label', { number: teamNumber })
}

function playerName(player: { nickname: string | null }): string {
  return player.nickname ?? '-'
}

// Protoss needs no marker; Terran and Zerg are what the series games turn on.
function gamePlayerLine(player: TournamentGamePlayer): string {
  return player.assignedRace === 'T' || player.assignedRace === 'Z'
    ? `${playerName(player)} (${player.assignedRace})`
    : playerName(player)
}

function memberLine(player: TournamentPlayer, showMmr: boolean): string {
  const race = player.race ? ` · ${player.race}` : ''
  return showMmr && typeof player.mmr === 'number' ? `${playerName(player)}${race} (${player.mmr})` : `${playerName(player)}${race}`
}

function roundLabel(series: TournamentSeries, teamCount: number): string {
  if (series.round === 'SEMIFINAL' && teamCount === 3) {
    return t('teamTournament.rounds.SEMIFINAL_ONLY')
  }
  return t(`teamTournament.rounds.${series.round}`, { slot: series.bracketSlot })
}

function describeError(error: unknown, fallback: string): string {
  return error instanceof Error && error.message.trim().length > 0 ? `${fallback} (${error.message})` : fallback
}

function ErrorAlert({ message }: { message: string }) {
  return (
    <Alert variant="destructive" appearance="light" size="sm">
      <AlertIcon icon="destructive">!</AlertIcon>
      <AlertContent>
        <AlertDescription>{message}</AlertDescription>
      </AlertContent>
    </Alert>
  )
}

export function TeamTournamentPanel({ groupId, selectedPlayerIds, showMmr }: TeamTournamentPanelProps) {
  const [tournament, setTournament] = useState<TeamTournament | null>(null)
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)
  const [creating, setCreating] = useState<boolean>(false)
  const [cancelling, setCancelling] = useState<boolean>(false)
  const [drafts, setDrafts] = useState<Record<number, GameDraft>>({})
  const [submittingMatchId, setSubmittingMatchId] = useState<number | null>(null)
  const [gameErrors, setGameErrors] = useState<Record<number, string>>({})

  const loadLatest = useCallback(
    async (quiet = false) => {
      if (!quiet) {
        setLoading(true)
      }
      try {
        const response = await apiClient.getLatestTeamTournament(groupId)
        setTournament(response.tournament)
        setError(null)
      } catch {
        if (!quiet) {
          setError(t('teamTournament.loadError'))
        }
      } finally {
        if (!quiet) {
          setLoading(false)
        }
      }
    },
    [groupId],
  )

  useEffect(() => {
    void loadLatest()
  }, [loadLatest])

  // Other admins record games too, so a running tournament is refreshed while the page is visible.
  const runningTournamentId = tournament?.status === 'IN_PROGRESS' ? tournament.tournamentId : null
  useEffect(() => {
    if (runningTournamentId === null) {
      return
    }
    return startVisiblePolling(() => {
      void loadLatest(true)
    }, TEAM_TOURNAMENT_POLL_MS)
  }, [loadLatest, runningTournamentId])

  const selectedCount = selectedPlayerIds.length
  const teamCount = teamTournamentTeamCount(selectedCount)
  const waitingCount = teamTournamentWaitingCount(selectedCount)
  const running = tournament?.status === 'IN_PROGRESS'
  const canCreate = teamCount !== null && !creating && !running

  const setGameError = (matchId: number, message: string | null) => {
    setGameErrors((previous) => {
      const next = { ...previous }
      if (message === null) {
        delete next[matchId]
      } else {
        next[matchId] = message
      }
      return next
    })
  }

  const draftFor = (game: TournamentGame): GameDraft =>
    (game.matchId !== null ? drafts[game.matchId] : undefined) ?? { winner: '', races: initialRaceDraft(game) }

  const updateDraft = (game: TournamentGame, change: (draft: GameDraft) => GameDraft) => {
    const matchId = game.matchId
    if (matchId === null) {
      return
    }
    setDrafts((previous) => ({
      ...previous,
      [matchId]: change(previous[matchId] ?? { winner: '', races: initialRaceDraft(game) }),
    }))
    setGameError(matchId, null)
  }

  const handleCreate = async () => {
    if (teamCount === null) {
      return
    }
    if (!window.confirm(t('teamTournament.create.confirm', { count: selectedCount }))) {
      return
    }
    setCreating(true)
    setError(null)
    try {
      const created = await apiClient.createTeamTournament(groupId, selectedPlayerIds)
      setTournament(created)
      setDrafts({})
      setGameErrors({})
    } catch (createError) {
      setError(describeError(createError, t('teamTournament.create.error')))
    } finally {
      setCreating(false)
    }
  }

  const handleCancel = async () => {
    if (!tournament || !window.confirm(t('teamTournament.cancel.confirm'))) {
      return
    }
    setCancelling(true)
    setError(null)
    try {
      await apiClient.cancelTeamTournament(groupId, tournament.tournamentId)
      setTournament(null)
      setDrafts({})
      setGameErrors({})
    } catch (cancelError) {
      setError(describeError(cancelError, t('teamTournament.cancel.error')))
      void loadLatest(true)
    } finally {
      setCancelling(false)
    }
  }

  const handleSubmitGame = async (series: TournamentSeries, game: TournamentGame) => {
    const matchId = game.matchId
    if (matchId === null) {
      return
    }
    const draft = draftFor(game)
    if (draft.winner !== 'HOME' && draft.winner !== 'AWAY') {
      setGameError(matchId, t('teamTournament.games.winnerPlaceholder'))
      return
    }
    if (!raceDraftMatchesComposition(game, draft.races)) {
      setGameError(matchId, t('teamTournament.games.racesMismatch', { composition: game.raceComposition ?? '' }))
      return
    }
    const winnerTeamNumber = draft.winner === 'HOME' ? series.homeTeamNumber : series.awayTeamNumber
    const winners = (draft.winner === 'HOME' ? game.homePlayers : game.awayPlayers).map(playerName).join(', ')
    if (!window.confirm(t('teamTournament.games.confirmWinner', { team: teamLabel(winnerTeamNumber), players: winners }))) {
      return
    }

    setSubmittingMatchId(matchId)
    setGameError(matchId, null)
    try {
      await apiClient.submitMatchResult(matchId, {
        winnerTeam: draft.winner,
        participantRaces: buildParticipantRaces(game, draft.races),
      })
      setDrafts((previous) => {
        const next = { ...previous }
        delete next[matchId]
        return next
      })
      await loadLatest(true)
    } catch (submitError) {
      if (isApiConflictError(submitError)) {
        setGameError(matchId, t('teamTournament.games.conflict'))
        await loadLatest(true)
      } else if (isApiForbiddenError(submitError)) {
        setGameError(matchId, t('teamTournament.games.forbidden'))
      } else {
        setGameError(matchId, describeError(submitError, t('teamTournament.games.submitError')))
      }
    } finally {
      setSubmittingMatchId(null)
    }
  }

  const renderLineups = (game: TournamentGame, series: TournamentSeries, muted: boolean) => (
    <div className={`grid gap-2 sm:grid-cols-2 ${muted ? 'opacity-70' : ''}`}>
      {TEAM_SIDES.map((side) => (
        <div key={side} className="rounded-md bg-slate-50 px-3 py-2 dark:bg-slate-800/60">
          <p className="text-[11px] font-medium text-slate-500 dark:text-slate-400">
            {teamLabel(side === 'HOME' ? series.homeTeamNumber : series.awayTeamNumber)}
          </p>
          <p className="mt-0.5 text-xs text-slate-800 dark:text-slate-200">
            {(side === 'HOME' ? game.homePlayers : game.awayPlayers).map(gamePlayerLine).join(', ')}
          </p>
        </div>
      ))}
    </div>
  )

  const renderResultForm = (series: TournamentSeries, game: TournamentGame) => {
    const draft = draftFor(game)
    const submitting = submittingMatchId === game.matchId
    const gameError = game.matchId !== null ? gameErrors[game.matchId] : undefined
    return (
      <div className="space-y-2">
        <p className="text-[11px] text-slate-500 dark:text-slate-400">{t('teamTournament.games.plannedHint')}</p>
        <div className="grid gap-2 sm:grid-cols-2">
          {TEAM_SIDES.map((side) => (
            <div key={side} className="rounded-md border border-slate-200 px-3 py-2 dark:border-slate-700">
              <p className="text-[11px] font-medium text-slate-500 dark:text-slate-400">
                {teamLabel(side === 'HOME' ? series.homeTeamNumber : series.awayTeamNumber)}
              </p>
              <ul className="mt-1 space-y-1">
                {(side === 'HOME' ? game.homePlayers : game.awayPlayers).map((player, index) => {
                  const race = draft.races[side][index] ?? null
                  return (
                    <li key={`${side}-${player.playerId ?? index}`} className="flex items-center justify-between gap-2 text-sm">
                      <span className="text-slate-800 dark:text-slate-200">{playerName(player)}</span>
                      <select
                        value={race ?? ''}
                        disabled={submitting || player.playerId === null}
                        onChange={(event) =>
                          updateDraft(game, (current) => {
                            const nextSide = [...current.races[side]]
                            nextSide[index] = normalizeAssignedRace(event.target.value)
                            return { ...current, races: { ...current.races, [side]: nextSide } }
                          })
                        }
                        aria-label={t('balance.quickResult.actualRaces.raceAriaLabel', { nickname: playerName(player) })}
                        className={SELECT_CLASS}
                      >
                        {race === null && (
                          <option value="" disabled>
                            -
                          </option>
                        )}
                        {ASSIGNED_RACES.map((option) => (
                          <option key={option} value={option}>
                            {option}
                          </option>
                        ))}
                      </select>
                    </li>
                  )
                })}
              </ul>
            </div>
          ))}
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <select
            value={draft.winner}
            disabled={submitting}
            onChange={(event) =>
              updateDraft(game, (current) => ({
                ...current,
                winner: event.target.value === 'HOME' || event.target.value === 'AWAY' ? event.target.value : '',
              }))
            }
            aria-label={t('teamTournament.games.winner')}
            className={`${SELECT_CLASS} py-1.5 text-sm`}
          >
            <option value="">{t('teamTournament.games.winnerPlaceholder')}</option>
            <option value="HOME">{teamLabel(series.homeTeamNumber)}</option>
            <option value="AWAY">{teamLabel(series.awayTeamNumber)}</option>
          </select>
          <button
            type="button"
            onClick={() => void handleSubmitGame(series, game)}
            disabled={submitting}
            className={PRIMARY_BUTTON_CLASS}
          >
            {submitting ? t('teamTournament.games.submitting') : t('teamTournament.games.submit')}
          </button>
        </div>
        {gameError && <p className="text-xs text-rose-600 dark:text-rose-300">{gameError}</p>}
      </div>
    )
  }

  const renderGame = (series: TournamentSeries, game: TournamentGame) => {
    const statusTone =
      game.status === 'NEXT'
        ? 'bg-indigo-100 text-indigo-800 dark:bg-indigo-950/60 dark:text-indigo-200'
        : game.status === 'PLAYED'
          ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950/60 dark:text-emerald-200'
          : 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300'
    return (
      <div className="space-y-2 rounded-lg border border-slate-100 p-3 dark:border-slate-800">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="text-sm font-semibold text-slate-900 dark:text-slate-100">
            {t('teamTournament.games.label', { number: game.gameNumber })}
            {game.raceComposition && <span className="ml-2 text-xs font-medium text-slate-500">{game.raceComposition}</span>}
          </p>
          <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${statusTone}`}>
            {game.status === 'PLAYED' && game.winnerTeam
              ? t('teamTournament.games.won', {
                  team: teamLabel(game.winnerTeam === 'HOME' ? series.homeTeamNumber : series.awayTeamNumber),
                })
              : t(`teamTournament.games.${game.status}`)}
          </span>
        </div>
        {game.status === 'NEXT' && running
          ? renderResultForm(series, game)
          : game.status !== 'SKIPPED' && renderLineups(game, series, game.status === 'UPCOMING')}
      </div>
    )
  }

  const standings = tournament?.status === 'COMPLETED' ? rankedTeams(tournament) : []
  const finalWaitingTeam = tournament ? finalWaitingTeamNumber(tournament) : null

  return (
    <section className="space-y-4">
      <article className={`${CARD_CLASS} space-y-3`}>
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div className="space-y-1">
            <h3 className="text-base font-semibold text-slate-900 dark:text-slate-100">{t('teamTournament.title')}</h3>
            <p className="text-xs text-slate-600 dark:text-slate-300">{t('teamTournament.description')}</p>
            <p className="text-xs text-slate-500 dark:text-slate-400">{t('teamTournament.rules')}</p>
          </div>
          {running && (
            <button type="button" onClick={() => void handleCancel()} disabled={cancelling} className={SECONDARY_BUTTON_CLASS}>
              {cancelling ? t('teamTournament.cancel.cancelling') : t('teamTournament.cancel.button')}
            </button>
          )}
        </div>
        <p className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200">
          {t('teamTournament.trialNotice')}
        </p>
        {!running && (
          <div className="space-y-2">
            {teamCount === null ? (
              <p className="text-xs text-slate-500 dark:text-slate-400">
                {t('teamTournament.create.unsupportedCount', { count: selectedCount })}
              </p>
            ) : (
              waitingCount > 0 && (
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  {t('teamTournament.create.waitingHint', { count: waitingCount })}
                </p>
              )
            )}
            <button type="button" onClick={() => void handleCreate()} disabled={!canCreate} className={PRIMARY_BUTTON_CLASS}>
              {creating ? t('teamTournament.create.creating') : t('teamTournament.create.button', { count: selectedCount })}
            </button>
          </div>
        )}
        {error && <ErrorAlert message={error} />}
      </article>

      {loading && <LoadingIndicator label={t('common.loading')} />}

      {tournament && (
        <>
          {standings.length > 0 && (
            <article className={`${CARD_CLASS} space-y-2`}>
              <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('teamTournament.standings.title')}</h4>
              <ol className="space-y-1">
                {standings.map((team) => (
                  <li key={team.teamId} className="flex flex-wrap items-baseline gap-2 text-sm">
                    <span className="font-semibold text-slate-900 dark:text-slate-100">
                      {t('teamTournament.standings.rank', { rank: team.finalRank ?? '-' })}
                    </span>
                    <span className="text-slate-700 dark:text-slate-200">{teamLabel(team.teamNumber)}</span>
                    <span className="text-xs text-slate-500 dark:text-slate-400">
                      {team.members.map(playerName).join(', ')}
                    </span>
                  </li>
                ))}
              </ol>
            </article>
          )}

          <article className={`${CARD_CLASS} space-y-3`}>
            <div className="flex items-center justify-between">
              <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('teamTournament.teams.title')}</h4>
              <span className="text-xs text-slate-500 dark:text-slate-400">{t(`teamTournament.status.${tournament.status}`)}</span>
            </div>
            <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
              {tournament.teams.map((team) => (
                <div key={team.teamId} className="rounded-lg border border-slate-200 px-3 py-2 dark:border-slate-700">
                  <div className="flex items-center justify-between">
                    <p className="text-sm font-semibold text-slate-900 dark:text-slate-100">{teamLabel(team.teamNumber)}</p>
                    {team.finalRank !== null ? (
                      <span className="text-xs font-medium text-emerald-700 dark:text-emerald-300">
                        {t('teamTournament.standings.rank', { rank: team.finalRank })}
                      </span>
                    ) : (
                      team.teamNumber === finalWaitingTeam && (
                        <span className="text-xs font-medium text-indigo-700 dark:text-indigo-300">
                          {t('teamTournament.teams.waitsInFinal')}
                        </span>
                      )
                    )}
                  </div>
                  <ul className="mt-1 space-y-0.5 text-xs text-slate-700 dark:text-slate-200">
                    {team.members.map((member, index) => (
                      <li key={`${team.teamId}-${member.playerId ?? index}`}>{memberLine(member, showMmr)}</li>
                    ))}
                  </ul>
                  {showMmr && typeof team.totalMmr === 'number' && (
                    <p className="mt-1 text-[11px] text-slate-500 dark:text-slate-400">
                      {t('teamTournament.teams.totalMmr')}: {team.totalMmr}
                    </p>
                  )}
                </div>
              ))}
            </div>
            {tournament.waitingPlayers.length > 0 && (
              <p className="text-xs text-amber-700 dark:text-amber-300">
                {t('teamTournament.teams.waiting')}: {tournament.waitingPlayers.map(playerName).join(', ')}
              </p>
            )}
          </article>

          <div className="grid gap-4 lg:grid-cols-2">
            {orderSeries(tournament.series).map((series) => (
              <article key={series.seriesId} className={`${CARD_CLASS} space-y-3`}>
                <header className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <p className="text-xs font-semibold text-indigo-700 dark:text-indigo-300">{roundLabel(series, tournament.teamCount)}</p>
                    <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
                      {t('teamTournament.series.versus', {
                        home: teamLabel(series.homeTeamNumber),
                        away: teamLabel(series.awayTeamNumber),
                      })}
                    </h4>
                    <p className="text-xs text-slate-500 dark:text-slate-400">{t(`teamTournament.formats.${series.format}`)}</p>
                  </div>
                  <div className="text-right">
                    <p className="text-lg font-semibold text-slate-900 dark:text-slate-100">
                      {series.homeWins} : {series.awayWins}
                    </p>
                    <p className="text-xs text-slate-500 dark:text-slate-400">
                      {series.winnerTeamNumber !== null
                        ? t('teamTournament.series.winner', { team: teamLabel(series.winnerTeamNumber) })
                        : t('teamTournament.status.IN_PROGRESS')}
                    </p>
                  </div>
                </header>
                <ol className="space-y-2">
                  {series.games.map((game) => (
                    <li key={`${series.seriesId}-${game.gameNumber}`}>{renderGame(series, game)}</li>
                  ))}
                </ol>
              </article>
            ))}
          </div>
        </>
      )}
    </section>
  )
}
