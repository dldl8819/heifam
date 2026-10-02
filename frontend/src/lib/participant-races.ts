import { normalizeRaceComposition, type RaceCompositionTeamSize } from '@/lib/race-composition'
import type { AssignedRace, RaceComposition } from '@/types/api'

export const ASSIGNED_RACES: readonly AssignedRace[] = ['P', 'T', 'Z']

const RACE_ORDER: Record<AssignedRace, number> = { P: 0, T: 1, Z: 2 }

export function normalizeAssignedRace(value: unknown): AssignedRace | null {
  if (typeof value !== 'string') {
    return null
  }
  const normalized = value.trim().toUpperCase()
  return normalized === 'P' || normalized === 'T' || normalized === 'Z' ? normalized : null
}

export function composeTeamRaces(races: ReadonlyArray<AssignedRace | null>): string | null {
  if (races.length === 0 || races.some((race) => race === null)) {
    return null
  }
  return [...(races as AssignedRace[])].sort((left, right) => RACE_ORDER[left] - RACE_ORDER[right]).join('')
}

// Protoss needs no marker, so only the players recorded on Terran or Zerg get one. Races the
// recorder never confirmed were assigned automatically, so they are not shown as if recorded.
export function formatRecordedRacePlayer(
  nickname: string,
  race: AssignedRace | null,
  racesRecorded: boolean,
): string {
  return racesRecorded && (race === 'T' || race === 'Z') ? `${nickname} (${race})` : nickname
}

// A match stores one race composition for both teams, so both must add up to the same supported one.
export function resolveCompositionFromTeamRaces(
  teamSize: RaceCompositionTeamSize,
  homeRaces: ReadonlyArray<AssignedRace | null>,
  awayRaces: ReadonlyArray<AssignedRace | null>,
): RaceComposition | null {
  const home = composeTeamRaces(homeRaces)
  if (home === null || home !== composeTeamRaces(awayRaces)) {
    return null
  }
  return normalizeRaceComposition(teamSize, home)
}
