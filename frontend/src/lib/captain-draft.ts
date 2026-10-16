import { t } from '@/lib/i18n'
import type { CaptainDraftEntry, PlayerRosterItem, TeamSide } from '@/types/api'

export type DraftWinnerSelection = '' | TeamSide

export const ROUND_CODES = ['PPP', 'PPT', 'PPZ', 'PTZ'] as const

// Slots of one side, index 0 is slot 1; null where the slot is empty.
export type SidePlayerIds = Array<number | null>

// What the host changed on a set and has not saved yet. A field left out keeps the saved value, so
// a poll that brings in the latest draft does not undo the host's changes.
export type EntryEdit = {
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

export type SetsPerRoundOption = {
  sets: number
  label: string
}

/** The sets a round can have for teams of this size: from one 3:3 game up to all 2:2 games. */
export function setsPerRoundOptions(playersPerTeam: number): SetsPerRoundOption[] {
  const maximum = Math.floor(playersPerTeam / 2)
  return Array.from({ length: Math.max(0, maximum) }, (_, index) => {
    const sets = index + 1
    const sizes = setTeamSizes(playersPerTeam, sets)
    const resting = playersPerTeam - sizes.reduce((sum, size) => sum + size, 0)
    const games = sizes.map((size) => `${size}:${size}`).join(' + ')
    return {
      sets,
      label:
        resting > 0
          ? t('captainDraft.settings.optionResting', { sets, games, resting })
          : t('captainDraft.settings.option', { sets, games }),
    }
  })
}

/** Left open, enough sets that everyone plays each round, as many of them 3:3 as the players allow. */
export function defaultSetsPerRound(playersPerTeam: number): number {
  return Math.max(1, Math.min(Math.floor(playersPerTeam / 2), Math.ceil(playersPerTeam / 3)))
}

/**
 * The races a set is played with: the round's composition for a 3:3 game, and for a 2:2 game the
 * same less one Protoss (PPP → PP, PPT → PT, PPZ → PZ, PTZ → TZ).
 */
export function setComposition(roundCode: string, teamSize: number): string {
  if (teamSize !== 2) {
    return roundCode
  }
  const index = roundCode.indexOf('P')
  return index < 0 ? roundCode.slice(1) : roundCode.slice(0, index) + roundCode.slice(index + 1)
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
