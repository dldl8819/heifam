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

type TierColumn = {
  key: PlayerTierStatus
  label: string
  headerClassName: string
  cellClassName: string
}

type TierBoardPlayer = SelectableParticipant & {
  slotIndex: number
}

const TIER_COLUMNS: TierColumn[] = [
  {
    key: 'S',
    label: 'S',
    headerClassName: 'bg-slate-50 text-slate-900',
    cellClassName: 'bg-white',
  },
  {
    key: 'A+',
    label: 'A+',
    headerClassName: 'bg-sky-100 text-sky-950',
    cellClassName: 'bg-sky-50/50',
  },
  {
    key: 'A',
    label: 'A',
    headerClassName: 'bg-sky-100 text-sky-950',
    cellClassName: 'bg-sky-50/50',
  },
  {
    key: 'A-',
    label: 'A-',
    headerClassName: 'bg-sky-100 text-sky-950',
    cellClassName: 'bg-sky-50/50',
  },
  {
    key: 'B+',
    label: 'B+',
    headerClassName: 'bg-amber-100 text-amber-950',
    cellClassName: 'bg-amber-50/60',
  },
  {
    key: 'B',
    label: 'B',
    headerClassName: 'bg-amber-100 text-amber-950',
    cellClassName: 'bg-amber-50/60',
  },
  {
    key: 'B-',
    label: 'B-',
    headerClassName: 'bg-amber-100 text-amber-950',
    cellClassName: 'bg-amber-50/60',
  },
  {
    key: 'C+',
    label: 'C+',
    headerClassName: 'bg-lime-100 text-lime-950',
    cellClassName: 'bg-lime-50/60',
  },
  {
    key: 'C',
    label: 'C',
    headerClassName: 'bg-lime-100 text-lime-950',
    cellClassName: 'bg-lime-50/60',
  },
  {
    key: 'C-',
    label: 'C-',
    headerClassName: 'bg-lime-100 text-lime-950',
    cellClassName: 'bg-lime-50/60',
  },
  {
    key: 'UNASSIGNED',
    label: t('common.tierBoard.unassigned'),
    headerClassName: 'bg-orange-100 text-orange-950',
    cellClassName: 'bg-orange-50/60',
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
  minimumBoardRows = 12,
  inputRefs,
  onReset,
  onSlotInputChange,
  onSlotAutocomplete,
}: TierParticipantBoardProps) {
  const selectedPlayers = slots.flatMap((slot, slotIndex) => {
    if (typeof slot.playerId !== 'number' || !Number.isFinite(slot.playerId)) {
      return []
    }

    const player = players.find((candidate) => candidate.id === slot.playerId)
    if (!player) {
      return []
    }

    return [
      {
        ...player,
        slotIndex,
      },
    ]
  })

  const buckets = TIER_COLUMNS.reduce<Record<PlayerTierStatus, TierBoardPlayer[]>>(
    (accumulator, column) => ({
      ...accumulator,
      [column.key]: [],
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

  const boardRowCount = Math.max(
    minimumBoardRows,
    ...TIER_COLUMNS.map((column) => buckets[column.key].length),
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

          <div className="mt-5 rounded-[28px] border border-[#dacdb5] bg-[#fbf4e8] p-4 shadow-sm sm:p-5">
            <div className="space-y-3">
              <div className="text-center">
                <p className="text-[11px] font-semibold tracking-[0.35em] text-[#8b7551]">HEI</p>
                <div className="mt-2 flex items-center gap-4 text-[#6f5b3c]">
                  <div className="h-px flex-1 bg-[#bca98b]" />
                  <h4 className="text-2xl font-semibold tracking-tight sm:text-4xl">
                    {t('common.tierBoard.title')}
                  </h4>
                  <div className="h-px flex-1 bg-[#bca98b]" />
                </div>
                <p className="mt-2 text-xs text-[#8b7551]">{selectedCountLabel}</p>
              </div>

              <div className="overflow-x-auto rounded-2xl border border-[#c9baa0] bg-white/80">
                <table className="min-w-[1040px] w-full table-fixed border-collapse text-xs sm:text-sm">
                  <thead>
                    <tr className="text-center">
                      <th className="w-14 border border-[#ccbca0] bg-[#f2e6d4] px-2 py-2 font-semibold text-[#594629]">
                        {t('common.tierBoard.index')}
                      </th>
                      {TIER_COLUMNS.map((column) => (
                        <th
                          key={`tier-board-header-${column.key}`}
                          className={cn(
                            'border border-[#ccbca0] px-2 py-2 font-semibold',
                            column.headerClassName,
                          )}
                        >
                          {column.label}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {Array.from({ length: boardRowCount }, (_, rowIndex) => (
                      <tr key={`tier-board-row-${rowIndex}`} className="text-center">
                        <td className="border border-[#d8c9af] bg-[#faf4ea] px-2 py-2 font-medium text-slate-700">
                          {rowIndex + 1}
                        </td>
                        {TIER_COLUMNS.map((column) => {
                          const player = buckets[column.key][rowIndex] ?? null
                          return (
                            <td
                              key={`tier-board-cell-${column.key}-${rowIndex}`}
                              className={cn(
                                'h-12 border border-[#d8c9af] px-2 py-2 align-middle',
                                column.cellClassName,
                              )}
                            >
                              {player ? (
                                <div className="flex items-center justify-between gap-2">
                                  <span className="truncate font-semibold text-slate-900">
                                    {player.nickname}
                                  </span>
                                  <span className="shrink-0 text-[11px] font-medium text-slate-500">
                                    {player.race}
                                  </span>
                                </div>
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
          </div>
        </>
      )}
    </article>
  )
}
