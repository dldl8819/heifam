import { describe, expect, it } from 'vitest'
import {
  CANNON_MATCHUP_PRESET,
  cannonFlight,
  cannonLabelSize,
  drawCannonNumber,
  fireCannon,
  parseCannonItems,
  parseLastNumber,
  validateCannonItems,
} from './cannon-draw'

/** Hands out the given values in turn, as the browser's random source would. */
function source(...values: number[]) {
  let index = 0
  return () => values[index++]
}

describe('parseLastNumber', () => {
  it('takes a whole number from 2 to 999', () => {
    expect(parseLastNumber('50')).toBe(50)
    expect(parseLastNumber(' 4 ')).toBe(4)
    expect(parseLastNumber('2')).toBe(2)
    expect(parseLastNumber('999')).toBe(999)
  })

  it('takes nothing else', () => {
    for (const text of ['', '  ', '0', '1', '1000', '-4', '4.5', '1e2', 'abc', '4번']) {
      expect(parseLastNumber(text), text).toBeNull()
    }
  })
})

describe('the items to draw from', () => {
  it('reads one a line or several with commas, leaving blanks out and keeping the order written', () => {
    expect(parseCannonItems('PTZ\n PPP , PPZ\n\n ,  \nPPT')).toEqual(['PTZ', 'PPP', 'PPZ', 'PPT'])
    expect(parseCannonItems('   ')).toEqual([])
  })

  it('wants two to fifty items of up to twenty characters', () => {
    expect(validateCannonItems(['PPP'])).toBe('tooFew')
    expect(validateCannonItems([])).toBe('tooFew')
    expect(validateCannonItems(Array.from({ length: 51 }, (_, index) => `item${index}`))).toBe('tooMany')
    expect(validateCannonItems(['PPP', 'x'.repeat(21)])).toBe('tooLong')
    expect(validateCannonItems(['PPP', '가'.repeat(20)])).toBeNull()
    expect(validateCannonItems([...CANNON_MATCHUP_PRESET])).toBeNull()
  })
})

describe('drawCannonNumber', () => {
  it('gives a number from 1 to the last one', () => {
    expect(drawCannonNumber(4, source(0))).toBe(1)
    expect(drawCannonNumber(4, source(3))).toBe(4)
    expect(drawCannonNumber(4, source(6))).toBe(3)
    expect(drawCannonNumber(50, source(2 ** 32 - 1 - (2 ** 32 % 50)))).toBe(50)
  })

  it('throws back the values that would favour the low numbers', () => {
    // 2^32 leaves 46 over when shared among 50, so the top 46 values are drawn again.
    const top = 2 ** 32 - 1
    expect(drawCannonNumber(50, source(top, top - 45, 7))).toBe(8)
    expect(drawCannonNumber(50, source(top - 46))).toBe(50)
  })

  it('gives every number as often as the next over the whole random range', () => {
    const counts = new Map<number, number>()
    for (let value = 0; value < 3_000; value += 1) {
      const number = drawCannonNumber(3, source(value))
      counts.set(number, (counts.get(number) ?? 0) + 1)
    }
    expect([...counts.entries()].sort()).toEqual([[1, 1000], [2, 1000], [3, 1000]])
  })

  it("draws with the browser's own random source", () => {
    for (let round = 0; round < 200; round += 1) {
      const number = drawCannonNumber(50)
      expect(Number.isInteger(number) && number >= 1 && number <= 50).toBe(true)
    }
  })

  it('refuses a range it cannot draw from', () => {
    for (const last of [0, -1, 1.5, 1000, Number.NaN]) {
      expect(() => drawCannonNumber(last, source(0))).toThrow(RangeError)
    }
  })
})

describe('fireCannon', () => {
  it('draws from 1 to the last number', () => {
    expect(fireCannon(1, 'number', 50, [], source(16))).toEqual({ id: 1, number: 17, last: 50, label: null })
  })

  it('draws one of the items written down, in the order written, whatever the last number says', () => {
    const items = ['PTZ', 'PPP', 'PPZ']
    expect(fireCannon(2, 'items', 50, items, source(0))).toEqual({ id: 2, number: 1, last: 3, label: 'PTZ' })
    expect(fireCannon(3, 'items', 50, items, source(5))).toEqual({ id: 3, number: 3, last: 3, label: 'PPZ' })
  })
})

describe('cannonLabelSize', () => {
  it('writes a short name at full size and a long one smaller, never below 12', () => {
    expect(cannonLabelSize('PTZ')).toBe(30)
    expect(cannonLabelSize('테란')).toBe(30)
    expect(cannonLabelSize('투혼 맵 3:3')).toBeLessThan(30)
    expect(cannonLabelSize('가'.repeat(20))).toBe(12)
  })
})

describe('cannonFlight', () => {
  it('leaves the muzzle small and comes to rest at full size', () => {
    expect(cannonFlight(0)).toEqual({ rise: 0, scale: 0.15 })
    expect(cannonFlight(1)).toEqual({ rise: 1, scale: 1 })
    expect(cannonFlight(-1)).toEqual(cannonFlight(0))
    expect(cannonFlight(2)).toEqual(cannonFlight(1))
  })

  it('climbs all the way and swells a little past its size before settling', () => {
    let previous = -1
    for (let step = 0; step <= 100; step += 1) {
      const { rise } = cannonFlight(step / 100)
      expect(rise).toBeGreaterThanOrEqual(previous)
      previous = rise
    }
    expect(cannonFlight(0.8).scale).toBeCloseTo(1.1)
    expect(cannonFlight(0.9).scale).toBeCloseTo(1.05)
  })
})
