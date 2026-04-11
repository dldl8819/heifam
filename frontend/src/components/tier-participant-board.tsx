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
import type { PlayerTierStatus } from '@/types/api'

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
}

type TierRow = {
  key: PlayerTierStatus
  label: string
  rowHeaderClassName: string
}

const TIER_ROWS: TierRow[] = [
  { key: 'S', label: 'S', rowHeaderClassName: 'bg-[#f5e2d2] text-slate-950' },
  { key: 'A+', label: 'A+', rowHeaderClassName: 'bg-[#f5e2d2] text-slate-950' },
  { key: 'A', label: 'A', rowHeaderClassName: 'bg-[#f5e2d2] text-slate-950' },
  { key: 'A-', label: 'A-', rowHeaderClassName: 'bg-[#f5e2d2] text-slate-950' },
  { key: 'B+', label: 'B+', rowHeaderClassName: 'bg-[#f5e2d2] text-slate-950' },
  { key: 'B', label: 'B', rowHeaderClassName: 'bg-[#f5e2d2] text-slate-950' },
  { key: 'B-', label: 'B-', rowHeaderClassName: 'bg-[#f5e2d2] text-slate-950' },
  { key: 'C+', label: 'C+', rowHeaderClassName: 'bg-[#f5e2d2] text-slate-950' },
  { key: 'C', label: 'C', rowHeaderClassName: 'bg-[#f5e2d2] text-slate-950' },
  { key: 'C-', label: 'C-', rowHeaderClassName: 'bg-[#f5e2d2] text-slate-950' },
  {
    key: 'UNASSIGNED',
    label: t('common.tierBoard.unassigned'),
    rowHeaderClassName: 'bg-[#f5e2d2] text-slate-950',
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
      S: [],
      'A+': [],
      A: [],
      'A-': [],
      'B+': [],
      B: [],
      'B-': [],
      'C+': [],
      C: [],
      'C-': [],
      UNASSIGNED: [],
    },
  )

  selectedPlayers.forEach((player) => {
    buckets[normalizeTier(player.tier)].push(player)
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
    <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold text-slate-900">{title}</h3>
          <p className="mt-1 text-xs text-slate-500">{helper}</p>
        </div>
        <button
          type="button"
          onClick={onReset}
          className="rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-600 transition-colors hover:bg-slate-50 hover:text-slate-900"
        >
          {resetLabel}
        </button>
      </div>

      <p className="mt-3 text-xs font-medium text-slate-500">{selectedCountLabel}</p>

      {loading ? (
        <div className="mt-4 rounded-xl border border-dashed border-slate-200 px-4 py-10 text-center text-sm text-slate-500">
          {t('common.loading')}
        </div>
      ) : players.length === 0 ? (
        <div className="mt-4 rounded-xl border border-dashed border-slate-200 px-4 py-10 text-center text-sm text-slate-500">
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
                  className="space-y-1 text-xs font-medium text-slate-500"
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
                    className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
                    placeholder={t('common.tierBoard.placeholder')}
                  />
                  {selectedPlayer && (
                    <p className="text-[11px] text-slate-500">
                      {formatParticipantSlotLabel(selectedPlayer, showMmr)}
                    </p>
                  )}
                  {isDuplicateSelection && (
                    <p className="text-[11px] text-rose-700">{duplicateMessage}</p>
                  )}
                </label>
              )
            })}
          </div>

          <div className="mt-5 rounded-[24px] border border-[#d5c4ab] bg-[#fbf4e8] p-4 shadow-sm sm:p-5">
            <div className="text-center">
              <h4 className="text-2xl font-semibold tracking-tight text-[#6f5b3c] sm:text-4xl">
                {t('common.tierBoard.title')}
              </h4>
              <p className="mt-2 text-xs text-[#8b7551]">{selectedCountLabel}</p>
            </div>

            <div className="mt-4 overflow-x-auto rounded-xl border border-[#8d7760] bg-white/90">
              <table
                className="w-full border-collapse text-xs sm:text-sm"
                style={{ minWidth: `${Math.max(520, 96 + boardColumnCount * 104)}px` }}
              >
                <tbody>
                  {TIER_ROWS.map((row) => (
                    <tr key={`tier-board-row-${row.key}`} className="text-center">
                      <th
                        className={cn(
                          'w-20 border border-[#8d7760] px-2 py-2 font-semibold',
                          row.rowHeaderClassName,
                        )}
                      >
                        {row.label}
                      </th>
                      {Array.from({ length: boardColumnCount }, (_, columnIndex) => {
                        const player = buckets[row.key][columnIndex] ?? null
                        return (
                          <td
                            key={`tier-board-cell-${row.key}-${columnIndex}`}
                            className={cn(
                              'h-10 border border-[#8d7760] px-2 py-1.5 align-middle',
                              player ? 'bg-[#dff0f7]' : 'bg-white',
                            )}
                          >
                            {player ? (
                              <span className="block truncate text-sm font-medium text-slate-900">
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
          </div>
        </>
      )}
    </article>
  )
}
