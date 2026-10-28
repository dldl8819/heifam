import { t } from '@/lib/i18n'
import { toTierOrder } from '@/lib/player-tier'
import type {
  CaptainDraftEntry,
  CaptainDraftLineupRequest,
  CaptainDraftLineupStage,
  CaptainDraftResponse,
  CaptainDraftSummary,
  PlayerRosterItem,
  TeamSide,
} from '@/types/api'

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
  // Who put the saved result in, while the result shown is that one; null otherwise.
  resultRecordedBy: string | null
  // The set of the ace decider (에이스 결정전), the extra round played when the rounds end level.
  aceDecider: boolean
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
  const savedWinner: DraftWinnerSelection = entry.winnerTeam ?? ''
  const winnerTeam = edit && 'winnerTeam' in edit ? (edit.winnerTeam ?? '') : savedWinner
  return {
    key: entryKey(entry),
    roundNumber: entry.roundNumber,
    setNumber: entry.setNumber,
    teamSize: Math.max(1, entry.teamSize),
    raceCode: edit?.raceCode ?? entry.roundCode,
    homePlayerIds: edit?.homePlayerIds ?? savedSide(entry, 'HOME'),
    awayPlayerIds: edit?.awayPlayerIds ?? savedSide(entry, 'AWAY'),
    requiredTier: edit && 'requiredTier' in edit ? (edit.requiredTier ?? '') : (entry.requiredTier ?? ''),
    winnerTeam,
    // A result changed here and not saved yet is nobody's yet.
    resultRecordedBy: winnerTeam !== '' && winnerTeam === savedWinner ? (entry.resultRecordedByNickname ?? null) : null,
    aceDecider: Boolean(entry.aceDecider),
  }
}

/** One side with one slot changed. */
export function withSlot(side: SidePlayerIds, index: number, playerId: number | null): SidePlayerIds {
  return side.map((current, slot) => (slot === index ? playerId : current))
}

function hasSavedResult(entry: CaptainDraftEntry): boolean {
  return entry.winnerTeam === 'HOME' || entry.winnerTeam === 'AWAY'
}

/** Whether any set has a saved result: the games have begun, and no pick can be taken back. */
export function hasAnyResult(entries: readonly CaptainDraftEntry[]): boolean {
  return entries.some(hasSavedResult)
}

/** A draft whose captains hand in their own team's line-up, unseen by the other team until both are in. */
export function isRunByCaptains(draft: Pick<CaptainDraftResponse, 'entryMode'> | null | undefined): boolean {
  return draft?.entryMode === 'CAPTAINS'
}

/** What a captain hands in at once: every regular round (MAIN), then the ace decider (ACE). */
export function stageOf(entry: Pick<CaptainDraftEntry, 'aceDecider'>): CaptainDraftLineupStage {
  return entry.aceDecider ? 'ACE' : 'MAIN'
}

// Which team has handed a stage's line-up in, and whether both have (both shown to everyone).
export type StageLineup = {
  homeSubmitted: boolean
  awaySubmitted: boolean
  revealed: boolean
}

export function teamSubmitted(lineup: StageLineup | undefined, team: TeamSide): boolean {
  return Boolean(team === 'HOME' ? lineup?.homeSubmitted : lineup?.awaySubmitted)
}

/** Each stage's line-ups of a draft run by its captains; none for a draft the host fills in. */
export function stageLineups(
  draft: Pick<CaptainDraftResponse, 'entryMode' | 'lineups'> | null,
): Map<CaptainDraftLineupStage, StageLineup> {
  const stages = new Map<CaptainDraftLineupStage, StageLineup>()
  if (!isRunByCaptains(draft)) {
    return stages
  }
  for (const lineup of draft?.lineups ?? []) {
    stages.set(lineup.stage, {
      homeSubmitted: lineup.homeSubmitted,
      awaySubmitted: lineup.awaySubmitted,
      revealed: lineup.revealed,
    })
  }
  return stages
}

