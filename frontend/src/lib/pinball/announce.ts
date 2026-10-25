/**
 * What a screen reader is told about a pinball draw, which it cannot see on the canvas: that the
 * draw has started, in a first-in draw each place as its ball arrives, and once it is over every
 * place in order. Nothing about the balls' running about, which changes many times a second.
 */

import { t } from '@/lib/i18n'
import { winnersOf, type DrawMode } from './engine'

export type StageStatus = 'ready' | 'countdown' | 'running' | 'done'

function placeLine(place: number, name: string | undefined): string {
  return t('prizeDraw.stage.announcePlace', { place: t('prizeDraw.place', { place }), name: name ?? '' })
}

/**
 * The line for the page's polite live region. It changes only when there is news, since a screen
 * reader speaks it again on every change: after the last first-in place it stays on that place.
 * Empty before the start.
 */
export function drawAnnouncement(
  status: StageStatus,
  arrivals: readonly number[],
  names: readonly string[],
  winnerCount: number,
  mode: DrawMode
): string {
  if (status === 'ready') {
    return ''
  }
  if (status === 'done') {
    const places = winnersOf([...arrivals], winnerCount, mode).map((ballIndex, index) => placeLine(index + 1, names[ballIndex]))
    return t('prizeDraw.stage.announceFinished', { places: places.join(', ') })
  }
  // A last-in draw is decided only when every ball is in, so until then there is no place to tell.
  if (status === 'running' && mode === 'FIRST' && arrivals.length > 0) {
    const place = Math.min(arrivals.length, winnerCount)
    return placeLine(place, names[arrivals[place - 1]])
  }
  return t('prizeDraw.stage.announceStart')
}
