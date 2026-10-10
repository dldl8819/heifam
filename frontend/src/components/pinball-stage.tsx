'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { createCourse } from '@/lib/pinball/course'
import {
  BALL_RADIUS,
  TICKS_PER_SECOND,
  createWorld,
  isWorldDone,
  spinnerEnds,
  stepWorld,
  winnersOf,
  type DrawMode,
  type World,
} from '@/lib/pinball/engine'
import { ballColor, ballLabel, formatDrawClock, type DrawEntrant } from '@/lib/prize-draw'
import { t } from '@/lib/i18n'

type PinballStageProps = {
  entrants: DrawEntrant[]
  winnerCount: number
  mode: DrawMode
  // Called once, when the winners are decided, with the ball indexes in the order they arrived.
  onFinished: (finishedOrder: number[]) => void
}

type Status = 'ready' | 'running' | 'done'

const COURSE = createCourse()
const SPEEDS = [1, 2, 4] as const
// A frame that took this long (a hidden tab) is not caught up on all at once.
const MAX_TICKS_PER_FRAME = TICKS_PER_SECOND / 4
const VIEW_MAX_WIDTH = 460

const controlClass =
  'rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800'

/** A seed nobody chose: the run it gives cannot be picked beforehand. */
function freshSeed(): number {
  const values = new Uint32Array(1)
  window.crypto.getRandomValues(values)
  return values[0]
}

/**
 * With FIRST the draw is decided once enough balls are in; with LAST every ball has to arrive,
 * since the last ones win.
 */
function isDecided(world: World, winnerCount: number, mode: DrawMode): boolean {
  return mode === 'FIRST' ? world.finishedOrder.length >= Math.min(winnerCount, world.balls.length) : isWorldDone(world)
}

function drawScene(
  context: CanvasRenderingContext2D,
  world: World,
  entrants: DrawEntrant[],
  width: number,
  height: number,
  cameraY: number
) {
  const scale = width / COURSE.width
  context.save()
  context.clearRect(0, 0, width, height)
  context.fillStyle = '#0f172a'
  context.fillRect(0, 0, width, height)
  context.scale(scale, scale)
  context.translate(0, -cameraY)

  const top = cameraY - 40
  const bottom = cameraY + height / scale + 40
  const visible = (y: number) => y >= top && y <= bottom

  // The goal line, and the tray under it.
  context.fillStyle = 'rgba(16, 185, 129, 0.18)'
  context.fillRect(0, COURSE.goalY, COURSE.width, COURSE.height - COURSE.goalY)
  context.strokeStyle = '#34d399'
  context.lineWidth = 3
  context.setLineDash([10, 8])
  context.beginPath()
  context.moveTo(0, COURSE.goalY)
  context.lineTo(COURSE.width, COURSE.goalY)
  context.stroke()
  context.setLineDash([])

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

  for (const peg of COURSE.pegs) {
    if (!visible(peg.y)) {
      continue
    }
    const bumper = peg.r > 8
    context.fillStyle = bumper ? '#f59e0b' : '#cbd5e1'
    context.beginPath()
    context.arc(peg.x, peg.y, peg.r, 0, Math.PI * 2)
    context.fill()
    if (bumper) {
      context.strokeStyle = '#fde68a'
      context.lineWidth = 2
      context.stroke()
    }
  }

  context.strokeStyle = '#38bdf8'
  context.lineWidth = 5
  for (const spinner of COURSE.spinners) {
    if (!visible(spinner.y)) {
      continue
    }
    const ends = spinnerEnds(spinner, world.tick)
    context.beginPath()
    context.moveTo(ends.x1, ends.y1)
    context.lineTo(ends.x2, ends.y2)
    context.stroke()
    context.fillStyle = '#e0f2fe'
    context.beginPath()
    context.arc(spinner.x, spinner.y, 4, 0, Math.PI * 2)
    context.fill()
  }

  for (const ball of world.balls) {
    if (ball.finished || !visible(ball.y)) {
      continue
    }
    context.fillStyle = ballColor(ball.id)
    context.beginPath()
    context.arc(ball.x, ball.y, BALL_RADIUS, 0, Math.PI * 2)
    context.fill()
    context.strokeStyle = 'rgba(15, 23, 42, 0.55)'
    context.lineWidth = 1
    context.stroke()
  }

  // Names last, over everything: a ball is watched by its name.
  context.font = '600 10px system-ui, sans-serif'
  context.textAlign = 'center'
  context.textBaseline = 'bottom'
  context.lineJoin = 'round'
  for (const ball of world.balls) {
    if (ball.finished || !visible(ball.y)) {
      continue
    }
    const label = ballLabel(entrants[ball.id]?.name ?? '')
    const x = Math.min(COURSE.width - 20, Math.max(20, ball.x))
    context.strokeStyle = 'rgba(15, 23, 42, 0.9)'
    context.lineWidth = 3
    context.strokeText(label, x, ball.y - BALL_RADIUS - 1)
    context.fillStyle = '#f8fafc'
    context.fillText(label, x, ball.y - BALL_RADIUS - 1)
  }
  context.restore()

  // A strip down the right edge: where every ball is on the whole course, and what part is shown.
  const stripWidth = 8
  const stripX = width - stripWidth - 3
  context.fillStyle = 'rgba(148, 163, 184, 0.18)'
  context.fillRect(stripX, 6, stripWidth, height - 12)
  const toStrip = (y: number) => 6 + (Math.min(COURSE.goalY, Math.max(0, y)) / COURSE.goalY) * (height - 12)
  context.strokeStyle = 'rgba(248, 250, 252, 0.7)'
  context.lineWidth = 1
  context.strokeRect(stripX - 0.5, toStrip(cameraY), stripWidth + 1, Math.max(6, toStrip(cameraY + height / scale) - toStrip(cameraY)))
  for (const ball of world.balls) {
    if (!ball.finished) {
      context.fillStyle = ballColor(ball.id)
      context.fillRect(stripX + 1, toStrip(ball.y) - 1, stripWidth - 2, 2)
    }
  }
}

