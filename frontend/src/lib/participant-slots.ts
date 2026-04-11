import type { PlayerTierStatus } from '@/types/api'

export type SelectableParticipant = {
  id: number
  nickname: string
  race: string
  tier?: PlayerTierStatus
  currentMmr?: number
}

export type ParticipantSlotState = {
  playerId: number | null
  inputValue: string
}

type UpdateParticipantSlotInputParams = {
  slots: ParticipantSlotState[]
  index: number
  inputValue: string
  players: SelectableParticipant[]
  showMmr: boolean
  minimumSlots: number
}

type AutocompleteParticipantSlotParams = {
  slots: ParticipantSlotState[]
  index: number
  players: SelectableParticipant[]
  minimumSlots: number
}

function createEmptySlot(): ParticipantSlotState {
  return {
    playerId: null,
    inputValue: '',
  }
}

function normalizeMinimumSlots(minimumSlots: number): number {
  if (!Number.isFinite(minimumSlots) || minimumSlots < 1) {
    return 1
  }
  return Math.max(1, Math.floor(minimumSlots))
}

function normalizeInputValue(value: string): string {
  return value.trim().toLowerCase()
}

function findPlayerById(
  players: SelectableParticipant[],
  playerId: number,
): SelectableParticipant | null {
  for (const player of players) {
    if (player.id === playerId) {
      return player
    }
  }
  return null
}

function isPlayerAlreadySelected(
  slots: ParticipantSlotState[],
  playerId: number,
  currentIndex: number,
): boolean {
  return slots.some(
    (slot, slotIndex) => slotIndex !== currentIndex && slot.playerId === playerId,
  )
}

function normalizeMaxSlots(minimumSlots: number, maxSlots: number): number {
  const normalizedMinimumSlots = normalizeMinimumSlots(minimumSlots)
  if (!Number.isFinite(maxSlots) || maxSlots < normalizedMinimumSlots) {
    return normalizedMinimumSlots
  }
  return Math.max(normalizedMinimumSlots, Math.floor(maxSlots))
}

export function createParticipantSlots(minimumSlots: number): ParticipantSlotState[] {
  return Array.from({ length: normalizeMinimumSlots(minimumSlots) }, () => createEmptySlot())
}

export function compactParticipantIds(slots: ParticipantSlotState[]): number[] {
  return slots.flatMap((slot) =>
    typeof slot.playerId === 'number' && Number.isFinite(slot.playerId) ? [slot.playerId] : [],
  )
}

export function formatParticipantSlotLabel(
  player: SelectableParticipant,
  showMmr: boolean,
): string {
  const tierText = player.tier ? ` [${player.tier}]` : ''
  const mmrText =
    showMmr && typeof player.currentMmr === 'number' ? ` · ${player.currentMmr} MMR` : ''
  return `${player.nickname} (${player.race})${tierText}${mmrText}`
}

export function findParticipantByInputValue(
  players: SelectableParticipant[],
  inputValue: string,
  showMmr: boolean,
): SelectableParticipant | null {
  const normalizedInput = normalizeInputValue(inputValue)
  if (normalizedInput.length === 0) {
    return null
  }

  return (
    players.find((player) => normalizeInputValue(player.nickname) === normalizedInput) ??
    players.find(
      (player) =>
        normalizeInputValue(formatParticipantSlotLabel(player, showMmr)) === normalizedInput,
    ) ??
    players.find(
      (player) => normalizeInputValue(formatParticipantSlotLabel(player, true)) === normalizedInput,
    ) ??
    null
  )
}

