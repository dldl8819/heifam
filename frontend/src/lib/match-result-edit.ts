import {
  normalizeRaceComposition,
  resolveSharedRaceComposition,
  type RaceCompositionTeamSize,
} from '@/lib/race-composition'
import type { AssignedRace, MatchResultUpdateRequest, TeamSide } from '@/types/api'

export type MatchResultEditSnapshot = {
  winnerTeam: TeamSide | null
  teamSize: RaceCompositionTeamSize | null
  homeRaceComposition: string | null | undefined
  awayRaceComposition: string | null | undefined
}

export type ParticipantRaceEdit = {
  playerId: number | null
  originalRace: AssignedRace | null
  race: AssignedRace | null
}

// With participantRaceEdits the composition follows from the players' races, so it only
// counts as changed once a race was changed; the races are sent only when they add up.
export function buildMatchResultUpdateRequest(
  current: MatchResultEditSnapshot,
  selectedWinnerTeam: TeamSide,
  selectedRaceComposition: string | null | undefined,
  participantRaceEdits: ParticipantRaceEdit[] = [],
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
  const racesChanged = participantRaceEdits.some((edit) => edit.race !== edit.originalRace)
  const raceCompositionChanged =
    participantRaceEdits.length === 0 &&
    nextRaceComposition !== null &&
    nextRaceComposition !== currentRaceComposition

  if (!winnerChanged && !raceCompositionChanged && !racesChanged) {
    return null
  }

  const editablePlayerRaces = participantRaceEdits.filter(
    (edit): edit is ParticipantRaceEdit & { playerId: number } => edit.playerId !== null,
  )
  const sendRaces =
    racesChanged &&
    nextRaceComposition !== null &&
    editablePlayerRaces.every((edit) => edit.race !== null)

  return {
    winnerTeam: selectedWinnerTeam,
    ...(raceCompositionChanged || sendRaces ? { raceComposition: nextRaceComposition ?? undefined } : {}),
    ...(sendRaces
      ? {
          participantRaces: editablePlayerRaces.map((edit) => ({
            playerId: edit.playerId,
            race: edit.race as AssignedRace,
          })),
        }
      : {}),
  }
}
