import type { Course, Peg, Spinner, Wall } from './engine'

const WIDTH = 420
const PEG_RADIUS = 5
const PEG_BOUNCE = 0.5
const BUMPER_RADIUS = 13
const BUMPER_BOUNCE = 0.95

/** Rows of pegs, every other row set half a step aside, so a ball is turned left or right at each. */
function pegField(top: number, rows: number, rowGap: number): Peg[] {
  const pegs: Peg[] = []
  const step = 45
  for (let row = 0; row < rows; row += 1) {
    const shifted = row % 2 === 1
    for (let x = shifted ? 52.5 : 30; x <= WIDTH - 25; x += step) {
      pegs.push({ x, y: top + row * rowGap, r: PEG_RADIUS, bounce: PEG_BOUNCE })
    }
  }
  return pegs
}

/** Large pegs that throw a ball back almost as hard as it came. */
function bumperField(top: number, rows: number, rowGap: number): Peg[] {
  const pegs: Peg[] = []
  for (let row = 0; row < rows; row += 1) {
    for (const x of row % 2 === 0 ? [70, 210, 350] : [140, 280]) {
      pegs.push({ x, y: top + row * rowGap, r: BUMPER_RADIUS, bounce: BUMPER_BOUNCE })
    }
  }
  return pegs
}

/** Two walls leaning in towards a gap in the middle. */
function funnel(top: number, bottom: number, gap: number): Wall[] {
  const left = (WIDTH - gap) / 2
  return [
    { x1: 0, y1: top, x2: left, y2: bottom },
    { x1: WIDTH, y1: top, x2: WIDTH - left, y2: bottom },
  ]
}

/** Shelves sloping down from one side and then the other, each leaving a gap at its low end. */
function zigzag(top: number, count: number, rowGap: number, drop: number, gap: number): Wall[] {
  const walls: Wall[] = []
  for (let index = 0; index < count; index += 1) {
    const y = top + index * rowGap
    walls.push(
      index % 2 === 0
        ? { x1: 0, y1: y, x2: WIDTH - gap, y2: y + drop }
        : { x1: WIDTH, y1: y, x2: gap, y2: y + drop }
    )
  }
  return walls
}

/**
 * The course of the prize draw, top to bottom: a field of pegs, a funnel onto a spinning bar,
 * shelves that send the balls from side to side, a field of bumpers with two more bars, and a last
 * funnel into the chute that ends at the goal. Nothing in it is level and no gap is narrow enough
 * for balls to wedge in, so every ball gets through.
 */
export function createCourse(): Course {
  const pegs: Peg[] = [...pegField(310, 12, 50), ...bumperField(1790, 8, 90)]
  // A few pegs in the chute, so the last stretch is not a straight fall.
  for (const [x, y] of [[196, 2790], [224, 2830], [196, 2870]] as const) {
    pegs.push({ x, y, r: 3, bounce: PEG_BOUNCE })
  }

  const walls: Wall[] = [
    ...funnel(940, 1060, 120),
    ...zigzag(1220, 4, 130, 80, 115),
    ...funnel(2560, 2740, 64),
    // The chute under the last funnel.
    { x1: 178, y1: 2740, x2: 178, y2: 2920 },
    { x1: 242, y1: 2740, x2: 242, y2: 2920 },
  ]

  const spinners: Spinner[] = [
    { x: 210, y: 1130, halfLength: 55, speed: 0.035, phase: 0 },
    { x: 115, y: 2530, halfLength: 42, speed: -0.04, phase: 0.6 },
    { x: 305, y: 2530, halfLength: 42, speed: 0.04, phase: 0 },
    // Stirs the mouth of the last funnel, where the balls queue.
    { x: 210, y: 2672, halfLength: 22, speed: 0.05, phase: 0.3 },
  ]

  return { width: WIDTH, height: 2960, goalY: 2910, startY: 240, pegs, walls, spinners }
}
