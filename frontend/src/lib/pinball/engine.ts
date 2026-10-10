/**
 * The physics of the prize draw: balls falling through a course of pegs, walls and spinning bars
 * to a goal line. It knows nothing of names or of drawing on a screen; it only says in which order
 * the balls arrive. It runs in fixed ticks, so a draw plays the same at any frame rate and can be
 * run to its end in one go. One seed gives one run.
 */

export const BALL_RADIUS = 7
export const TICKS_PER_SECOND = 120
/** After this long a draw is ended by force, the balls still on the course ranked by how far they got. */
export const MAX_TICKS = TICKS_PER_SECOND * 180

const GRAVITY = 0.05
// Under a ball's radius per tick, so a ball never passes through a wall between two ticks.
const MAX_SPEED = 5.5
const DRAG = 0.9992
const BALL_BOUNCE = 0.55
const WALL_BOUNCE = 0.4
const WALL_HALF_THICKNESS = 1.5
const SIDE_BOUNCE = 0.5
// Obstacles and balls are looked up by horizontal bands of this height.
const BAND_HEIGHT = 64
// A ball that has not come this much closer to the goal in this many ticks is given a push.
const STUCK_TICKS = TICKS_PER_SECOND * 2
const STUCK_PROGRESS = 4

export type Peg = { x: number; y: number; r: number; bounce: number }
export type Wall = { x1: number; y1: number; x2: number; y2: number }
/** A bar turning about its middle; speed in radians per tick, negative for the other way round. */
export type Spinner = { x: number; y: number; halfLength: number; speed: number; phase: number }

export type Course = {
  width: number
  height: number
  /** A ball below this line has arrived. */
  goalY: number
  /** Where the first row of balls starts. */
  startY: number
  pegs: Peg[]
  walls: Wall[]
  spinners: Spinner[]
}

export type Ball = {
  id: number
  x: number
  y: number
  vx: number
  vy: number
  finished: boolean
  // For telling a stuck ball: where it was at the last check.
  checkY: number
}

export type World = {
  course: Course
  balls: Ball[]
  tick: number
  /** Ball ids in the order they arrived. */
  finishedOrder: number[]
  /** True when the draw had to be ended at MAX_TICKS instead of every ball arriving. */
  forced: boolean
  /** How many pushes stuck balls were given; a healthy course needs few. */
  kicks: number
  random: () => number
  pegBands: number[][]
  wallBands: number[][]
}

/** A small seeded generator (mulberry32): the same seed gives the same run. */
export function createRandom(seed: number): () => number {
  let state = seed >>> 0
  return () => {
    state = (state + 0x6d2b79f5) >>> 0
    let mixed = state
    mixed = Math.imul(mixed ^ (mixed >>> 15), mixed | 1)
    mixed ^= mixed + Math.imul(mixed ^ (mixed >>> 7), mixed | 61)
    return ((mixed ^ (mixed >>> 14)) >>> 0) / 4294967296
  }
}

/** The numbers 0..count-1 in a random order, every order equally likely. */
export function shuffledIndexes(count: number, random: () => number): number[] {
  const order = Array.from({ length: count }, (_, index) => index)
  for (let index = count - 1; index > 0; index -= 1) {
    const other = Math.floor(random() * (index + 1))
    const kept = order[index]
    order[index] = order[other]
    order[other] = kept
  }
  return order
}

function bandOf(y: number): number {
  return Math.max(0, Math.floor(y / BAND_HEIGHT))
}

function bandsFor(course: Course): { pegBands: number[][]; wallBands: number[][] } {
  const count = bandOf(course.height) + 2
  const pegBands: number[][] = Array.from({ length: count }, () => [])
  const wallBands: number[][] = Array.from({ length: count }, () => [])
  const reach = BALL_RADIUS + WALL_HALF_THICKNESS
  course.pegs.forEach((peg, index) => {
    for (let band = bandOf(peg.y - peg.r - reach); band <= Math.min(count - 1, bandOf(peg.y + peg.r + reach)); band += 1) {
      pegBands[band].push(index)
    }
  })
  course.walls.forEach((wall, index) => {
    const top = Math.min(wall.y1, wall.y2) - reach
    const bottom = Math.max(wall.y1, wall.y2) + reach
    for (let band = bandOf(top); band <= Math.min(count - 1, bandOf(bottom)); band += 1) {
      wallBands[band].push(index)
    }
  })
  return { pegBands, wallBands }
}

/**
 * The balls at the top of the course, in rows. Which ball gets which place is shuffled, so no
 * entrant is favoured by where the list put them.
 */
