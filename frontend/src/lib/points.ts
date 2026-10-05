import type { PointReason } from '@/types/api'


const KST_OFFSET_MS = 9 * 60 * 60 * 1000

const REASON_KEYS: Record<PointReason, string> = {
  DAILY_LOGIN: 'points.reasons.dailyLogin',
  MATCH_RESULT: 'points.reasons.matchResult',
  MATCH_RESULT_REVERSED: 'points.reasons.matchResultReversed',
  ADJUSTMENT: 'points.reasons.adjustment',
  PREDICTION_HIT: 'points.reasons.predictionHit',
  PREDICTION_HIT_REVERSED: 'points.reasons.predictionHitReversed',
  NOTICE_READ: 'points.reasons.noticeRead',
  NOTICE_LIKE: 'points.reasons.noticeLike',
  NOTICE_COMMENT: 'points.reasons.noticeComment',
  MATCH_CONFIRM: 'points.reasons.matchConfirm',
  MATCH_CONFIRM_REVERSED: 'points.reasons.matchConfirmReversed',
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


