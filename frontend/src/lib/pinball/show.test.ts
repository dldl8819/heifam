import { describe, expect, it } from 'vitest'
import { createCourse } from './course'
import { createWorld, runToEnd, stepWorld, type World } from './engine'
import { DRAMA_DISTANCE, approach, decidingPlace, focusOf, isDecided, placeOfArrival, standings } from './show'

const course = createCourse()

function worldWith(positions: Array<number | null>): World {
  const world = createWorld(course, positions.length, 1)
  positions.forEach((y, index) => {
    if (y === null) {
      world.balls[index].finished = true
      world.finishedOrder.push(index)
    } else {
      world.balls[index].y = y
    }
  })
  return world
}

describe('watching a pinball draw', () => {
  it('follows the ball furthest down, at normal speed while far from the goal', () => {
    const world = worldWith([400, 900, 650])

    expect(focusOf(world, 1, 'FIRST')).toEqual({ ballId: 1, zoom: 1, timeScale: 1, dramatic: false })
  })

  it('closes in and slows down when the leader may take the first place', () => {
    const world = worldWith([400, course.goalY - DRAMA_DISTANCE / 2, 650])
    const focus = focusOf(world, 1, 'FIRST')

    expect(focus.ballId).toBe(1)
    expect(focus.dramatic).toBe(true)
    expect(focus.zoom).toBeGreaterThan(1)
    expect(focus.timeScale).toBeLessThan(1)
  })

  it('stops the drama once the places are filled', () => {
    const world = worldWith([null, course.goalY - 50, 650])

    expect(isDecided(world, 1, 'FIRST')).toBe(true)
    expect(focusOf(world, 1, 'FIRST').dramatic).toBe(false)
  })

  it('with the last ones winning, saves the drama for when only they and one more are left', () => {
    const near = course.goalY - 40
    expect(focusOf(worldWith([near, 500, 600, 700]), 1, 'LAST').dramatic).toBe(false)
    expect(focusOf(worldWith([null, null, near, 700]), 1, 'LAST').dramatic).toBe(true)
    // The one ball left has won already.
    expect(focusOf(worldWith([null, null, null, near]), 1, 'LAST').dramatic).toBe(false)
  })

  it('names the place the next arrival decides', () => {
    expect(decidingPlace(worldWith([null, 500, 600]), 2, 'FIRST')).toBe(2)
    // With LAST: three left for two places, the next one in only settles who wins.
    expect(decidingPlace(worldWith([null, 500, 600, 700]), 2, 'LAST')).toBeNull()
    expect(decidingPlace(worldWith([null, null, 600, 700]), 2, 'LAST')).toBe(2)
  })

  it('tells which arrivals won which place', () => {
    expect([0, 1, 2, 3].map((index) => placeOfArrival(index, 4, 2, 'FIRST'))).toEqual([1, 2, null, null])
    expect([0, 1, 2, 3].map((index) => placeOfArrival(index, 4, 2, 'LAST'))).toEqual([null, null, 2, 1])
  })

  it('ranks the running balls nearest the goal first, or with LAST furthest first', () => {
    const world = worldWith([400, null, 900, 650])

    expect(standings(world, 'FIRST', 2)).toEqual([2, 3])
    expect(standings(world, 'LAST', 5)).toEqual([0, 3, 2])
  })

  it('never changes how a run ends', () => {
    const watched = createWorld(course, 12, 77)
    while (!isDecided(watched, 12, 'LAST')) {
      focusOf(watched, 3, 'FIRST')
      standings(watched, 'FIRST', 5)
      stepWorld(watched)
    }
    const unwatched = createWorld(course, 12, 77)
    runToEnd(unwatched)

    expect(watched.finishedOrder).toEqual(unwatched.finishedOrder)
  })

  it('eases a value towards its target', () => {
    expect(approach(0, 10, 0.5)).toBe(5)
    expect(approach(10, 10, 0.1)).toBe(10)
  })
})
