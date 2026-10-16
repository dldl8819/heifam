import { t } from '@/lib/i18n'
import type { CaptainDraftEntry, PlayerRosterItem, TeamSide } from '@/types/api'

export type DraftWinnerSelection = '' | TeamSide

// Rounds take these compositions in turn unless the host picks others.
export const DEFAULT_ROUND_CODES = ['PPP', 'PPT', 'PPZ', 'PTZ'] as const
export const DEFAULT_ROUND_COUNT = 4
export const MAX_ROUND_COUNT = 7
export const MAX_SETS_PER_ROUND = 10
export const SET_SIZES = [3, 2, 1] as const
// P, T and Z, and R for random, always written in this order.
const RACE_ORDER = 'PTZR'

// Slots of one side, index 0 is slot 1; null where the slot is empty.
export type SidePlayerIds = Array<number | null>

// What the host changed on a set and has not saved yet. A field left out keeps the saved value, so
// a poll that brings in the latest draft does not undo the host's changes.
export type EntryEdit = {
  raceCode?: string
  homePlayerIds?: SidePlayerIds
  awayPlayerIds?: SidePlayerIds
  requiredTier?: string
  winnerTeam?: DraftWinnerSelection
}

export type EntryEdits = Record<string, EntryEdit>

export type EffectiveEntry = {
  key: string
  roundNumber: number
  setNumber: number
  teamSize: number
  raceCode: string
  homePlayerIds: SidePlayerIds
  awayPlayerIds: SidePlayerIds
  requiredTier: string
  winnerTeam: DraftWinnerSelection
}

export type SetWins = {
  home: number
  away: number
  byPlayer: Map<number, number>
}

export function entryKey(entry: Pick<CaptainDraftEntry, 'roundNumber' | 'setNumber'>): string {
  return `${entry.roundNumber}-${entry.setNumber}`
}

function savedSide(entry: CaptainDraftEntry, team: TeamSide): SidePlayerIds {
  const saved = team === 'HOME' ? entry.homePlayers : entry.awayPlayers
  return Array.from({ length: Math.max(1, entry.teamSize) }, (_, index) => saved?.[index]?.playerId ?? null)
}

/** A set as the sheet shows it: the host's unsaved changes where there are some, the saved values otherwise. */
export function effectiveEntry(entry: CaptainDraftEntry, edit: EntryEdit | undefined): EffectiveEntry {
  return {
    key: entryKey(entry),
    roundNumber: entry.roundNumber,
    setNumber: entry.setNumber,
    teamSize: Math.max(1, entry.teamSize),
    raceCode: edit?.raceCode ?? entry.roundCode,
    homePlayerIds: edit?.homePlayerIds ?? savedSide(entry, 'HOME'),
    awayPlayerIds: edit?.awayPlayerIds ?? savedSide(entry, 'AWAY'),
    requiredTier: edit && 'requiredTier' in edit ? (edit.requiredTier ?? '') : (entry.requiredTier ?? ''),
    winnerTeam: edit && 'winnerTeam' in edit ? (edit.winnerTeam ?? '') : (entry.winnerTeam ?? ''),
  }
}

/** One side with one slot changed. */
export function withSlot(side: SidePlayerIds, index: number, playerId: number | null): SidePlayerIds {
  return side.map((current, slot) => (slot === index ? playerId : current))
}

/** Sets won by each team, and by each player on the winning side of one. */
export function countSetWins(entries: EffectiveEntry[]): SetWins {
  const wins: SetWins = { home: 0, away: 0, byPlayer: new Map() }
  for (const entry of entries) {
    if (entry.winnerTeam !== 'HOME' && entry.winnerTeam !== 'AWAY') {
      continue
    }
    if (entry.winnerTeam === 'HOME') {
      wins.home += 1
    } else {
      wins.away += 1
    }
    for (const playerId of entry.winnerTeam === 'HOME' ? entry.homePlayerIds : entry.awayPlayerIds) {
      if (typeof playerId === 'number') {
        wins.byPlayer.set(playerId, (wins.byPlayer.get(playerId) ?? 0) + 1)
      }
    }
  }
  return wins
}

