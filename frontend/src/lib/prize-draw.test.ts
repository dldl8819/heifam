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
  draftEntrantIds,
  drawIdsOfKstDay,
  formatDrawClock,
  parseManualEntrants,
  pastWinnerMatcher,
  splitPastWinners,
  validatePrizeDraw,
  type PastDraw,
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

describe('draftEntrantIds', () => {
  it('ticks the draft\'s players who are on the roster, and counts the others', () => {
    expect(draftEntrantIds([{ playerId: 3 }, { playerId: 1 }, { playerId: 9 }, { playerId: null }, { playerId: 3 }], ROSTER)).toEqual({
      ids: [3, 1],
      missing: 2,
    })
    expect(draftEntrantIds([], ROSTER)).toEqual({ ids: [], missing: 0 })
  })
})

describe('drawIdsOfKstDay', () => {
  // 2026-11-21 10:00 in Korea.
  const now = new Date('2026-11-21T01:00:00Z')

  it('takes the draws saved on the same day in Korea, which starts at 15:00 UTC the day before', () => {
    const draws = [
      { id: 5, createdAt: '2026-11-21T00:30:00Z' },
      { id: 4, createdAt: '2026-11-20T15:00:00Z' },
      { id: 3, createdAt: '2026-11-20T14:59:59Z' },
      { id: 2, createdAt: '2026-11-21T15:00:00Z' },
      { id: 1, createdAt: 'not a time' },
    ]

    expect(drawIdsOfKstDay(draws, now)).toEqual([5, 4])
  })

  it('is empty when nothing was saved that day', () => {
    expect(drawIdsOfKstDay([], now)).toEqual([])
    expect(drawIdsOfKstDay([{ id: 1, createdAt: '2026-11-19T03:00:00Z' }], now)).toEqual([])
  })
})

describe('leaving past winners out', () => {
  const draws: PastDraw[] = [
    // Picked from the roster as Alpha (1), who is called something else now, and a name typed by hand.
    { id: 10, createdAt: '2026-11-21T01:00:00Z', winners: [{ name: 'OldAlpha', playerId: 1 }, { name: 'Delta', playerId: null }] },
    // Picked from the roster as player 4, who was called Bravo then.
    { id: 11, createdAt: '2026-11-21T02:00:00Z', winners: [{ name: 'Bravo', playerId: 4 }] },
    { id: 12, createdAt: '2026-11-20T02:00:00Z', winners: [{ name: 'Charlie', playerId: 3 }] },
  ]
  const entrants = buildEntrants(ROSTER, new Set([1, 2, 3]), 'delta, Echo')

  it('leaves out the winners of the chosen draws only', () => {
    const { entrants: running, leftOut } = splitPastWinners(entrants, pastWinnerMatcher(draws, new Set([10])))

    expect(leftOut).toEqual([{ name: 'Alpha', playerId: 1 }, { name: 'delta' }])
    expect(running).toEqual([{ name: 'Bravo', playerId: 2 }, { name: 'Charlie', playerId: 3 }, { name: 'Echo' }])
  })

  it('knows a roster winner by the player, whatever the name was', () => {
    const isPastWinner = pastWinnerMatcher(draws, new Set([10]))

    expect(isPastWinner({ name: 'Alpha', playerId: 1 })).toBe(true)
    expect(isPastWinner({ name: 'OldAlpha', playerId: 2 })).toBe(false)
  })

  it('goes by the name, whatever its case, when either side was typed by hand', () => {
    const isPastWinner = pastWinnerMatcher(draws, new Set([10, 11]))

    // Typed by hand in the past draw, picked from the roster now.
    expect(isPastWinner({ name: ' DELTA ', playerId: 7 })).toBe(true)
    // Picked from the roster in the past draw, typed by hand now.
    expect(isPastWinner({ name: 'bravo' })).toBe(true)
    expect(isPastWinner({ name: 'oldalpha' })).toBe(true)
    // Another roster player who only shares the name of a roster winner.
    expect(isPastWinner({ name: 'Bravo', playerId: 2 })).toBe(false)
  })

  it('leaves out the winners of several draws together', () => {
    const { entrants: running, leftOut } = splitPastWinners(entrants, pastWinnerMatcher(draws, new Set([10, 11, 12])))

    expect(leftOut.map((entrant) => entrant.name)).toEqual(['Alpha', 'Charlie', 'delta'])
    expect(running.map((entrant) => entrant.name)).toEqual(['Bravo', 'Echo'])
  })

  it('leaves everyone in when no draw is chosen, or a chosen one is gone', () => {
    expect(splitPastWinners(entrants, pastWinnerMatcher(draws, new Set())).entrants).toEqual(entrants)
    expect(splitPastWinners(entrants, pastWinnerMatcher(draws, new Set([99]))).leftOut).toEqual([])
  })

  it('can leave too few to run, which the setup then says', () => {
    const two = buildEntrants(ROSTER, new Set([1, 3]), '')
    const { entrants: running } = splitPastWinners(two, pastWinnerMatcher(draws, new Set([10])))

    expect(running).toEqual([{ name: 'Charlie', playerId: 3 }])
    expect(validatePrizeDraw('t', running, 1, [''])?.key).toBe('entrantsTooFew')
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
