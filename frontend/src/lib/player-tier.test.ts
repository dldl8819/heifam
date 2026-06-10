import { describe, expect, it } from 'vitest'

import { toTierOrder } from './player-tier'

describe('toTierOrder', () => {
  it('orders ranked tiers from S down to D and then unassigned', () => {
    expect(toTierOrder('S')).toBeLessThan(toTierOrder('A+'))
    expect(toTierOrder('A')).toBeLessThan(toTierOrder('B+'))
    expect(toTierOrder('C-')).toBeLessThan(toTierOrder('D'))
    expect(toTierOrder('D')).toBeLessThan(toTierOrder('UNASSIGNED'))
  })
})