/** Players already placed in a round, so a select offers each one for a single slot of the round. */
export function playersInRound(entries: EffectiveEntry[], roundNumber: number): Set<number> {
  const placed = new Set<number>()
  for (const entry of entries) {
    if (entry.roundNumber !== roundNumber) {
      continue
    }
    for (const playerId of [...entry.homePlayerIds, ...entry.awayPlayerIds]) {
      if (typeof playerId === 'number') {
        placed.add(playerId)
      }
    }
  }
  return placed
}

/**
 * Takes a player taken out of their team off the host's unsaved changes too. The backend already
 * cleared the saved sets that named them, with their results.
 */
export function dropEditsNaming(edits: EntryEdits, playerId: number): EntryEdits {
  const clear = (side: SidePlayerIds | undefined) => side?.map((current) => (current === playerId ? null : current))
  const next: EntryEdits = {}
  for (const [key, edit] of Object.entries(edits)) {
    next[key] = {
      ...edit,
      ...(edit.homePlayerIds ? { homePlayerIds: clear(edit.homePlayerIds) } : {}),
      ...(edit.awayPlayerIds ? { awayPlayerIds: clear(edit.awayPlayerIds) } : {}),
    }
  }
  return next
}

/**
 * The team size of each set of a round, as the backend makes them: as many 3:3 games as the players
 * of a team allow, then 2:2 games. With fewer sets than that needs, the rest sit the round out.
 */
export function setTeamSizes(playersPerTeam: number, sets: number): number[] {
  const threes = sets * 3 <= playersPerTeam ? sets : Math.max(0, playersPerTeam - 2 * sets)
  return Array.from({ length: sets }, (_, index) => (index < threes ? 3 : 2))
}

/** Left open, enough sets that everyone plays each round, as many of them 3:3 as the players allow. */
export function defaultSetSizes(playersPerTeam: number): number[] {
  const sets = Math.max(1, Math.min(Math.floor(playersPerTeam / 2), Math.ceil(playersPerTeam / 3)))
  return setTeamSizes(playersPerTeam, sets)
}

/** How many of a team play each round with these sets, and how many sit it out. */
export function setLayoutSummary(sizes: number[], playersPerTeam: number): { playing: number; resting: number } {
  const playing = sizes.reduce((sum, size) => sum + size, 0)
  return { playing, resting: Math.max(0, playersPerTeam - playing) }
}

/** The sizes one set of the layout can take without needing more players than a team has. */
export function setSizeChoices(sizes: number[], index: number, playersPerTeam: number): number[] {
  const others = sizes.reduce((sum, size, position) => (position === index ? sum : sum + size), 0)
  return SET_SIZES.filter((size) => others + size <= playersPerTeam)
}

/** Whether one more set (1:1 at least) still fits the teams and the limit of sets a round. */
export function canAddSet(sizes: number[], playersPerTeam: number): boolean {
  return sizes.length < MAX_SETS_PER_ROUND && setLayoutSummary(sizes, playersPerTeam).playing + 1 <= playersPerTeam
}

/** A newly added set: the biggest game the players left can fill. */
export function nextSetSize(sizes: number[], playersPerTeam: number): number {
  const left = playersPerTeam - setLayoutSummary(sizes, playersPerTeam).playing
  return SET_SIZES.find((size) => size <= left) ?? 1
}

/** The layout and number of rounds of a draft already started, read back from its sets. */
export function draftLayout(entries: CaptainDraftEntry[]): { setSizes: number[]; roundCount: number } {
  const rounds = new Set(entries.map((entry) => entry.roundNumber))
  const firstRound = Math.min(...rounds)
  return {
    setSizes: entries
      .filter((entry) => entry.roundNumber === firstRound)
      .sort((left, right) => left.setNumber - right.setNumber)
      .map((entry) => entry.teamSize),
    roundCount: rounds.size,
  }
}

