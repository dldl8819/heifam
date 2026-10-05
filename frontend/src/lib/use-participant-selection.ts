'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { apiClient } from '@/lib/api'
import { t } from '@/lib/i18n'
import { recallPageState, rememberPageState } from '@/lib/page-memory'
import {
  autocompleteParticipantSlot,
  compactParticipantIds,
  createParticipantSlots,
  fillParticipantSlotLabels,
  type ParticipantSlotState,
  updateParticipantSlotInput,
} from '@/lib/participant-slots'
import type { BalancePlayerOption } from '@/types/api'

type ParticipantSelectionOptions = {
  groupId: number
  showMmr: boolean
  minimumSlots: number
  // Called whenever the selection changes, so a page can clear results made from the old one.
  onSelectionChange?: () => void
  // Given, the slots are kept while the member visits another menu (lib/page-memory: in memory only).
  memoryKey?: string
}

/** The group's players and the tier-board slots picked from them, as the multi-balance and tournament pages use. */
export function useParticipantSelection({
  groupId,
  showMmr,
  minimumSlots,
  onSelectionChange,
  memoryKey,
}: ParticipantSelectionOptions) {
  const [players, setPlayers] = useState<BalancePlayerOption[]>([])
  const [playersLoading, setPlayersLoading] = useState<boolean>(true)
  const [playersError, setPlayersError] = useState<string | null>(null)
  const [participantSlots, setParticipantSlots] = useState<ParticipantSlotState[]>(
    () =>
      (memoryKey ? recallPageState<ParticipantSlotState[]>(memoryKey) : null) ?? createParticipantSlots(minimumSlots),
  )

  useEffect(() => {
    if (memoryKey) {
      rememberPageState(memoryKey, participantSlots)
    }
  }, [memoryKey, participantSlots])
  const participantInputRefs = useRef<Array<HTMLInputElement | null>>([])

  useEffect(() => {
    let active = true

    const loadPlayers = async () => {
      setPlayersLoading(true)
      setPlayersError(null)

      try {
        const response = await apiClient.getGroupPlayers(groupId)
        if (!active) {
          return
        }

        const mappedPlayers: BalancePlayerOption[] = response
          .map((player) => ({
            id: player.id,
            nickname: player.nickname,
            race: player.race,
            currentMmr: player.currentMmr,
            tier: player.liveTier ?? player.tier,
          }))
          .sort((a, b) => {
            if (!showMmr) {
              return a.nickname.localeCompare(b.nickname, 'ko-KR')
            }

            const aMmr = typeof a.currentMmr === 'number' ? a.currentMmr : -1
            const bMmr = typeof b.currentMmr === 'number' ? b.currentMmr : -1
            if (bMmr !== aMmr) {
              return bMmr - aMmr
            }

            return a.nickname.localeCompare(b.nickname, 'ko-KR')
          })

        setPlayers(mappedPlayers)
      } catch {
        if (!active) {
          return
        }
        setPlayers([])
        setPlayersError(t('multiBalance.loadError'))
      } finally {
        if (active) {
          setPlayersLoading(false)
        }
      }
    }

    void loadPlayers()

    return () => {
      active = false
    }
  }, [groupId, showMmr])

  useEffect(() => {
    if (players.length === 0) {
      return
    }

    setParticipantSlots((previous) => fillParticipantSlotLabels(previous, players, minimumSlots))
  }, [minimumSlots, players])

  const selectedIds = useMemo(() => compactParticipantIds(participantSlots), [participantSlots])

  const resetSelection = useCallback(() => {
    setParticipantSlots(createParticipantSlots(minimumSlots))
  }, [minimumSlots])

  const handleSlotInputChange = (index: number, value: string) => {
    onSelectionChange?.()
    setParticipantSlots((previous) =>
      updateParticipantSlotInput({
        slots: previous,
        index,
        inputValue: value,
        players,
        showMmr,
        minimumSlots,
      }),
    )
  }

  const handleSlotAutocomplete = (index: number): boolean => {
    const nextSlots = autocompleteParticipantSlot({
      slots: participantSlots,
      index,
      players,
      minimumSlots,
    })
    if (!nextSlots) {
      return false
    }

    onSelectionChange?.()
    setParticipantSlots(nextSlots)

    window.requestAnimationFrame(() => {
      const nextInput = participantInputRefs.current[index + 1]
      if (!nextInput) {
        return
      }
      nextInput.focus()
      nextInput.select()
    })

    return true
  }

  return {
    players,
    playersLoading,
    playersError,
    participantSlots,
    participantInputRefs,
    selectedIds,
    resetSelection,
    handleSlotInputChange,
    handleSlotAutocomplete,
  }
}
