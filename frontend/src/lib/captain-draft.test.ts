import { describe, expect, it } from 'vitest'
import {
  canAddSet,
  canonicalComposition,
  compositionsOfSize,
  countSetWins,
  defaultDraftTitle,
  defaultSetSizes,
  draftLayout,
  draftPlayerOptionLabel,
  draftRecordState,
  draftPlayerTags,
  draftSheet,
  dropEditsNaming,
  dropLineupEdits,
  effectiveEntry,
  entryKey,
  hasAnyLineup,
  hasAnyResult,
  isRunByCaptains,
  lineupRoundsChanged,
  lineupSubmission,
  nextSetSize,
  playersInRound,
  requiredTierPresets,
  roundComposition,
  roundLineups,
  setComposition,
  setControls,
  setLayoutSummary,
  setSizeChoices,
  setTeamSizes,
  setupDiffersFromDraft,
  sortByTier,
  withSlot,
  type DraftViewer,
  type RoundLineup,
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

const HOST_MEMBER: DraftViewer = { byCaptains: false, viewerTeam: null, viewerCaptain: false, canEditResults: false }
const NOT_IN: RoundLineup = { homeSubmitted: false, awaySubmitted: false, revealed: false }
const HOME_IN: RoundLineup = { homeSubmitted: true, awaySubmitted: false, revealed: false }
const BOTH_IN: RoundLineup = { homeSubmitted: true, awaySubmitted: true, revealed: true }

function captainsViewer(overrides: Partial<DraftViewer> = {}): DraftViewer {
  return { byCaptains: true, viewerTeam: null, viewerCaptain: false, canEditResults: false, ...overrides }
}

// A set of a round not shown to everyone, as the backend sends it to one who may not see a side.
function hiding(set: CaptainDraftEntry, team: 'HOME' | 'AWAY'): CaptainDraftEntry {
  return team === 'HOME'
    ? { ...set, homePlayers: set.homePlayers.map(() => null), homeHidden: true }
    : { ...set, awayPlayers: set.awayPlayers.map(() => null), awayHidden: true }
}

describe('captain draft sets', () => {
  it('locks the sets with a saved result for those who may not edit results, and none for those who may', () => {
    const decided = entry(1, 1, [1], [2], 'HOME')

    expect(setControls(decided, undefined, HOST_MEMBER)).toEqual({
      sides: { HOME: 'read', AWAY: 'read' },
      settings: false,
      result: false,
      locked: true,
    })
    expect(setControls(decided, undefined, { ...HOST_MEMBER, canEditResults: true }).locked).toBe(false)
    expect(setControls(entry(1, 2, [3], [4]), undefined, HOST_MEMBER)).toEqual({
      sides: { HOME: 'edit', AWAY: 'edit' },
      settings: true,
      result: true,
      locked: false,
    })
  })

  it('lets a captain fill in only their own side before both line-ups are in, and hides the other', () => {
    const set = hiding(entry(1, 1, [1, 3, 5], [null, null, null]), 'AWAY')
    const homeCaptain = captainsViewer({ viewerTeam: 'HOME', viewerCaptain: true })

    expect(setControls(set, NOT_IN, homeCaptain)).toEqual({
      sides: { HOME: 'edit', AWAY: 'hidden' },
      settings: true,
      result: false,
      locked: false,
    })
    // A team player reads their team's line-up; one in no team sees neither side.
    expect(setControls(set, HOME_IN, captainsViewer({ viewerTeam: 'HOME' })).sides).toEqual({ HOME: 'read', AWAY: 'hidden' })
    expect(setControls(hiding(set, 'HOME'), HOME_IN, captainsViewer()).sides).toEqual({ HOME: 'hidden', AWAY: 'hidden' })
    // A line-up is in: its races and tier notes are fixed but for those who may edit results.
    expect(setControls(set, HOME_IN, homeCaptain).settings).toBe(false)
    expect(setControls(set, HOME_IN, captainsViewer({ canEditResults: true })).settings).toBe(true)
  })

  it('shows both sides once both line-ups are in, with players fixed and the first result open to all', () => {
    const set = entry(1, 1, [1, 3, 5], [2, 4, 6])

    expect(setControls(set, BOTH_IN, captainsViewer({ viewerTeam: 'HOME', viewerCaptain: true }))).toEqual({
      sides: { HOME: 'read', AWAY: 'read' },
      settings: false,
      result: true,
      locked: false,
    })
    expect(setControls(set, BOTH_IN, captainsViewer({ canEditResults: true })).sides).toEqual({ HOME: 'edit', AWAY: 'edit' })
    expect(setControls({ ...set, winnerTeam: 'AWAY' }, BOTH_IN, captainsViewer()).locked).toBe(true)
  })

  it('lays a captain\'s line-up over their own side and keeps the rest of the players as handed in', () => {
    const entries = [hiding(entry(1, 1, [1, 3, 5], [null, null, null]), 'AWAY'), entry(2, 1, [1, 3, 5], [2, 4, 6])]
    const lineups = new Map([[1, HOME_IN], [2, BOTH_IN]])
    const homeCaptain = captainsViewer({ viewerTeam: 'HOME', viewerCaptain: true })

    const rows = draftSheet(
      entries,
      // Side changes through the host's edits never count in a round not shown, nor results.
      { '1-1': { awayPlayerIds: [9, 9, 9], winnerTeam: 'HOME' }, '2-1': { homePlayerIds: [5, 3, 1] } },
      { '1-1': [5, 1, 3] },
      lineups,
      homeCaptain,
    )

    expect(rows[0].set.homePlayerIds).toEqual([5, 1, 3])
    expect(rows[0].set.awayPlayerIds).toEqual([null, null, null])
    expect(rows[0].set.winnerTeam).toBe('')
    expect(rows[0].edited).toBe(false)
    expect(rows[1].set.homePlayerIds).toEqual([1, 3, 5])
    expect(rows[1].edited).toBe(false)
    // The same line-up once the round is shown to everyone: what was handed in counts.
    expect(draftSheet(entries.slice(1), {}, { '2-1': [5, 3, 1] }, lineups, homeCaptain)[0].set.homePlayerIds).toEqual([1, 3, 5])
  })

  it('keeps the host\'s edits in a draft the host fills in, but none to a set they may not change', () => {
    const rows = draftSheet(
      [entry(1, 1, [1], [2]), entry(1, 2, [3], [4], 'HOME')],
      { '1-1': { homePlayerIds: [7] }, '1-2': { winnerTeam: 'AWAY' } },
      {},
      new Map(),
      HOST_MEMBER,
    )

    expect(rows.map((row) => row.set.homePlayerIds)).toEqual([[7], [3]])
    expect(rows.map((row) => row.set.winnerTeam)).toEqual(['', 'HOME'])
    expect(rows.map((row) => row.edited)).toEqual([true, false])
  })

  it('hands in a round\'s line-up only with every slot of every set filled', () => {
    const sheet = [
      effectiveEntry(entry(1, 2, [7], [null]), undefined),
      effectiveEntry(entry(1, 1, [1, 3], [null, null]), undefined),
      effectiveEntry(entry(2, 1, [1, null], [null, null]), undefined),
    ]

    expect(lineupSubmission(sheet, 1, 'HOME')).toEqual({
      request: { sets: [{ setNumber: 1, playerIds: [1, 3] }, { setNumber: 2, playerIds: [7] }] },
      complete: true,
    })
    expect(lineupSubmission(sheet, 2, 'HOME').complete).toBe(false)
    expect(lineupSubmission(sheet, 1, 'AWAY').complete).toBe(false)
    expect(lineupSubmission(sheet, 3, 'HOME').complete).toBe(false)
  })

  it('tells the rounds whose line-up on screen is not the one handed in, and drops a round handed in', () => {
    const entries = [entry(1, 1, [1, 3], [2, 4]), entry(2, 1, [1, 3], [2, 4]), entry(2, 2, [5], [6])]
    const edits = { '1-1': [1, 3], '2-1': [3, 1] }

    expect([...lineupRoundsChanged(edits, entries, 'HOME')]).toEqual([2])
    expect(dropLineupEdits(edits, entries, 2)).toEqual({ '1-1': [1, 3] })
  })

  it('reads the line-ups of a draft run by its captains, and none for one the host fills in', () => {
    const lineups = [{ roundNumber: 1, homeSubmitted: true, awaySubmitted: false, revealed: false }]

    expect(isRunByCaptains({ entryMode: 'CAPTAINS' })).toBe(true)
    expect(isRunByCaptains({ entryMode: 'HOST' })).toBe(false)
    expect(isRunByCaptains({})).toBe(false)
    expect(roundLineups({ entryMode: 'CAPTAINS', lineups }).get(1)).toEqual(HOME_IN)
    expect(roundLineups({ entryMode: 'HOST', lineups }).size).toBe(0)
    expect(hasAnyLineup(roundLineups({ entryMode: 'CAPTAINS', lineups }))).toBe(true)
    expect(hasAnyLineup(new Map([[1, NOT_IN]]))).toBe(false)
  })

  it('names who put a saved result in, but not for a result changed here and not saved', () => {
    const saved = { ...entry(1, 1, [1], [2], 'HOME'), resultRecordedByNickname: 'YOUR_USERNAME' }

    expect(effectiveEntry(saved, undefined).resultRecordedBy).toBe('YOUR_USERNAME')
    expect(effectiveEntry(saved, { requiredTier: '상위 3' }).resultRecordedBy).toBe('YOUR_USERNAME')
    expect(effectiveEntry(saved, { winnerTeam: 'AWAY' }).resultRecordedBy).toBeNull()
    expect(effectiveEntry(saved, { winnerTeam: '' }).resultRecordedBy).toBeNull()
    expect(effectiveEntry(entry(1, 1, [1], [2]), undefined).resultRecordedBy).toBeNull()
  })

  it('tells whether the games have begun: any set with a saved result', () => {
    expect(hasAnyResult([entry(1, 1, [1], [2]), entry(1, 2, [3], [4])])).toBe(false)
    expect(hasAnyResult([entry(1, 1, [1], [2]), entry(2, 1, [3], [4], 'AWAY')])).toBe(true)
    expect(hasAnyResult([])).toBe(false)
  })

  it('keeps the host\'s unsaved changes over the saved values, field by field', () => {
    const saved = entry(1, 1, [1, 3, 5], [2, 4, 6], 'HOME')

    expect(effectiveEntry(saved, undefined)).toMatchObject({
      key: '1-1',
      teamSize: 3,
      raceCode: 'PPP',
      homePlayerIds: [1, 3, 5],
      awayPlayerIds: [2, 4, 6],
      requiredTier: '',
      winnerTeam: 'HOME',
    })
    expect(
      effectiveEntry(saved, { raceCode: 'PRR', awayPlayerIds: [2, null, 6], requiredTier: '핫티어 1', winnerTeam: '' }),
    ).toMatchObject({
      raceCode: 'PRR',
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
      effectiveEntry(entry(1, 2, [7], [8], 'AWAY'), undefined),
      effectiveEntry(entry(2, 1, [1, 3, 5], [2, 4, 6], null), undefined),
    ])

    expect([wins.home, wins.away]).toEqual([1, 1])
    expect(Object.fromEntries(wins.byPlayer)).toEqual({ 1: 1, 3: 1, 5: 1, 8: 1 })
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
    expect([defaultSetSizes(4), defaultSetSizes(5), defaultSetSizes(9)]).toEqual([[2, 2], [3, 2], [3, 3, 3]])
  })

  it('lays out a round from 3:3, 2:2 and 1:1 sets within what a team can field', () => {
    // Five a side: 3:3 + 1:1 leaves one resting; a third set fits only as 1:1.
    expect(setLayoutSummary([3, 1], 5)).toEqual({ playing: 4, resting: 1 })
    expect(setLayoutSummary([3, 1, 1], 5)).toEqual({ playing: 5, resting: 0 })
    expect(setLayoutSummary([3], 5)).toEqual({ playing: 3, resting: 2 })
    expect(setSizeChoices([3, 1], 1, 5)).toEqual([2, 1])
    expect(setSizeChoices([3, 2], 0, 5)).toEqual([3, 2, 1])
    expect([canAddSet([3, 1], 5), canAddSet([3, 2], 5), canAddSet([3], 5)]).toEqual([true, false, true])
    expect([nextSetSize([3], 5), nextSetSize([3, 1], 5)]).toEqual([2, 1])
  })

  it('reads the layout and rounds back from a started draft', () => {
    const entries = [entry(1, 1, [1, 3, 5], [2, 4, 6]), entry(1, 2, [7], [8]), entry(2, 1, [1, 3, 5], [2, 4, 6]), entry(2, 2, [7], [8])]
    expect(draftLayout(entries)).toEqual({ setSizes: [3, 1], roundCount: 2 })
  })

  it('offers only the usual race compositions and the random ones', () => {
    expect(compositionsOfSize(3)).toEqual(['PPP', 'PPT', 'PPZ', 'PTZ', 'PPR', 'PRR', 'RRR'])
    expect(compositionsOfSize(2)).toEqual(['PP', 'PT', 'PZ', 'TZ', 'PR', 'RR'])
    expect(compositionsOfSize(1)).toEqual(['P', 'T', 'Z', 'R'])
    // Every 2:2 and 1:1 a round's composition starts its smaller sets with is still offered.
    for (const code of compositionsOfSize(3)) {
      expect(compositionsOfSize(2)).toContain(setComposition(code, 2))
      expect(compositionsOfSize(1)).toContain(setComposition(code, 1))
    }
    expect(canonicalComposition('rpr')).toBe('PRR')
  })

  it('starts 2:2 and 1:1 sets from the round\'s races as the clan plays them', () => {
    expect(['PPP', 'PPT', 'PPZ', 'PTZ'].map((code) => setComposition(code, 2))).toEqual(['PP', 'PT', 'PZ', 'PR'])
    expect(['PPP', 'PPT', 'PTZ', 'RRR'].map((code) => setComposition(code, 1))).toEqual(['P', 'P', 'P', 'R'])
    expect([setComposition('PRR', 2), setComposition('PPR', 2), setComposition('PPT', 3)]).toEqual(['RR', 'PR', 'PPT'])
    expect(requiredTierPresets()).toEqual(['상위 3', '상위 2', '상위 1', '핫티어 1', '핫티어 2', '핫티어 3'])
  })

  it('shows a round by its biggest set and keeps each set\'s own races', () => {
    const sets = [
      effectiveEntry(entry(1, 1, [1, 3, 5], [2, 4, 6]), { raceCode: 'PRR' }),
      effectiveEntry(entry(1, 2, [7], [8]), undefined),
    ]
    expect(roundComposition(sets)).toBe('PRR')
    expect(sets[1].raceCode).toBe('PPP')
  })

  it('shows the live tier and the playable races beside a nickname', () => {
    expect(draftPlayerTags(rosterPlayer({ liveTier: 'A+' }))).toEqual({ tier: 'A+', races: 'PT' })
    expect(draftPlayerTags(rosterPlayer({ tier: 'UNASSIGNED' })).tier).toBe('배정 필요')
    expect(draftPlayerTags(undefined, 'Z')).toEqual({ tier: null, races: 'Z' })
    expect(draftPlayerOptionLabel('Ace', { tier: 'A+', races: 'PT' })).toBe('Ace · A+ · PT')
  })

  it('orders players for picking: captains, then the highest live tier down, then off the roster', () => {
    const roster = new Map<number, PlayerRosterItem>([
      [1, rosterPlayer({ id: 1, nickname: '다', tier: 'B' })],
      [2, rosterPlayer({ id: 2, nickname: '나', tier: 'B', liveTier: 'S' })],
      [3, rosterPlayer({ id: 3, nickname: '가', tier: 'B' })],
      [4, rosterPlayer({ id: 4, nickname: '라', tier: 'UNASSIGNED' })],
      [5, rosterPlayer({ id: 5, nickname: '마', tier: 'D-' })],
    ])
    const participant = (playerId: number | null, nickname: string, captain = false) => ({ playerId, nickname, captain })

    const sorted = sortByTier(
      [participant(4, '라'), participant(null, '탈퇴'), participant(1, '다'), participant(5, '마', true), participant(3, '가'), participant(2, '나')],
      roster,
    )

    expect(sorted.map((item) => item.nickname)).toEqual(['마', '나', '가', '다', '라', '탈퇴'])
  })

  it('tells when the attendance or captains on screen are not the draft shown, before loading over them', () => {
    const shown = {
      participants: [{ playerId: 1 }, { playerId: 2 }, { playerId: 3 }, { playerId: null }].map((participant) => ({
        ...participant, nickname: 'n', race: 'P' as const, team: 'UNASSIGNED' as const, captain: false, pickOrder: null,
      })),
      homeCaptainPlayerId: 1,
      awayCaptainPlayerId: 2,
    }

    // The draft as loaded, in any order: nothing typed to lose.
    expect(setupDiffersFromDraft(shown, [3, 2, 1], 1, 2)).toBe(false)
    // A player added or dropped, or another captain: typed for the next draft.
    expect(setupDiffersFromDraft(shown, [1, 2, 3, 4], 1, 2)).toBe(true)
    expect(setupDiffersFromDraft(shown, [1, 2], 1, 2)).toBe(true)
    expect(setupDiffersFromDraft(shown, [1, 2, 3], 3, 2)).toBe(true)
    // No draft shown: anything typed at all.
    expect(setupDiffersFromDraft(null, [], null, null)).toBe(false)
    expect(setupDiffersFromDraft(null, [5], null, null)).toBe(true)
  })
})

describe('captain draft records', () => {
  const summary = {
    draftId: 1, title: 't', createdAt: '2026-10-17T11:00:00Z', status: 'READY', homeCaptainNickname: 'A', awayCaptainNickname: 'B',
    participantCount: 10, roundCount: 4, setsPerRound: 2, homeRoundWins: 2, awayRoundWins: 1, homeSetWins: 5, awaySetWins: 3,
    decidedSets: 8, totalSets: 8,
  }

  it('tells a draft still picking teams, being played, and finished', () => {
    expect(draftRecordState({ ...summary, status: 'DRAFTING' })).toBe('drafting')
    expect(draftRecordState({ ...summary, decidedSets: 5 })).toBe('playing')
    expect(draftRecordState(summary)).toBe('finished')
  })

  it('titles a new draft by the Korean date', () => {
    // 15:30 UTC on the 17th is already past midnight of the 18th in Korea.
    expect(defaultDraftTitle(new Date('2026-10-17T15:30:00Z'))).toBe('10월 18일 정기 감전')
  })
})
