'use client'

import { t } from '@/lib/i18n'
import {
  ROUND_CODES,
  draftPlayerOptionLabel,
  draftPlayerTags,
  playersInRound,
  requiredTierPresets,
  setComposition,
  withSlot,
  type DraftWinnerSelection,
  type EffectiveEntry,
  type EntryEdit,
} from '@/lib/captain-draft'
import type { CaptainDraftParticipant, PlayerRosterItem, TeamSide } from '@/types/api'

const TIER_PRESET_LIST_ID = 'captain-draft-tier-presets'
const WINNER_OPTIONS = [
  { value: 'HOME' as const, label: 'captainDraft.entry.homeWin' },
  { value: 'AWAY' as const, label: 'captainDraft.entry.awayWin' },
]
const TEAM_LABELS: Record<TeamSide, string> = {
  HOME: 'captainDraft.scoreboard.homeHeader',
  AWAY: 'captainDraft.scoreboard.awayHeader',
}

export type EntrySheetRound = {
  roundNumber: number
  savedCode: string
  // The composition as shown: the host's unsaved choice, or the saved one.
  roundCode: string
  sets: EffectiveEntry[]
}

export type EntrySheetMember = CaptainDraftParticipant & { playerId: number }

type CaptainDraftEntrySheetProps = {
  rounds: EntrySheetRound[]
  sheet: EffectiveEntry[]
  teamMembers: Record<TeamSide, EntrySheetMember[]>
  playerMap: Map<number, PlayerRosterItem>
  onEditEntry: (key: string, change: EntryEdit) => void
  onChangeRoundCode: (roundNumber: number, savedCode: string, code: string) => void
}

function selectedPlayerId(value: string): number | null {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
}

/**
 * The round sheet of the regular draft: a row per set with each side's players on one line, the
 * round's race composition under its name, the tiers a set asks for, and the results.
 */
