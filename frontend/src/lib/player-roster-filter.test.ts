import { describe, expect, it } from 'vitest'
import { filterPlayerRosterByView } from '@/lib/player-roster-filter'
import type { PlayerRosterItem } from '@/types/api'

function player(
  id: number,
  { active = true }: { active?: boolean } = {}
): PlayerRosterItem {
  return {
    id,
    nickname: `PLAYER_${id}`,
    race: 'P',
    tier: 'UNASSIGNED',
    wins: 0,
    losses: 0,
    games: 0,
    active,
  }
}

describe('filterPlayerRosterByView', () => {
  it('includes only active players returned by the server dormant list', () => {
    const dormant = player(1)
    const ordinary = player(2)
    const inactiveDormant = player(3, { active: false })
    const dormantPlayerIds = new Set([dormant.id, inactiveDormant.id])

    expect(
      filterPlayerRosterByView(
        [dormant, ordinary, inactiveDormant],
        'dormant',
        dormantPlayerIds
      )
    ).toEqual([dormant])
  })

  it('keeps ordinary active and inactive views separate', () => {
    const active = player(1)
    const inactive = player(2, { active: false })
    const rows = [active, inactive]

    expect(filterPlayerRosterByView(rows, 'active', new Set([inactive.id]))).toEqual([active])
    expect(filterPlayerRosterByView(rows, 'inactive', new Set([active.id]))).toEqual([inactive])
  })
})
