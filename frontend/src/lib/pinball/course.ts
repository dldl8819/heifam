import type { Course, Peg, Spinner, Wall } from './engine'

const WIDTH = 420
const PEG_RADIUS = 5
const PEG_BOUNCE = 0.5
const BUMPER_RADIUS = 13
// A bumper throws a ball back harder than it came; the speed limit keeps that in bounds.
const BUMPER_BOUNCE = 1.15
// Everything under the windmills sits this much lower than it did before they were added.
const WINDMILL_ZONE = 320

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

/** Two bars crossing at their middles and turning together. */
function windmill(x: number, y: number, halfLength: number, speed: number, phase: number): Spinner[] {
  return [
    { x, y, halfLength, speed, phase },
    { x, y, halfLength, speed, phase: phase + Math.PI / 2 },
  ]
}

/**
 * The course of the prize draw, top to bottom: a field of pegs, a funnel onto a spinning bar,
 * shelves that send the balls from side to side, three windmills among a few pegs, a field of
 * bumpers with two more bars, and a last funnel into the chute that ends at the goal. Nothing in it
 * is level and no gap is narrow enough for balls to wedge in, so every ball gets through.
 */
export function createCourse(): Course {
  const low = WINDMILL_ZONE
  const pegs: Peg[] = [...pegField(310, 12, 50), ...bumperField(1790 + low, 8, 90)]
  // Pegs around the windmills, so no ball drops between them untouched.
  for (const [x, y] of [[40, 1790], [210, 1785], [380, 1790], [60, 1960], [360, 1960], [150, 2045], [270, 2045]] as const) {
    pegs.push({ x, y, r: PEG_RADIUS, bounce: PEG_BOUNCE })
  }
  // A few pegs in the chute, so the last stretch is not a straight fall.
  for (const [x, y] of [[196, 2790 + low], [224, 2830 + low], [196, 2870 + low]] as const) {
    pegs.push({ x, y, r: 3, bounce: PEG_BOUNCE })
  }

  const walls: Wall[] = [
    ...funnel(940, 1060, 120),
    ...zigzag(1220, 4, 130, 80, 115),
    ...funnel(2560 + low, 2740 + low, 64),
    // The chute under the last funnel.
    { x1: 178, y1: 2740 + low, x2: 178, y2: 2920 + low },
    { x1: 242, y1: 2740 + low, x2: 242, y2: 2920 + low },
  ]

  const spinners: Spinner[] = [
    { x: 210, y: 1130, halfLength: 55, speed: 0.035, phase: 0 },
    // The windmills: two turning against each other, then one in the middle under them.
    ...windmill(115, 1870, 50, 0.03, 0),
    ...windmill(305, 1870, 50, -0.03, 0.4),
    ...windmill(210, 1985, 42, 0.04, 0.2),
    { x: 115, y: 2530 + low, halfLength: 42, speed: -0.04, phase: 0.6 },
    { x: 305, y: 2530 + low, halfLength: 42, speed: 0.04, phase: 0 },
    // Stirs the mouth of the last funnel, where the balls queue.
    { x: 210, y: 2672 + low, halfLength: 22, speed: 0.05, phase: 0.3 },
  ]

  return { width: WIDTH, height: 2960 + low, goalY: 2910 + low, startY: 240, pegs, walls, spinners }
}