/**
 * The prize draw as it is watched: the balls, each under its entrant's name, run down the course
 * to the goal. It is run here, in this browser, from a seed taken when the draw is started; the
 * order of arrival is what the draw decides. It can be sped up or run straight to its end.
 */
export function PinballStage({ entrants, winnerCount, mode, onFinished }: PinballStageProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const worldRef = useRef<World | null>(null)
  const statusRef = useRef<Status>('ready')
  const speedRef = useRef<number>(1)
  const cameraRef = useRef<number>(0)
  const reportedRef = useRef<boolean>(false)
  const onFinishedRef = useRef(onFinished)
  useEffect(() => {
    onFinishedRef.current = onFinished
  }, [onFinished])

  const [status, setStatus] = useState<Status>('ready')
  const [speed, setSpeed] = useState<number>(1)
  const [arrivals, setArrivals] = useState<number[]>([])
  const [clock, setClock] = useState<string>('0:00')
  const [size, setSize] = useState<{ width: number; height: number }>({ width: 360, height: 560 })

  // A new list of entrants, or other rules, is a new draw: the balls go back to the top.
  useEffect(() => {
    worldRef.current = createWorld(COURSE, entrants.length, freshSeed())
    statusRef.current = 'ready'
    cameraRef.current = 0
    reportedRef.current = false
    setStatus('ready')
    setArrivals([])
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
    setClock(formatDrawClock(world.tick, TICKS_PER_SECOND))
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

    const loop = (now: number) => {
      const world = worldRef.current
      if (world) {
        if (statusRef.current === 'running') {
          owed = Math.min(MAX_TICKS_PER_FRAME * speedRef.current, owed + ((now - previous) / 1000) * TICKS_PER_SECOND * speedRef.current)
          while (owed >= 1 && statusRef.current === 'running') {
            stepWorld(world)
            owed -= 1
            if (isDecided(world, winnerCount, mode)) {
              finish(world)
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
        }

        // The view follows whichever ball is furthest down and still running.
        const viewHeight = size.height / (size.width / COURSE.width)
        let leader = statusRef.current === 'ready' ? 0 : COURSE.goalY
        let anyRunning = false
        for (const ball of world.balls) {
          if (!ball.finished && (!anyRunning || ball.y > leader)) {
            leader = ball.y
            anyRunning = true
          }
        }
        const target = statusRef.current === 'ready'
          ? 0
          : Math.max(0, Math.min(COURSE.height - viewHeight, leader - viewHeight * 0.55))
        cameraRef.current += (target - cameraRef.current) * 0.09
        drawScene(context, world, entrants, size.width, size.height, cameraRef.current)
      }
      previous = now
      frame = window.requestAnimationFrame(loop)
    }
    frame = window.requestAnimationFrame(loop)
    return () => window.cancelAnimationFrame(frame)
  }, [entrants, finish, mode, size, winnerCount])

  const start = () => {
    statusRef.current = 'running'
    setStatus('running')
  }

  const skip = () => {
    const world = worldRef.current
    if (!world || statusRef.current === 'done') {
      return
    }
    while (!isDecided(world, winnerCount, mode)) {
      stepWorld(world)
    }
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
        <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
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

        <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
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
