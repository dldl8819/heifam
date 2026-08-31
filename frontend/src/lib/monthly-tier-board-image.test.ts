import { describe, expect, it } from 'vitest'
import {
  buildMonthlyTierBoardModel,
  resolveMonthlyTierBoardPeriod,
  selectMonthlyTierBoardPlayers,
} from '@/lib/monthly-tier-board-image'
import type { GroupPlayerTierBoardItem } from '@/types/api'

function tierBoardItem(
  nickname: string,
  tier: GroupPlayerTierBoardItem['tier'],
  options: { active?: boolean; liveTier?: GroupPlayerTierBoardItem['liveTier'] } = {},
): GroupPlayerTierBoardItem {
  return {
    id: nickname.length,
    nickname,
    race: 'P',
    tier,
    liveTier: options.liveTier ?? tier,
    active: options.active ?? true,
  }
}

describe('monthly tier board image', () => {
  it('uses the live MMR tier instead of the stored monthly tier', () => {
    const players = selectMonthlyTierBoardPlayers([
      tierBoardItem('YOUR_USERNAME_1', 'B+', { liveTier: 'A+' }),
    ])
    const model = buildMonthlyTierBoardModel(
      players,
      new Date('2026-08-08T00:00:00.000Z'),
    )

    expect(model.buckets['A+']).toEqual(['YOUR_USERNAME_1'])
    expect(model.buckets['B+']).toEqual([])
  })

  it('excludes inactive players and trims placeholder nicknames', () => {
    const players = selectMonthlyTierBoardPlayers([
      tierBoardItem('  YOUR_USERNAME_1  ', 'A'),
      tierBoardItem('YOUR_USERNAME_2', 'B', { active: false }),
    ])

    expect(players).toEqual([{ nickname: 'YOUR_USERNAME_1', tier: 'A' }])
  })

  it('places unassigned players in the reassignment bucket and preserves input order', () => {
    const players = selectMonthlyTierBoardPlayers([
      tierBoardItem('YOUR_USERNAME_1', 'UNASSIGNED'),
      tierBoardItem('YOUR_USERNAME_2', 'UNASSIGNED'),
    ])
    const model = buildMonthlyTierBoardModel(
      players,
      new Date('2026-08-08T00:00:00.000Z'),
    )

    expect(model.buckets.UNASSIGNED).toEqual(['YOUR_USERNAME_1', 'YOUR_USERNAME_2'])
    expect(model.totalCount).toBe(2)
    expect(model.rowCount).toBe(10)
  })

  it('expands beyond ten rows when a tier contains more players', () => {
    const players = Array.from({ length: 11 }, (_, index) => ({
      nickname: `YOUR_USERNAME_${index + 1}`,
      tier: 'C' as const,
    }))
    const model = buildMonthlyTierBoardModel(
      players,
      new Date('2026-08-08T00:00:00.000Z'),
    )

    expect(model.rowCount).toBe(11)
  })

  it('resolves the as-of date label and file name in Korea time', () => {
    expect(resolveMonthlyTierBoardPeriod(new Date('2026-07-31T15:30:00.000Z'))).toEqual({
      periodLabel: '2026-08-01 기준',
      fileName: 'heifam-tier-board-20260801-0030.png',
    })
  })
})