export function createWorld(course: Course, ballCount: number, seed: number): World {
  const random = createRandom(seed)
  const columns = Math.max(1, Math.min(10, ballCount))
  const spacing = Math.min(36, (course.width - 4 * BALL_RADIUS) / columns)
  const left = (course.width - spacing * (columns - 1)) / 2
  const slots = shuffledIndexes(ballCount, random)
  const balls: Ball[] = []
  for (let id = 0; id < ballCount; id += 1) {
    const slot = slots[id]
    const row = Math.floor(slot / columns)
    const column = slot % columns
    // Odd rows sit half a step aside, and every ball a hair off its place, so none falls dead straight.
    const x = left + column * spacing + (row % 2 === 1 ? spacing / 2 - BALL_RADIUS : 0) + (random() - 0.5) * 2
    const y = course.startY - row * (BALL_RADIUS * 2 + 6)
    balls.push({ id, x: Math.min(course.width - BALL_RADIUS, Math.max(BALL_RADIUS, x)), y, vx: (random() - 0.5) * 0.6, vy: 0, finished: false, checkY: y })
  }
  return { course, balls, tick: 0, finishedOrder: [], forced: false, kicks: 0, random, ...bandsFor(course) }
}

export function isWorldDone(world: World): boolean {
  return world.finishedOrder.length >= world.balls.length
}

/** Where the two ends of a spinner are at a tick. */
export function spinnerEnds(spinner: Spinner, tick: number): Wall {
  const angle = spinner.phase + spinner.speed * tick
  const dx = Math.cos(angle) * spinner.halfLength
  const dy = Math.sin(angle) * spinner.halfLength
  return { x1: spinner.x - dx, y1: spinner.y - dy, x2: spinner.x + dx, y2: spinner.y + dy }
}

/**
 * Pushes the ball out of something it overlaps at (px, py) and turns its speed around, as seen
 * from a surface moving at (svx, svy). Returns whether they touched.
 */
function bounceOff(ball: Ball, px: number, py: number, minDistance: number, bounce: number, svx: number, svy: number): boolean {
  let dx = ball.x - px
  let dy = ball.y - py
  const squared = dx * dx + dy * dy
  if (squared >= minDistance * minDistance) {
    return false
  }
  let distance = Math.sqrt(squared)
  if (distance < 1e-6) {
    // Dead centre: there is no way out to prefer, so upwards.
    dx = 0
    dy = -1
    distance = 1
  }
  const nx = dx / distance
  const ny = dy / distance
  ball.x = px + nx * minDistance
  ball.y = py + ny * minDistance
  const approach = (ball.vx - svx) * nx + (ball.vy - svy) * ny
  if (approach < 0) {
    ball.vx -= (1 + bounce) * approach * nx
    ball.vy -= (1 + bounce) * approach * ny
  }
  return true
}

function bounceOffSegment(ball: Ball, wall: Wall, bounce: number, pivotX: number, pivotY: number, turn: number): boolean {
  const ex = wall.x2 - wall.x1
  const ey = wall.y2 - wall.y1
  const lengthSquared = ex * ex + ey * ey
  const along = lengthSquared === 0 ? 0 : Math.max(0, Math.min(1, ((ball.x - wall.x1) * ex + (ball.y - wall.y1) * ey) / lengthSquared))
  const px = wall.x1 + ex * along
  const py = wall.y1 + ey * along
  // A turning bar moves under the ball: the point touched has the speed of turning about the pivot.
  return bounceOff(ball, px, py, BALL_RADIUS + WALL_HALF_THICKNESS, bounce, -turn * (py - pivotY), turn * (px - pivotX))
}

function collideBalls(a: Ball, b: Ball): void {
  let dx = b.x - a.x
  let dy = b.y - a.y
  const squared = dx * dx + dy * dy
  const reach = BALL_RADIUS * 2
  if (squared >= reach * reach) {
    return
  }
  let distance = Math.sqrt(squared)
  if (distance < 1e-6) {
    dx = 1
    dy = 0
    distance = 1
  }
  const nx = dx / distance
  const ny = dy / distance
  const overlap = (reach - distance) / 2
  a.x -= nx * overlap
  a.y -= ny * overlap
  b.x += nx * overlap
  b.y += ny * overlap
  const approach = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny
  if (approach < 0) {
    const impulse = (-(1 + BALL_BOUNCE) * approach) / 2
    a.vx -= impulse * nx
    a.vy -= impulse * ny
    b.vx += impulse * nx
    b.vy += impulse * ny
  }
}

