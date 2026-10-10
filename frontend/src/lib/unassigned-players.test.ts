import { describe, expect, it } from 'vitest'
import { unassignedNicknames } from './unassigned-players'
import type { BalancePlayerOption } from '@/types/api'

const players: BalancePlayerOption[] = [
  { id: 1, nickname: 'PlayerBravo', race: 'P', tier: 'UNASSIGNED' },
  { id: 2, nickname: 'PlayerAlpha', race: 'T', tier: 'UNASSIGNED' },
  { id: 3, nickname: 'PlayerCharlie', race: 'Z', tier: 'A' },
  { id: 4, nickname: 'PlayerDelta', race: 'P' },
]

describe('unassignedNicknames', () => {
  it('names the chosen players whose tier is still to be set, in name order', () => {
    expect(unassignedNicknames([1, 2, 3, 4], players)).toEqual(['PlayerAlpha', 'PlayerBravo'])
  })

  it('leaves out players not chosen, and guests without an id', () => {
    expect(unassignedNicknames([3, 4, undefined, null], players)).toEqual([])
    expect(unassignedNicknames([1], players)).toEqual(['PlayerBravo'])
  })
})
