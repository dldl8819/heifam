import { describe, expect, it } from 'vitest'
import {
  countSetWins,
  draftPlayerOptionLabel,
  draftPlayerTags,
  dropEditsNaming,
  effectiveEntry,
  entryKey,
} from '@/lib/captain-draft'
import type { CaptainDraftEntry, PlayerRosterItem } from '@/types/api'

function entry(setNumber: number, home: number | null, away: number | null, winner: 'HOME' | 'AWAY' | null): CaptainDraftEntry {
  return {
    roundNumber: 1,
    roundCode: 'PPP',
    setNumber,
    homePlayerId: home,
    homePlayerNickname: null,
    awayPlayerId: away,
    awayPlayerNickname: null,
    winnerTeam: winner,
  }
}

function rosterPlayer(overrides: Partial<PlayerRosterItem>): PlayerRosterItem {
  return { id: 3, nickname: 'Ace', race: 'PT', tier: 'A', wins: 0, losses: 0, games: 0, ...overrides }
}

describe('captain draft sheet', () => {
  it('keeps the host\'s unsaved changes over the saved values, field by field', () => {
    const saved = entry(1, 3, 4, 'HOME')

    expect(effectiveEntry(saved, undefined)).toEqual({ key: '1-1', homePlayerId: 3, awayPlayerId: 4, winnerTeam: 'HOME' })
    expect(effectiveEntry(saved, { awayPlayerId: 6 })).toEqual({ key: '1-1', homePlayerId: 3, awayPlayerId: 6, winnerTeam: 'HOME' })
    // Clearing a player or the result is a change too.
    expect(effectiveEntry(saved, { homePlayerId: null, winnerTeam: '' })).toEqual({
      key: '1-1',
      homePlayerId: null,
      awayPlayerId: 4,
      winnerTeam: '',
    })
    expect(entryKey({ roundNumber: 2, setNumber: 3 })).toBe('2-3')
  })

  it('counts set wins for each team and each winning player', () => {
    const wins = countSetWins([
      effectiveEntry(entry(1, 3, 4, 'HOME'), undefined),
      effectiveEntry(entry(2, 3, 5, 'HOME'), undefined),
      effectiveEntry(entry(3, 7, 5, 'AWAY'), undefined),
      effectiveEntry(entry(4, 7, null, null), undefined),
    ])

    expect([wins.home, wins.away]).toEqual([2, 1])
    expect(Object.fromEntries(wins.byPlayer)).toEqual({ 3: 2, 5: 1 })
  })

  it('drops unsaved changes that name a player taken out of their team', () => {
    expect(dropEditsNaming({ '1-1': { homePlayerId: 3 }, '1-2': { awayPlayerId: 4 }, '1-3': { winnerTeam: 'HOME' } }, 3)).toEqual({
      '1-2': { awayPlayerId: 4 },
      '1-3': { winnerTeam: 'HOME' },
    })
  })

  it('shows the live tier and the playable races beside a nickname', () => {
    expect(draftPlayerTags(rosterPlayer({ liveTier: 'A+' }))).toEqual({ tier: 'A+', races: 'PT' })
    expect(draftPlayerTags(rosterPlayer({ tier: 'UNASSIGNED' })).tier).toBe('배정 필요')
    // A player missing from the roster keeps the draft's race and shows no tier.
    expect(draftPlayerTags(undefined, 'Z')).toEqual({ tier: null, races: 'Z' })
    expect(draftPlayerOptionLabel('Ace', { tier: 'A+', races: 'PT' })).toBe('Ace · A+ · PT')
    expect(draftPlayerOptionLabel('Ace', { tier: null, races: null })).toBe('Ace')
  })
})