export function CaptainDraftEntrySheet({
  rounds,
  sheet,
  teamMembers,
  playerMap,
  onEditEntry,
  onChangeRoundCode,
}: CaptainDraftEntrySheetProps) {
  // The players a slot can take: that team's players not yet placed elsewhere in the round.
  const playerOptions = (team: TeamSide, set: EffectiveEntry, slot: number) => {
    const placed = playersInRound(sheet, set.roundNumber)
    const current = (team === 'HOME' ? set.homePlayerIds : set.awayPlayerIds)[slot]
    return teamMembers[team]
      .filter((participant) => participant.playerId === current || !placed.has(participant.playerId))
      .map((participant) => (
        <option key={`${team}-${set.key}-${slot}-${participant.playerId}`} value={participant.playerId}>
          {draftPlayerOptionLabel(participant.nickname, draftPlayerTags(playerMap.get(participant.playerId), participant.race))}
        </option>
      ))
  }

  const changeSlot = (set: EffectiveEntry, team: TeamSide, slot: number, value: string) => {
    const side = team === 'HOME' ? set.homePlayerIds : set.awayPlayerIds
    const next = withSlot(side, slot, selectedPlayerId(value))
    onEditEntry(set.key, team === 'HOME' ? { homePlayerIds: next } : { awayPlayerIds: next })
  }

  return (
    <div className="overflow-x-auto rounded-xl border border-slate-300 bg-white dark:border-slate-600 dark:bg-slate-950">
      <table className="w-full min-w-[1180px] table-fixed border-collapse text-sm">
        <thead>
          <tr className="bg-[#f4eadf] text-slate-900 dark:bg-[#5b4438] dark:text-slate-100">
            <th className="w-28 border border-slate-400 px-3 py-2 text-center font-semibold dark:border-slate-600">
              {t('captainDraft.entry.roundLabel')}
            </th>
            <th className="w-20 border border-slate-400 px-2 py-2 text-center font-semibold dark:border-slate-600">
              {t('captainDraft.entry.setLabel')}
            </th>
            <th className="w-16 border border-slate-400 px-2 py-2 text-center font-semibold dark:border-slate-600">
              {t('captainDraft.entry.raceLabel')}
            </th>
            <th className="w-32 border border-slate-400 px-2 py-2 text-center font-semibold dark:border-slate-600">
              {t('captainDraft.entry.requiredTierLabel')}
            </th>
            <th className="border border-slate-400 px-3 py-2 text-center font-semibold text-sky-700 dark:border-slate-600 dark:text-sky-300">
              {t('captainDraft.entry.homeSheetLabel')}
            </th>
            <th className="border border-slate-400 px-3 py-2 text-center font-semibold text-rose-700 dark:border-slate-600 dark:text-rose-300">
              {t('captainDraft.entry.awaySheetLabel')}
            </th>
            <th className="w-24 border border-slate-400 px-2 py-2 text-center font-semibold dark:border-slate-600">
              {t('captainDraft.entry.resultLabel')}
            </th>
            <th className="w-20 border border-slate-400 px-2 py-2 text-center font-semibold dark:border-slate-600">
              {t('captainDraft.entry.finalWinLabel')}
            </th>
          </tr>
        </thead>
        <tbody>
          {rounds.map((round, roundIndex) => {
            const roundHomeWins = round.sets.filter((set) => set.winnerTeam === 'HOME').length
            const roundAwayWins = round.sets.filter((set) => set.winnerTeam === 'AWAY').length

            return round.sets.map((set, setIndex) => {
              const dividerClass = roundIndex > 0 && setIndex === 0 ? 'border-t-[3px] border-t-slate-800' : ''
              return (
                <tr key={`entry-${set.key}`} className={`bg-white dark:bg-slate-900 ${dividerClass}`.trim()}>
                  {setIndex === 0 && (
                    <th
                      rowSpan={round.sets.length}
                      className={`border border-slate-400 bg-white px-2 py-2 text-center align-middle font-semibold text-slate-900 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 ${dividerClass}`.trim()}
                    >
                      <span className="block text-base">{t('captainDraft.entry.roundOnly', { round: round.roundNumber })}</span>
                      <select
                        value={round.roundCode}
                        onChange={(event) => onChangeRoundCode(round.roundNumber, round.savedCode, event.target.value)}
                        aria-label={t('captainDraft.entry.roundCodeLabel', { round: round.roundNumber })}
                        className="mt-1 w-full rounded-md border border-slate-300 bg-white px-1 py-1 text-sm font-semibold text-slate-800 outline-none focus:border-slate-500 dark:border-slate-600 dark:bg-slate-950 dark:text-slate-100"
                      >
                        {ROUND_CODES.map((code) => (
                          <option key={`round-${round.roundNumber}-${code}`} value={code}>
                            {code}
                          </option>
                        ))}
                      </select>
                    </th>
                  )}
                  <td className={`border border-slate-300 px-2 py-2 text-center text-slate-700 dark:border-slate-700 dark:text-slate-300 ${dividerClass}`.trim()}>
                    <span className="block font-medium">{t('captainDraft.entry.setCell', { set: set.setNumber })}</span>
                    <span className="block text-xs text-slate-500 dark:text-slate-400">
                      {t('captainDraft.entry.teamSize', { size: set.teamSize })}
                    </span>
                  </td>
                  <td className={`border border-slate-300 bg-slate-50 px-2 py-2 text-center font-medium text-slate-800 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200 ${dividerClass}`.trim()}>
                    {setComposition(round.roundCode, set.teamSize)}
                  </td>
                  <td className={`border border-slate-300 px-1 py-1 dark:border-slate-700 ${dividerClass}`.trim()}>
                    <input
                      value={set.requiredTier}
                      onChange={(event) => onEditEntry(set.key, { requiredTier: event.target.value })}
                      list={TIER_PRESET_LIST_ID}
                      maxLength={30}
                      placeholder={t('captainDraft.entry.requiredTierPlaceholder')}
                      aria-label={`${t('captainDraft.entry.requiredTierLabel')} ${t('captainDraft.entry.roundOnly', { round: round.roundNumber })} ${t('captainDraft.entry.setCell', { set: set.setNumber })}`}
                      className="w-full rounded-md border border-transparent bg-transparent px-2 py-1.5 text-center text-sm text-slate-800 outline-none placeholder:text-slate-300 focus:border-slate-300 focus:bg-slate-50 dark:text-slate-100 dark:placeholder:text-slate-600 dark:focus:border-slate-600 dark:focus:bg-slate-800"
                    />
                  </td>
                  {(['HOME', 'AWAY'] as const).map((team) => (
                    <td key={`${set.key}-${team}`} className={`border border-slate-300 px-1 py-1 dark:border-slate-700 ${dividerClass}`.trim()}>
                      <div className={`grid gap-1 ${set.teamSize >= 3 ? 'grid-cols-3' : set.teamSize === 2 ? 'grid-cols-2' : 'grid-cols-1'}`}>
                        {(team === 'HOME' ? set.homePlayerIds : set.awayPlayerIds).map((playerId, slot) => (
                          <select
                            key={`${set.key}-${team}-${slot}`}
                            value={playerId ?? ''}
                            onChange={(event) => changeSlot(set, team, slot, event.target.value)}
                            aria-label={t('captainDraft.entry.slotLabel', {
                              team: t(TEAM_LABELS[team]),
                              set: `${round.roundNumber}-${set.setNumber}`,
                              slot: slot + 1,
                            })}
                            className={`w-full min-w-0 truncate rounded-md border border-slate-200 bg-transparent px-1 py-1.5 text-sm font-medium outline-none dark:border-slate-700 ${
                              team === 'HOME'
                                ? 'text-sky-700 focus:bg-sky-50 dark:text-sky-300 dark:focus:bg-sky-950/50'
                                : 'text-rose-700 focus:bg-rose-50 dark:text-rose-300 dark:focus:bg-rose-950/50'
                            }`}
                          >
                            <option value="">{t('captainDraft.entry.notSet')}</option>
                            {playerOptions(team, set, slot)}
                          </select>
                        ))}
                      </div>
                    </td>
                  ))}
                  <td className={`border border-slate-300 px-1 py-1 text-center dark:border-slate-700 ${dividerClass}`.trim()}>
                    <select
                      value={set.winnerTeam}
                      onChange={(event) => onEditEntry(set.key, { winnerTeam: event.target.value as DraftWinnerSelection })}
                      aria-label={`${t('captainDraft.entry.resultLabel')} ${t('captainDraft.entry.roundOnly', { round: round.roundNumber })} ${t('captainDraft.entry.setCell', { set: set.setNumber })}`}
                      className="w-full border-0 bg-transparent px-1 py-2 text-sm font-medium text-slate-700 outline-none focus:bg-slate-50 dark:text-slate-200 dark:focus:bg-slate-800"
                    >
                      <option value="">{t('captainDraft.entry.notSet')}</option>
                      {WINNER_OPTIONS.map((option) => (
                        <option key={`${set.key}-${option.value}`} value={option.value}>
                          {t(option.label)}
                        </option>
                      ))}
                    </select>
                  </td>
                  {setIndex === 0 && (
                    <td
                      rowSpan={round.sets.length}
                      className={`border border-slate-300 px-2 py-2 text-center align-middle font-semibold text-slate-700 dark:border-slate-700 dark:text-slate-300 ${dividerClass}`.trim()}
                    >
                      {`${roundHomeWins} : ${roundAwayWins}`}
                    </td>
                  )}
                </tr>
              )
            })
          })}
        </tbody>
      </table>
      <datalist id={TIER_PRESET_LIST_ID}>
        {requiredTierPresets().map((preset) => (
          <option key={preset} value={preset} />
        ))}
      </datalist>
    </div>
  )
}
