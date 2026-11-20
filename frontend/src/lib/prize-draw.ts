/** Small rules of a prize draw; the backend (PrizeDrawService) enforces the same limits on what is saved. */

import { kstDay } from '@/lib/menu-attendance'

export const PRIZE_DRAW_MIN_ENTRANTS = 2
export const PRIZE_DRAW_MAX_ENTRANTS = 100
export const PRIZE_DRAW_MAX_WINNERS = 10
export const PRIZE_DRAW_TITLE_MAX_LENGTH = 100
export const PRIZE_DRAW_NAME_MAX_LENGTH = 50
export const PRIZE_DRAW_PRIZE_MAX_LENGTH = 100

/** One ball: a name, and the roster player it belongs to when it was picked from the roster. */
export type DrawEntrant = {
  name: string
  playerId?: number
}

export type PrizeDrawProblem = {
  // The name of the message under "prizeDraw." that says what is wrong.
  key: 'titleRequired' | 'titleTooLong' | 'entrantsTooFew' | 'entrantsTooMany' | 'nameTooLong' | 'winnersInvalid' | 'prizeTooLong'
  min: number
  max: number
}

/** Names typed by hand: one a line, or several on a line with commas between them. */
export function parseManualEntrants(text: string): string[] {
  return text
    .split(/[\n,]/)
    .map((name) => name.trim())
    .filter((name) => name.length > 0)
}

/**
 * The balls of a draw: the roster players that were ticked, in roster order, then the names typed
 * by hand. A name that is already there, whatever its case, is not added again: everyone runs
 * with one ball.
 */
export function buildEntrants(
  players: { id: number; nickname: string }[],
  selectedPlayerIds: ReadonlySet<number>,
  manualText: string
): DrawEntrant[] {
  const entrants: DrawEntrant[] = []
  const taken = new Set<string>()
  const add = (name: string, playerId?: number) => {
    const key = name.trim().toLowerCase()
    if (key.length === 0 || taken.has(key)) {
      return
    }
    taken.add(key)
    entrants.push(playerId === undefined ? { name: name.trim() } : { name: name.trim(), playerId })
  }
  for (const player of players) {
    if (selectedPlayerIds.has(player.id)) {
      add(player.nickname, player.id)
    }
  }
  for (const name of parseManualEntrants(manualText)) {
    add(name)
  }
  return entrants
}

/**
 * The roster players of a regular draft (정기 감전), to tick at once: their ids in the order of the
 * draft, and how many of its players are not on the roster now (withdrawn, dormant, or shown
 * without an id) and so cannot be ticked.
 */
export function draftEntrantIds(
  participants: ReadonlyArray<{ playerId: number | null }>,
  roster: ReadonlyArray<{ id: number }>
): { ids: number[]; missing: number } {
  const onRoster = new Set(roster.map((player) => player.id))
  const ids: number[] = []
  let missing = 0
  for (const participant of participants) {
    if (participant.playerId !== null && onRoster.has(participant.playerId)) {
      if (!ids.includes(participant.playerId)) {
        ids.push(participant.playerId)
      }
    } else {
      missing += 1
    }
  }
  return { ids, missing }
}

/** A saved draw, as far as leaving its winners out of a later draw needs. */
export type PastDraw = {
  id: number
  createdAt: string
  winners: ReadonlyArray<{ name: string; playerId?: number | null }>
}

/**
 * The draws saved on the Korean day of `now`. Several draws are often run one after another (one a
 * sponsor, say), so all of the day's are left out together unless the member picks otherwise.
 */
export function drawIdsOfKstDay(draws: ReadonlyArray<Pick<PastDraw, 'id' | 'createdAt'>>, now: Date = new Date()): number[] {
  const today = kstDay(now)
  return draws
    .filter((draw) => {
      const savedAt = Date.parse(draw.createdAt)
      return !Number.isNaN(savedAt) && kstDay(new Date(savedAt)) === today
    })
    .map((draw) => draw.id)
}

/**
 * Who won one of the chosen draws. A roster player is a past winner picked as that same player,
 * whatever either was called then or now. A name typed by hand, in the past draw or in this one,
 * goes by the name, whatever its case. A roster player who only shares a name with a past winner
 * picked as another player is someone else, and stays in.
 */
