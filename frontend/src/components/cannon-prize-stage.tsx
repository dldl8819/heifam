'use client'

import { useCallback, useRef, useState } from 'react'
import { CannonStage } from '@/components/cannon-stage'
import { drawCannonNumber, type CannonShot } from '@/lib/cannon-draw'
import { t } from '@/lib/i18n'
import type { DrawEntrant } from '@/lib/prize-draw'

type CannonPrizeStageProps = {
  entrants: DrawEntrant[]
  winnerCount: number
  // Called once, when every place has its winner, with the entrant indexes by place (first place first).
  onFinished: (winnersByPlace: number[]) => void
}

/**
 * The prize draw with the cannon: each shot draws one of the entrants not yet drawn, the last place
 * first and the first place last. The pick is made in this browser the moment the cannon fires,
 * every remaining entrant as likely as the next; the flight only shows it.
 */
export function CannonPrizeStage({ entrants, winnerCount, onFinished }: CannonPrizeStageProps) {
  const places = Math.max(1, Math.min(winnerCount, entrants.length))
  const [byPlace, setByPlace] = useState<Array<number | null>>(() => Array.from({ length: places }, () => null))
  const [shot, setShot] = useState<CannonShot | null>(null)
  const [flying, setFlying] = useState<boolean>(false)
  const [announcement, setAnnouncement] = useState<string>('')
  const nextId = useRef<number>(1)
  // The place the ball in the air is for.
  const flyingPlace = useRef<number>(places)
  const reported = useRef<boolean>(false)
  // The places as they stand, for the landing handler, which outlives the render it was made in.
  const byPlaceRef = useRef<Array<number | null>>(byPlace)

  const drawn = byPlace.filter((index): index is number => index !== null)
  const nextPlace = byPlace.lastIndexOf(null) + 1
  const finished = nextPlace === 0

  const fire = () => {
    if (flying || finished) {
      return
    }
    const remaining = entrants.map((_, index) => index).filter((index) => !drawn.includes(index))
    const entrantIndex = remaining[drawCannonNumber(remaining.length) - 1]
    const id = nextId.current
    nextId.current += 1
    flyingPlace.current = nextPlace
    setShot({ id, number: entrantIndex + 1, last: entrants.length, label: entrants[entrantIndex].name })
    setFlying(true)
    setAnnouncement('')
  }

  const handleLanded = useCallback(
    (landed: CannonShot) => {
      setFlying(false)
      const place = flyingPlace.current
      setAnnouncement(t('prizeDraw.cannon.landed', { place, name: landed.label ?? '' }))
      const next = byPlaceRef.current.map((index, position) => (position === place - 1 ? landed.number - 1 : index))
      byPlaceRef.current = next
      setByPlace(next)
      if (next.every((index) => index !== null) && !reported.current) {
        reported.current = true
        onFinished(next as number[])
      }
    },
    [onFinished]
  )

  return (
    <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,320px)]">
      <CannonStage shot={shot} onLanded={handleLanded}>
        <span className="absolute left-3 top-3 rounded-md bg-white/85 px-2.5 py-1 text-xs font-semibold text-slate-800 shadow-xs">
          {t('prizeDraw.cannon.range', { count: entrants.length - drawn.length })}
        </span>
        {!finished && (
          <button
            type="button"
            onClick={fire}
            disabled={flying}
            className="absolute bottom-2 right-2 rounded-lg bg-white/90 px-3 py-1.5 text-sm font-bold text-slate-900 shadow-md hover:bg-white disabled:cursor-not-allowed disabled:opacity-60 sm:bottom-3 sm:right-3 sm:px-4 sm:py-2 sm:text-base"
          >
            {flying ? t('cannonDraw.flying') : t('prizeDraw.cannon.fire', { place: nextPlace })}
          </button>
        )}
      </CannonStage>
      <p className="sr-only" aria-live="polite">
        {announcement}
      </p>

      <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-xs dark:border-slate-700 dark:bg-slate-900">
        <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('prizeDraw.cannon.rule', { count: places })}</h3>
        <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
          {t('prizeDraw.cannon.progress', { done: drawn.length, count: places, remaining: entrants.length - drawn.length })}
        </p>
        <ol className="mt-3 space-y-1">
          {byPlace.map((entrantIndex, position) => {
            const pending = entrantIndex === null
            const next = !finished && position === nextPlace - 1
            return (
              <li
                key={position}
                className={`flex items-center gap-2 rounded-lg px-3 py-1.5 text-sm ${
                  pending
                    ? next
                      ? 'border border-dashed border-amber-400 text-slate-500 dark:border-amber-600 dark:text-slate-400'
                      : 'bg-slate-50 text-slate-500 dark:bg-slate-800/60 dark:text-slate-400'
                    : 'bg-amber-50 dark:bg-amber-950/40'
                }`}
              >
                <span className="w-10 shrink-0 text-xs font-semibold text-amber-700 dark:text-amber-300">
                  {t('prizeDraw.place', { place: position + 1 })}
                </span>
                <span className="min-w-0 break-all font-semibold text-slate-900 dark:text-slate-100">
                  {pending ? t('prizeDraw.cannon.pending') : entrants[entrantIndex]?.name}
                </span>
              </li>
            )
          })}
        </ol>
      </div>
    </div>
  )
}
