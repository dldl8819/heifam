import type { RaceComposition } from '@/types/api'

export type RaceCompositionTeamSize = 2 | 3

const RACE_COMPOSITION_OPTIONS: Record<RaceCompositionTeamSize, RaceComposition[]> = {
  2: ['PP', 'PT', 'PZ'],
  3: ['PPP', 'PPT', 'PPZ', 'PTZ'],
}

export function getRaceCompositionOptions(teamSize: RaceCompositionTeamSize): RaceComposition[] {
  return RACE_COMPOSITION_OPTIONS[teamSize]
}

export function normalizeRaceComposition(
  teamSize: RaceCompositionTeamSize,
  value: string | null | undefined,
): RaceComposition | null {
  if (typeof value !== 'string') {
    return null
  }

  const normalized = value.trim().toUpperCase()
  return getRaceCompositionOptions(teamSize).includes(normalized as RaceComposition)
    ? (normalized as RaceComposition)
    : null
}
