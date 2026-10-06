'use client'

import { useCallback, useEffect, useState } from 'react'
import { useAdminAuth } from '@/lib/admin-auth'
import { apiClient, isApiConflictError, isApiNotFoundError } from '@/lib/api'
import { Alert, AlertContent, AlertDescription, AlertIcon } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { MatchConfirmationsPanel } from '@/components/match-confirmations-panel'
import { t } from '@/lib/i18n'
import { formatKstDateTime } from '@/lib/kst-time'
import {
  PREDICTION_POLL_MS,
  canCallOffMatch,
  formatCountdown,
  formatPickers,
  formatPredictionPlayers,
  hitRate,
  remainingSeconds,
  serverOffsetMs,
} from '@/lib/predictions'
import { startVisiblePolling } from '@/lib/visible-polling'
import type { PredictionBoard, PredictionMatch, TeamSide } from '@/types/api'

const TEMP_GROUP_ID = 1
const CARD_CLASS = 'rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900'

function teamName(team: TeamSide): string {
  return team === 'HOME' ? t('predictions.home') : t('predictions.away')
}

function describeError(error: unknown, fallback: string): string {
  return error instanceof Error && error.message.trim().length > 0 ? `${fallback} (${error.message})` : fallback
}

function MatchTitle({ match }: { match: PredictionMatch }) {
  const setUpAt = formatKstDateTime(match.createdAt)
  return (
    <p className="text-xs font-semibold text-indigo-700 dark:text-indigo-300">
      {match.seriesGameNumber !== null ? `${t('predictions.tournamentGame', { number: match.seriesGameNumber })} · ` : ''}
      {match.raceComposition ?? ''}
      {/* Whom the match is waiting on for its result, and since when. */}
      <span className="ml-2 font-normal text-slate-500 dark:text-slate-400">
        {[
          match.createdByNickname ? t('predictions.createdBy', { nickname: match.createdByNickname }) : '',
          setUpAt ? t('predictions.createdAt', { time: setUpAt }) : '',
        ]
          .filter((part) => part.length > 0)
          .join(' · ')}
      </span>
    </p>
  )
}

function Lineups({ match }: { match: PredictionMatch }) {
  return (
    <div className="grid gap-2 sm:grid-cols-2">
      {(['HOME', 'AWAY'] as TeamSide[]).map((side) => (
        <div key={side} className="rounded-md bg-slate-50 px-3 py-2 dark:bg-slate-800/60">
          <p className="text-[11px] font-medium text-slate-500 dark:text-slate-400">{teamName(side)}</p>
          <p className="mt-0.5 text-sm text-slate-800 dark:text-slate-200">
            {formatPredictionPlayers(side === 'HOME' ? match.homePlayers : match.awayPlayers)}
          </p>
        </div>
      ))}
    </div>
  )
}

function PickCounts({ match }: { match: PredictionMatch }) {
  const { homePicks, awayPicks } = match
  if (homePicks === null || awayPicks === null) {
    return null
  }
  // Counts and names come only once picks are closed, so nobody follows the crowd.
  return (
    <div className="space-y-0.5 text-xs text-slate-600 dark:text-slate-300">
      {(['HOME', 'AWAY'] as TeamSide[]).map((side) => {
        const names = formatPickers(
          side === 'HOME' ? match.homePickers : match.awayPickers,
          t('predictions.unknownPicker'),
        )
        return (
          <p key={side}>
            <span className="font-medium">
              {t('predictions.pickCount', {
                team: teamName(side),
                count: side === 'HOME' ? homePicks : awayPicks,
              })}
            </span>
            {names && `: ${names}`}
          </p>
        )
      })}
    </div>
  )
}

