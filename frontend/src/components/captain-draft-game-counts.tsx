'use client'

import type { EntrySheetMember } from '@/components/captain-draft-entry-sheet'
import { t } from '@/lib/i18n'
import { gamesByPlayer, sortByRanking, teamGameTarget, type EffectiveEntry, type SetControls } from '@/lib/captain-draft'
import { cn } from '@/lib/utils'
import type { TeamSide } from '@/types/api'

const TITLE_ID = 'captain-draft-games-title'
const TEAMS: Array<{ side: TeamSide; label: string; className: string }> = [
  { side: 'HOME', label: 'captainDraft.scoreboard.homeHeader', className: 'text-sky-700 dark:text-sky-300' },
  { side: 'AWAY', label: 'captainDraft.scoreboard.awayHeader', className: 'text-rose-700 dark:text-rose-300' },
]

type CaptainDraftGameCountsProps = {
  sheet: EffectiveEntry[]
  teamMembers: Record<TeamSide, EntrySheetMember[]>
  // Each roster player's place in the ranking: a team's players are listed in ranking order.
  ranking: ReadonlyMap<number, number>
  // What this viewer may change in each set, by set key: a side kept from them is not counted.
  controls: ReadonlyMap<string, SetControls>
}

/**
 * The regular games each player plays on the sheet, team by team, against an even share of the
 * team's places, so the rounds can be filled with everyone playing about as often: 7 rounds of two
 * 2:2 sets give 7 players 4 games each. A team's side kept from this viewer (a draft run by its
 * captains, before both line-ups are in) shows the share only.
 */
export function CaptainDraftGameCounts({ sheet, teamMembers, ranking, controls }: CaptainDraftGameCountsProps) {
  const games = gamesByPlayer(sheet)
  const rounds = new Set(sheet.filter((set) => !set.aceDecider).map((set) => set.roundNumber)).size

  return (
    <section
      aria-labelledby={TITLE_ID}
      className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 dark:border-slate-700 dark:bg-slate-800"
    >
      <h4 id={TITLE_ID} className="text-sm font-semibold text-slate-900 dark:text-slate-100">
        {t('captainDraft.games.title')}
      </h4>
      <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">{t('captainDraft.games.helper', { rounds })}</p>
      <div className="mt-2 space-y-2">
        {TEAMS.map(({ side, label, className }) => {
          const members = sortByRanking(teamMembers[side], ranking)
          const target = teamGameTarget(sheet, side, members.length)
          if (!target) {
            return null
          }
          const hidden = sheet.some((set) => !set.aceDecider && controls.get(set.key)?.sides[side] === 'hidden')
          return (
            <div key={`games-${side}`}>
              <p className="flex flex-wrap items-baseline gap-x-2 text-xs text-slate-700 dark:text-slate-200">
                <span className={cn('font-semibold', className)}>{t(label)}</span>
                <span className="font-medium">
                  {t('captainDraft.games.target', {
                    target: target.low === target.high ? target.low : `${target.low}~${target.high}`,
                  })}
                </span>
                {!hidden && (
                  <span className="text-slate-500 dark:text-slate-400">
                    {t('captainDraft.games.places', { filled: target.filled, places: target.places })}
                  </span>
                )}
              </p>
              {hidden ? (
                <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{t('captainDraft.games.hidden')}</p>
              ) : (
                <ul className="mt-1 flex flex-wrap gap-1.5">
                  {members.map((member) => {
                    const count = games.get(member.playerId) ?? 0
                    const over = count - target.high
                    const reached = over <= 0 && count >= Math.max(1, target.low)
                    return (
                      <li
                        key={`games-${side}-${member.playerId}`}
                        className={cn(
                          'flex items-center gap-1 rounded-md border px-2 py-0.5 text-xs',
                          over > 0
                            ? 'border-amber-400 bg-amber-50 text-amber-900 dark:border-amber-700 dark:bg-amber-950/40 dark:text-amber-200'
                            : reached
                              ? 'border-emerald-300 bg-emerald-50 text-emerald-800 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-200'
                              : 'border-slate-200 bg-white text-slate-700 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-200',
                        )}
                      >
                        <span>{member.nickname}</span>
                        <span className="font-semibold">{t('captainDraft.games.count', { count })}</span>
                        {reached && (
                          <>
                            <span aria-hidden="true">✓</span>
                            <span className="sr-only">{t('captainDraft.games.reached')}</span>
                          </>
                        )}
                        {over > 0 && <span className="font-semibold">· {t('captainDraft.games.over', { count: over })}</span>}
                      </li>
                    )
                  })}
                </ul>
              )}
            </div>
          )
        })}
      </div>
    </section>
  )
}
