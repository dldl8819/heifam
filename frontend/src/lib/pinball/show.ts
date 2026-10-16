/**
 * How a pinball draw is shown, apart from its physics: which ball the camera follows, how close it
 * goes and how slow the playback runs at the moment that decides the draw, and who leads. None of
 * it changes the run; the same seed ends the same however it is watched.
 */

import type { DrawMode, World } from './engine'

// The last stretch before the goal, where a deciding arrival is played slow and close up.
export const DRAMA_DISTANCE = 260
const DRAMA_ZOOM = 1.8
const DRAMA_TIME_SCALE = 0.3

export type Focus = {
  // The ball the camera follows; null before the start or once every ball is in.
  ballId: number | null
  zoom: number
  timeScale: number
  // True while the arrival that decides the draw may be about to happen.
  dramatic: boolean
}

/** Whether enough balls are in for the winners to be known. */
export function isDecided(world: World, winnerCount: number, mode: DrawMode): boolean {
  return mode === 'FIRST'
    ? world.finishedOrder.length >= Math.min(winnerCount, world.balls.length)
    : world.finishedOrder.length >= world.balls.length
}

/**
 * The camera follows whichever running ball is furthest down. When that ball's arrival can decide
 * the draw and it is near the goal, the view closes in and the playback slows: with FIRST while
 * places are still open, with LAST once only the balls that will win and the next one are left
 * (and more than one, since a ball left alone has already won).
 */
export function focusOf(world: World, winnerCount: number, mode: DrawMode): Focus {
  let leader: { id: number; y: number } | null = null
  let running = 0
  for (const ball of world.balls) {
    if (ball.finished) {
      continue
    }
    running += 1
    if (!leader || ball.y > leader.y) {
      leader = { id: ball.id, y: ball.y }
    }
  }
  if (!leader || isDecided(world, winnerCount, mode)) {
    return { ballId: leader?.id ?? null, zoom: 1, timeScale: 1, dramatic: false }
  }
  const deciding = mode === 'FIRST' || (running >= 2 && running <= winnerCount + 1)
  const near = leader.y > world.course.goalY - DRAMA_DISTANCE
  return deciding && near
    ? { ballId: leader.id, zoom: DRAMA_ZOOM, timeScale: DRAMA_TIME_SCALE, dramatic: true }
    : { ballId: leader.id, zoom: 1, timeScale: 1, dramatic: false }
}

/**
 * The place the next arrival takes, while the drama is on: with FIRST the next open place; with
 * LAST the place of the last of the balls still running, or null while one more ball than there
 * are places is left, since then the next arrival only settles who the winners are.
 */
export function decidingPlace(world: World, winnerCount: number, mode: DrawMode): number | null {
  if (mode === 'FIRST') {
    return world.finishedOrder.length + 1
  }
  const running = world.balls.length - world.finishedOrder.length
  return running > winnerCount ? null : running
}

/** The place the ball that arrived at this index (0 for the first) won, or null for no place. */
export function placeOfArrival(arrivalIndex: number, ballCount: number, winnerCount: number, mode: DrawMode): number | null {
  const place = mode === 'FIRST' ? arrivalIndex + 1 : ballCount - arrivalIndex
  return place >= 1 && place <= winnerCount ? place : null
}

/**
 * The running balls that matter now, at most count of them: with FIRST the ones nearest the goal,
 * with LAST the ones furthest from it, since the last to arrive win.
 */
export function standings(world: World, mode: DrawMode, count: number): number[] {
  return world.balls
    .filter((ball) => !ball.finished)
    .sort((left, right) => (mode === 'FIRST' ? right.y - left.y : left.y - right.y))
    .slice(0, Math.max(0, count))
    .map((ball) => ball.id)
}

/** A value moved part of the way towards a target, for camera moves that ease instead of jumping. */
export function approach(current: number, target: number, rate: number): number {
  return current + (target - current) * rate
}