/** Whether a captain has handed any line-up in: the teams then stay as they are. */
export function hasAnyLineup(lineups: ReadonlyMap<CaptainDraftLineupStage, StageLineup>): boolean {
  return [...lineups.values()].some((lineup) => lineup.homeSubmitted || lineup.awaySubmitted)
}

/** Whether one captain alone has handed a stage in; the draft cannot go back to its host then. */
export function hasLoneLineup(lineups: ReadonlyMap<CaptainDraftLineupStage, StageLineup>): boolean {
  return [...lineups.values()].some((lineup) => lineup.homeSubmitted !== lineup.awaySubmitted)
}

// Who reads the sheet: how the draft is run, the reader's team (the backend knows it from their
// account), whether they captain it, and whether they may edit results (admins and editors).
export type DraftViewer = {
  byCaptains: boolean
  viewerTeam: TeamSide | null
  viewerCaptain: boolean
  canEditResults: boolean
}

/** A side as this viewer has it: to fill in, to read, or kept from them until both teams are in. */
export type SideAccess = 'edit' | 'read' | 'hidden'

export type SetControls = {
  sides: Record<TeamSide, SideAccess>
  // Its races and tier note.
  settings: boolean
  result: boolean
  // Its result is saved and this viewer may not change it.
  locked: boolean
}

/**
 * What this viewer may change in a set. The backend holds to the same; this only shapes the sheet.
 *
 * Filled in by the host, or run by the captains once both line-ups of the set's stage are in:
 * anything, but a set whose result is saved only by those who may edit results. Run by the captains
 * before that: a captain fills in their own side until they hand it in, their team reads it, and the
 * other side is hidden; races and tier notes are anyone's, and no result yet.
 */
export function setControls(entry: CaptainDraftEntry, lineup: StageLineup | undefined, viewer: DraftViewer): SetControls {
  const resultLocked = hasSavedResult(entry) && !viewer.canEditResults
  if (!viewer.byCaptains || lineup?.revealed) {
    const side: SideAccess = resultLocked ? 'read' : 'edit'
    return { sides: { HOME: side, AWAY: side }, settings: !resultLocked, result: !resultLocked, locked: resultLocked }
  }
  const access = (team: TeamSide): SideAccess => {
    if (team === 'HOME' ? entry.homeHidden : entry.awayHidden) {
      return 'hidden'
    }
    return viewer.viewerCaptain && viewer.viewerTeam === team && !teamSubmitted(lineup, team) ? 'edit' : 'read'
  }
  return { sides: { HOME: access('HOME'), AWAY: access('AWAY') }, settings: true, result: false, locked: false }
}

// A captain's line-up not handed in yet: their side of each set, by set key.
export type LineupEdits = Record<string, SidePlayerIds>

export type SheetRow = {
  set: EffectiveEntry
  controls: SetControls
  // Whether it carries changes not saved yet (races, tier note, result or players, but not a captain's line-up).
  edited: boolean
}

function sideKey(team: TeamSide): 'homePlayerIds' | 'awayPlayerIds' {
  return team === 'HOME' ? 'homePlayerIds' : 'awayPlayerIds'
}

/**
 * The sheet as this viewer sees it: the saved sets with the changes made here laid over them, only
 * those the viewer may make. In a stage of a draft run by its captains that is not in for both
 * teams, the players are the captains': a captain's line-up not handed in yet shows on their side.
 */
export function draftSheet(
  entries: readonly CaptainDraftEntry[],
  edits: EntryEdits,
  lineupEdits: LineupEdits,
  lineups: ReadonlyMap<CaptainDraftLineupStage, StageLineup>,
  viewer: DraftViewer,
): SheetRow[] {
  return entries.map((entry) => {
    const key = entryKey(entry)
    const lineup = lineups.get(stageOf(entry))
    const controls = setControls(entry, lineup, viewer)
    const own: EntryEdit = { ...(edits[key] ?? {}) }
    if (!controls.settings) {
      delete own.raceCode
      delete own.requiredTier
    }
    if (!controls.result) {
      delete own.winnerTeam
    }
    const byCaptainsUnshown = viewer.byCaptains && !lineup?.revealed
    for (const team of ['HOME', 'AWAY'] as const) {
      if (byCaptainsUnshown || controls.sides[team] !== 'edit') {
        delete own[sideKey(team)]
      }
    }
    const edited = Object.keys(own).length > 0
    const team = viewer.viewerTeam
    if (byCaptainsUnshown && team && controls.sides[team] === 'edit' && lineupEdits[key]) {
      own[sideKey(team)] = lineupEdits[key]
    }
    return { set: effectiveEntry(entry, own), controls, edited }
  })
}

