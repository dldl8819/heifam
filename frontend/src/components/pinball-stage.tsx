'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { createCourse } from '@/lib/pinball/course'
import {
  BALL_RADIUS,
  SHOCKWAVE_RADIUS,
  TICKS_PER_SECOND,
  createWorld,
  spinnerEnds,
  stepWorld,
  winnersOf,
  type DrawMode,
  type World,
} from '@/lib/pinball/engine'
import { approach, decidingPlace, focusOf, isDecided, placeOfArrival, standings } from '@/lib/pinball/show'
import { ballColor, ballLabel, formatDrawClock, type DrawEntrant } from '@/lib/prize-draw'
import { t } from '@/lib/i18n'

type PinballStageProps = {
  entrants: DrawEntrant[]
  winnerCount: number
  mode: DrawMode
  // Called once, when the winners are decided, with the ball indexes in the order they arrived.
  onFinished: (finishedOrder: number[]) => void
}

type Status = 'ready' | 'countdown' | 'running' | 'done'

// Where the view looks: its centre on the course, how close, and how slow the run plays.
type Camera = { x: number; y: number; zoom: number; timeScale: number }
// A ball crossing the goal, lit up for a moment where it crossed, with the place it won if any.
type Arrival = { ballId: number; x: number; at: number; place: number | null }
type Confetti = { x: number; y: number; vx: number; vy: number; angle: number; spin: number; size: number; color: string }
type Trails = Map<number, Array<{ x: number; y: number }>>

const COURSE = createCourse()
const SPEEDS = [1, 2, 4] as const
// A frame that took this long (a hidden tab) is not caught up on all at once.
const MAX_TICKS_PER_FRAME = TICKS_PER_SECOND / 4
const VIEW_MAX_WIDTH = 460
// The count before the balls drop: three numbers, this long each, then "go" for a moment.
const COUNT_FROM = 3
const COUNT_MS = 700
const GO_MS = 600
// How long what happened on the course stays on the screen, in ticks of the run.
const PEG_FLASH_TICKS = 18
const SHOCKWAVE_SHOWN_TICKS = 60
const SHOCKWAVE_NOTICE_TICKS = 150
// How long an arrival stays lit up at the goal, in milliseconds of watching.
const ARRIVAL_SHOWN_MS = 1400
const TRAIL_LENGTH = 7
const STANDINGS_SHOWN = 5
const STANDINGS_EVERY_MS = 250
// The winner banner lists everyone in one column up to this many, in two beyond.
const BANNER_ONE_COLUMN = 5
const CONFETTI_COUNT = 140
const CONFETTI_GRAVITY = 900
const CONFETTI_COLORS = ['#f59e0b', '#34d399', '#38bdf8', '#f472b6', '#a78bfa', '#facc15', '#fb7185']

const controlClass =
  'rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800'

/** A seed nobody chose: the run it gives cannot be picked beforehand. */
function freshSeed(): number {
  const values = new Uint32Array(1)
  window.crypto.getRandomValues(values)
  return values[0]
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value))
}

