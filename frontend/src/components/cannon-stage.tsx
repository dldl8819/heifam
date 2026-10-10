'use client'

import { useEffect, useRef, useState, type ReactNode } from 'react'
import { CANNON_FLIGHT_MS, cannonFlight, cannonLabelSize, type CannonShot } from '@/lib/cannon-draw'
import { t } from '@/lib/i18n'

type CannonStageProps = {
  shot: CannonShot | null
  // Called once for each shot, when its ball has come to rest.
  onLanded: (shot: CannonShot) => void
  // The controls laid over the picture.
  children?: ReactNode
}

const CENTER_X = 320
const MUZZLE_Y = 318
const REST_Y = 178
const BALL_RADIUS = 96
// The number turns over while the ball climbs and stops on the drawn one this far into the flight.
const SETTLE_AT = 0.82
const FLICKER_MS = 70
const SMOKE_UNTIL = 0.55
const RECOIL_UNTIL = 0.16

function prefersReducedMotion(): boolean {
  return typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

/**
 * The cannon under a sky: each shot sends a ball up out of the muzzle that grows as it climbs and
 * shows the drawn number once it comes to rest. With reduced motion the ball is simply there.
 */
export function CannonStage({ shot, onLanded, children }: CannonStageProps) {
  // How far the ball of which shot has come; a new shot starts at the muzzle before its first frame.
  const [flightState, setFlightState] = useState<{ id: number; progress: number } | null>(null)
  const [flicker, setFlicker] = useState<number>(1)
  const onLandedRef = useRef(onLanded)
  const landedIdRef = useRef<number | null>(null)

  useEffect(() => {
    onLandedRef.current = onLanded
  }, [onLanded])

  useEffect(() => {
    if (!shot) {
      return
    }
    const land = () => {
      if (landedIdRef.current !== shot.id) {
        landedIdRef.current = shot.id
        onLandedRef.current(shot)
      }
    }
    if (prefersReducedMotion()) {
      setFlightState({ id: shot.id, progress: 1 })
      land()
      return
    }
    let frame = 0
    let lastFlicker = Number.NEGATIVE_INFINITY
    const start = performance.now()
    const step = (now: number) => {
      const next = Math.min(1, (now - start) / CANNON_FLIGHT_MS)
      setFlightState({ id: shot.id, progress: next })
      if (now - lastFlicker >= FLICKER_MS) {
        lastFlicker = now
        // Only for show: the number was drawn before the cannon fired.
        setFlicker(1 + Math.floor(Math.random() * shot.last))
      }
      if (next < 1) {
        frame = window.requestAnimationFrame(step)
      } else {
        land()
      }
    }
    frame = window.requestAnimationFrame(step)
    return () => window.cancelAnimationFrame(frame)
  }, [shot])

  const progress = shot && flightState?.id === shot.id ? flightState.progress : 0
  const flight = cannonFlight(progress)
  const ballY = MUZZLE_Y - flight.rise * (MUZZLE_Y - REST_Y)
  const radius = BALL_RADIUS * flight.scale
  const settled = progress >= SETTLE_AT
  const shown = shot ? (settled ? shot.number : flicker) : null
  const numberSize = (String(shown ?? '').length >= 3 ? 78 : 112) * flight.scale
  const label = shot && settled ? (shot.label ?? t('cannonDraw.ballSuffix')) : null
  const named = shot?.label != null
  const recoil = shot && progress < RECOIL_UNTIL ? 12 * Math.sin((progress / RECOIL_UNTIL) * Math.PI) : 0
  const smoke = shot && progress < SMOKE_UNTIL ? progress / SMOKE_UNTIL : null

  return (
    <div className="relative overflow-hidden rounded-xl border border-slate-200 shadow-sm dark:border-slate-700">
      <svg viewBox="0 0 640 440" className="block h-auto w-full select-none" aria-hidden="true">
        <defs>
          <linearGradient id="cannon-sky" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="#3f6fd1" />
            <stop offset="0.75" stopColor="#a9c1ec" />
            <stop offset="1" stopColor="#d4e0f7" />
          </linearGradient>
          <radialGradient id="cannon-ball" cx="0.4" cy="0.32" r="0.75">
            <stop offset="0" stopColor="#4cf54c" />
            <stop offset="0.5" stopColor="#14b814" />
            <stop offset="1" stopColor="#077507" />
          </radialGradient>
          <linearGradient id="cannon-barrel" x1="0" y1="0" x2="1" y2="0">
            <stop offset="0" stopColor="#f59e0b" />
            <stop offset="0.55" stopColor="#fb9a1e" />
            <stop offset="0.8" stopColor="#ea6a0c" />
            <stop offset="1" stopColor="#b8460a" />
          </linearGradient>
          <radialGradient id="cannon-knob" cx="0.4" cy="0.35" r="0.7">
            <stop offset="0" stopColor="#fff3a3" />
            <stop offset="0.5" stopColor="#facc15" />
            <stop offset="1" stopColor="#a16207" />
          </radialGradient>
        </defs>

        <rect width="640" height="440" fill="url(#cannon-sky)" />
        <g fill="#ffffff" opacity="0.5">
          <ellipse cx="120" cy="105" rx="58" ry="30" />
          <ellipse cx="162" cy="86" rx="44" ry="28" />
          <ellipse cx="192" cy="110" rx="50" ry="22" />
          <ellipse cx="150" cy="215" rx="70" ry="32" />
          <ellipse cx="202" cy="198" rx="48" ry="30" />
          <ellipse cx="108" cy="230" rx="44" ry="20" />
          <ellipse cx="520" cy="82" rx="46" ry="20" />
          <ellipse cx="552" cy="70" rx="34" ry="20" />
        </g>
        <ellipse cx="90" cy="500" rx="360" ry="160" fill="#13852c" stroke="#0b3d17" strokeWidth="2" />
        <ellipse cx="580" cy="505" rx="400" ry="160" fill="#5fe01a" stroke="#1d3a0a" strokeWidth="2" />

        {shot && shown !== null && (
          <g>
            <circle cx={CENTER_X} cy={ballY} r={radius} fill="url(#cannon-ball)" stroke="#065f06" strokeWidth="1.5" />
            <text
              x={CENTER_X}
              y={ballY - 14 * flight.scale}
              fontSize={numberSize}
              // Lining figures: Georgia's 4, 7 and 9 hang below the line, onto the words under them.
              fontFamily="'Times New Roman', Times, serif"
              textAnchor="middle"
              dominantBaseline="central"
              fill="#fcd34d"
              stroke="#713f12"
              strokeWidth={numberSize * 0.03}
              paintOrder="stroke"
            >
              {shown}
            </text>
            {label && (
              <text
                x={CENTER_X}
                y={ballY + 60 * flight.scale}
                fontSize={(named && label ? cannonLabelSize(label) : 24) * flight.scale}
                fontWeight={named ? 700 : 500}
                textAnchor="middle"
                dominantBaseline="central"
                fill={named ? '#ffffff' : '#052e16'}
                stroke={named ? '#052e16' : 'none'}
                strokeWidth={named ? 3 : 0}
                paintOrder="stroke"
              >
                {label}
              </text>
            )}
          </g>
        )}

        <g transform={`translate(0 ${recoil})`}>
          <rect x="282" y="318" width="76" height="140" fill="url(#cannon-barrel)" stroke="#7c2d12" strokeWidth="2" />
          <rect x="334" y="322" width="9" height="130" fill="#fde68a" opacity="0.45" />
          <ellipse cx="320" cy="318" rx="40" ry="10" fill="#f97316" stroke="#7c2d12" strokeWidth="2" />
          <ellipse cx="320" cy="318" rx="31" ry="6" fill="#3b1606" />
          <circle cx="320" cy="410" r="21" fill="#27272a" stroke="#09090b" strokeWidth="2" />
          <circle cx="320" cy="408" r="12" fill="url(#cannon-knob)" />
        </g>

        {smoke !== null && (
          <g fill="#e5e7eb" opacity={0.8 * (1 - smoke)}>
            {[-28, 0, 28].map((offset) => (
              <circle key={offset} cx={CENTER_X + offset * (1 + smoke)} cy={MUZZLE_Y - 8 - smoke * 30} r={12 + smoke * 28} />
            ))}
          </g>
        )}
      </svg>
      {children}
    </div>
  )
}