export default function PredictionsPage() {
  const { isAdmin } = useAdminAuth()
  const [board, setBoard] = useState<PredictionBoard | null>(null)
  const [offsetMs, setOffsetMs] = useState<number>(0)
  const [clientNow, setClientNow] = useState<number>(() => Date.now())
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)
  const [busyMatchId, setBusyMatchId] = useState<number | null>(null)
  const [matchErrors, setMatchErrors] = useState<Record<number, string>>({})

  const loadBoard = useCallback(async (quiet = false) => {
    if (!quiet) {
      setLoading(true)
    }
    try {
      const response = await apiClient.getPredictionBoard(TEMP_GROUP_ID)
      const fetchedAt = Date.now()
      setBoard(response)
      setOffsetMs(serverOffsetMs(response.now, fetchedAt))
      setClientNow(fetchedAt)
      setError(null)
    } catch {
      if (!quiet) {
        setError(t('predictions.loadError'))
      }
    } finally {
      if (!quiet) {
        setLoading(false)
      }
    }
  }, [])

  useEffect(() => {
    void loadBoard()
    return startVisiblePolling(() => {
      void loadBoard(true)
    }, PREDICTION_POLL_MS)
  }, [loadBoard])

  // The countdown ticks locally; the board reloads once when a window runs out.
  const openCount = board?.open.length ?? 0
  useEffect(() => {
    if (openCount === 0) {
      return
    }
    const intervalId = setInterval(() => setClientNow(Date.now()), 1000)
    return () => clearInterval(intervalId)
  }, [openCount])

  const anyExpired =
    board?.open.some((match) => remainingSeconds(match.closesAt, offsetMs, clientNow) === 0) ?? false
  useEffect(() => {
    if (anyExpired) {
      void loadBoard(true)
    }
  }, [anyExpired, loadBoard])

  const setMatchError = (matchId: number, message: string | null) => {
    setMatchErrors((previous) => {
      const next = { ...previous }
      if (message === null) {
        delete next[matchId]
      } else {
        next[matchId] = message
      }
      return next
    })
  }

  const handlePick = async (match: PredictionMatch, team: TeamSide) => {
    setBusyMatchId(match.matchId)
    setMatchError(match.matchId, null)
    try {
      const updated = await apiClient.submitPrediction(TEMP_GROUP_ID, match.matchId, team)
      setBoard((previous) =>
        previous
          ? { ...previous, open: previous.open.map((item) => (item.matchId === updated.matchId ? updated : item)) }
          : previous,
      )
    } catch (pickError) {
      if (isApiConflictError(pickError)) {
        setMatchError(match.matchId, t('predictions.closedNow'))
        void loadBoard(true)
      } else {
        setMatchError(match.matchId, describeError(pickError, t('predictions.submitError')))
      }
    } finally {
      setBusyMatchId(null)
    }
  }

  const handleClose = async (match: PredictionMatch) => {
    if (!window.confirm(t('predictions.closeConfirm'))) {
      return
    }
    setBusyMatchId(match.matchId)
    try {
      await apiClient.closePredictions(TEMP_GROUP_ID, match.matchId)
      await loadBoard(true)
    } catch (closeError) {
      setMatchError(match.matchId, describeError(closeError, t('predictions.submitError')))
    } finally {
      setBusyMatchId(null)
    }
  }

  // The game is not being played after all: the match and the picks on it go.
  const handleCallOff = async (match: PredictionMatch) => {
    if (!window.confirm(t('predictions.callOffConfirm'))) {
      return
    }
    setBusyMatchId(match.matchId)
    setMatchError(match.matchId, null)
    try {
      await apiClient.cancelMatch(match.matchId)
      await loadBoard(true)
    } catch (callOffError) {
      if (isApiNotFoundError(callOffError)) {
        // Someone else called it off already.
        await loadBoard(true)
      } else if (isApiConflictError(callOffError)) {
        setMatchError(match.matchId, t('predictions.callOffConflict'))
        await loadBoard(true)
      } else {
        setMatchError(match.matchId, describeError(callOffError, t('predictions.callOffError')))
      }
    } finally {
      setBusyMatchId(null)
    }
  }

  const callOffButton = (match: PredictionMatch) =>
    canCallOffMatch(match, isAdmin) ? (
      <button
        type="button"
        disabled={busyMatchId === match.matchId}
        onClick={() => void handleCallOff(match)}
        className="rounded-lg border border-rose-300 px-3 py-1 text-xs text-rose-700 hover:bg-rose-50 disabled:opacity-60 dark:border-rose-800 dark:text-rose-300 dark:hover:bg-rose-950/40"
      >
        {t('predictions.callOff')}
      </button>
    ) : null

  const rate = board ? hitRate(board.stats) : null

  return (
    <section className="space-y-6">
      <div className="space-y-1">
        <h1 className="text-lg font-semibold text-slate-900 dark:text-slate-100">{t('predictions.title')}</h1>
        <p className="text-sm text-slate-500 dark:text-slate-400">
          {t('predictions.description', { minutes: board?.windowMinutes ?? 3 })}
        </p>
      </div>

      {loading && !board && <LoadingIndicator label={t('common.loading')} />}
      {error && (
        <Alert variant="destructive" appearance="light">
          <AlertIcon icon="destructive">!</AlertIcon>
          <AlertContent>
            <AlertDescription>{error}</AlertDescription>
          </AlertContent>
        </Alert>
      )}

      {board && (
        <>
          <p className="text-sm font-medium text-slate-700 dark:text-slate-200">
            {t('predictions.stats', { hits: board.stats.hits, resolved: board.stats.resolved })}
            {rate !== null && <span className="ml-2 text-slate-500 dark:text-slate-400">{t('predictions.hitRate', { rate })}</span>}
          </p>

          <div className="space-y-3">
            <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('predictions.open.title')}</h2>
            {board.open.length === 0 && (
              <p className="text-sm text-slate-500 dark:text-slate-400">{t('predictions.open.empty')}</p>
            )}
            {board.open.map((match) => {
              const seconds = remainingSeconds(match.closesAt, offsetMs, clientNow)
              const busy = busyMatchId === match.matchId
              return (
                <article key={match.matchId} className={`${CARD_CLASS} space-y-3`}>
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <MatchTitle match={match} />
                    <span className="text-xs font-semibold text-rose-600 dark:text-rose-300">
                      {t('predictions.timeLeft', { time: formatCountdown(seconds) })}
                    </span>
                  </div>
                  <Lineups match={match} />
                  {match.ownMatch ? (
                    <p className="text-xs text-slate-500 dark:text-slate-400">{t('predictions.ownMatch')}</p>
                  ) : (
                    <div className="flex flex-wrap gap-2">
                      {(['HOME', 'AWAY'] as TeamSide[]).map((team) => {
                        const picked = match.myPick === team
                        return (
                          <button
                            key={team}
                            type="button"
                            disabled={busy || seconds === 0}
                            onClick={() => void handlePick(match, team)}
                            className={`rounded-lg px-4 py-2 text-sm font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-60 ${
                              picked
                                ? 'bg-indigo-600 text-white hover:bg-indigo-500'
                                : 'border border-slate-300 text-slate-700 hover:bg-slate-50 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800'
                            }`}
                          >
                            {team === 'HOME' ? t('predictions.pickHome') : t('predictions.pickAway')}
                          </button>
                        )
                      })}
                    </div>
                  )}
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    {match.myPick && (
                      <p className="text-xs text-indigo-700 dark:text-indigo-300">
                        {t('predictions.myPick', { team: teamName(match.myPick) })}
                      </p>
                    )}
                    <div className="flex flex-wrap items-center gap-2">
                      {isAdmin && (
                        <button
                          type="button"
                          disabled={busy}
                          onClick={() => void handleClose(match)}
                          className="rounded-lg border border-slate-300 px-3 py-1 text-xs text-slate-600 hover:bg-slate-50 disabled:opacity-60 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-800"
                        >
                          {t('predictions.close')}
                        </button>
                      )}
                      {callOffButton(match)}
                    </div>
                  </div>
                  {matchErrors[match.matchId] && (
                    <p className="text-xs text-rose-600 dark:text-rose-300">{matchErrors[match.matchId]}</p>
                  )}
                </article>
              )
            })}
          </div>

          <MatchConfirmationsPanel groupId={TEMP_GROUP_ID} />

          <div className="space-y-3">
            <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('predictions.closed.title')}</h2>
            {board.closed.length === 0 && (
              <p className="text-sm text-slate-500 dark:text-slate-400">{t('predictions.closed.empty')}</p>
            )}
            {board.closed.map((match) => (
              <article key={match.matchId} className={`${CARD_CLASS} space-y-2`}>
                <MatchTitle match={match} />
                <Lineups match={match} />
                <PickCounts match={match} />
                <div className="flex flex-wrap items-center justify-between gap-2">
                  {match.myPick ? (
                    <p className="text-xs text-indigo-700 dark:text-indigo-300">
                      {t('predictions.myPick', { team: teamName(match.myPick) })}
                    </p>
                  ) : (
                    <span />
                  )}
                  {callOffButton(match)}
                </div>
                {matchErrors[match.matchId] && (
                  <p className="text-xs text-rose-600 dark:text-rose-300">{matchErrors[match.matchId]}</p>
                )}
              </article>
            ))}
          </div>

          <div className="space-y-3">
            <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('predictions.history.title')}</h2>
            {board.history.length === 0 && (
              <p className="text-sm text-slate-500 dark:text-slate-400">{t('predictions.history.empty')}</p>
            )}
            {board.history.length > 0 && (
              <ul className={`${CARD_CLASS} divide-y divide-slate-100 p-0 dark:divide-slate-800`}>
                {board.history.map((match) => (
                  <li key={match.matchId} className="space-y-1 px-4 py-3">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <MatchTitle match={match} />
                      <span
                        className={`text-xs font-semibold ${
                          match.hit ? 'text-emerald-600 dark:text-emerald-400' : 'text-slate-500 dark:text-slate-400'
                        }`}
                      >
                        {match.hit ? t('predictions.history.hit') : t('predictions.history.miss')}
                      </span>
                    </div>
                    <p className="text-xs text-slate-600 dark:text-slate-300">
                      {formatPredictionPlayers(match.homePlayers)} vs {formatPredictionPlayers(match.awayPlayers)}
                    </p>
                    <p className="text-xs text-slate-500 dark:text-slate-400">
                      {match.myPick && t('predictions.myPick', { team: teamName(match.myPick) })}
                      {match.winnerTeam && ` · ${t('predictions.history.winner', { team: teamName(match.winnerTeam) })}`}
                      {match.pointsExcluded && ` · ${t('predictions.history.excluded')}`}
                    </p>
                    <PickCounts match={match} />
                  </li>
                ))}
              </ul>
            )}
          </div>
        </>
      )}
    </section>
  )
}
