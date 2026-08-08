import {
  normalizeRaceComposition,
  resolveSharedRaceComposition,
  type RaceCompositionTeamSize,
} from '@/lib/race-composition'
import type { MatchResultUpdateRequest, TeamSide } from '@/types/api'

export type MatchResultEditSnapshot = {
  winnerTeam: TeamSide | null
  teamSize: RaceCompositionTeamSize | null
  homeRaceComposition: string | null | undefined
  awayRaceComposition: string | null | undefined
}

export function buildMatchResultUpdateRequest(
  current: MatchResultEditSnapshot,
  selectedWinnerTeam: TeamSide,
  selectedRaceComposition: string | null | undefined,
): MatchResultUpdateRequest | null {
  const winnerChanged = current.winnerTeam !== selectedWinnerTeam
  const currentRaceComposition =
    current.teamSize === null
      ? null
      : resolveSharedRaceComposition(
          current.teamSize,
          current.homeRaceComposition,
          current.awayRaceComposition,
        )
  const nextRaceComposition =
    current.teamSize === null
      ? null
      : normalizeRaceComposition(current.teamSize, selectedRaceComposition)
  const raceCompositionChanged =
    nextRaceComposition !== null && nextRaceComposition !== currentRaceComposition

  if (!winnerChanged && !raceCompositionChanged) {
    return null
  }

  return {
    winnerTeam: selectedWinnerTeam,
    ...(raceCompositionChanged ? { raceComposition: nextRaceComposition } : {}),
  }
}
