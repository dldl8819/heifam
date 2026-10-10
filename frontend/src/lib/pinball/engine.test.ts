import { describe, expect, it } from 'vitest'
import { createCourse } from './course'
import {
  BALL_RADIUS,
  MAX_TICKS,
  TICKS_PER_SECOND,
  createRandom,
  createWorld,
  isWorldDone,
  runToEnd,
  shuffledIndexes,
  stepWorld,
  winnersOf,
} from './engine'

const course = createCourse()

function run(ballCount: number, seed: number) {
  const world = createWorld(course, ballCount, seed)
  runToEnd(world)
  return world
}

describe('a draw on the course', () => {
  it('brings every ball to the goal, each exactly once', () => {
    for (const ballCount of [1, 2, 7, 30, 100]) {
      const world = run(ballCount, 1000 + ballCount)

      expect(isWorldDone(world)).toBe(true)
      expect([...world.finishedOrder].sort((left, right) => left - right)).toEqual(
        Array.from({ length: ballCount }, (_, index) => index)
      )
    }
  })

  it('never has to be ended by force, whatever the seed and however many balls', () => {
    const slowest: number[] = []
    for (let seed = 1; seed <= 12; seed += 1) {
      for (const ballCount of [2, 20, 100]) {
        const world = run(ballCount, seed * 7919 + ballCount)

        expect(world.forced).toBe(false)
        slowest.push(world.tick)
      }
    }
    // The last ball is in well before the time limit: nothing on the course holds a ball back.
    expect(Math.max(...slowest)).toBeLessThan(MAX_TICKS / 2)
  })

  it('takes long enough to watch and not long enough to bore', () => {
    const few = run(5, 42)
    const many = run(100, 42)

    expect(few.tick / TICKS_PER_SECOND).toBeGreaterThan(8)
    expect(few.tick / TICKS_PER_SECOND).toBeLessThan(60)
    expect(many.tick / TICKS_PER_SECOND).toBeLessThan(90)
  })

  it('needs hardly any pushes to keep the balls moving', () => {
    let kicks = 0
    let balls = 0
    for (let seed = 1; seed <= 8; seed += 1) {
      const world = run(60, seed * 104729)
      kicks += world.kicks
      balls += 60
    }

    // Balls waiting their turn at a funnel are pushed now and then; more than that would mean a trap.
    expect(kicks / balls).toBeLessThan(3)
  })

  it('keeps every ball on the course', () => {
    const world = createWorld(course, 40, 77)
    while (!isWorldDone(world)) {
      stepWorld(world)
      if (world.tick % 30 === 0) {
        for (const ball of world.balls) {
          expect(Number.isFinite(ball.x) && Number.isFinite(ball.y)).toBe(true)
          expect(ball.x).toBeGreaterThanOrEqual(BALL_RADIUS - 0.001)
          expect(ball.x).toBeLessThanOrEqual(course.width - BALL_RADIUS + 0.001)
        }
      }
    }
  })

  it('plays the same for the same seed and differently for another', () => {
    expect(run(25, 2026).finishedOrder).toEqual(run(25, 2026).finishedOrder)
    expect(run(25, 2026).finishedOrder).not.toEqual(run(25, 2027).finishedOrder)
  })

  it('gives every entrant about the same chance of winning', () => {
    const wins = new Array<number>(6).fill(0)
    const runs = 240
    for (let seed = 1; seed <= runs; seed += 1) {
      wins[run(6, seed * 31337).finishedOrder[0]] += 1
    }

    // 40 each on average; far outside this would mean a place in the list is favoured.
    for (const count of wins) {
      expect(count).toBeGreaterThan(18)
      expect(count).toBeLessThan(66)
    }
  })
})

describe('shuffledIndexes', () => {
  it('returns every index once', () => {
    const order = shuffledIndexes(50, createRandom(5))

    expect([...order].sort((left, right) => left - right)).toEqual(Array.from({ length: 50 }, (_, index) => index))
    expect(order).not.toEqual(Array.from({ length: 50 }, (_, index) => index))
  })

  it('puts each index in each place about equally often', () => {
    const firstPlace = new Array<number>(4).fill(0)
    const random = createRandom(99)
    for (let round = 0; round < 4000; round += 1) {
      firstPlace[shuffledIndexes(4, random)[0]] += 1
    }

    for (const count of firstPlace) {
      expect(count).toBeGreaterThan(880)
      expect(count).toBeLessThan(1120)
    }
  })
})

describe('winnersOf', () => {
  const arrivals = [4, 0, 3, 1, 2]

  it('takes the first to arrive, in order', () => {
    expect(winnersOf(arrivals, 1, 'FIRST')).toEqual([4])
    expect(winnersOf(arrivals, 3, 'FIRST')).toEqual([4, 0, 3])
  })

  it('takes the last to arrive when the last one wins, the very last first', () => {
    expect(winnersOf(arrivals, 1, 'LAST')).toEqual([2])
    expect(winnersOf(arrivals, 3, 'LAST')).toEqual([2, 1, 3])
  })

  it('fills no more places than balls ran, and none when none are asked for', () => {
    expect(winnersOf([1, 0], 5, 'FIRST')).toEqual([1, 0])
    expect(winnersOf(arrivals, 0, 'LAST')).toEqual([])
  })
})
