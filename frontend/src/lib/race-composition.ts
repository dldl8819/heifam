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

export function resolveRaceCompositionTeamSize(
  homeTeamSize: number,
  awayTeamSize: number,
): RaceCompositionTeamSize | null {
  if (homeTeamSize !== awayTeamSize) {
    return null
  }

  return homeTeamSize === 2 || homeTeamSize === 3 ? homeTeamSize : null
}

export function resolveSharedRaceComposition(
  teamSize: RaceCompositionTeamSize,
  homeRaceComposition: string | null | undefined,
  awayRaceComposition: string | null | undefined,
): RaceComposition | null {
  const home = normalizeRaceComposition(teamSize, homeRaceComposition)
  const away = normalizeRaceComposition(teamSize, awayRaceComposition)

  return home !== null && home === away ? home : null
}

export function buildRaceCompositionUpdateFields(
  teamSize: RaceCompositionTeamSize | null,
  selectedRaceComposition: string | null | undefined,
  touched: boolean,
): { raceComposition?: RaceComposition } {
  if (!touched || teamSize === null) {
    return {}
  }

  const raceComposition = normalizeRaceComposition(teamSize, selectedRaceComposition)
  return raceComposition === null ? {} : { raceComposition }
}
