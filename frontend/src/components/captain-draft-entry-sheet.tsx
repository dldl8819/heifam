'use client'

import { t } from '@/lib/i18n'
import {
  compositionsOfSize,
  draftPlayerOptionLabel,
  draftPlayerTags,
  playersInRound,
  requiredTierPresets,
  teamSubmitted,
  withSlot,
  type DraftWinnerSelection,
  type EffectiveEntry,
  type EntryEdit,
  type SetControls,
  type StageLineup,
} from '@/lib/captain-draft'
import type { CaptainDraftLineupStage, CaptainDraftParticipant, PlayerRosterItem, TeamSide } from '@/types/api'

const TIER_PRESET_LIST_ID = 'captain-draft-tier-presets'
const WINNER_OPTIONS = [
  { value: 'HOME' as const, label: 'captainDraft.entry.homeWin' },
  { value: 'AWAY' as const, label: 'captainDraft.entry.awayWin' },
]
const TEAM_LABELS: Record<TeamSide, string> = {
  HOME: 'captainDraft.scoreboard.homeHeader',
  AWAY: 'captainDraft.scoreboard.awayHeader',
}
// What a set shows before its controls are known: nothing to change.
const READ_ONLY: SetControls = { sides: { HOME: 'read', AWAY: 'read' }, settings: false, result: false, locked: false }

export type EntrySheetRound = {
  roundNumber: number
  // The composition of the round's biggest set, as shown.
  roundCode: string
  sets: EffectiveEntry[]
  // The ace decider (에이스 결정전), played when the rounds end level.
  aceDecider: boolean
}

export type EntrySheetMember = CaptainDraftParticipant & { playerId: number }

type CaptainDraftEntrySheetProps = {
  rounds: EntrySheetRound[]
  sheet: EffectiveEntry[]
  teamMembers: Record<TeamSide, EntrySheetMember[]>
  playerMap: Map<number, PlayerRosterItem>
  // What this viewer may change in each set, by set key.
  controls: ReadonlyMap<string, SetControls>
  onEditEntry: (key: string, change: EntryEdit) => void
  onChangeRoundCode: (roundNumber: number, code: string) => void
  // A draft run by its captains: each stage's line-ups (the regular rounds, the ace decider).
  lineups?: ReadonlyMap<CaptainDraftLineupStage, StageLineup>
}

// The compositions a set of this size can take, with the current one kept even if unusual.
function compositionOptions(size: number, current: string): string[] {
  const options = compositionsOfSize(size)
  return current && !options.includes(current) ? [current, ...options] : options
}

function selectedPlayerId(value: string): number | null {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
}

/**
 * The round sheet of the regular draft: a row per set with each side's players on one line, the
 * round's race composition under its name, the tiers a set asks for, and the results. What the
 * viewer may change comes with each set: a set whose result is saved is read-only to those who may
 * not edit results, and in a draft run by its captains a side not yet shown to the viewer is hidden.
 */