export function normalizeParticipantSlots(
  slots: ParticipantSlotState[],
  minimumSlots: number,
  maxSlots: number,
): ParticipantSlotState[] {
  const normalizedMinimumSlots = normalizeMinimumSlots(minimumSlots)
  const normalizedMaxSlots = normalizeMaxSlots(normalizedMinimumSlots, maxSlots)
  const sanitized = slots.map((slot) => ({
    playerId:
      typeof slot.playerId === 'number' && Number.isFinite(slot.playerId) ? slot.playerId : null,
    inputValue: typeof slot.inputValue === 'string' ? slot.inputValue : '',
  }))

  let lastMeaningfulIndex = -1
  for (let index = 0; index < sanitized.length; index += 1) {
    const slot = sanitized[index]
    if (slot.playerId !== null || slot.inputValue.trim().length > 0) {
      lastMeaningfulIndex = index
    }
  }

  const desiredLength = Math.min(
    normalizedMaxSlots,
    Math.max(normalizedMinimumSlots, lastMeaningfulIndex + 2),
  )
  const next = sanitized.slice(0, desiredLength)
  while (next.length < desiredLength) {
    next.push(createEmptySlot())
  }
  return next
}

export function createParticipantSlotsFromIds(
  players: SelectableParticipant[],
  selectedIds: number[],
  minimumSlots: number,
): ParticipantSlotState[] {
  const normalizedMinimumSlots = normalizeMinimumSlots(minimumSlots)
  const slots = selectedIds.map((playerId) => {
    const player = findPlayerById(players, playerId)
    return {
      playerId,
      inputValue: player?.nickname ?? '',
    }
  })

  return normalizeParticipantSlots(
    slots,
    normalizedMinimumSlots,
    Math.max(normalizedMinimumSlots, players.length),
  )
}

export function fillParticipantSlotLabels(
  slots: ParticipantSlotState[],
  players: SelectableParticipant[],
  minimumSlots: number,
): ParticipantSlotState[] {
  const normalizedMinimumSlots = normalizeMinimumSlots(minimumSlots)

  return normalizeParticipantSlots(
    slots.map((slot) => {
      if (slot.playerId === null) {
        return slot
      }

      const player = findPlayerById(players, slot.playerId)
      if (!player) {
        return {
          playerId: null,
          inputValue: slot.inputValue,
        }
      }

      if (slot.inputValue.trim().length > 0) {
        return slot
      }

      return {
        playerId: player.id,
        inputValue: player.nickname,
      }
    }),
    normalizedMinimumSlots,
    Math.max(normalizedMinimumSlots, players.length),
  )
}

export function updateParticipantSlotInput({
  slots,
  index,
  inputValue,
  players,
  showMmr,
  minimumSlots,
}: UpdateParticipantSlotInputParams): ParticipantSlotState[] {
  const normalizedMinimumSlots = normalizeMinimumSlots(minimumSlots)
  const next = normalizeParticipantSlots(
    slots,
    normalizedMinimumSlots,
    Math.max(normalizedMinimumSlots, players.length),
  )
  const matchedPlayer = findParticipantByInputValue(players, inputValue, showMmr)
  const nextPlayerId =
    matchedPlayer && !isPlayerAlreadySelected(next, matchedPlayer.id, index)
      ? matchedPlayer.id
      : null

  next[index] = {
    playerId: nextPlayerId,
    inputValue,
  }

  return normalizeParticipantSlots(
    next,
    normalizedMinimumSlots,
    Math.max(normalizedMinimumSlots, players.length),
  )
}

export function autocompleteParticipantSlot({
  slots,
  index,
  players,
  minimumSlots,
}: AutocompleteParticipantSlotParams): ParticipantSlotState[] | null {
  const normalizedInput = normalizeInputValue(slots[index]?.inputValue ?? '')
  if (normalizedInput.length === 0) {
    return null
  }

  const matches = players.filter((player) =>
    normalizeInputValue(player.nickname).startsWith(normalizedInput),
  )
  if (matches.length !== 1) {
    return null
  }

  const matchedPlayer = matches[0]
  const normalizedMinimumSlots = normalizeMinimumSlots(minimumSlots)
  const next = normalizeParticipantSlots(
    slots,
    normalizedMinimumSlots,
    Math.max(normalizedMinimumSlots, players.length),
  )

  if (isPlayerAlreadySelected(next, matchedPlayer.id, index)) {
    return null
  }

  next[index] = {
    playerId: matchedPlayer.id,
    inputValue: matchedPlayer.nickname,
  }

  return normalizeParticipantSlots(
    next,
    normalizedMinimumSlots,
    Math.max(normalizedMinimumSlots, players.length),
  )
}
