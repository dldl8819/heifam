import type { PrizeCandidate, PrizeEventMetric, PrizeWinnerPick } from '@/types/api'

export type PrizeDraft = {
  selected: boolean
  prize: string
  amount: string
}

/**
 * Who a candidate is, as a key for the drafts: a point account in a points event, a player in a
 * matches-played one.
 */
export function prizeCandidateKey(candidate: PrizeCandidate): string {
  return candidate.playerId != null ? `player:${candidate.playerId}` : `account:${candidate.pointAccountId}`
}

/** What ranks an event; an event from before the choice is ranked by points. */
export function prizeEventMetric(event: { metric?: PrizeEventMetric | null }): PrizeEventMetric {
  return event.metric === 'GAMES' ? 'GAMES' : 'POINTS'
}

/** The first winnerCount candidates start ticked; anyone tied below the cut can be swapped in. */
export function initialPrizeDrafts(candidates: PrizeCandidate[], winnerCount: number): Record<string, PrizeDraft> {
  const drafts: Record<string, PrizeDraft> = {}
  candidates.forEach((candidate, index) => {
    drafts[prizeCandidateKey(candidate)] = { selected: index < winnerCount, prize: '', amount: '' }
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
  drafts: Record<string, PrizeDraft>,
): PrizeWinnerPick[] | null {
  const winners: PrizeWinnerPick[] = []
  for (const candidate of candidates) {
    const draft = drafts[prizeCandidateKey(candidate)]
    if (!draft?.selected) {
      continue
    }
    const amount = parsePrizeAmount(draft.amount)
    if (amount === null) {
      return null
    }
    const prize = draft.prize.trim()
    winners.push({
      pointAccountId: candidate.pointAccountId,
      playerId: candidate.playerId ?? null,
      prize: prize.length > 0 ? prize : null,
      amount,
    })
  }
  return winners
}
