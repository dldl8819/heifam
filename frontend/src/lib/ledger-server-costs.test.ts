import { describe, expect, it } from 'vitest'
import { formatUsd, parseOptionalAmount, summarizeServerCosts } from '@/lib/ledger-server-costs'
import type { LedgerServerCost } from '@/types/api'

function cost(overrides: Partial<LedgerServerCost>): LedgerServerCost {
  return {
    id: 1,
    serviceName: 'Render',
    billingMonth: '2026-08',
    chargedDate: '2026-09-01',
    usdAmount: null,
    krwAmount: null,
    paidBy: null,
    reimbursedDate: null,
    memo: null,
    authorNickname: null,
    createdAt: '2026-09-16T00:00:00Z',
    ...overrides,
  }
}

describe('ledger server cost helpers', () => {
  it('splits known won amounts into reimbursed and pending and counts missing ones', () => {
    const summary = summarizeServerCosts([
      cost({ id: 1, usdAmount: 5.71, krwAmount: 8_100, reimbursedDate: '2026-09-20' }),
      cost({ id: 2, usdAmount: 7, krwAmount: 9_900 }),
      cost({ id: 3, usdAmount: 7.41 }),
    ])

    expect(summary).toEqual({
      totalKrw: 18_000,
      totalUsd: 20.12,
      reimbursedKrw: 8_100,
      reimbursedCount: 1,
      pendingKrw: 9_900,
      pendingCount: 1,
      missingKrwCount: 1,
    })
  })

  it('formats dollar amounts and blanks', () => {
    expect(formatUsd(7.4)).toBe('$7.40')
    expect(formatUsd(null)).toBe('-')
  })

  it('parses optional amounts', () => {
    expect(parseOptionalAmount('')).toBeNull()
    expect(parseOptionalAmount(' 10,300 ')).toBe(10_300)
    expect(parseOptionalAmount('7.41')).toBe(7.41)
    expect(parseOptionalAmount('-1')).toBe('invalid')
    expect(parseOptionalAmount('abc')).toBe('invalid')
  })
})
