/**
 * The cannon draw: a number from 1 up to a last number, or one of a list of items written down for
 * the occasion, such as the race matchups of an ace match (에결). Nothing is kept anywhere. The
 * pick is made the moment the cannon fires; the flight only shows it.
 */

export const CANNON_MIN_LAST = 2
export const CANNON_MAX_LAST = 999
export const CANNON_DEFAULT_LAST = 50
export const CANNON_MAX_ITEMS = 50
export const CANNON_MAX_ITEM_LENGTH = 20
// Filled in by a button, to be kept, reordered or changed: the race matchups of an ace match.
export const CANNON_MATCHUP_PRESET = ['PPP', 'PPT', 'PPZ', 'PTZ'] as const
export const CANNON_FLIGHT_MS = 1400
export const CANNON_HISTORY_SIZE = 10

export type CannonMode = 'number' | 'items'

export type CannonShot = {
  id: number
  number: number
  last: number
  // The item the number stands for when drawing from a list; null when drawing a number.
  label: string | null
}

export type CannonItemsError = 'tooFew' | 'tooMany' | 'tooLong'

const UINT32_RANGE = 2 ** 32

/** The last number typed, or null when it is not a whole number from 2 to 999. */
export function parseLastNumber(text: string): number | null {
  const trimmed = text.trim()
  if (!/^\d{1,3}$/.test(trimmed)) {
    return null
  }
  const value = Number(trimmed)
  return value >= CANNON_MIN_LAST && value <= CANNON_MAX_LAST ? value : null
}

/** The items written down, one a line or several with commas between them, blanks left out. */
export function parseCannonItems(text: string): string[] {
  return text
    .split(/[\n,]/)
    .map((item) => item.trim())
    .filter((item) => item.length > 0)
}

/** What is wrong with a list to draw from, or null when it can be drawn from. */
export function validateCannonItems(items: readonly string[]): CannonItemsError | null {
  if (items.length < 2) {
    return 'tooFew'
  }
  if (items.length > CANNON_MAX_ITEMS) {
    return 'tooMany'
  }
  return items.some((item) => Array.from(item).length > CANNON_MAX_ITEM_LENGTH) ? 'tooLong' : null
}

function cryptoUint32(): number {
  const values = new Uint32Array(1)
  globalThis.crypto.getRandomValues(values)
  return values[0]
}

/**
 * A number from 1 to last, every one as likely as the next. The few values at the top of the 32-bit
 * range that would favour the low numbers are thrown back and drawn again.
 */
export function drawCannonNumber(last: number, nextUint32: () => number = cryptoUint32): number {
  if (!Number.isInteger(last) || last < 1 || last > CANNON_MAX_LAST) {
    throw new RangeError(`last must be a whole number from 1 to ${CANNON_MAX_LAST}`)
  }
  const limit = UINT32_RANGE - (UINT32_RANGE % last)
  for (;;) {
    const value = nextUint32()
    if (value < limit) {
      return (value % last) + 1
    }
  }
}

/** Fires once: a number from 1 to last, or with a list one of its items and its number. */
export function fireCannon(
  id: number,
  mode: CannonMode,
  last: number,
  items: readonly string[],
  nextUint32?: () => number
): CannonShot {
  if (mode === 'items') {
    const number = drawCannonNumber(items.length, nextUint32)
    return { id, number, last: items.length, label: items[number - 1] }
  }
  return { id, number: drawCannonNumber(last, nextUint32), last, label: null }
}

/**
 * How big an item's name can be written across the ball: up to 30, smaller for a long one so it
 * stays inside. Latin letters and digits take about 0.6 of the size in width, Hangul about 1.
 */
export function cannonLabelSize(label: string): number {
  const units = Array.from(label).reduce((total, character) => total + (/[\x20-\x7e]/.test(character) ? 0.62 : 1), 0)
  return Math.max(12, Math.min(30, 150 / Math.max(units, 1)))
}

/**
 * Where the ball is on its way up: rise goes from 0 at the muzzle to 1 at rest in the sky, slowing
 * as it climbs; scale goes from a small ball to a little past full size, then settles at 1.
 */
export function cannonFlight(progress: number): { rise: number; scale: number } {
  const p = Math.min(1, Math.max(0, progress))
  const rise = 1 - (1 - p) ** 3
  if (p < 0.8) {
    const grow = p / 0.8
    return { rise, scale: 0.15 + 0.95 * (1 - (1 - grow) ** 2) }
  }
  return { rise, scale: 1 + 0.5 * (1 - p) }
}
