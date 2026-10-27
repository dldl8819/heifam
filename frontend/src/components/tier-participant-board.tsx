'use client'

import type { KeyboardEvent as ReactKeyboardEvent, MutableRefObject } from 'react'
import { t } from '@/lib/i18n'
import {
  findParticipantByInputValue,
  formatParticipantSlotLabel,
  type ParticipantSlotState,
  type SelectableParticipant,
} from '@/lib/participant-slots'
import { cn } from '@/lib/utils'
import type { PlayerTierStatus, TeamSide } from '@/types/api'

type TierParticipantBoardProps = {
  title: string
  helper: string
  players: SelectableParticipant[]
  slots: ParticipantSlotState[]
  showMmr: boolean
  loading?: boolean
  selectedCountLabel: string
  emptyMessage: string
  duplicateMessage: string
  resetLabel: string
  minimumBoardRows?: number
  inputRefs?: MutableRefObject<Array<HTMLInputElement | null>>
  onReset: () => void
  onSlotInputChange: (index: number, value: string) => void
  onSlotAutocomplete: (index: number) => boolean
  // The team each player is already on (a regular draft's 청팀/백팀): their cell takes the team's
  // colour and name, so the board shows who is picked. Left out, every player looks the same.
  playerTeams?: ReadonlyMap<number, TeamSide>
}

// A picked player's cell: the team's colour (white text stays above 4.5:1, high contrast darkens
// it further), a short team tag so the colour is not the only sign, and the team's name for
// screen readers.
const TEAM_CELLS: Record<TeamSide, { cellClassName: string; tag: string; name: string }> = {
  HOME: { cellClassName: 'bg-sky-700 dark:bg-sky-700', tag: 'common.tierBoard.homeTag', name: 'common.tierBoard.homeTeam' },
  AWAY: { cellClassName: 'bg-rose-700 dark:bg-rose-700', tag: 'common.tierBoard.awayTag', name: 'common.tierBoard.awayTeam' },
}
const UNPICKED_CELL_CLASS_NAME = 'bg-[#dff0f7] dark:bg-sky-950/60'

type TierRow = {
  key: PlayerTierStatus
  label: string
  rowHeaderClassName: string
}

const TIER_ROWS: TierRow[] = [
  { key: 'S+', label: 'S+', rowHeaderClassName: 'bg-[#f5e2d2] text-slate-950 dark:bg-[#5b4438] dark:text-slate-100' },
  { key: 'S', label: 'S', rowHeaderClassName: 'bg-[#f5e2d2] text-slate-950 dark:bg-[#5b4438] dark:text-slate-100' },
  { key: 'S-', label: 'S-', rowHeaderClassName: 'bg-[#f5e2d2] text-slate-950 dark:bg-[#5b4438] dark:text-slate-100' },
  { key: 'A+', label: 'A+', rowHeaderClassName: 'bg-[#f5e2d2] text-slate-950 dark:bg-[#5b4438] dark:text-slate-100' },
  { key: 'A', label: 'A', rowHeaderClassName: 'bg-[#f5e2d2] text-slate-950 dark:bg-[#5b4438] dark:text-slate-100' },
  { key: 'A-', label: 'A-', rowHeaderClassName: 'bg-[#f5e2d2] text-slate-950 dark:bg-[#5b4438] dark:text-slate-100' },
  { key: 'B+', label: 'B+', rowHeaderClassName: 'bg-[#f5e2d2] text-slate-950 dark:bg-[#5b4438] dark:text-slate-100' },
  { key: 'B', label: 'B', rowHeaderClassName: 'bg-[#f5e2d2] text-slate-950 dark:bg-[#5b4438] dark:text-slate-100' },
  { key: 'B-', label: 'B-', rowHeaderClassName: 'bg-[#f5e2d2] text-slate-950 dark:bg-[#5b4438] dark:text-slate-100' },
  { key: 'C+', label: 'C+', rowHeaderClassName: 'bg-[#f5e2d2] text-slate-950 dark:bg-[#5b4438] dark:text-slate-100' },
  { key: 'C', label: 'C', rowHeaderClassName: 'bg-[#f5e2d2] text-slate-950 dark:bg-[#5b4438] dark:text-slate-100' },
  { key: 'C-', label: 'C-', rowHeaderClassName: 'bg-[#f5e2d2] text-slate-950 dark:bg-[#5b4438] dark:text-slate-100' },
  { key: 'D+', label: 'D+', rowHeaderClassName: 'bg-[#f5e2d2] text-slate-950 dark:bg-[#5b4438] dark:text-slate-100' },
  { key: 'D', label: 'D', rowHeaderClassName: 'bg-[#f5e2d2] text-slate-950 dark:bg-[#5b4438] dark:text-slate-100' },
  { key: 'D-', label: 'D-', rowHeaderClassName: 'bg-[#f5e2d2] text-slate-950 dark:bg-[#5b4438] dark:text-slate-100' },
  {
    key: 'UNASSIGNED',
    label: t('common.tierBoard.unassigned'),
    rowHeaderClassName: 'bg-[#f5e2d2] text-slate-950 dark:bg-[#5b4438] dark:text-slate-100',
  },
]

