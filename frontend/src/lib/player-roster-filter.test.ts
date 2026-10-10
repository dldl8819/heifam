import { describe, expect, it } from 'vitest'
import { filterPlayerRosterByView, isDormantRosterRow } from '@/lib/player-roster-filter'
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
    identityHidden: false,
    lifecycleStatus: active ? 'ACTIVE' : 'INACTIVE',
  }
}

describe('filterPlayerRosterByView', () => {
  it('includes only active players returned by the server list of idle players', () => {
    const idle = player(1)
    const ordinary = player(2)
    const inactiveIdle = player(3, { active: false })
    const idleSetAside = { ...player(4), dormantAt: '2026-10-01T03:00:00Z' }
    const idlePlayerIds = new Set([idle.id, inactiveIdle.id, idleSetAside.id])

    expect(
      filterPlayerRosterByView(
        [idle, ordinary, inactiveIdle, idleSetAside],
        'idle',
        idlePlayerIds
      )
    ).toEqual([idle])
  })

  it('keeps players set aside (휴면) out of the roster and in a view of their own', () => {
    const onRoster = player(1)
    const setAside = { ...player(2), dormantAt: '2026-10-01T03:00:00Z' }
    const inactive = { ...player(3, { active: false }), dormantAt: '2026-10-01T03:00:00Z' }
    const rows = [onRoster, setAside, inactive]

    expect(filterPlayerRosterByView(rows, 'active')).toEqual([onRoster])
    expect(filterPlayerRosterByView(rows, 'dormant')).toEqual([setAside])
    expect(filterPlayerRosterByView(rows, 'inactive')).toEqual([inactive])
    expect(rows.map(isDormantRosterRow)).toEqual([false, true, false])
  })

  it('keeps ordinary active and inactive views separate', () => {
    const active = player(1)
    const inactive = player(2, { active: false })
    const rows = [active, inactive]

    expect(filterPlayerRosterByView(rows, 'active', new Set([inactive.id]))).toEqual([active])
    expect(filterPlayerRosterByView(rows, 'inactive', new Set([active.id]))).toEqual([inactive])
  })

  it('omits withdrawn and anonymized rows from the administrator inactive view', () => {
    const retainedInactive = player(1, { active: false })
    const withdrawn = {
      ...player(2, { active: false }),
      identityHidden: true,
      lifecycleStatus: 'WITHDRAWN' as const,
    }
    const anonymized = {
      ...player(3, { active: false }),
      identityHidden: true,
      lifecycleStatus: 'ANONYMIZED' as const,
    }

    expect(
      filterPlayerRosterByView([retainedInactive, withdrawn, anonymized], 'inactive')
    ).toEqual([retainedInactive])
  })
})
