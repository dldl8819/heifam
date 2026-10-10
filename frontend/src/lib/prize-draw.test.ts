import { describe, expect, it } from 'vitest'
import {
  PRIZE_DRAW_MAX_ENTRANTS,
  PRIZE_DRAW_MAX_WINNERS,
  PRIZE_DRAW_NAME_MAX_LENGTH,
  PRIZE_DRAW_PRIZE_MAX_LENGTH,
  PRIZE_DRAW_TITLE_MAX_LENGTH,
  ballColor,
  ballLabel,
  buildEntrants,
  formatDrawClock,
  parseManualEntrants,
  validatePrizeDraw,
} from './prize-draw'

const ROSTER = [
  { id: 1, nickname: 'Alpha' },
  { id: 2, nickname: 'Bravo' },
  { id: 3, nickname: 'Charlie' },
]

describe('parseManualEntrants', () => {
  it('reads one name a line or several with commas between them', () => {
    expect(parseManualEntrants('Delta\n Echo ,Foxtrot\n\n ,  \nGolf')).toEqual(['Delta', 'Echo', 'Foxtrot', 'Golf'])
    expect(parseManualEntrants('   ')).toEqual([])
  })
})

describe('buildEntrants', () => {
  it('takes the ticked roster players in roster order, then the names typed', () => {
    expect(buildEntrants(ROSTER, new Set([3, 1]), 'Delta, Echo')).toEqual([
      { name: 'Alpha', playerId: 1 },
      { name: 'Charlie', playerId: 3 },
      { name: 'Delta' },
      { name: 'Echo' },
    ])
  })

  it('gives everyone one ball: a name that is already there is not added again', () => {
    expect(buildEntrants(ROSTER, new Set([1]), 'alpha, Delta, DELTA ,delta')).toEqual([
      { name: 'Alpha', playerId: 1 },
      { name: 'Delta' },
    ])
  })

  it('is empty when nothing is ticked or typed', () => {
    expect(buildEntrants(ROSTER, new Set(), '')).toEqual([])
  })
})

describe('validatePrizeDraw', () => {
  const entrants = buildEntrants(ROSTER, new Set([1, 2, 3]), '')

  it('takes a titled draw with two or more balls and at least one winner', () => {
    expect(validatePrizeDraw('October draw', entrants, 1, [''])).toBeNull()
    expect(validatePrizeDraw('October draw', entrants, 3, ['mouse', '', 'keyboard'])).toBeNull()
  })

  it('asks for a title of a sensible length', () => {
    expect(validatePrizeDraw('  ', entrants, 1, [])?.key).toBe('titleRequired')
    expect(validatePrizeDraw('x'.repeat(PRIZE_DRAW_TITLE_MAX_LENGTH + 1), entrants, 1, [])?.key).toBe('titleTooLong')
  })

  it('needs at least two balls and no more than the course takes', () => {
    expect(validatePrizeDraw('t', entrants.slice(0, 1), 1, [])?.key).toBe('entrantsTooFew')
    const crowd = Array.from({ length: PRIZE_DRAW_MAX_ENTRANTS + 1 }, (_, index) => ({ name: `name ${index}` }))
    expect(validatePrizeDraw('t', crowd, 1, [])?.key).toBe('entrantsTooMany')
    expect(validatePrizeDraw('t', crowd.slice(0, PRIZE_DRAW_MAX_ENTRANTS), 1, [])).toBeNull()
  })

  it('refuses a name too long to save', () => {
    expect(validatePrizeDraw('t', [...entrants, { name: 'x'.repeat(PRIZE_DRAW_NAME_MAX_LENGTH + 1) }], 1, [])?.key).toBe('nameTooLong')
  })

  it('takes no more winners than balls, nor than a draw has places', () => {
    expect(validatePrizeDraw('t', entrants, 0, [])?.key).toBe('winnersInvalid')
    expect(validatePrizeDraw('t', entrants, 4, [])).toEqual({ key: 'winnersInvalid', min: 1, max: 3 })
    expect(validatePrizeDraw('t', entrants, 1.5, [])?.key).toBe('winnersInvalid')
    const crowd = Array.from({ length: 40 }, (_, index) => ({ name: `name ${index}` }))
    expect(validatePrizeDraw('t', crowd, PRIZE_DRAW_MAX_WINNERS, [])).toBeNull()
    expect(validatePrizeDraw('t', crowd, PRIZE_DRAW_MAX_WINNERS + 1, [])?.max).toBe(PRIZE_DRAW_MAX_WINNERS)
  })

  it('refuses a prize name too long to save', () => {
    expect(validatePrizeDraw('t', entrants, 1, ['x'.repeat(PRIZE_DRAW_PRIZE_MAX_LENGTH + 1)])?.key).toBe('prizeTooLong')
  })
})

describe('what a ball shows', () => {
  it('gets a colour of its own', () => {
    const colours = Array.from({ length: 30 }, (_, index) => ballColor(index))

    expect(new Set(colours).size).toBe(30)
    expect(colours[0]).toMatch(/^hsl\(\d+, 78%, 60%\)$/)
  })

  it('carries a name cut to fit', () => {
    expect(ballLabel('Alpha')).toBe('Alpha')
    expect(ballLabel('  AlphaBravoCharlie ')).toBe('AlphaB…')
    expect(ballLabel('가나다라마바사아자')).toBe('가나다라마바…')
    expect(ballLabel('가나다라마바사')).toBe('가나다라마바사')
  })
})

describe('formatDrawClock', () => {
  it('shows minutes and seconds', () => {
    expect(formatDrawClock(0, 120)).toBe('0:00')
    expect(formatDrawClock(120 * 24 + 60, 120)).toBe('0:24')
    expect(formatDrawClock(120 * 75, 120)).toBe('1:15')
  })
})
