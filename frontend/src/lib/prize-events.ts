import type { PrizeCandidate, PrizeEventEntry, PrizeEventMetric, PrizeWinnerPick } from '@/types/api'

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
  return event.metric === 'GAMES' || event.metric === 'MANUAL' ? event.metric : 'POINTS'
}

export type ManualEntriesParse = {
  entries: PrizeEventEntry[]
  // Lines whose name is no player of the roster.
  unknown: string[]
  // Lines with no number at the end.
  unreadable: string[]
  // Players named on more than one line.
  repeated: string[]
}

// "1위 노바 427", "노바 427회", "노바\t427", "1. 노바 : 1,234": an optional rank, the name, then the number.
const MANUAL_LINE = /^\s*(?:\d+\s*(?:위|등|\.|\))\s*)?(.+?)[\s,:|]+([\d,]+)\s*[^\d\s]*\s*$/

/**
 * The lines pasted from another app's ranking, one player a line, read against the roster: a name
 * is matched ignoring case and spaces around it. Blank lines are skipped.
 */
export function parseManualEntries(text: string, roster: Array<{ id: number; nickname: string }>): ManualEntriesParse {
  const byName = new Map(roster.map((player) => [player.nickname.trim().toLowerCase(), player.id]))
  const result: ManualEntriesParse = { entries: [], unknown: [], unreadable: [], repeated: [] }
  const seen = new Set<number>()
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim()
    if (line.length === 0) {
      continue
    }
    const match = MANUAL_LINE.exec(line)
    const score = match ? Number(match[2].replace(/,/g, '')) : Number.NaN
    if (!match || !Number.isSafeInteger(score)) {
      result.unreadable.push(line)
      continue
    }
    const name = match[1].trim()
    const playerId = byName.get(name.toLowerCase())
    if (playerId === undefined) {
      result.unknown.push(name)
    } else if (seen.has(playerId)) {
      result.repeated.push(name)
    } else {
      seen.add(playerId)
      result.entries.push({ playerId, score })
    }
  }
  return result
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
