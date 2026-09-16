import type { LedgerServerCost } from '@/types/api'

export type ServerCostSummary = {
  totalKrw: number
  totalUsd: number
  reimbursedKrw: number
  reimbursedCount: number
  pendingKrw: number
  pendingCount: number
  missingKrwCount: number
}

/**
 * Totals for the server cost tab. Won totals only count costs whose card amount is known; the rest
 * are counted as missing so they are not silently treated as zero.
 */
export function summarizeServerCosts(costs: LedgerServerCost[]): ServerCostSummary {
  const summary: ServerCostSummary = {
    totalKrw: 0,
    totalUsd: 0,
    reimbursedKrw: 0,
    reimbursedCount: 0,
    pendingKrw: 0,
    pendingCount: 0,
    missingKrwCount: 0,
  }
  for (const cost of costs) {
    summary.totalUsd += cost.usdAmount ?? 0
    if (cost.krwAmount === null) {
      summary.missingKrwCount += 1
      continue
    }
    summary.totalKrw += cost.krwAmount
    if (cost.reimbursedDate) {
      summary.reimbursedKrw += cost.krwAmount
      summary.reimbursedCount += 1
    } else {
      summary.pendingKrw += cost.krwAmount
      summary.pendingCount += 1
    }
  }
  summary.totalUsd = Math.round(summary.totalUsd * 100) / 100
  return summary
}

export function formatUsd(value: number | null): string {
  return value === null ? '-' : `$${value.toFixed(2)}`
}

/** Parses an optional amount field: blank -> null, a non-negative number -> that number, otherwise 'invalid'. */
export function parseOptionalAmount(input: string): number | null | 'invalid' {
  const trimmed = input.trim().replace(/,/g, '')
  if (!trimmed) {
    return null
  }
  const value = Number(trimmed)
  return Number.isFinite(value) && value >= 0 ? value : 'invalid'
}
