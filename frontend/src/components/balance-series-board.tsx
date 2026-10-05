'use client'

import { useCallback, useEffect, useState } from 'react'
import { apiClient, isApiConflictError, isApiForbiddenError } from '@/lib/api'
import { SeriesGameView, type GameDraft } from '@/components/series-game-view'
import { useAdminAuth } from '@/lib/admin-auth'
import { Alert, AlertContent, AlertDescription, AlertIcon } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { BALANCE_SERIES_POLL_MS, orderBoardSeries, seriesTeamNumber, teamLine } from '@/lib/balance-series'
import { t } from '@/lib/i18n'
import { buildParticipantRaces, initialRaceDraft, raceDraftMatchesComposition } from '@/lib/team-tournament'
import { startVisiblePolling } from '@/lib/visible-polling'
import type { BalanceSeries, TeamSide, TournamentGame } from '@/types/api'

type BalanceSeriesBoardProps = {
  groupId: number
  // Bumped by the page after it starts series, so the board shows them at once.
  refreshSignal: number
}

const CARD_CLASS = 'rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900'
const SECONDARY_BUTTON_CLASS =
  'rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800'

// The team numbers of the multi-balance the series came from: match 2 plays teams 3 and 4.
function sideLabel(series: BalanceSeries, side: TeamSide): string {
  return t('balanceSeries.board.teamLabel', { number: seriesTeamNumber(series, side) })
}

function seriesTitle(series: BalanceSeries): string {
  return series.matchNumber === null
    ? t('balanceSeries.board.seriesLabel', { number: series.seriesId })
    : t('balanceSeries.board.matchLabel', { number: series.matchNumber })
}

function describeError(error: unknown, fallback: string): string {
  return error instanceof Error && error.message.trim().length > 0 ? `${fallback} (${error.message})` : fallback
}

