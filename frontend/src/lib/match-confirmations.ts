import type { MatchConfirmation, MatchConfirmationList } from '@/types/api'

// Results come in a few times an hour, so this list is polled less often than predictions.
export const MATCH_CONFIRM_POLL_MS = 60000

const DEADLINE_FORMAT = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  month: 'numeric',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

/** The matches still waiting for the player's confirmation, in the order shown. */
export function unconfirmedMatches(list: MatchConfirmationList): MatchConfirmation[] {
  return list.matches.filter((match) => !match.confirmed)
}

/** No more confirmation points today; the rest wait for tomorrow while their window lasts. */
export function confirmCapReached(list: MatchConfirmationList): boolean {
  return list.confirmedToday >= list.dailyCap
}

/** Whether the player's own team won the match. */
export function myTeamWon(match: MatchConfirmation): boolean {
  return match.winnerTeam !== null && match.winnerTeam === match.myTeam
}

/** A confirmation deadline in Korean time, such as "10. 6. 14:30". */
export function formatConfirmDeadline(deadline: string): string {
  const parsed = Date.parse(deadline)
  return Number.isNaN(parsed) ? '' : DEADLINE_FORMAT.format(new Date(parsed))
}
