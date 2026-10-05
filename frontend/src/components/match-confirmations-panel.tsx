'use client'

import { useCallback, useEffect, useState } from 'react'
import { apiClient, isApiBadRequestError, isApiConflictError, isApiForbiddenError } from '@/lib/api'
import { t } from '@/lib/i18n'
import {
  MATCH_CONFIRM_POLL_MS,
  confirmCapReached,
  formatConfirmDeadline,
  myTeamWon,
  unconfirmedMatches,
} from '@/lib/match-confirmations'
import { formatPredictionPlayers } from '@/lib/predictions'
import { startVisiblePolling } from '@/lib/visible-polling'
import type { MatchConfirmation, MatchConfirmationList, TeamSide } from '@/types/api'

type MatchConfirmationsPanelProps = {
  groupId: number
}

const CARD_CLASS = 'rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900'
const PRIMARY_BUTTON_CLASS =
  'rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-60'
const SECONDARY_BUTTON_CLASS =
  'rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800'

function teamName(team: TeamSide): string {
  return team === 'HOME' ? t('predictions.home') : t('predictions.away')
}

function confirmErrorMessage(error: unknown, list: MatchConfirmationList | null): string {
  if (isApiConflictError(error)) {
    return t('predictions.confirm.capReached', { cap: list?.dailyCap ?? 10 })
  }
  if (isApiBadRequestError(error)) {
    return t('predictions.confirm.expired')
  }
  if (isApiForbiddenError(error)) {
    return t('predictions.confirm.notMine')
  }
  return error instanceof Error && error.message.trim().length > 0
    ? `${t('predictions.confirm.error')} (${error.message})`
    : t('predictions.confirm.error')
}

function Lineup({ match, side }: { match: MatchConfirmation; side: TeamSide }) {
  const players = side === 'HOME' ? match.homePlayers : match.awayPlayers
  return (
    <div
      className={`rounded-md px-3 py-2 ${
        match.myTeam === side ? 'bg-indigo-50 dark:bg-indigo-950/40' : 'bg-slate-50 dark:bg-slate-800/60'
      }`}
    >
      <p className="flex flex-wrap items-center gap-1 text-[11px] font-medium text-slate-500 dark:text-slate-400">
        {teamName(side)}
        {match.myTeam === side && (
          <span className="rounded bg-indigo-600 px-1.5 py-0.5 text-[10px] text-white">{t('predictions.confirm.myTeamBadge')}</span>
        )}
        {match.winnerTeam === side && (
          <span className="rounded bg-emerald-600 px-1.5 py-0.5 text-[10px] text-white">{t('predictions.confirm.winnerBadge')}</span>
        )}
      </p>
      <p className="mt-0.5 text-sm text-slate-800 dark:text-slate-200">{formatPredictionPlayers(players)}</p>
    </div>
  )
}

/**
 * Balanced 3v3 matches the player played, whose result they confirm for a point. Predictions are
 * closed to a match's own players, so this is how they earn from it. Hidden while points are not
 * open to the account.
 */