/** A composition with its races in P, T, Z, R order: "rpr" becomes "PRR". */
export function canonicalComposition(code: string): string {
  return code
    .toUpperCase()
    .split('')
    .filter((race) => RACE_ORDER.includes(race))
    .sort((left, right) => RACE_ORDER.indexOf(left) - RACE_ORDER.indexOf(right))
    .join('')
}

const FEATURED: Record<number, string[]> = {
  3: ['PPP', 'PPT', 'PPZ', 'PTZ', 'PPR', 'PRR', 'RRR'],
  2: ['PP', 'PT', 'PZ', 'TZ', 'PR', 'RR'],
  1: ['P', 'T', 'Z', 'R'],
}

function allCompositions(size: number, from = 0): string[] {
  if (size === 0) {
    return ['']
  }
  const codes: string[] = []
  for (let index = from; index < RACE_ORDER.length; index += 1) {
    for (const rest of allCompositions(size - 1, index)) {
      codes.push(RACE_ORDER[index] + rest)
    }
  }
  return codes
}

/** Every race composition a set of this size can be played with: the usual ones first, then the rest. */
export function compositionsOfSize(size: number): string[] {
  const featured = FEATURED[size] ?? []
  return [...featured, ...allCompositions(size).filter((code) => !featured.includes(code))]
}

// The 2:2 game each usual round composition is played with, as the clan plays them.
const TWO_A_SIDE: Record<string, string> = { PPP: 'PP', PPT: 'PT', PPZ: 'PZ', PTZ: 'PR' }

/**
 * The races a set smaller than its round's composition starts with, as the backend makes them; the
 * host can change them. A 2:2 game plays PPP as PP, PPT as PT, PPZ as PZ and PTZ as PR, any other
 * composition less one Protoss (PRR → RR); a 1:1 game the first race of that.
 */
export function setComposition(roundCode: string, teamSize: number): string {
  if (roundCode.length <= teamSize) {
    return roundCode
  }
  if (teamSize <= 1) {
    return setComposition(roundCode, 2).slice(0, 1)
  }
  if (teamSize === 2 && TWO_A_SIDE[roundCode]) {
    return TWO_A_SIDE[roundCode]
  }
  let code = roundCode
  while (code.length > teamSize) {
    const protoss = code.indexOf('P')
    code = protoss >= 0 ? code.slice(0, protoss) + code.slice(protoss + 1) : code.slice(1)
  }
  return code
}

/** A round's composition as its header shows it: that of its biggest set. */
export function roundComposition(sets: EffectiveEntry[]): string {
  const biggest = sets.reduce<EffectiveEntry | null>(
    (best, set) => (best === null || set.teamSize > best.teamSize ? set : best),
    null,
  )
  return biggest?.raceCode ?? ''
}

/** Notes the host can pick for the tiers a set asks for; any other short text is fine too. */
export function requiredTierPresets(): string[] {
  return ['top3', 'top2', 'top1', 'hot1', 'hot2', 'hot3'].map((key) => t(`captainDraft.entry.tierPresets.${key}`))
}

export type DraftPlayerTags = {
  tier: string | null
  races: string | null
}

/** The tier and the races a player can play, shown beside their nickname. */
export function draftPlayerTags(player: PlayerRosterItem | undefined, fallbackRace?: string | null): DraftPlayerTags {
  const tier = player ? (player.liveTier ?? player.tier ?? 'UNASSIGNED') : null
  return {
    tier: tier === null ? null : tier === 'UNASSIGNED' ? t('captainDraft.teams.unassignedTier') : tier,
    races: player?.race ?? fallbackRace ?? null,
  }
}

/** A player in a select: the nickname with their tier and races, so a set can be filled by race. */
export function draftPlayerOptionLabel(nickname: string, tags: DraftPlayerTags): string {
  return [nickname, tags.tier, tags.races].filter((part): part is string => Boolean(part)).join(' · ')
}