function prefersReducedMotion(): boolean {
  return typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

function outlinedText(context: CanvasRenderingContext2D, text: string, x: number, y: number, fill: string, outline: number) {
  context.strokeStyle = 'rgba(15, 23, 42, 0.9)'
  context.lineWidth = outline
  context.strokeText(text, x, y)
  context.fillStyle = fill
  context.fillText(text, x, y)
}

type Effects = {
  now: number
  trails: Trails
  arrivals: Arrival[]
  // The ball the camera follows, ringed in gold.
  focusBallId: number | null
  // What is at stake while the deciding arrival may be about to happen; null otherwise.
  dramaLabel: string | null
  reducedMotion: boolean
}

function drawScene(
  context: CanvasRenderingContext2D,
  world: World,
  entrants: DrawEntrant[],
  width: number,
  height: number,
  camera: Camera,
  effects: Effects
) {
  const unit = (width / COURSE.width) * camera.zoom
  const viewWidth = width / unit
  const viewHeight = height / unit
  const left = camera.x - viewWidth / 2
  const top = camera.y - viewHeight / 2
  const bottom = top + viewHeight
  const visible = (y: number, margin = 40) => y >= top - margin && y <= bottom + margin

  context.save()
  context.fillStyle = '#0b1120'
  context.fillRect(0, 0, width, height)
  context.scale(unit, unit)
  context.translate(-left, -top)

  // Faint lines across the course, so the fall shows even where the course is empty.
  context.strokeStyle = 'rgba(148, 163, 184, 0.07)'
  context.lineWidth = 1
  context.beginPath()
  for (let y = Math.ceil(top / 100) * 100; y < bottom; y += 100) {
    context.moveTo(0, y)
    context.lineTo(COURSE.width, y)
  }
  context.stroke()

  // The goal: a chequered line like the end of a race, and the tray under it, brighter for a
  // moment after each arrival.
  if (visible(COURSE.goalY, 60)) {
    const lastArrival = effects.arrivals.length > 0 ? effects.arrivals[effects.arrivals.length - 1].at : -Infinity
    const glow = Math.max(0, 1 - (effects.now - lastArrival) / ARRIVAL_SHOWN_MS)
    context.fillStyle = `rgba(16, 185, 129, ${0.16 + glow * 0.32})`
    context.fillRect(0, COURSE.goalY, COURSE.width, COURSE.height - COURSE.goalY)
    const square = 10
    for (let column = 0; column * square < COURSE.width; column += 1) {
      for (let row = 0; row < 2; row += 1) {
        context.fillStyle = (column + row) % 2 === 0 ? '#f8fafc' : '#0f172a'
        context.fillRect(column * square, COURSE.goalY + row * (square / 2), square, square / 2)
      }
    }
    context.font = '900 22px system-ui, sans-serif'
    context.textAlign = 'center'
    context.textBaseline = 'middle'
    context.fillStyle = `rgba(248, 250, 252, ${0.22 + glow * 0.5})`
    context.fillText(t('prizeDraw.stage.goal'), COURSE.width / 2, (COURSE.goalY + 10 + COURSE.height) / 2)
  }

  context.lineCap = 'round'
  context.strokeStyle = '#94a3b8'
  context.lineWidth = 3
  context.beginPath()
  for (const wall of COURSE.walls) {
    if (visible(wall.y1) || visible(wall.y2) || (wall.y1 < top && wall.y2 > bottom)) {
      context.moveTo(wall.x1, wall.y1)
      context.lineTo(wall.x2, wall.y2)
    }
  }
  context.stroke()

  // Pegs and bumpers, lit for a moment when a ball hits them.
  for (let index = 0; index < COURSE.pegs.length; index += 1) {
    const peg = COURSE.pegs[index]
    if (!visible(peg.y)) {
      continue
    }
    const hit = world.pegHitTicks[index]
    const flash = hit < 0 ? 0 : Math.max(0, 1 - (world.tick - hit) / PEG_FLASH_TICKS)
    const bumper = peg.r > 8
    if (bumper && flash > 0) {
      context.fillStyle = `rgba(253, 224, 71, ${0.4 * flash})`
      context.beginPath()
      context.arc(peg.x, peg.y, peg.r + 3 + 7 * flash, 0, Math.PI * 2)
      context.fill()
    }
    context.fillStyle = flash > 0 ? (bumper ? '#fde047' : '#fef3c7') : bumper ? '#f59e0b' : '#cbd5e1'
    context.beginPath()
    context.arc(peg.x, peg.y, peg.r, 0, Math.PI * 2)
    context.fill()
    if (bumper) {
      context.strokeStyle = flash > 0 ? '#ffffff' : '#fde68a'
      context.lineWidth = 2
      context.stroke()
    }
  }

  context.strokeStyle = '#38bdf8'
  context.lineWidth = 5
  for (const spinner of COURSE.spinners) {
    if (!visible(spinner.y, spinner.halfLength + 40)) {
      continue
    }
    const ends = spinnerEnds(spinner, world.tick)
    context.beginPath()
    context.moveTo(ends.x1, ends.y1)
    context.lineTo(ends.x2, ends.y2)
    context.stroke()
  }
  context.fillStyle = '#e0f2fe'
  for (const spinner of COURSE.spinners) {
    if (visible(spinner.y)) {
      context.beginPath()
      context.arc(spinner.x, spinner.y, 4, 0, Math.PI * 2)
      context.fill()
    }
  }

  // Shockwaves: a ring spreading from where it went off, in the colour of the ball that set it off.
  context.textAlign = 'center'
  context.textBaseline = 'middle'
  context.lineJoin = 'round'
  for (const wave of world.shockwaves) {
    const age = world.tick - wave.tick
    if (age < 0 || age > SHOCKWAVE_SHOWN_TICKS || !visible(wave.y, SHOCKWAVE_RADIUS)) {
      continue
    }
    const progress = age / SHOCKWAVE_SHOWN_TICKS
    const fade = 1 - progress
    context.strokeStyle = ballColor(wave.ballId)
    context.globalAlpha = fade
    context.lineWidth = 1 + 3 * fade
    context.beginPath()
    context.arc(wave.x, wave.y, SHOCKWAVE_RADIUS * (0.25 + 0.75 * progress), 0, Math.PI * 2)
    context.stroke()
    context.globalAlpha = fade * 0.45
    context.beginPath()
    context.arc(wave.x, wave.y, SHOCKWAVE_RADIUS * 0.6 * progress, 0, Math.PI * 2)
    context.stroke()
    context.globalAlpha = fade
    context.font = '800 13px system-ui, sans-serif'
    outlinedText(context, t('prizeDraw.stage.shockwave'), clamp(wave.x, 34, COURSE.width - 34), wave.y - 20 - progress * 16, '#fde047', 3)
  }
  context.globalAlpha = 1

  // A short fading trail behind each running ball.
  context.lineCap = 'round'
  for (const ball of world.balls) {
    const trail = effects.trails.get(ball.id)
    if (ball.finished || !trail || trail.length < 2 || !visible(ball.y)) {
      continue
    }
    context.strokeStyle = ballColor(ball.id)
    for (let index = 1; index < trail.length; index += 1) {
      const share = index / trail.length
      context.globalAlpha = share * 0.35
      context.lineWidth = BALL_RADIUS * 1.6 * share
      context.beginPath()
      context.moveTo(trail[index - 1].x, trail[index - 1].y)
      context.lineTo(trail[index].x, trail[index].y)
      context.stroke()
    }
  }
  context.globalAlpha = 1

  for (const ball of world.balls) {
    if (ball.finished || !visible(ball.y)) {
      continue
    }
    if (ball.id === effects.focusBallId) {
      const pulse = effects.reducedMotion ? 0.5 : 0.5 + 0.5 * Math.sin(effects.now / 160)
      context.strokeStyle = `rgba(250, 204, 21, ${0.5 + 0.4 * pulse})`
      context.lineWidth = 2
      context.beginPath()
      context.arc(ball.x, ball.y, BALL_RADIUS + 4 + pulse * 2, 0, Math.PI * 2)
      context.stroke()
    }
    context.fillStyle = ballColor(ball.id)
    context.beginPath()
    context.arc(ball.x, ball.y, BALL_RADIUS, 0, Math.PI * 2)
    context.fill()
    context.strokeStyle = 'rgba(15, 23, 42, 0.55)'
    context.lineWidth = 1
    context.stroke()
    // A glint, so the balls look round.
    context.fillStyle = 'rgba(255, 255, 255, 0.55)'
    context.beginPath()
    context.arc(ball.x - 2.2, ball.y - 2.4, 2.2, 0, Math.PI * 2)
    context.fill()
  }

  // Each arrival: a ring where the ball crossed, and the place it won rising from the goal.
  for (const arrival of effects.arrivals) {
    const progress = (effects.now - arrival.at) / ARRIVAL_SHOWN_MS
    if (progress < 0 || progress > 1) {
      continue
    }
    context.globalAlpha = 1 - progress
    context.strokeStyle = ballColor(arrival.ballId)
    context.lineWidth = 3
    context.beginPath()
    context.arc(arrival.x, COURSE.goalY, 6 + 34 * progress, 0, Math.PI * 2)
    context.stroke()
    if (arrival.place !== null) {
      context.font = '900 18px system-ui, sans-serif'
      outlinedText(
        context,
        t('prizeDraw.stage.placeArrived', { place: arrival.place }),
        clamp(arrival.x, 30, COURSE.width - 30),
        COURSE.goalY - 18 - progress * 30,
        '#facc15',
        4
      )
    }
  }
  context.globalAlpha = 1

  // Names last, over everything: a ball is watched by its name.
  context.font = '600 10px system-ui, sans-serif'
  context.textAlign = 'center'
  context.textBaseline = 'bottom'
  for (const ball of world.balls) {
    if (ball.finished || !visible(ball.y)) {
      continue
    }
    const label = ballLabel(entrants[ball.id]?.name ?? '')
    outlinedText(context, label, clamp(ball.x, 20, COURSE.width - 20), ball.y - BALL_RADIUS - 1, '#f8fafc', 3)
  }
  context.restore()

  // A strip down the right edge: where every ball is on the whole course, and what part is shown.
  const stripWidth = 8
  const stripX = width - stripWidth - 3
  context.fillStyle = 'rgba(148, 163, 184, 0.18)'
  context.fillRect(stripX, 6, stripWidth, height - 12)
  const toStrip = (y: number) => 6 + (clamp(y, 0, COURSE.goalY) / COURSE.goalY) * (height - 12)
  context.strokeStyle = 'rgba(248, 250, 252, 0.7)'
  context.lineWidth = 1
  context.strokeRect(stripX - 0.5, toStrip(top), stripWidth + 1, Math.max(6, toStrip(bottom) - toStrip(top)))
  for (const ball of world.balls) {
    if (!ball.finished) {
      context.fillStyle = ballColor(ball.id)
      context.fillRect(stripX + 1, toStrip(ball.y) - 1, stripWidth - 2, 2)
    }
  }

  // The latest shockwave by the name of the ball that set it off, in the corner: most go off out
  // of the view, which follows the ball furthest down.
  const latestWave = world.shockwaves[world.shockwaves.length - 1]
  if (latestWave && world.tick - latestWave.tick <= SHOCKWAVE_NOTICE_TICKS) {
    context.globalAlpha = Math.min(1, 2 * (1 - (world.tick - latestWave.tick) / SHOCKWAVE_NOTICE_TICKS))
    context.font = '800 13px system-ui, sans-serif'
    context.textAlign = 'left'
    context.textBaseline = 'bottom'
    context.lineJoin = 'round'
    const name = ballLabel(entrants[latestWave.ballId]?.name ?? '')
    outlinedText(context, t('prizeDraw.stage.shockwaveBy', { name }), 10, height - 10, '#fde047', 4)
    context.globalAlpha = 1
  }

  // While the draw may be decided any moment: a gold frame round the view, and what is at stake.
  if (effects.dramaLabel) {
    const pulse = effects.reducedMotion ? 1 : 0.6 + 0.4 * Math.sin(effects.now / 120)
    context.strokeStyle = `rgba(250, 204, 21, ${0.6 * pulse})`
    context.lineWidth = 6
    context.strokeRect(3, 3, width - 6, height - 6)
    context.font = '800 18px system-ui, sans-serif'
    context.textAlign = 'center'
    context.textBaseline = 'top'
    context.lineJoin = 'round'
    outlinedText(context, effects.dramaLabel, width / 2, 16, '#facc15', 5)
  }
}

/** A big number (or "go") in the middle of the view, growing in and settling. */
function drawCountdown(context: CanvasRenderingContext2D, width: number, height: number, text: string, progress: number, dim: boolean, reducedMotion: boolean) {
  if (dim) {
    context.fillStyle = 'rgba(2, 6, 23, 0.45)'
    context.fillRect(0, 0, width, height)
  }
  const grow = reducedMotion ? 1 : 1.5 - 0.5 * Math.min(1, progress * 2.2)
  context.globalAlpha = Math.max(0, 1 - progress * 0.35)
  context.font = `900 ${Math.round(84 * grow)}px system-ui, sans-serif`
  context.textAlign = 'center'
  context.textBaseline = 'middle'
  context.lineJoin = 'round'
  outlinedText(context, text, width / 2, height / 2, '#facc15', 10)
  context.globalAlpha = 1
}

/** Confetti shot up from both bottom corners. It is only for show, so any randomness does. */
function launchConfetti(width: number, height: number): Confetti[] {
  return Array.from({ length: CONFETTI_COUNT }, (_, index) => {
    const fromLeft = index % 2 === 0
    return {
      x: fromLeft ? 0 : width,
      y: height,
      vx: (fromLeft ? 1 : -1) * (80 + Math.random() * 320),
      vy: -(520 + Math.random() * 520),
      angle: Math.random() * Math.PI,
      spin: (Math.random() - 0.5) * 12,
      size: 4 + Math.random() * 5,
      color: CONFETTI_COLORS[index % CONFETTI_COLORS.length],
    }
  })
}

/** Moves the confetti on and draws it; returns the pieces still in sight. */
function drawConfetti(context: CanvasRenderingContext2D, pieces: Confetti[], seconds: number, height: number): Confetti[] {
  const kept: Confetti[] = []
  for (const piece of pieces) {
    piece.vy += CONFETTI_GRAVITY * seconds
    piece.vx *= 0.99
    piece.x += piece.vx * seconds
    piece.y += piece.vy * seconds
    piece.angle += piece.spin * seconds
    if (piece.y > height + 20 && piece.vy > 0) {
      continue
    }
    kept.push(piece)
    context.save()
    context.translate(piece.x, piece.y)
    context.rotate(piece.angle)
    context.fillStyle = piece.color
    context.fillRect(-piece.size / 2, -piece.size / 4, piece.size, piece.size / 2)
    context.restore()
  }
  return kept
}

/**
 * The prize draw as it is watched: after a count of three the balls, each under its entrant's
 * name, run down the course to the goal, bumpers flashing and shockwaves throwing them about. When
 * an arrival can decide the draw the view closes in and the run slows down. It is run here, in this
 * browser, from a seed taken when the draw is set up; the order of arrival is what the draw
 * decides, and none of the show changes it. It can be sped up or run straight to its end.
 */
export function PinballStage({ entrants, winnerCount, mode, onFinished }: PinballStageProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const worldRef = useRef<World | null>(null)
  const statusRef = useRef<Status>('ready')
  const speedRef = useRef<number>(1)
  const cameraRef = useRef<Camera>({ x: COURSE.width / 2, y: 0, zoom: 1, timeScale: 1 })
  // When the count before the start began, by the frames' clock; -1 until its first frame.
  const countdownStartRef = useRef<number>(-1)
  const goAtRef = useRef<number>(-Infinity)
  const trailsRef = useRef<Trails>(new Map())
  const arrivalsRef = useRef<Arrival[]>([])
  // How many arrivals have been lit up at the goal; a skipped run lights none.
  const litArrivalsRef = useRef<number>(0)
  const confettiRef = useRef<Confetti[]>([])
  const reducedMotionRef = useRef<boolean>(false)
  const sizeRef = useRef<{ width: number; height: number }>({ width: 360, height: 560 })
  const reportedRef = useRef<boolean>(false)
  const onFinishedRef = useRef(onFinished)
  useEffect(() => {
    onFinishedRef.current = onFinished
  }, [onFinished])

  const [status, setStatus] = useState<Status>('ready')
  const [speed, setSpeed] = useState<number>(1)
  const [arrivals, setArrivals] = useState<number[]>([])
  const [leaders, setLeaders] = useState<number[]>([])
  const [clock, setClock] = useState<string>('0:00')
  const [size, setSize] = useState<{ width: number; height: number }>({ width: 360, height: 560 })

  useEffect(() => {
    reducedMotionRef.current = prefersReducedMotion()
  }, [])

  useEffect(() => {
    sizeRef.current = size
  }, [size])

  // A new list of entrants, or other rules, is a new draw: the balls go back to the top.
  useEffect(() => {
    worldRef.current = createWorld(COURSE, entrants.length, freshSeed())
    statusRef.current = 'ready'
    cameraRef.current = { x: COURSE.width / 2, y: 0, zoom: 1, timeScale: 1 }
    goAtRef.current = -Infinity
    trailsRef.current = new Map()
    arrivalsRef.current = []
    litArrivalsRef.current = 0
    confettiRef.current = []
    reportedRef.current = false
    setStatus('ready')
    setArrivals([])
    setLeaders([])
    setClock('0:00')
  }, [entrants, winnerCount, mode])

  useEffect(() => {
    const container = containerRef.current
    if (!container) {
      return
    }
    const measure = () => {
      const width = Math.max(260, Math.min(VIEW_MAX_WIDTH, Math.floor(container.clientWidth)))
      const height = Math.max(380, Math.min(700, Math.round(window.innerHeight * 0.72)))
      setSize((previous) => (previous.width === width && previous.height === height ? previous : { width, height }))
    }
    measure()
    window.addEventListener('resize', measure)
    return () => window.removeEventListener('resize', measure)
  }, [])

  const finish = useCallback((world: World) => {
    statusRef.current = 'done'
    setStatus('done')
    setArrivals([...world.finishedOrder])
    setLeaders([])
    setClock(formatDrawClock(world.tick, TICKS_PER_SECOND))
    trailsRef.current = new Map()
    if (!reducedMotionRef.current) {
      confettiRef.current = launchConfetti(sizeRef.current.width, sizeRef.current.height)
    }
    if (!reportedRef.current) {
      reportedRef.current = true
      onFinishedRef.current([...world.finishedOrder])
    }
  }, [])

  useEffect(() => {
    const canvas = canvasRef.current
    const context = canvas?.getContext('2d')
    if (!canvas || !context) {
      return
    }
    const ratio = Math.min(2, window.devicePixelRatio || 1)
    canvas.width = Math.round(size.width * ratio)
    canvas.height = Math.round(size.height * ratio)
    context.setTransform(ratio, 0, 0, ratio, 0, 0)

    let frame = 0
    let previous = performance.now()
    let owed = 0
    let shownArrivals = -1
    let shownSecond = -1
    let standingsShownAt = -Infinity

    const loop = () => {
      // One clock for everything the show times, the same performance.now() the loop started on.
      const now = performance.now()
      const world = worldRef.current
      const seconds = Math.min(0.1, Math.max(0, (now - previous) / 1000))
      const camera = cameraRef.current
      const reducedMotion = reducedMotionRef.current
      if (world) {
        // The count starts on the first frame after the click, timed by the frames' own clock.
        if (statusRef.current === 'countdown' && countdownStartRef.current < 0) {
          countdownStartRef.current = now
        }
        if (statusRef.current === 'countdown' && now - countdownStartRef.current >= COUNT_FROM * COUNT_MS) {
          statusRef.current = 'running'
          goAtRef.current = now
          setStatus('running')
        }

        if (statusRef.current === 'running') {
          owed = Math.min(
            MAX_TICKS_PER_FRAME * speedRef.current,
            owed + seconds * TICKS_PER_SECOND * speedRef.current * camera.timeScale
          )
          while (owed >= 1 && statusRef.current === 'running') {
            stepWorld(world)
            owed -= 1
            if (isDecided(world, winnerCount, mode)) {
              finish(world)
            }
          }
          while (litArrivalsRef.current < world.finishedOrder.length) {
            const index = litArrivalsRef.current
            const ballId = world.finishedOrder[index]
            arrivalsRef.current.push({
              ballId,
              x: world.balls[ballId].x,
              at: now,
              place: placeOfArrival(index, world.balls.length, winnerCount, mode),
            })
            litArrivalsRef.current += 1
          }
          if (statusRef.current === 'running') {
            for (const ball of world.balls) {
              if (ball.finished) {
                trailsRef.current.delete(ball.id)
                continue
              }
              const trail = trailsRef.current.get(ball.id) ?? []
              trail.push({ x: ball.x, y: ball.y })
              if (trail.length > TRAIL_LENGTH) {
                trail.shift()
              }
              trailsRef.current.set(ball.id, trail)
            }
          }
          if (world.finishedOrder.length !== shownArrivals) {
            shownArrivals = world.finishedOrder.length
            setArrivals([...world.finishedOrder])
          }
          const second = Math.floor(world.tick / TICKS_PER_SECOND)
          if (second !== shownSecond) {
            shownSecond = second
            setClock(formatDrawClock(world.tick, TICKS_PER_SECOND))
          }
          if (statusRef.current === 'running' && now - standingsShownAt >= STANDINGS_EVERY_MS) {
            standingsShownAt = now
            setLeaders(standings(world, mode, STANDINGS_SHOWN))
          }
        }
        arrivalsRef.current = arrivalsRef.current.filter((arrival) => now - arrival.at <= ARRIVAL_SHOWN_MS)

        // The camera: at the top before the start, then after the ball furthest down, closer and
        // slower while an arrival can decide the draw; once the draw is decided it stays put.
        const focus = statusRef.current === 'running' ? focusOf(world, winnerCount, mode) : null
        camera.zoom = approach(camera.zoom, focus && !reducedMotion ? focus.zoom : 1, 0.08)
        camera.timeScale = approach(camera.timeScale, focus ? focus.timeScale : 1, 0.1)
        const unit = (size.width / COURSE.width) * camera.zoom
        const viewWidth = size.width / unit
        const viewHeight = size.height / unit
        let targetX = COURSE.width / 2
        let targetY = camera.y
        if (statusRef.current === 'ready' || statusRef.current === 'countdown') {
          targetY = 0
        } else if (focus && focus.ballId !== null) {
          const ball = world.balls[focus.ballId]
          // Close up, halfway between the ball and the goal, so both stay in sight.
          targetY = focus.dramatic ? (ball.y + COURSE.goalY) / 2 : ball.y - viewHeight * 0.05
          if (camera.zoom > 1.01) {
            targetX = ball.x
          }
        }
        camera.x = clamp(approach(camera.x, targetX, 0.12), viewWidth / 2, COURSE.width - viewWidth / 2)
        camera.y = clamp(approach(camera.y, targetY, 0.1), viewHeight / 2, COURSE.height - viewHeight / 2)

        let dramaLabel: string | null = null
        if (focus?.dramatic) {
          const place = decidingPlace(world, winnerCount, mode)
          dramaLabel = place === null ? t('prizeDraw.stage.dramaWinners') : t('prizeDraw.stage.dramaPlace', { place })
        }
        drawScene(context, world, entrants, size.width, size.height, camera, {
          now,
          trails: trailsRef.current,
          arrivals: arrivalsRef.current,
          focusBallId: focus?.ballId ?? null,
          dramaLabel,
          reducedMotion,
        })

        if (statusRef.current === 'countdown') {
          const elapsed = Math.max(0, now - countdownStartRef.current)
          const step = Math.min(COUNT_FROM - 1, Math.floor(elapsed / COUNT_MS))
          drawCountdown(context, size.width, size.height, String(COUNT_FROM - step), (elapsed - step * COUNT_MS) / COUNT_MS, true, reducedMotion)
        } else if (now - goAtRef.current < GO_MS) {
          drawCountdown(context, size.width, size.height, t('prizeDraw.stage.go'), (now - goAtRef.current) / GO_MS, false, reducedMotion)
        }
        if (confettiRef.current.length > 0) {
          confettiRef.current = drawConfetti(context, confettiRef.current, seconds, size.height)
        }
      }
      previous = now
      frame = window.requestAnimationFrame(loop)
    }
    frame = window.requestAnimationFrame(loop)
    return () => window.cancelAnimationFrame(frame)
  }, [entrants, finish, mode, size, winnerCount])

  const start = () => {
    if (statusRef.current !== 'ready') {
      return
    }
    countdownStartRef.current = -1
    statusRef.current = 'countdown'
    setStatus('countdown')
  }

  const skip = () => {
    const world = worldRef.current
    if (!world || statusRef.current === 'done') {
      return
    }
    while (!isDecided(world, winnerCount, mode)) {
      stepWorld(world)
    }
    litArrivalsRef.current = world.finishedOrder.length
    finish(world)
  }

  const changeSpeed = (next: number) => {
    speedRef.current = next
    setSpeed(next)
  }

  const winners = status === 'done' ? winnersOf(arrivals, winnerCount, mode) : mode === 'FIRST' ? arrivals.slice(0, winnerCount) : []
  const remaining = entrants.length - arrivals.length

  return (
    <div className="grid gap-4 lg:grid-cols-[minmax(0,460px)_minmax(0,1fr)]">
      <div ref={containerRef} className="space-y-2">
        <div className="relative overflow-hidden rounded-xl border border-slate-700 bg-slate-900" style={{ height: size.height }}>
          <canvas ref={canvasRef} style={{ width: size.width, height: size.height }} className="mx-auto block" />
          {status === 'ready' && (
            <div className="absolute inset-0 flex items-end justify-center bg-slate-950/30 pb-10">
              <button
                type="button"
                onClick={start}
                className="rounded-xl bg-amber-500 px-6 py-3 text-base font-bold text-slate-950 shadow-lg hover:bg-amber-400"
              >
                {t('prizeDraw.stage.start')}
              </button>
            </div>
          )}
          {status === 'done' && winners.length > 0 && (
            <div className="pointer-events-none absolute inset-x-3 top-3 flex justify-center">
              <div className="rounded-xl bg-slate-950/85 px-5 py-3 text-center shadow-xl ring-1 ring-amber-400/60">
                <p className="text-xs font-semibold text-amber-300">{t('prizeDraw.stage.finished')}</p>
                {/* Every place drawn for, in two columns when there are many. */}
                <ol className={`mt-1 ${winners.length > BANNER_ONE_COLUMN ? 'grid grid-cols-2 gap-x-4 gap-y-0.5 text-left' : 'space-y-0.5'}`}>
                  {winners.map((ballIndex, place) => (
                    <li key={ballIndex} className="break-all text-sm font-bold text-white">
                      <span className="mr-1.5 text-amber-300">{t('prizeDraw.place', { place: place + 1 })}</span>
                      {entrants[ballIndex]?.name}
                    </li>
                  ))}
                </ol>
              </div>
            </div>
          )}
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs tabular-nums text-slate-500 dark:text-slate-400">{clock}</span>
          <div className="flex gap-1" role="group" aria-label={t('prizeDraw.stage.speed')}>
            {SPEEDS.map((option) => (
              <button
                key={option}
                type="button"
                onClick={() => changeSpeed(option)}
                aria-pressed={speed === option}
                className={`${controlClass} ${speed === option ? 'border-slate-900 bg-slate-900 text-white hover:bg-slate-800 dark:border-slate-100 dark:bg-slate-100 dark:text-slate-900' : ''}`}
              >
                {option}×
              </button>
            ))}
          </div>
          <button type="button" onClick={skip} disabled={status === 'done'} className={controlClass}>
            {t('prizeDraw.stage.skip')}
          </button>
        </div>
      </div>

      <div className="space-y-3">
        <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-xs dark:border-slate-700 dark:bg-slate-900">
          <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
            {t(mode === 'FIRST' ? 'prizeDraw.stage.firstRule' : 'prizeDraw.stage.lastRule', { count: winnerCount })}
          </h3>
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
            {t('prizeDraw.stage.progress', { arrived: arrivals.length, total: entrants.length, remaining })}
          </p>
          {winners.length > 0 && (
            <ol className="mt-3 space-y-1">
              {winners.map((ballIndex, place) => (
                <li key={ballIndex} className="flex items-center gap-2 rounded-lg bg-amber-50 px-3 py-1.5 text-sm dark:bg-amber-950/40">
                  <span className="w-10 shrink-0 text-xs font-semibold text-amber-700 dark:text-amber-300">
                    {t('prizeDraw.place', { place: place + 1 })}
                  </span>
                  <span className="h-3 w-3 shrink-0 rounded-full" style={{ backgroundColor: ballColor(ballIndex) }} aria-hidden="true" />
                  <span className="min-w-0 break-all font-semibold text-slate-900 dark:text-slate-100">{entrants[ballIndex]?.name}</span>
                </li>
              ))}
            </ol>
          )}
        </div>

        {status === 'running' && leaders.length > 0 && (
          <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-xs dark:border-slate-700 dark:bg-slate-900">
            <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
              {t(mode === 'FIRST' ? 'prizeDraw.stage.leadersFirst' : 'prizeDraw.stage.leadersLast')}
            </h3>
            <ol className="mt-2 space-y-0.5 text-sm text-slate-700 dark:text-slate-200">
              {leaders.map((ballIndex, position) => {
                // The place this ball would take if they all arrived in the order they are in now.
                const place = mode === 'FIRST' ? arrivals.length + position + 1 : position + 1
                const winning = place <= winnerCount
                return (
                  <li key={ballIndex} className="flex items-center gap-2">
                    <span
                      className={`w-7 shrink-0 text-right text-xs tabular-nums ${winning ? 'font-bold text-amber-600 dark:text-amber-300' : 'text-slate-400'}`}
                    >
                      {place}
                    </span>
                    <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ backgroundColor: ballColor(ballIndex) }} aria-hidden="true" />
                    <span className={`min-w-0 break-all ${winning ? 'font-semibold text-slate-900 dark:text-slate-100' : ''}`}>
                      {entrants[ballIndex]?.name}
                    </span>
                  </li>
                )
              })}
            </ol>
          </div>
        )}

        <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-xs dark:border-slate-700 dark:bg-slate-900">
          <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('prizeDraw.stage.arrivals')}</h3>
          {arrivals.length === 0 ? (
            <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{t('prizeDraw.stage.noArrivals')}</p>
          ) : (
            <ol className="mt-2 max-h-72 space-y-0.5 overflow-y-auto text-xs text-slate-700 dark:text-slate-200">
              {arrivals.map((ballIndex, position) => (
                <li key={ballIndex} className="flex items-center gap-2">
                  <span className="w-7 shrink-0 text-right tabular-nums text-slate-400">{position + 1}</span>
                  <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ backgroundColor: ballColor(ballIndex) }} aria-hidden="true" />
                  <span className="min-w-0 break-all">{entrants[ballIndex]?.name}</span>
                </li>
              ))}
            </ol>
          )}
        </div>
      </div>
    </div>
  )
}