export function CaptainDraftEntrySheet({
  rounds,
  sheet,
  teamMembers,
  playerMap,
  controls,
  onEditEntry,
  onChangeRoundCode,
  lineups,
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
            <th className="w-20 border border-slate-400 px-2 py-2 text-center font-semibold dark:border-slate-600">
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
            const biggestSize = Math.max(...round.sets.map((set) => set.teamSize))
            const roundHomeWins = round.sets.filter((set) => set.winnerTeam === 'HOME').length
            const roundAwayWins = round.sets.filter((set) => set.winnerTeam === 'AWAY').length
            const roundSettingsOpen = round.sets.some((set) => controls.get(set.key)?.settings)
            const lineup = lineups?.get(round.aceDecider ? 'ACE' : 'MAIN')

            return round.sets.map((set, setIndex) => {
              const dividerClass = roundIndex > 0 && setIndex === 0 ? 'border-t-[3px] border-t-slate-800' : ''
              const allowed = controls.get(set.key) ?? READ_ONLY
              return (
                <tr key={`entry-${set.key}`} className={`bg-white dark:bg-slate-900 ${dividerClass}`.trim()}>
                  {setIndex === 0 && (
                    <th
                      rowSpan={round.sets.length}
                      className={`border border-slate-400 bg-white px-2 py-2 text-center align-middle font-semibold text-slate-900 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100 ${dividerClass}`.trim()}
                    >
                      <span className={`block ${round.aceDecider ? 'text-sm text-amber-700 dark:text-amber-300' : 'text-base'}`}>
                        {round.aceDecider ? t('captainDraft.ace.roundLabel') : t('captainDraft.entry.roundOnly', { round: round.roundNumber })}
                      </span>
                      <select
                        value={round.roundCode}
                        onChange={(event) => onChangeRoundCode(round.roundNumber, event.target.value)}
                        disabled={!roundSettingsOpen}
                        aria-label={t('captainDraft.entry.roundCodeLabel', { round: round.roundNumber })}
                        className="mt-1 w-full rounded-md border border-slate-300 bg-white px-1 py-1 text-sm font-semibold text-slate-800 outline-hidden focus:border-slate-500 dark:border-slate-600 dark:bg-slate-950 dark:text-slate-100"
                      >
                        {compositionOptions(biggestSize, round.roundCode).map((code) => (
                          <option key={`round-${round.roundNumber}-${code}`} value={code}>
                            {code}
                          </option>
                        ))}
                      </select>
                      {lineup?.revealed && (
                        <span className="mt-2 inline-block rounded-sm bg-emerald-100 px-1.5 py-0.5 text-[11px] font-semibold text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-300">
                          {t('captainDraft.lineup.revealed')}
                        </span>
                      )}
                    </th>
                  )}
                  <td className={`border border-slate-300 px-2 py-2 text-center text-slate-700 dark:border-slate-700 dark:text-slate-300 ${dividerClass}`.trim()}>
                    <span className="block font-medium">{t('captainDraft.entry.setCell', { set: set.setNumber })}</span>
                    <span className="block text-xs text-slate-500 dark:text-slate-400">
                      {t('captainDraft.entry.teamSize', { size: set.teamSize })}
                    </span>
                    {allowed.locked && (
                      <span
                        title={t('captainDraft.entry.lockedHint')}
                        className="mt-1 inline-block rounded-sm bg-slate-200 px-1 text-[11px] font-semibold text-slate-600 dark:bg-slate-700 dark:text-slate-300"
                      >
                        {t('captainDraft.entry.lockedBadge')}
                      </span>
                    )}
                  </td>
                  <td className={`border border-slate-300 bg-slate-50 px-1 py-1 text-center dark:border-slate-700 dark:bg-slate-800 ${dividerClass}`.trim()}>
                    <select
                      value={set.raceCode}
                      onChange={(event) => onEditEntry(set.key, { raceCode: event.target.value })}
                      disabled={!allowed.settings}
                      aria-label={t('captainDraft.entry.raceCodeLabel', { round: round.roundNumber, set: set.setNumber })}
                      className="w-full rounded-md border border-transparent bg-transparent px-1 py-1.5 text-center text-sm font-medium text-slate-800 outline-hidden focus:border-slate-300 dark:text-slate-200 dark:focus:border-slate-600"
                    >
                      {compositionOptions(set.teamSize, set.raceCode).map((code) => (
                        <option key={`${set.key}-race-${code}`} value={code}>
                          {code}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td className={`border border-slate-300 px-1 py-1 dark:border-slate-700 ${dividerClass}`.trim()}>
                    <input
                      value={set.requiredTier}
                      onChange={(event) => onEditEntry(set.key, { requiredTier: event.target.value })}
                      disabled={!allowed.settings}
                      list={TIER_PRESET_LIST_ID}
                      maxLength={30}
                      placeholder={t('captainDraft.entry.requiredTierPlaceholder')}
                      aria-label={`${t('captainDraft.entry.requiredTierLabel')} ${t('captainDraft.entry.roundOnly', { round: round.roundNumber })} ${t('captainDraft.entry.setCell', { set: set.setNumber })}`}
                      className="w-full rounded-md border border-transparent bg-transparent px-2 py-1.5 text-center text-sm text-slate-800 outline-hidden placeholder:text-slate-300 focus:border-slate-300 focus:bg-slate-50 dark:text-slate-100 dark:placeholder:text-slate-600 dark:focus:border-slate-600 dark:focus:bg-slate-800"
                    />
                  </td>
                  {(['HOME', 'AWAY'] as const).map((team) => (
                    <td key={`${set.key}-${team}`} className={`border border-slate-300 px-1 py-1 dark:border-slate-700 ${dividerClass}`.trim()}>
                      {allowed.sides[team] === 'hidden' ? (
                        <div className="rounded-md border border-dashed border-slate-300 px-2 py-1.5 text-center text-xs font-medium text-slate-500 dark:border-slate-600 dark:text-slate-400">
                          {t('captainDraft.lineup.hidden')}
                          {' · '}
                          {t(teamSubmitted(lineup, team) ? 'captainDraft.lineup.teamIn' : 'captainDraft.lineup.teamPending', {
                            team: t(TEAM_LABELS[team]),
                          })}
                        </div>
                      ) : (
                        <div className={`grid gap-1 ${set.teamSize >= 3 ? 'grid-cols-3' : set.teamSize === 2 ? 'grid-cols-2' : 'grid-cols-1'}`}>
                          {(team === 'HOME' ? set.homePlayerIds : set.awayPlayerIds).map((playerId, slot) => (
                            <select
                              key={`${set.key}-${team}-${slot}`}
                              value={playerId ?? ''}
                              onChange={(event) => changeSlot(set, team, slot, event.target.value)}
                              disabled={allowed.sides[team] !== 'edit'}
                              aria-label={t('captainDraft.entry.slotLabel', {
                                team: t(TEAM_LABELS[team]),
                                set: `${round.roundNumber}-${set.setNumber}`,
                                slot: slot + 1,
                              })}
                              className={`w-full min-w-0 truncate rounded-md border border-slate-200 bg-transparent px-1 py-1.5 text-sm font-medium outline-hidden dark:border-slate-700 ${
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
                      )}
                    </td>
                  ))}
                  <td className={`border border-slate-300 px-1 py-1 text-center dark:border-slate-700 ${dividerClass}`.trim()}>
                    <select
                      value={set.winnerTeam}
                      onChange={(event) => onEditEntry(set.key, { winnerTeam: event.target.value as DraftWinnerSelection })}
                      disabled={!allowed.result}
                      title={
                        allowed.locked
                          ? t('captainDraft.entry.lockedHint')
                          : lineups && !lineup?.revealed
                            ? t('captainDraft.lineup.resultAfterReveal')
                            : undefined
                      }
                      aria-label={`${t('captainDraft.entry.resultLabel')} ${t('captainDraft.entry.roundOnly', { round: round.roundNumber })} ${t('captainDraft.entry.setCell', { set: set.setNumber })}`}
                      className="w-full border-0 bg-transparent px-1 py-2 text-sm font-medium text-slate-700 outline-hidden focus:bg-slate-50 dark:text-slate-200 dark:focus:bg-slate-800"
                    >
                      <option value="">{t('captainDraft.entry.notSet')}</option>
                      {WINNER_OPTIONS.map((option) => (
                        <option key={`${set.key}-${option.value}`} value={option.value}>
                          {t(option.label)}
                        </option>
                      ))}
                    </select>
                    {set.resultRecordedBy && (
                      <span className="block truncate px-1 text-[11px] text-slate-500 dark:text-slate-400" title={set.resultRecordedBy}>
                        {t('captainDraft.entry.recordedBy', { nickname: set.resultRecordedBy })}
                      </span>
                    )}
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
