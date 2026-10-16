import { t } from '@/lib/i18n'
import type { CaptainDraftEntry, PlayerRosterItem, TeamSide } from '@/types/api'

export type DraftWinnerSelection = '' | TeamSide

// What the host changed on the entry sheet and has not saved yet. A field left out keeps the
// saved value, so a poll that brings in the latest draft does not undo the host's changes.
export type EntryEdit = {
  homePlayerId?: number | null
  awayPlayerId?: number | null
  winnerTeam?: DraftWinnerSelection
}

export type EntryEdits = Record<string, EntryEdit>

export type EffectiveEntry = {
  key: string
  homePlayerId: number | null
  awayPlayerId: number | null
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

/** A set as the sheet shows it: the host's unsaved change where there is one, the saved value otherwise. */
export function effectiveEntry(entry: CaptainDraftEntry, edit: EntryEdit | undefined): EffectiveEntry {
  return {
    key: entryKey(entry),
    homePlayerId: edit && 'homePlayerId' in edit ? (edit.homePlayerId ?? null) : entry.homePlayerId,
    awayPlayerId: edit && 'awayPlayerId' in edit ? (edit.awayPlayerId ?? null) : entry.awayPlayerId,
    winnerTeam: edit && 'winnerTeam' in edit ? (edit.winnerTeam ?? '') : (entry.winnerTeam ?? ''),
  }
}

/** Sets won by each team and by each player who won one. */
export function countSetWins(entries: EffectiveEntry[]): SetWins {
  const wins: SetWins = { home: 0, away: 0, byPlayer: new Map() }
  for (const entry of entries) {
    const winner = entry.winnerTeam === 'HOME' ? entry.homePlayerId : entry.winnerTeam === 'AWAY' ? entry.awayPlayerId : null
    if (entry.winnerTeam === 'HOME') {
      wins.home += 1
    } else if (entry.winnerTeam === 'AWAY') {
      wins.away += 1
    }
    if (typeof winner === 'number') {
      wins.byPlayer.set(winner, (wins.byPlayer.get(winner) ?? 0) + 1)
    }
  }
  return wins
}

/**
 * Drops unsaved changes that name a player taken out of their team. The backend already cleared
 * the saved sets that named them, with their results.
 */
export function dropEditsNaming(edits: EntryEdits, playerId: number): EntryEdits {
  const next: EntryEdits = {}
  for (const [key, edit] of Object.entries(edits)) {
    if (edit.homePlayerId === playerId || edit.awayPlayerId === playerId) {
      continue
    }
    next[key] = edit
  }
  return next
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

/** A player in a select: the nickname with their tier and races, so an entry can be picked by race. */
export function draftPlayerOptionLabel(nickname: string, tags: DraftPlayerTags): string {
  return [nickname, tags.tier, tags.races].filter((part): part is string => Boolean(part)).join(' · ')
}