export function MatchConfirmationsPanel({ groupId }: MatchConfirmationsPanelProps) {
  const [list, setList] = useState<MatchConfirmationList | null>(null)
  const [hidden, setHidden] = useState<boolean>(false)
  const [loadError, setLoadError] = useState<boolean>(false)
  const [busyMatchId, setBusyMatchId] = useState<number | null>(null)
  const [confirmingAll, setConfirmingAll] = useState<boolean>(false)
  const [errors, setErrors] = useState<Record<number, string>>({})

  const load = useCallback(
    async (quiet = false) => {
      try {
        setList(await apiClient.getMatchConfirmations(groupId))
        setLoadError(false)
      } catch (error) {
        if (isApiForbiddenError(error)) {
          setHidden(true)
        } else if (!quiet) {
          setLoadError(true)
        }
      }
    },
    [groupId],
  )

  useEffect(() => {
    void load()
    return startVisiblePolling(() => {
      void load(true)
    }, MATCH_CONFIRM_POLL_MS)
  }, [load])

  const setError = (matchId: number, message: string | null) => {
    setErrors((previous) => {
      const next = { ...previous }
      if (message === null) {
        delete next[matchId]
      } else {
        next[matchId] = message
      }
      return next
    })
  }

  // Resolves to whether the confirmation went through, so "confirm all" stops at the first refusal.
  const confirmOne = async (match: MatchConfirmation): Promise<boolean> => {
    setBusyMatchId(match.matchId)
    setError(match.matchId, null)
    try {
      setList(await apiClient.confirmMatchResult(groupId, match.matchId))
      return true
    } catch (error) {
      setError(match.matchId, confirmErrorMessage(error, list))
      void load(true)
      return false
    } finally {
      setBusyMatchId(null)
    }
  }

  const confirmAll = async () => {
    if (!list) {
      return
    }
    setConfirmingAll(true)
    try {
      for (const match of unconfirmedMatches(list)) {
        if (!(await confirmOne(match))) {
          break
        }
      }
    } finally {
      setConfirmingAll(false)
    }
  }

  if (hidden) {
    return null
  }

  const capReached = list ? confirmCapReached(list) : false
  const pending = list ? unconfirmedMatches(list) : []
  const busy = busyMatchId !== null || confirmingAll

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('predictions.confirm.title')}</h2>
        {list && (
          <span className="text-xs text-slate-500 dark:text-slate-400">
            {t('predictions.confirm.today', { count: list.confirmedToday, cap: list.dailyCap })}
          </span>
        )}
      </div>
      {list && (
        <div className="space-y-1 text-xs">
          <p className="text-slate-500 dark:text-slate-400">
            {t('predictions.confirm.description', { points: list.points, hours: list.windowHours })}
          </p>
          <p className="text-amber-700 dark:text-amber-300">{t('predictions.confirm.notice')}</p>
        </div>
      )}
      {loadError && <p className="text-xs text-rose-600 dark:text-rose-300">{t('predictions.confirm.error')}</p>}
      {list && capReached && pending.length > 0 && (
        <p className="text-xs text-rose-600 dark:text-rose-300">{t('predictions.confirm.capReached', { cap: list.dailyCap })}</p>
      )}
      {list && !capReached && pending.length >= 2 && (
        <button type="button" disabled={busy} onClick={() => void confirmAll()} className={SECONDARY_BUTTON_CLASS}>
          {t('predictions.confirm.confirmAll', { count: pending.length })}
        </button>
      )}
      {list && list.matches.length === 0 && (
        <p className="text-sm text-slate-500 dark:text-slate-400">{t('predictions.confirm.empty')}</p>
      )}
      {list?.matches.map((match) => (
        <article key={match.matchId} className={`${CARD_CLASS} space-y-3`}>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="text-xs font-semibold text-indigo-700 dark:text-indigo-300">
              {match.seriesGameNumber !== null
                ? `${t('predictions.confirm.seriesGame', { number: match.seriesGameNumber })} · `
                : ''}
              {match.raceComposition ?? ''}
            </p>
            {!match.confirmed && (
              <span className="text-xs text-slate-500 dark:text-slate-400">
                {t('predictions.confirm.deadline', { time: formatConfirmDeadline(match.confirmDeadline) })}
              </span>
            )}
          </div>
          <div className="grid gap-2 sm:grid-cols-2">
            <Lineup match={match} side="HOME" />
            <Lineup match={match} side="AWAY" />
          </div>
          <div className="flex flex-wrap items-center justify-between gap-2">
            {match.winnerTeam && (
              <p className="text-sm font-medium text-slate-700 dark:text-slate-200">
                {t('predictions.confirm.result', {
                  winner: teamName(match.winnerTeam),
                  outcome: myTeamWon(match) ? t('predictions.confirm.won') : t('predictions.confirm.lost'),
                })}
              </p>
            )}
            {match.confirmed ? (
              <span className="text-sm font-semibold text-emerald-600 dark:text-emerald-400">
                {t('predictions.confirm.confirmed', { points: list.points })}
              </span>
            ) : (
              <button
                type="button"
                disabled={busy || capReached}
                onClick={() => void confirmOne(match)}
                className={PRIMARY_BUTTON_CLASS}
              >
                {busyMatchId === match.matchId
                  ? t('predictions.confirm.confirming')
                  : t('predictions.confirm.confirm', { points: list.points })}
              </button>
            )}
          </div>
          {errors[match.matchId] && <p className="text-xs text-rose-600 dark:text-rose-300">{errors[match.matchId]}</p>}
        </article>
      ))}
    </div>
  )
}
