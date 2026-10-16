import { describe, expect, it } from 'vitest'
import {
  countSetWins,
  defaultSetsPerRound,
  draftPlayerOptionLabel,
  draftPlayerTags,
  dropEditsNaming,
  effectiveEntry,
  entryKey,
  playersInRound,
  requiredTierPresets,
  setComposition,
  setTeamSizes,
  setsPerRoundOptions,
  withSlot,
} from '@/lib/captain-draft'
import type { CaptainDraftEntry, PlayerRosterItem } from '@/types/api'

function slot(playerId: number | null) {
  return playerId === null ? null : { playerId, nickname: `P${playerId}` }
}

function entry(
  roundNumber: number,
  setNumber: number,
  home: Array<number | null>,
  away: Array<number | null>,
  winner: 'HOME' | 'AWAY' | null = null,
): CaptainDraftEntry {
  return {
    roundNumber,
    roundCode: 'PPP',
    setNumber,
    teamSize: home.length,
    requiredTier: null,
    homePlayers: home.map(slot),
    awayPlayers: away.map(slot),
    winnerTeam: winner,
  }
}

function rosterPlayer(overrides: Partial<PlayerRosterItem>): PlayerRosterItem {
  return { id: 3, nickname: 'Ace', race: 'PT', tier: 'A', wins: 0, losses: 0, games: 0, ...overrides }
}

describe('captain draft sets', () => {
  it('keeps the host\'s unsaved changes over the saved values, field by field', () => {
    const saved = entry(1, 1, [1, 3, 5], [2, 4, 6], 'HOME')

    expect(effectiveEntry(saved, undefined)).toMatchObject({
      key: '1-1',
      teamSize: 3,
      homePlayerIds: [1, 3, 5],
      awayPlayerIds: [2, 4, 6],
      requiredTier: '',
      winnerTeam: 'HOME',
    })
    expect(effectiveEntry(saved, { awayPlayerIds: [2, null, 6], requiredTier: '핫티어 1', winnerTeam: '' })).toMatchObject({
      homePlayerIds: [1, 3, 5],
      awayPlayerIds: [2, null, 6],
      requiredTier: '핫티어 1',
      winnerTeam: '',
    })
    // A saved side shorter than the set fills its empty slots.
    expect(effectiveEntry({ ...saved, homePlayers: [slot(1)] }, undefined).homePlayerIds).toEqual([1, null, null])
    expect(withSlot([1, null, 5], 1, 7)).toEqual([1, 7, 5])
    expect(entryKey({ roundNumber: 2, setNumber: 3 })).toBe('2-3')
  })

  it('counts set wins for each team and for every player on the winning side', () => {
    const wins = countSetWins([
      effectiveEntry(entry(1, 1, [1, 3, 5], [2, 4, 6], 'HOME'), undefined),
      effectiveEntry(entry(1, 2, [7, 9], [8, 10], 'AWAY'), undefined),
      effectiveEntry(entry(2, 1, [1, 3, 5], [2, 4, 6], null), undefined),
    ])

    expect([wins.home, wins.away]).toEqual([1, 1])
    expect(Object.fromEntries(wins.byPlayer)).toEqual({ 1: 1, 3: 1, 5: 1, 8: 1, 10: 1 })
  })

  it('knows who is already placed in a round', () => {
    const sheet = [
      effectiveEntry(entry(1, 1, [1, null, 5], [2, 4, 6]), undefined),
      effectiveEntry(entry(1, 2, [7, null], [null, null]), undefined),
      effectiveEntry(entry(2, 1, [3, 9, 11], [8, 10, 12]), undefined),
    ]

    expect([...playersInRound(sheet, 1)].sort((a, b) => a - b)).toEqual([1, 2, 4, 5, 6, 7])
  })

  it('takes a player taken out of their team off the unsaved changes', () => {
    expect(dropEditsNaming({ '1-1': { homePlayerIds: [3, 5, null], requiredTier: '상위 3' }, '1-2': { winnerTeam: 'HOME' } }, 3)).toEqual({
      '1-1': { homePlayerIds: [null, 5, null], requiredTier: '상위 3' },
      '1-2': { winnerTeam: 'HOME' },
    })
  })

  it('sizes a round\'s sets from the players of a team, as the backend does', () => {
    expect(setTeamSizes(5, 1)).toEqual([3])
    expect(setTeamSizes(5, 2)).toEqual([3, 2])
    expect(setTeamSizes(4, 2)).toEqual([2, 2])
    expect(setTeamSizes(9, 3)).toEqual([3, 3, 3])
    expect(setTeamSizes(7, 3)).toEqual([3, 2, 2])
  })

  it('offers only the sets a round can have, saying who sits out', () => {
    expect(setsPerRoundOptions(5)).toEqual([
      { sets: 1, label: '1세트 · 3:3 (2명씩 휴식)' },
      { sets: 2, label: '2세트 · 3:3 + 2:2' },
    ])
    expect(setsPerRoundOptions(4).map((option) => option.sets)).toEqual([1, 2])
    expect(setsPerRoundOptions(3)).toEqual([{ sets: 1, label: '1세트 · 3:3' }])
    expect([defaultSetsPerRound(4), defaultSetsPerRound(5), defaultSetsPerRound(9), defaultSetsPerRound(10)]).toEqual([2, 2, 3, 4])
  })

  it('plays a 2:2 set with the round\'s races less one Protoss', () => {
    expect(['PPP', 'PPT', 'PPZ', 'PTZ'].map((code) => setComposition(code, 2))).toEqual(['PP', 'PT', 'PZ', 'TZ'])
    expect(setComposition('PPT', 3)).toBe('PPT')
    expect(requiredTierPresets()).toEqual(['상위 3', '상위 2', '상위 1', '핫티어 1', '핫티어 2', '핫티어 3'])
  })

  it('shows the live tier and the playable races beside a nickname', () => {
    expect(draftPlayerTags(rosterPlayer({ liveTier: 'A+' }))).toEqual({ tier: 'A+', races: 'PT' })
    expect(draftPlayerTags(rosterPlayer({ tier: 'UNASSIGNED' })).tier).toBe('배정 필요')
    expect(draftPlayerTags(undefined, 'Z')).toEqual({ tier: null, races: 'Z' })
    expect(draftPlayerOptionLabel('Ace', { tier: 'A+', races: 'PT' })).toBe('Ace · A+ · PT')
  })
})
