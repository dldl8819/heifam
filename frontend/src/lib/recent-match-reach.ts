/**
 * How far back the results page lets someone page through recent matches.
 *
 * Members reach the newest 30, enough to find a match they played in and check it was recorded
 * correctly. Admins reach the newest 100, so they can look over results that were changed after a
 * busy evening has pushed them out of the newest 30. Super admins are not limited by this at all:
 * they read the full history page by page.
 *
 * The backend holds every request to the same reach (MatchQueryService.MEMBER_REACH and
 * ADMIN_REACH), so change both together.
 */
export const RECENT_MATCH_MEMBER_MAX_LIMIT = 30
export const RECENT_MATCH_ADMIN_MAX_LIMIT = 100

export function recentMatchMaxLimit(isAdmin: boolean): number {
  return isAdmin ? RECENT_MATCH_ADMIN_MAX_LIMIT : RECENT_MATCH_MEMBER_MAX_LIMIT
}
