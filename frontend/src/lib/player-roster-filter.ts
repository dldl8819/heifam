import type { PlayerRosterItem } from '@/types/api'

// active: the roster as members see it. inactive: players taken off (비활성). idle: on the roster
// but long without a game, from the admins' list of them. dormant: set aside by an admin (휴면).
export type PlayerRosterView = 'active' | 'inactive' | 'idle' | 'dormant'

/** A visible player an admin has set aside (휴면). */
export function isDormantRosterRow(row: PlayerRosterItem): boolean {
  return row.active !== false && row.identityHidden !== true && typeof row.dormantAt === 'string'
}

export function filterPlayerRosterByView(
  rows: PlayerRosterItem[],
  view: PlayerRosterView,
  idlePlayerIds: ReadonlySet<number> = new Set<number>()
): PlayerRosterItem[] {
  switch (view) {
    case 'inactive':
      return rows.filter(
        (row) =>
          row.active === false &&
          row.identityHidden !== true &&
          row.lifecycleStatus === 'INACTIVE'
      )
    case 'idle':
      return rows.filter((row) => row.active !== false && !isDormantRosterRow(row) && idlePlayerIds.has(row.id))
    case 'dormant':
      return rows.filter(isDormantRosterRow)
    case 'active':
    default:
      return rows.filter((row) => row.active !== false && !isDormantRosterRow(row))
  }
}