/** The series started from multi-balance results: score, games and a result form for the game in play. */
export function BalanceSeriesBoard({ groupId, refreshSignal }: BalanceSeriesBoardProps) {
  // Members run series; cancelling one stays with admins, as the backend enforces.
  const { isAdmin } = useAdminAuth()
  const [seriesList, setSeriesList] = useState<BalanceSeries[]>([])
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)
  const [cancellingId, setCancellingId] = useState<number | null>(null)
  const [drafts, setDrafts] = useState<Record<number, GameDraft>>({})
  const [submittingMatchId, setSubmittingMatchId] = useState<number | null>(null)
  const [gameErrors, setGameErrors] = useState<Record<number, string>>({})

  const load = useCallback(
    async (quiet = false) => {
      if (!quiet) {
        setLoading(true)
      }
      try {
        const response = await apiClient.getBalanceSeries(groupId)
        setSeriesList(orderBoardSeries(response.series))
        setError(null)
      } catch {
        if (!quiet) {
          setError(t('balanceSeries.board.loadError'))
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
    void load(refreshSignal > 0)
  }, [load, refreshSignal])

  // Other admins record games too, so running series are refreshed while the page is visible.
  const hasRunning = seriesList.some((series) => series.status === 'IN_PROGRESS')
  useEffect(() => {
    if (!hasRunning) {
      return
    }
    return startVisiblePolling(() => {
      void load(true)
    }, BALANCE_SERIES_POLL_MS)
  }, [hasRunning, load])

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

  const handleSubmitGame = async (series: BalanceSeries, game: TournamentGame) => {
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
    const winners = (draft.winner === 'HOME' ? game.homePlayers : game.awayPlayers)
      .map((player) => player.nickname ?? '-')
      .join(', ')
    if (!window.confirm(t('teamTournament.games.confirmWinner', { team: sideLabel(series, draft.winner), players: winners }))) {
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
      await load(true)
    } catch (submitError) {
      if (isApiConflictError(submitError)) {
        setGameError(matchId, t('balanceSeries.board.conflict'))
        await load(true)
      } else if (isApiForbiddenError(submitError)) {
        setGameError(matchId, t('teamTournament.games.forbidden'))
      } else {
        setGameError(matchId, describeError(submitError, t('teamTournament.games.submitError')))
      }
    } finally {
      setSubmittingMatchId(null)
    }
  }

  const handleCancel = async (series: BalanceSeries) => {
    if (!window.confirm(t('balanceSeries.board.cancelConfirm'))) {
      return
    }
    setCancellingId(series.seriesId)
    setError(null)
    try {
      const response = await apiClient.cancelBalanceSeries(groupId, series.seriesId)
      setSeriesList(orderBoardSeries(response.series))
    } catch (cancelError) {
      setError(describeError(cancelError, t('balanceSeries.board.cancelError')))
      void load(true)
    } finally {
      setCancellingId(null)
    }
  }

  return (
    <section className="space-y-4">
      <article className={`${CARD_CLASS} space-y-1`}>
        <h3 className="text-base font-semibold text-slate-900 dark:text-slate-100">{t('balanceSeries.board.title')}</h3>
        <p className="text-xs text-slate-600 dark:text-slate-300">{t('balanceSeries.board.description')}</p>
        {error && (
          <Alert variant="destructive" appearance="light" size="sm" className="mt-2">
            <AlertIcon icon="destructive">!</AlertIcon>
            <AlertContent>
              <AlertDescription>{error}</AlertDescription>
            </AlertContent>
          </Alert>
        )}
        {!loading && !error && seriesList.length === 0 && (
          <p className="pt-1 text-xs text-slate-500 dark:text-slate-400">{t('balanceSeries.board.empty')}</p>
        )}
      </article>

      {loading && <LoadingIndicator label={t('common.loading')} />}

      {seriesList.length > 0 && (
        <div className="grid gap-4 lg:grid-cols-2">
          {seriesList.map((series) => {
            const running = series.status === 'IN_PROGRESS'
            return (
              <article key={series.seriesId} className={`${CARD_CLASS} space-y-3`}>
                <header className="flex flex-wrap items-start justify-between gap-2">
                  <div className="space-y-0.5">
                    <p className="text-xs font-semibold text-indigo-700 dark:text-indigo-300">
                      {seriesTitle(series)} ·{' '}
                      {t(`balanceSeries.formats.${series.format}`)}
                    </p>
                    <p className="text-xs text-slate-700 dark:text-slate-200">
                      <span className="font-medium">{sideLabel(series, 'HOME')}</span> {teamLine(series.homePlayers)}
                    </p>
                    <p className="text-xs text-slate-700 dark:text-slate-200">
                      <span className="font-medium">{sideLabel(series, 'AWAY')}</span> {teamLine(series.awayPlayers)}
                    </p>
                  </div>
                  <div className="text-right">
                    <p className="text-lg font-semibold text-slate-900 dark:text-slate-100">
                      {series.homeWins} : {series.awayWins}
                    </p>
                    <p className="text-xs text-slate-500 dark:text-slate-400">
                      {series.winnerTeam
                        ? t('balanceSeries.board.winner', { team: sideLabel(series, series.winnerTeam) })
                        : t(`balanceSeries.board.status.${series.status}`)}
                    </p>
                    {running && isAdmin && (
                      <button
                        type="button"
                        onClick={() => void handleCancel(series)}
                        disabled={cancellingId === series.seriesId}
                        className={`${SECONDARY_BUTTON_CLASS} mt-1`}
                      >
                        {cancellingId === series.seriesId
                          ? t('balanceSeries.board.cancelling')
                          : t('balanceSeries.board.cancelButton')}
                      </button>
                    )}
                  </div>
                </header>
                <ol className="space-y-2">
                  {series.games.map((game) => (
                    <li key={`${series.seriesId}-${game.gameNumber}`}>
                      <SeriesGameView
                        game={game}
                        homeLabel={sideLabel(series, 'HOME')}
                        awayLabel={sideLabel(series, 'AWAY')}
                        editable={game.status === 'NEXT' && running}
                        draft={draftFor(game)}
                        submitting={submittingMatchId === game.matchId}
                        error={game.matchId !== null ? gameErrors[game.matchId] : undefined}
                        onDraftChange={(change) => updateDraft(game, change)}
                        onSubmit={() => void handleSubmitGame(series, game)}
                      />
                    </li>
                  ))}
                </ol>
              </article>
            )
          })}
        </div>
      )}
    </section>
  )
}
