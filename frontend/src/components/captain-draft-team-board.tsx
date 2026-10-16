'use client'

import { t } from '@/lib/i18n'
import { draftPlayerTags, sortByTier, type DraftPlayerTags, type SetWins } from '@/lib/captain-draft'
import { cn } from '@/lib/utils'
import type { CaptainDraftParticipant, CaptainDraftResponse, PlayerRosterItem, TeamSide } from '@/types/api'

type CaptainDraftTeamBoardProps = {
  draft: CaptainDraftResponse
  playerMap: Map<number, PlayerRosterItem>
  setWins: SetWins
  // A pick or a take-back in flight; every button waits for it.
  busy: boolean
  onPick: (team: TeamSide, playerId: number) => void
  onUnpick: (playerId: number) => void
}

const TEAM_STYLES: Record<TeamSide, { header: string; button: string }> = {
  HOME: {
    header: 'text-sky-700 dark:text-sky-300',
    button:
      'border-sky-300 text-sky-700 hover:bg-sky-50 dark:border-sky-700 dark:text-sky-300 dark:hover:bg-sky-950/50',
  },
  AWAY: {
    header: 'text-rose-700 dark:text-rose-300',
    button:
      'border-rose-300 text-rose-700 hover:bg-rose-50 dark:border-rose-700 dark:text-rose-300 dark:hover:bg-rose-950/50',
  },
}

function Tags({ tags }: { tags: DraftPlayerTags }) {
  return (
    <>
      {tags.tier && (
        <span className="rounded bg-amber-100 px-1 text-[11px] font-semibold text-amber-800 dark:bg-amber-900/50 dark:text-amber-200">
          {tags.tier}
        </span>
      )}
      {tags.races && (
        <span className="rounded bg-slate-100 px-1 text-[11px] font-semibold text-slate-600 dark:bg-slate-800 dark:text-slate-300">
          {tags.races}
        </span>
      )}
    </>
  )
}

function playerTags(participant: CaptainDraftParticipant, playerMap: Map<number, PlayerRosterItem>): DraftPlayerTags {
  return draftPlayerTags(
    typeof participant.playerId === 'number' ? playerMap.get(participant.playerId) : undefined,
    participant.race,
  )
}

/**
 * Who is on which team, captain first, with each player's tier, races and set wins. The host puts
 * the players the captains call out into a team, in any order, and takes back a pick made by mistake.
 * Teams and the players left are listed by tier, highest first, which is how captains pick.
 */
export function CaptainDraftTeamBoard({ draft, playerMap, setWins, busy, onPick, onUnpick }: CaptainDraftTeamBoardProps) {
  const teams: { side: TeamSide; label: string; wins: number; members: CaptainDraftParticipant[] }[] = [
    {
      side: 'HOME',
      label: t('captainDraft.scoreboard.homeHeader'),
      wins: setWins.home,
      members: sortByTier(draft.participants.filter((participant) => participant.team === 'HOME'), playerMap),
    },
    {
      side: 'AWAY',
      label: t('captainDraft.scoreboard.awayHeader'),
      wins: setWins.away,
      members: sortByTier(draft.participants.filter((participant) => participant.team === 'AWAY'), playerMap),
    },
  ]
  const remaining = sortByTier(
    draft.participants.filter((participant) => participant.team === 'UNASSIGNED' && !participant.captain),
    playerMap,
  )

  return (
    <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
      <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('captainDraft.scoreboard.title')}</h3>
      <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{t('captainDraft.scoreboard.helper')}</p>

      <div className="mt-3 grid grid-cols-2 gap-2">
        {teams.map((team) => (
          <section
            key={team.side}
            aria-label={team.label}
            className="min-w-0 rounded-lg border border-slate-200 dark:border-slate-700"
          >
            <header className="flex items-baseline justify-between gap-1 border-b border-slate-200 px-2 py-1.5 dark:border-slate-700">
              <span className={cn('text-sm font-semibold', TEAM_STYLES[team.side].header)}>
                {team.label}{' '}
                <span className="text-xs font-normal text-slate-500 dark:text-slate-400">
                  {t('captainDraft.teams.memberCount', { count: team.members.length })}
                </span>
              </span>
              <span className="text-lg font-bold text-slate-900 dark:text-slate-100">{team.wins}</span>
            </header>
            <ul className="divide-y divide-slate-100 dark:divide-slate-800">
              {team.members.map((member) => {
                const wins = typeof member.playerId === 'number' ? (setWins.byPlayer.get(member.playerId) ?? 0) : 0
                return (
                  <li key={`${team.side}-${member.playerId ?? member.nickname}`} className="flex items-start gap-1 px-2 py-1.5">
                    <div className="flex min-w-0 flex-1 flex-wrap items-center gap-1">
                      <span className="truncate text-sm font-medium text-slate-800 dark:text-slate-100">{member.nickname}</span>
                      {member.captain && (
                        <span className="rounded bg-slate-900 px-1 text-[11px] font-semibold text-white dark:bg-slate-100 dark:text-slate-900">
                          {t('captainDraft.teams.captainBadge')}
                        </span>
                      )}
                      <Tags tags={playerTags(member, playerMap)} />
                    </div>
                    <span className="shrink-0 text-xs text-slate-500 dark:text-slate-400">
                      {t('captainDraft.teams.setWins', { count: wins })}
                    </span>
                    {!member.captain && typeof member.playerId === 'number' && (
                      <button
                        type="button"
                        onClick={() => onUnpick(member.playerId as number)}
                        disabled={busy}
                        aria-label={t('captainDraft.teams.unpick', { nickname: member.nickname })}
                        title={t('captainDraft.teams.unpick', { nickname: member.nickname })}
                        className="shrink-0 rounded px-1 text-xs text-slate-400 hover:bg-slate-100 hover:text-rose-600 disabled:cursor-not-allowed disabled:opacity-50 dark:hover:bg-slate-800 dark:hover:text-rose-400"
                      >
                        ×
                      </button>
                    )}
                  </li>
                )
              })}
            </ul>
          </section>
        ))}
      </div>

      <div className="mt-4">
        <h4 className="text-xs font-semibold text-slate-700 dark:text-slate-300">
          {t('captainDraft.teams.remaining', { count: remaining.length })}
        </h4>
        {remaining.length === 0 ? (
          <p className="mt-2 text-xs text-slate-500 dark:text-slate-400">{t('captainDraft.teams.allPicked')}</p>
        ) : (
          <ul className="mt-2 space-y-1">
            {remaining.map((participant) => (
              <li
                key={`remaining-${participant.playerId ?? participant.nickname}`}
                className="flex items-center gap-2 rounded-lg bg-slate-50 px-2 py-1.5 dark:bg-slate-800/60"
              >
                <div className="flex min-w-0 flex-1 flex-wrap items-center gap-1">
                  <span className="truncate text-sm font-medium text-slate-800 dark:text-slate-100">{participant.nickname}</span>
                  <Tags tags={playerTags(participant, playerMap)} />
                </div>
                {typeof participant.playerId === 'number' &&
                  teams.map((team) => (
                    <button
                      key={`assign-${team.side}`}
                      type="button"
                      onClick={() => onPick(team.side, participant.playerId as number)}
                      disabled={busy}
                      aria-label={t('captainDraft.teams.assign', { nickname: participant.nickname, team: team.label })}
                      className={cn(
                        'shrink-0 rounded-md border px-2 py-0.5 text-xs font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-50',
                        TEAM_STYLES[team.side].button,
                      )}
                    >
                      {team.label}
                    </button>
                  ))}
              </li>
            ))}
          </ul>
        )}
      </div>
    </article>
  )
}