/**
 * A captain's line-up for a stage as the backend takes it, once: their side of every set of every
 * round of it, and whether every slot is filled (the backend takes none with an empty slot).
 */
export function lineupSubmission(
  sheet: readonly EffectiveEntry[],
  stage: CaptainDraftLineupStage,
  team: TeamSide,
): { request: CaptainDraftLineupRequest; complete: boolean } {
  const sets = sheet
    .filter((set) => stageOf(set) === stage)
    .sort((left, right) => left.roundNumber - right.roundNumber || left.setNumber - right.setNumber)
    .map((set) => ({
      roundNumber: set.roundNumber,
      setNumber: set.setNumber,
      slots: team === 'HOME' ? set.homePlayerIds : set.awayPlayerIds,
    }))
  return {
    request: {
      stage,
      sets: sets.map(({ roundNumber, setNumber, slots }) => ({
        roundNumber,
        setNumber,
        playerIds: slots.filter((playerId): playerId is number => typeof playerId === 'number'),
      })),
    },
    complete: sets.length > 0 && sets.every(({ slots }) => slots.every((playerId) => typeof playerId === 'number')),
  }
}

/** The stages a captain has a line-up on screen for that is not handed in yet. */
export function stagesWithLineupEdits(lineupEdits: LineupEdits, entries: readonly CaptainDraftEntry[]): Set<CaptainDraftLineupStage> {
  const stages = new Set<CaptainDraftLineupStage>()
  for (const entry of entries) {
    if (lineupEdits[entryKey(entry)]) {
      stages.add(stageOf(entry))
    }
  }
  return stages
}

/** Takes a stage handed in off a captain's line-up not handed in yet. */
export function dropLineupEdits(
  lineupEdits: LineupEdits,
  entries: readonly CaptainDraftEntry[],
  stage: CaptainDraftLineupStage,
): LineupEdits {
  const keys = new Set(entries.filter((entry) => stageOf(entry) === stage).map(entryKey))
  return Object.fromEntries(Object.entries(lineupEdits).filter(([key]) => !keys.has(key)))
}

/**
 * The round score when an ace decider (에이스 결정전) can be offered: every set of the draft has a
 * result, the rounds ended level, and none has been played; null otherwise. A round goes to the team
 * that won more of its sets, as the backend counts it; a level round goes to nobody.
 */
export function aceDeciderOffer(entries: readonly CaptainDraftEntry[]): { home: number; away: number } | null {
  if (entries.length === 0 || entries.some((entry) => entry.aceDecider) || !entries.every(hasSavedResult)) {
    return null
  }
  const rounds = new Map<number, { home: number; away: number }>()
  for (const entry of entries) {
    const round = rounds.get(entry.roundNumber) ?? { home: 0, away: 0 }
    if (entry.winnerTeam === 'HOME') {
      round.home += 1
    } else {
      round.away += 1
    }
    rounds.set(entry.roundNumber, round)
  }
  let home = 0
  let away = 0
  for (const round of rounds.values()) {
    if (round.home > round.away) {
      home += 1
    } else if (round.away > round.home) {
      away += 1
    }
  }
  return home === away ? { home, away } : null
}

