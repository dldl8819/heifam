'use client'

import { t } from '@/lib/i18n'
import { ASSIGNED_RACES, normalizeAssignedRace } from '@/lib/participant-races'
import { TEAM_SIDES, type GameRaceDraft } from '@/lib/team-tournament'
import type { TeamSide, TournamentGame, TournamentGamePlayer } from '@/types/api'

export type GameDraft = {
  winner: TeamSide | ''
  races: GameRaceDraft
}

type SeriesGameViewProps = {
  game: TournamentGame
  homeLabel: string
  awayLabel: string
  // True for the game waiting for its result while the series runs: it shows the result form.
  editable: boolean
  draft: GameDraft
  submitting: boolean
  error?: string
  onDraftChange: (change: (draft: GameDraft) => GameDraft) => void
  onSubmit: () => void
}

const PRIMARY_BUTTON_CLASS =
  'rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white dark:disabled:bg-slate-700 dark:disabled:text-slate-400'
const SELECT_CLASS =
  'rounded-md border border-slate-300 bg-white px-2 py-1 text-xs text-slate-800 outline-none focus:border-slate-400 focus:ring-1 focus:ring-slate-200 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-slate-500 dark:focus:ring-slate-700'

function playerName(player: { nickname: string | null }): string {
  return player.nickname ?? '-'
}

// Protoss needs no marker; Terran and Zerg are what the series games turn on.
function gamePlayerLine(player: TournamentGamePlayer): string {
  return player.assignedRace === 'T' || player.assignedRace === 'Z'
    ? `${playerName(player)} (${player.assignedRace})`
    : playerName(player)
}

/** One game of a series, in a team tournament or after a multi-balance. */
export function SeriesGameView({
  game,
  homeLabel,
  awayLabel,
  editable,
  draft,
  submitting,
  error,
  onDraftChange,
  onSubmit,
}: SeriesGameViewProps) {
  const sideLabel = (side: TeamSide) => (side === 'HOME' ? homeLabel : awayLabel)
  const sidePlayers = (side: TeamSide) => (side === 'HOME' ? game.homePlayers : game.awayPlayers)
  const statusTone =
    game.status === 'NEXT'
      ? 'bg-indigo-100 text-indigo-800 dark:bg-indigo-950/60 dark:text-indigo-200'
      : game.status === 'PLAYED'
        ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950/60 dark:text-emerald-200'
        : 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300'

  const lineups = (
    <div className={`grid gap-2 sm:grid-cols-2 ${game.status === 'UPCOMING' ? 'opacity-70' : ''}`}>
      {TEAM_SIDES.map((side) => (
        <div key={side} className="rounded-md bg-slate-50 px-3 py-2 dark:bg-slate-800/60">
          <p className="text-[11px] font-medium text-slate-500 dark:text-slate-400">{sideLabel(side)}</p>
          <p className="mt-0.5 text-xs text-slate-800 dark:text-slate-200">
            {sidePlayers(side).map(gamePlayerLine).join(', ')}
          </p>
        </div>
      ))}
    </div>
  )

  const resultForm = (
    <div className="space-y-2">
      <p className="text-[11px] text-slate-500 dark:text-slate-400">{t('teamTournament.games.plannedHint')}</p>
      <div className="grid gap-2 sm:grid-cols-2">
        {TEAM_SIDES.map((side) => (
          <div key={side} className="rounded-md border border-slate-200 px-3 py-2 dark:border-slate-700">
            <p className="text-[11px] font-medium text-slate-500 dark:text-slate-400">{sideLabel(side)}</p>
            <ul className="mt-1 space-y-1">
              {sidePlayers(side).map((player, index) => {
                const race = draft.races[side][index] ?? null
                return (
                  <li key={`${side}-${player.playerId ?? index}`} className="flex items-center justify-between gap-2 text-sm">
                    <span className="text-slate-800 dark:text-slate-200">{playerName(player)}</span>
                    <select
                      value={race ?? ''}
                      disabled={submitting || player.playerId === null}
                      onChange={(event) =>
                        onDraftChange((current) => {
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
            onDraftChange((current) => ({
              ...current,
              winner: event.target.value === 'HOME' || event.target.value === 'AWAY' ? event.target.value : '',
            }))
          }
          aria-label={t('teamTournament.games.winner')}
          className={`${SELECT_CLASS} py-1.5 text-sm`}
        >
          <option value="">{t('teamTournament.games.winnerPlaceholder')}</option>
          <option value="HOME">{homeLabel}</option>
          <option value="AWAY">{awayLabel}</option>
        </select>
        <button type="button" onClick={onSubmit} disabled={submitting} className={PRIMARY_BUTTON_CLASS}>
          {submitting ? t('teamTournament.games.submitting') : t('teamTournament.games.submit')}
        </button>
      </div>
      {error && <p className="text-xs text-rose-600 dark:text-rose-300">{error}</p>}
    </div>
  )

  return (
    <div className="space-y-2 rounded-lg border border-slate-100 p-3 dark:border-slate-800">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm font-semibold text-slate-900 dark:text-slate-100">
          {t('teamTournament.games.label', { number: game.gameNumber })}
          {game.raceComposition && <span className="ml-2 text-xs font-medium text-slate-500">{game.raceComposition}</span>}
        </p>
        <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${statusTone}`}>
          {game.status === 'PLAYED' && game.winnerTeam
            ? t('teamTournament.games.won', { team: sideLabel(game.winnerTeam) })
            : t(`teamTournament.games.${game.status}`)}
        </span>
      </div>
      {editable ? resultForm : game.status !== 'SKIPPED' && lineups}
    </div>
  )
}