export function pastWinnerMatcher(draws: ReadonlyArray<PastDraw>, drawIds: ReadonlySet<number>): (entrant: DrawEntrant) => boolean {
  const playerIds = new Set<number>()
  const typedNames = new Set<string>()
  const allNames = new Set<string>()
  for (const draw of draws) {
    if (!drawIds.has(draw.id)) {
      continue
    }
    for (const winner of draw.winners) {
      const name = winner.name.trim().toLowerCase()
      if (winner.playerId !== undefined && winner.playerId !== null) {
        playerIds.add(winner.playerId)
      } else {
        typedNames.add(name)
      }
      allNames.add(name)
    }
  }
  return (entrant) => {
    const name = entrant.name.trim().toLowerCase()
    return entrant.playerId === undefined ? allNames.has(name) : playerIds.has(entrant.playerId) || typedNames.has(name)
  }
}

/** The entrants who still run, and the past winners left out, each in the order they were put in. */
export function splitPastWinners(
  entrants: DrawEntrant[],
  isPastWinner: (entrant: DrawEntrant) => boolean
): { entrants: DrawEntrant[]; leftOut: DrawEntrant[] } {
  const running: DrawEntrant[] = []
  const leftOut: DrawEntrant[] = []
  for (const entrant of entrants) {
    if (isPastWinner(entrant)) {
      leftOut.push(entrant)
    } else {
      running.push(entrant)
    }
  }
  return { entrants: running, leftOut }
}

/** What stops a draw from being run, or null when it can be. */
export function validatePrizeDraw(title: string, entrants: DrawEntrant[], winnerCount: number, prizes: string[]): PrizeDrawProblem | null {
  const problem = (key: PrizeDrawProblem['key'], min: number, max: number): PrizeDrawProblem => ({ key, min, max })
  const cleanTitle = title.trim()
  if (cleanTitle.length === 0) {
    return problem('titleRequired', 1, PRIZE_DRAW_TITLE_MAX_LENGTH)
  }
  if (cleanTitle.length > PRIZE_DRAW_TITLE_MAX_LENGTH) {
    return problem('titleTooLong', 1, PRIZE_DRAW_TITLE_MAX_LENGTH)
  }
  if (entrants.length < PRIZE_DRAW_MIN_ENTRANTS) {
    return problem('entrantsTooFew', PRIZE_DRAW_MIN_ENTRANTS, PRIZE_DRAW_MAX_ENTRANTS)
  }
  if (entrants.length > PRIZE_DRAW_MAX_ENTRANTS) {
    return problem('entrantsTooMany', PRIZE_DRAW_MIN_ENTRANTS, PRIZE_DRAW_MAX_ENTRANTS)
  }
  if (entrants.some((entrant) => entrant.name.length > PRIZE_DRAW_NAME_MAX_LENGTH)) {
    return problem('nameTooLong', 1, PRIZE_DRAW_NAME_MAX_LENGTH)
  }
  // No more winners than balls ran, nor than a draw has places.
  if (!Number.isInteger(winnerCount) || winnerCount < 1 || winnerCount > Math.min(PRIZE_DRAW_MAX_WINNERS, entrants.length)) {
    return problem('winnersInvalid', 1, Math.min(PRIZE_DRAW_MAX_WINNERS, entrants.length))
  }
  if (prizes.some((prize) => prize.trim().length > PRIZE_DRAW_PRIZE_MAX_LENGTH)) {
    return problem('prizeTooLong', 0, PRIZE_DRAW_PRIZE_MAX_LENGTH)
  }
  return null
}

/** A colour for each ball, far from its neighbours' so that balls next to each other can be told apart. */
export function ballColor(index: number): string {
  return `hsl(${Math.round((index * 137.508) % 360)}, 78%, 60%)`
}

/** A name short enough to ride on a ball. */
export function ballLabel(name: string, maxLength = 7): string {
  const letters = Array.from(name.trim())
  return letters.length <= maxLength ? letters.join('') : `${letters.slice(0, maxLength - 1).join('')}…`
}

/** Minutes and seconds of a run, as "0:24". */
export function formatDrawClock(ticks: number, ticksPerSecond: number): string {
  const seconds = Math.max(0, Math.floor(ticks / ticksPerSecond))
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}
