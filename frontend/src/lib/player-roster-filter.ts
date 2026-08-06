import type { PlayerRosterItem } from '@/types/api'

export type PlayerRosterView = 'active' | 'inactive' | 'dormant'

export function filterPlayerRosterByView(
  rows: PlayerRosterItem[],
  view: PlayerRosterView,
  dormantPlayerIds: ReadonlySet<number> = new Set<number>()
): PlayerRosterItem[] {
  switch (view) {
    case 'inactive':
      return rows.filter(
        (row) =>
          row.active === false &&
          row.identityHidden !== true &&
          row.lifecycleStatus === 'INACTIVE'
      )
    case 'dormant':
      return rows.filter((row) => row.active !== false && dormantPlayerIds.has(row.id))
    case 'active':
    default:
      return rows.filter((row) => row.active !== false)
  }
}