function normalizeTier(tier?: PlayerTierStatus): PlayerTierStatus {
  return tier ?? 'UNASSIGNED'
}

export function TierParticipantBoard({
  title,
  helper,
  players,
  slots,
  showMmr,
  loading = false,
  selectedCountLabel,
  emptyMessage,
  duplicateMessage,
  resetLabel,
  minimumBoardRows = 6,
  inputRefs,
  onReset,
  onSlotInputChange,
  onSlotAutocomplete,
  playerTeams,
}: TierParticipantBoardProps) {
  const selectedPlayers = slots.flatMap((slot) => {
    if (typeof slot.playerId !== 'number' || !Number.isFinite(slot.playerId)) {
      return []
    }

    const player = players.find((candidate) => candidate.id === slot.playerId)
    return player ? [player] : []
  })

  const buckets = TIER_ROWS.reduce<Record<PlayerTierStatus, SelectableParticipant[]>>(
    (accumulator, row) => ({
      ...accumulator,
      [row.key]: [],
    }),
    {
      'S+': [],
      S: [],
      'S-': [],
      'A+': [],
      A: [],
      'A-': [],
      'B+': [],
      B: [],
      'B-': [],
      'C+': [],
      C: [],
      'C-': [],
      'D+': [],
      D: [],
      'D-': [],
      UNASSIGNED: [],
    },
  )

  selectedPlayers.forEach((player) => {
    buckets[normalizeTier(player.liveTier ?? player.tier)].push(player)
  })

  const boardColumnCount = Math.max(
    minimumBoardRows,
    ...TIER_ROWS.map((row) => buckets[row.key].length),
  )
  const selectedPlayerIds = new Set(
    slots.flatMap((slot) =>
      typeof slot.playerId === 'number' && Number.isFinite(slot.playerId) ? [slot.playerId] : [],
    ),
  )

  const handleSlotKeyDown = (
    event: ReactKeyboardEvent<HTMLInputElement>,
    index: number,
  ) => {
    if (event.key === 'Tab' && !event.shiftKey && onSlotAutocomplete(index)) {
      event.preventDefault()
    }
  }

  return (
    <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-xs dark:border-slate-700 dark:bg-slate-900">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{title}</h3>
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{helper}</p>
        </div>
        <button
          type="button"
          onClick={onReset}
          className="rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-600 transition-colors hover:bg-slate-50 hover:text-slate-900 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-white"
        >
          {resetLabel}
        </button>
      </div>

      <p className="mt-3 text-xs font-medium text-slate-500 dark:text-slate-400">{selectedCountLabel}</p>

      {loading ? (
        <div className="mt-4 rounded-xl border border-dashed border-slate-200 px-4 py-10 text-center text-sm text-slate-500 dark:border-slate-700 dark:text-slate-400">
          {t('common.loading')}
        </div>
      ) : players.length === 0 ? (
        <div className="mt-4 rounded-xl border border-dashed border-slate-200 px-4 py-10 text-center text-sm text-slate-500 dark:border-slate-700 dark:text-slate-400">
          {emptyMessage}
        </div>
      ) : (
        <>
          <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            {slots.map((slot, index) => {
              const selectedPlayer =
                typeof slot.playerId === 'number'
                  ? players.find((player) => player.id === slot.playerId) ?? null
                  : null
              const matchedPlayer =
                selectedPlayer ??
                findParticipantByInputValue(players, slot.inputValue, showMmr)
              const isDuplicateSelection =
                matchedPlayer !== null &&
                selectedPlayer === null &&
                selectedPlayerIds.has(matchedPlayer.id)

              return (
                <label
                  key={`tier-board-slot-${index}`}
                  className="space-y-1 text-xs font-medium text-slate-500 dark:text-slate-400"
                >
                  {t('common.tierBoard.slot', { index: index + 1 })}
                  <input
                    ref={(element) => {
                      if (!inputRefs) {
                        return
                      }
                      inputRefs.current[index] = element
                    }}
                    value={slot.inputValue}
                    onChange={(event) => onSlotInputChange(index, event.target.value)}
                    onKeyDown={(event) => handleSlotKeyDown(event, index)}
                    className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-hidden transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-slate-500 dark:focus:ring-slate-700"
                    placeholder={t('common.tierBoard.placeholder')}
                  />
                  {selectedPlayer && (
                    <p className="text-[11px] text-slate-500 dark:text-slate-400">
                      {formatParticipantSlotLabel(selectedPlayer, showMmr)}
                    </p>
                  )}
                  {isDuplicateSelection && (
                    <p role="alert" className="text-[11px] text-rose-700 dark:text-rose-300">{duplicateMessage}</p>
                  )}
                </label>
              )
            })}
          </div>

          <div className="mt-5 rounded-[24px] border border-[#d5c4ab] bg-[#fbf4e8] p-4 shadow-xs dark:border-[#6f5b3c] dark:bg-slate-950 sm:p-5">
            <div className="text-center">
              <h4 className="text-2xl font-semibold tracking-tight text-[#6f5b3c] dark:text-amber-200 sm:text-4xl">
                {t('common.tierBoard.title')}
              </h4>
              <p className="mt-2 text-xs text-[#76623f] dark:text-amber-300/80">{selectedCountLabel}</p>
            </div>

            {/* relative: the cells' screen-reader team names are placed inside this scroller, not past the page's edge. */}
            <div className="relative mt-4 overflow-x-auto rounded-xl border border-[#8d7760] bg-white/90 dark:border-slate-600 dark:bg-slate-950/90">
              <table
                className="w-full border-collapse text-xs sm:text-sm"
                style={{ minWidth: `${Math.max(520, 96 + boardColumnCount * 104)}px` }}
              >
                <tbody>
                  {TIER_ROWS.map((row) => (
                    <tr key={`tier-board-row-${row.key}`} className="text-center">
                      <th
                        className={cn(
                          'w-20 border border-[#8d7760] px-2 py-2 font-semibold dark:border-slate-600',
                          row.rowHeaderClassName,
                        )}
                      >
                        {row.label}
                      </th>
                      {Array.from({ length: boardColumnCount }, (_, columnIndex) => {
                        const player = buckets[row.key][columnIndex] ?? null
                        const team = player ? playerTeams?.get(player.id) : undefined
                        return (
                          <td
                            key={`tier-board-cell-${row.key}-${columnIndex}`}
                            className={cn(
                              'h-10 border border-[#8d7760] px-2 py-1.5 align-middle dark:border-slate-600',
                              !player
                                ? 'bg-white dark:bg-slate-950'
                                : team
                                  ? TEAM_CELLS[team].cellClassName
                                  : UNPICKED_CELL_CLASS_NAME,
                            )}
                          >
                            {player && team ? (
                              <span className="flex items-center justify-center gap-1 text-sm font-semibold text-white">
                                <span aria-hidden="true" className="shrink-0 rounded-sm bg-white/25 px-1 text-[11px] leading-4">
                                  {t(TEAM_CELLS[team].tag)}
                                </span>
                                <span className="sr-only">{t(TEAM_CELLS[team].name)} </span>
                                <span className="truncate">{player.nickname}</span>
                              </span>
                            ) : player ? (
                              <span className="block truncate text-sm font-medium text-slate-900 dark:text-slate-100">
                                {player.nickname}
                              </span>
                            ) : (
                              <span className="text-transparent">.</span>
                            )}
                          </td>
                        )
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {playerTeams && (
              <ul
                aria-label={t('common.tierBoard.teamLegend')}
                className="mt-3 flex flex-wrap items-center justify-center gap-x-4 gap-y-1 text-xs text-slate-700 dark:text-slate-200"
              >
                {(['HOME', 'AWAY'] as const).map((team) => (
                  <li key={`tier-board-legend-${team}`} className="flex items-center gap-1.5">
                    <span aria-hidden="true" className={cn('inline-block h-3 w-3 rounded-sm', TEAM_CELLS[team].cellClassName)} />
                    {t(TEAM_CELLS[team].name)}
                  </li>
                ))}
                <li className="flex items-center gap-1.5">
                  <span
                    aria-hidden="true"
                    className={cn('inline-block h-3 w-3 rounded-sm border border-[#8d7760] dark:border-slate-600', UNPICKED_CELL_CLASS_NAME)}
                  />
                  {t('common.tierBoard.notPicked')}
                </li>
              </ul>
            )}
          </div>
        </>
      )}
    </article>
  )
}
