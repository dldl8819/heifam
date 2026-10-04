import type { AccessEmailEntry, PointReason } from '@/types/api'

export const POINT_ADJUSTMENT_MAX = 1000
export const POINT_MEMO_MAX_LENGTH = 200

const KST_OFFSET_MS = 9 * 60 * 60 * 1000

const REASON_KEYS: Record<PointReason, string> = {
  DAILY_LOGIN: 'points.reasons.dailyLogin',
  MATCH_RESULT: 'points.reasons.matchResult',
  MATCH_RESULT_REVERSED: 'points.reasons.matchResultReversed',
  ADJUSTMENT: 'points.reasons.adjustment',
  PREDICTION_HIT: 'points.reasons.predictionHit',
  PREDICTION_HIT_REVERSED: 'points.reasons.predictionHitReversed',
}

export type PointMemberOption = {
  email: string
  label: string
}

/** The month in Korea at the given moment, as YYYY-MM. Points count days and months in Korean time. */
export function currentKstMonth(now: Date = new Date()): string {
  const kst = new Date(now.getTime() + KST_OFFSET_MS)
  return `${kst.getUTCFullYear()}-${String(kst.getUTCMonth() + 1).padStart(2, '0')}`
}

/** Moves a YYYY-MM month by whole months. */
export function shiftMonth(month: string, delta: number): string {
  const [yearText, monthText] = month.split('-')
  const index = Number(yearText) * 12 + (Number(monthText) - 1) + delta
  const year = Math.floor(index / 12)
  return `${year}-${String(index - year * 12 + 1).padStart(2, '0')}`
}

export function formatPointAmount(amount: number): string {
  return `${amount > 0 ? '+' : ''}${amount.toLocaleString('ko-KR')}p`
}

/** The i18n key for a ledger reason, or null for one this screen does not know yet. */
export function pointReasonKey(reason: string): string | null {
  return (REASON_KEYS as Record<string, string>)[reason] ?? null
}

/** A whole number from -1000 to 1000 other than 0, or null. */
export function parseAdjustmentAmount(value: string): number | null {
  const trimmed = value.trim()
  if (!/^[-+]?\d+$/.test(trimmed)) {
    return null
  }
  const amount = Number(trimmed)
  if (amount === 0 || Math.abs(amount) > POINT_ADJUSTMENT_MAX) {
    return null
  }
  return amount
}

/** Everyone who can hold points, once each, by the name shown; a nickname beats a bare email. */
export function buildPointMemberOptions(lists: AccessEmailEntry[][]): PointMemberOption[] {
  const byEmail = new Map<string, PointMemberOption>()
  for (const list of lists) {
    for (const entry of list) {
      const email = entry.email.trim().toLowerCase()
      const nickname = entry.nickname?.trim() ?? ''
      const existing = byEmail.get(email)
      if (email.length === 0 || (existing && (existing.label !== existing.email || nickname.length === 0))) {
        continue
      }
      byEmail.set(email, { email, label: nickname.length > 0 ? nickname : email })
    }
  }
  return [...byEmail.values()].sort((left, right) => left.label.localeCompare(right.label, 'ko'))
}