/** The sizes an ace decider can take: 1:1 up to 3:3, as far as the smaller team allows. */
export function aceSizeChoices(homePlayers: number, awayPlayers: number): number[] {
  return [1, 2, 3].filter((size) => size <= Math.min(homePlayers, awayPlayers))
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

/** The regular sets (not the ace decider) each player is placed in on the sheet: the games they play. */
export function gamesByPlayer(sheet: readonly EffectiveEntry[]): Map<number, number> {
  const games = new Map<number, number>()
  for (const set of sheet) {
    if (set.aceDecider) {
      continue
    }
    for (const playerId of [...set.homePlayerIds, ...set.awayPlayerIds]) {
      if (typeof playerId === 'number') {
        games.set(playerId, (games.get(playerId) ?? 0) + 1)
      }
    }
  }
  return games
}

// A team's places in the regular sets shared out evenly among its players: 7 rounds of two 2:2 sets
// are 28 places, 4 games each for 7 players. low and high differ when the places do not share out
// evenly (26 places for 7 players: 3 or 4 games each).
export type TeamGameTarget = {
  rounds: number
  places: number
  filled: number
  low: number
  high: number
}

/** The games each player of a team plays when its places are shared out evenly; null without players. */
export function teamGameTarget(sheet: readonly EffectiveEntry[], team: TeamSide, players: number): TeamGameTarget | null {
  if (players <= 0) {
    return null
  }
  const regular = sheet.filter((set) => !set.aceDecider)
  const sides = regular.map((set) => (team === 'HOME' ? set.homePlayerIds : set.awayPlayerIds))
  const places = sides.reduce((sum, side) => sum + side.length, 0)
  return {
    rounds: new Set(regular.map((set) => set.roundNumber)).size,
    places,
    filled: sides.reduce((sum, side) => sum + side.filter((playerId) => typeof playerId === 'number').length, 0),
    low: Math.floor(places / players),
    high: Math.ceil(places / players),
  }
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

/** Where a set was played: its round and set number. */
export type SetPlace = { round: number; set: number }

/** A group of a team's players playing a set together again, after a set of an earlier round. */
export type RepeatedSetGroup = {
  team: TeamSide
  playerIds: number[]
  first: SetPlace
  again: SetPlace
}

/**
 * For each team on its own: a group of its players (2:2 and 3:3 sets) who play a set together again
 * in a later round, in whichever set, after playing one together in an earlier round. The regular
 * draft is for everyone to play together across the tiers, so the page asks for groups that change
 * from round to round; it only says so and stops nothing. A 1:1 set has no teammates and is left
 * out, as are the ace decider, a set with a slot still empty, and a side not shown to the reader.
 */
export function repeatedSetGroups(sheet: readonly EffectiveEntry[]): RepeatedSetGroup[] {
  const sets = sheet
    .filter((set) => !set.aceDecider && set.teamSize >= 2)
    .sort((left, right) => left.roundNumber - right.roundNumber || left.setNumber - right.setNumber)
  const repeats: RepeatedSetGroup[] = []
  for (const team of ['HOME', 'AWAY'] as const) {
    const firstPlayed = new Map<string, SetPlace>()
    for (const set of sets) {
      const side = team === 'HOME' ? set.homePlayerIds : set.awayPlayerIds
      if (side.length < 2 || side.some((playerId) => typeof playerId !== 'number')) {
        continue
      }
      const playerIds = [...(side as number[])].sort((left, right) => left - right)
      const group = playerIds.join(',')
      const place = { round: set.roundNumber, set: set.setNumber }
      const first = firstPlayed.get(group)
      if (first === undefined) {
        firstPlayed.set(group, place)
      } else if (first.round !== place.round) {
        repeats.push({ team, playerIds, first, again: place })
      }
    }
  }
  return repeats
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

/** The layout and number of regular rounds of a draft already started, read back from its sets. */
export function draftLayout(entries: CaptainDraftEntry[]): { setSizes: number[]; roundCount: number } {
  const regular = entries.filter((entry) => !entry.aceDecider)
  const rounds = new Set(regular.map((entry) => entry.roundNumber))
  const firstRound = Math.min(...rounds)
  return {
    setSizes: regular
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

// The compositions the clan plays, the ones a set is offered. The operator cut the list down to
// these; a set saved with another one keeps it (the sheet adds the current one to its options).
const COMPOSITIONS: Record<number, string[]> = {
  3: ['PPP', 'PPT', 'PPZ', 'PTZ', 'PPR', 'PRR', 'RRR'],
  2: ['PP', 'PT', 'PZ', 'TZ', 'PR', 'RR'],
  1: ['P', 'T', 'Z', 'R'],
}

/** The race compositions offered for a set of this size, the usual ones and random ones. */
export function compositionsOfSize(size: number): string[] {
  return [...(COMPOSITIONS[size] ?? [])]
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

/**
 * Whether the attendance or the captains on the screen are something other than the draft shown
 * (any at all, when none is shown): typed for the next draft and not saved anywhere yet, so
 * loading a draft over them loses them.
 */
export function setupDiffersFromDraft(
  shown: Pick<CaptainDraftResponse, 'participants' | 'homeCaptainPlayerId' | 'awayCaptainPlayerId'> | null,
  selectedIds: readonly number[],
  homeCaptainId: number | null,
  awayCaptainId: number | null,
): boolean {
  if (!shown) {
    return selectedIds.length > 0 || homeCaptainId !== null || awayCaptainId !== null
  }
  const drafted = new Set(
    shown.participants.map((participant) => participant.playerId).filter((id): id is number => typeof id === 'number'),
  )
  const selected = new Set(selectedIds)
  return (
    drafted.size !== selected.size ||
    [...selected].some((id) => !drafted.has(id)) ||
    homeCaptainId !== shown.homeCaptainPlayerId ||
    awayCaptainId !== shown.awayCaptainPlayerId
  )
}

/**
 * Draft players in the order a captain picks by: captains first, then the highest tier (live tier
 * as on the tier board) down to unassigned, then those no longer on the roster; by nickname within
 * a tier.
 */
export function sortByTier<T extends { playerId: number | null; nickname: string; captain: boolean }>(
  participants: readonly T[],
  playerMap: ReadonlyMap<number, PlayerRosterItem>,
): T[] {
  const rank = (participant: T): number => {
    const player = typeof participant.playerId === 'number' ? playerMap.get(participant.playerId) : undefined
    return player ? toTierOrder(player.liveTier ?? player.tier ?? 'UNASSIGNED') : Number.MAX_SAFE_INTEGER
  }
  return [...participants].sort(
    (left, right) =>
      Number(right.captain) - Number(left.captain) ||
      rank(left) - rank(right) ||
      left.nickname.localeCompare(right.nickname, 'ko'),
  )
}

/**
 * A player in a select: the nickname with their tier and races, so a set can be filled by race, and
 * the games they already play when given, so the games can be shared out evenly.
 */
export function draftPlayerOptionLabel(nickname: string, tags: DraftPlayerTags, games?: number): string {
  const played = games === undefined ? null : t('captainDraft.games.count', { count: games })
  return [nickname, tags.tier, tags.races, played].filter((part): part is string => Boolean(part)).join(' · ')
}

export type DraftRecordState = 'drafting' | 'playing' | 'finished'

/** Where a recorded draft stands: still picking teams, playing (some sets left), or every set decided. */
export function draftRecordState(summary: CaptainDraftSummary): DraftRecordState {
  if (summary.status !== 'READY') {
    return 'drafting'
  }
  return summary.totalSets > 0 && summary.decidedSets === summary.totalSets ? 'finished' : 'playing'
}

const KST_MONTH_DAY = new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', month: 'numeric', day: 'numeric' })

/** The title a new draft starts with, by the Korean date: "10월 18일 정기 감전". */
export function defaultDraftTitle(now: Date = new Date()): string {
  const parts = KST_MONTH_DAY.formatToParts(now)
  const month = parts.find((part) => part.type === 'month')?.value ?? ''
  const day = parts.find((part) => part.type === 'day')?.value ?? ''
  return t('captainDraft.records.defaultTitle', { month, day })
}
