/**
 * A match's result may be changed, or the match deleted, for 48 hours after its result first went
 * in (the time the results list shows), by anyone but a super admin; after that only a super admin
 * may. The backend decides (MatchEditWindow, and canEditRaceComposition on each listed match); this
 * copy only tells the reader why a change was refused when the page was opened before the time ran out.
 */
export const RESULT_EDIT_WINDOW_HOURS = 48

type DecidedMatch = {
  winningTeam: string | null
  resultRecordedAt: string | null
  playedAt: string | null
}

export function isResultEditWindowOpen(match: DecidedMatch, now: number = Date.now()): boolean {
  if (!match.winningTeam) {
    return true
  }
  const since = Date.parse(match.resultRecordedAt ?? match.playedAt ?? '')
  return Number.isFinite(since) && now < since + RESULT_EDIT_WINDOW_HOURS * 60 * 60 * 1000
}