/** Moves the world on by one tick. */
export function stepWorld(world: World): void {
  if (isWorldDone(world)) {
    return
  }
  const { course, balls, random } = world
  world.tick += 1

  if (world.tick >= MAX_TICKS) {
    // Out of time: whoever is still on the course arrives now, the one furthest down first.
    balls
      .filter((ball) => !ball.finished)
      .sort((left, right) => right.y - left.y)
      .forEach((ball) => {
        ball.finished = true
        world.finishedOrder.push(ball.id)
      })
    world.forced = true
    return
  }

  const spinners = course.spinners.map((spinner) => ({ spinner, ends: spinnerEnds(spinner, world.tick) }))
  const ballBands = new Map<number, Ball[]>()

  for (const ball of balls) {
    if (ball.finished) {
      continue
    }
    ball.vy += GRAVITY
    ball.vx *= DRAG
    ball.vy *= DRAG
    const speed = Math.sqrt(ball.vx * ball.vx + ball.vy * ball.vy)
    if (speed > MAX_SPEED) {
      ball.vx = (ball.vx / speed) * MAX_SPEED
      ball.vy = (ball.vy / speed) * MAX_SPEED
    }
    ball.x += ball.vx
    ball.y += ball.vy

    const band = Math.min(world.pegBands.length - 1, bandOf(ball.y))
    for (const index of world.pegBands[band]) {
      const peg = course.pegs[index]
      if (bounceOff(ball, peg.x, peg.y, peg.r + BALL_RADIUS, peg.bounce, 0, 0)) {
        // A nudge to one side, so a ball never comes to rest balanced on a peg.
        ball.vx += (random() - 0.5) * 0.3
      }
    }
    for (const index of world.wallBands[band]) {
      bounceOffSegment(ball, course.walls[index], WALL_BOUNCE, 0, 0, 0)
    }
    for (const { spinner, ends } of spinners) {
      if (Math.abs(ball.y - spinner.y) <= spinner.halfLength + BALL_RADIUS + WALL_HALF_THICKNESS) {
        bounceOffSegment(ball, ends, WALL_BOUNCE, spinner.x, spinner.y, spinner.speed)
      }
    }

    if (ball.x < BALL_RADIUS) {
      ball.x = BALL_RADIUS
      ball.vx = Math.abs(ball.vx) * SIDE_BOUNCE
    } else if (ball.x > course.width - BALL_RADIUS) {
      ball.x = course.width - BALL_RADIUS
      ball.vx = -Math.abs(ball.vx) * SIDE_BOUNCE
    }

    if (ball.y > course.goalY) {
      ball.finished = true
      world.finishedOrder.push(ball.id)
      continue
    }

    const key = bandOf(ball.y)
    const sameBand = ballBands.get(key)
    if (sameBand) {
      sameBand.push(ball)
    } else {
      ballBands.set(key, [ball])
    }
  }

  // Balls against balls: only those in the same band or the next one down can touch.
  for (const [key, inBand] of ballBands) {
    const below = ballBands.get(key + 1)
    for (let first = 0; first < inBand.length; first += 1) {
      for (let second = first + 1; second < inBand.length; second += 1) {
        collideBalls(inBand[first], inBand[second])
      }
      if (below) {
        for (const other of below) {
          collideBalls(inBand[first], other)
        }
      }
    }
  }

  // A ball shoved by another must still be inside the side walls when the tick ends.
  for (const ball of balls) {
    if (!ball.finished) {
      ball.x = Math.min(course.width - BALL_RADIUS, Math.max(BALL_RADIUS, ball.x))
    }
  }

  if (world.tick % STUCK_TICKS === 0) {
    for (const ball of balls) {
      if (ball.finished) {
        continue
      }
      if (ball.y - ball.checkY < STUCK_PROGRESS) {
        // Going nowhere: knock it loose, sideways and a little up.
        ball.vx = (random() - 0.5) * 6
        ball.vy = -(1 + random() * 2)
        world.kicks += 1
      }
      ball.checkY = Math.max(ball.checkY, ball.y)
    }
  }
}

/** Runs the draw to its end without showing it. */
export function runToEnd(world: World): void {
  while (!isWorldDone(world)) {
    stepWorld(world)
  }
}

export type DrawMode = 'FIRST' | 'LAST'

/**
 * Who won, by place: with FIRST the first to arrive is first place; with LAST the last to arrive
 * is, the one before it second, and so on. Fewer places are filled when fewer balls ran.
 */
export function winnersOf(finishedOrder: number[], winnerCount: number, mode: DrawMode): number[] {
  const ranked = mode === 'LAST' ? [...finishedOrder].reverse() : finishedOrder
  return ranked.slice(0, Math.max(0, winnerCount))
}
