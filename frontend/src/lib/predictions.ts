import type { PredictionBoard, PredictionPlayer } from '@/types/api'

export const PREDICTION_POLL_MS = 15000

/** How far the server clock runs ahead of this browser, so countdowns follow the server. */
export function serverOffsetMs(serverNow: string, clientNow: number): number {
  const parsed = Date.parse(serverNow)
  return Number.isNaN(parsed) ? 0 : parsed - clientNow
}

export function remainingSeconds(closesAt: string | null, offsetMs: number, clientNow: number): number {
  if (!closesAt) {
    return 0
  }
  const closes = Date.parse(closesAt)
  if (Number.isNaN(closes)) {
    return 0
  }
  return Math.max(0, Math.ceil((closes - (clientNow + offsetMs)) / 1000))
}

export function formatCountdown(seconds: number): string {
  const safe = Math.max(0, Math.floor(seconds))
  return `${Math.floor(safe / 60)}:${String(safe % 60).padStart(2, '0')}`
}

export function hitRate(stats: PredictionBoard['stats']): number | null {
  return stats.resolved > 0 ? Math.round((stats.hits * 1000) / stats.resolved) / 10 : null
}

/** The people who picked a side, as shown once picks are closed. */
export function formatPickers(pickers: (string | null)[] | null | undefined, unknownLabel: string): string {
  return (pickers ?? []).map((nickname) => nickname ?? unknownLabel).join(', ')
}

// Protoss needs no marker; who takes Terran or Zerg is worth knowing before picking.
export function formatPredictionPlayers(players: PredictionPlayer[]): string {
  return players
    .map((player) => {
      const name = player.nickname ?? '-'
      return player.assignedRace === 'T' || player.assignedRace === 'Z' ? `${name} (${player.assignedRace})` : name
    })
    .join(', ')
}
