import type { PrizeCandidate } from '@/types/api'

export type PrizeDraft = {
  selected: boolean
  prize: string
  amount: string
}

/** The first winnerCount candidates start ticked; anyone tied below the cut can be swapped in. */
export function initialPrizeDrafts(candidates: PrizeCandidate[], winnerCount: number): Record<number, PrizeDraft> {
  const drafts: Record<number, PrizeDraft> = {}
  candidates.forEach((candidate, index) => {
    drafts[candidate.pointAccountId] = { selected: index < winnerCount, prize: '', amount: '' }
  })
  return drafts
}

/** A whole number of won, at least 0; blank counts as 0. null when it is not one. */
export function parsePrizeAmount(value: string): number | null {
  const trimmed = value.replace(/,/g, '').trim()
  if (trimmed.length === 0) {
    return 0
  }
  if (!/^\d+$/.test(trimmed)) {
    return null
  }
  const amount = Number(trimmed)
  return Number.isSafeInteger(amount) ? amount : null
}

/** Ticked candidates in candidate order, which sets their places. */
export function buildPrizeWinners(
  candidates: PrizeCandidate[],
  drafts: Record<number, PrizeDraft>,
): Array<{ pointAccountId: number; prize: string | null; amount: number }> | null {
  const winners: Array<{ pointAccountId: number; prize: string | null; amount: number }> = []
  for (const candidate of candidates) {
    const draft = drafts[candidate.pointAccountId]
    if (!draft?.selected) {
      continue
    }
    const amount = parsePrizeAmount(draft.amount)
    if (amount === null) {
      return null
    }
    const prize = draft.prize.trim()
    winners.push({ pointAccountId: candidate.pointAccountId, prize: prize.length > 0 ? prize : null, amount })
  }
  return winners
}
