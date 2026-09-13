/** Formatting and axis helpers for the donation ledger dashboard. */

export type AxisScale = {
  min: number
  max: number
  step: number
}

const STEP_MULTIPLIERS = [1, 2, 2.5, 5, 10]
const EMPTY_RANGE_STEP = 10_000

/**
 * Picks a readable axis for won amounts: a step of 1, 2, 2.5 or 5 times a power of ten, with both
 * bounds rounded out to whole steps so every gridline lands on a clean value.
 */
export function niceAxis(minValue: number, maxValue: number, maxTicks = 6): AxisScale {
  const low = Math.min(minValue, maxValue)
  const high = Math.max(minValue, maxValue)
  const span = high - low
  if (span <= 0) {
    const min = Math.floor(low / EMPTY_RANGE_STEP) * EMPTY_RANGE_STEP
    return { min, max: min + EMPTY_RANGE_STEP, step: EMPTY_RANGE_STEP }
  }

  const rawStep = span / Math.max(1, maxTicks)
  const magnitude = 10 ** Math.floor(Math.log10(rawStep))
  const step = (STEP_MULTIPLIERS.find((multiplier) => multiplier * magnitude >= rawStep) ?? 10) * magnitude
  const min = Math.floor(low / step) * step
  const max = Math.max(min + step, Math.ceil(high / step) * step)
  return { min, max, step }
}

export function axisTicks(scale: AxisScale): number[] {
  const count = Math.round((scale.max - scale.min) / scale.step)
  return Array.from({ length: count + 1 }, (_, index) => Math.round(scale.min + index * scale.step))
}

function trimDecimal(value: number): string {
  return Number.isInteger(value) ? String(value) : String(Number(value.toFixed(1)))
}

/** Compact won label for chart axes: 150000 -> "15만", 250000000 -> "2.5억". */
export function formatManwon(value: number): string {
  if (value === 0) {
    return '0'
  }
  const sign = value < 0 ? '−' : ''
  const absolute = Math.abs(value)
  if (absolute >= 100_000_000) {
    return `${sign}${trimDecimal(absolute / 100_000_000)}억`
  }
  if (absolute >= 10_000) {
    return `${sign}${trimDecimal(absolute / 10_000)}만`
  }
  return `${sign}${absolute.toLocaleString('ko-KR')}`
}

export function formatWon(value: number): string {
  return `${value.toLocaleString('ko-KR')}원`
}

export function formatSignedWon(value: number): string {
  const sign = value > 0 ? '+' : value < 0 ? '−' : ''
  return `${sign}${Math.abs(value).toLocaleString('ko-KR')}원`
}

/** "2026-03" -> "3월", or "2026년 3월" when the year has to be shown. */
export function monthLabel(month: string, includeYear = false): string {
  const [year, monthNumber] = month.split('-').map(Number)
  return includeYear ? `${year}년 ${monthNumber}월` : `${monthNumber}월`
}

/** "2026-09-07" -> "2026년 9월 7일". */
export function formatLedgerDate(date: string): string {
  const [year, month, day] = date.split('-').map(Number)
  return `${year}년 ${month}월 ${day}일`
}

/**
 * One tick per calendar month from the first to the last date. The first tick sits on the first
 * date itself rather than the 1st of its month, so it never falls left of the plotted range.
 */
export function monthTicks(firstDate: string, lastDate: string): { month: string; date: string }[] {
  const ticks: { month: string; date: string }[] = []
  let [year, month] = firstDate.split('-').map(Number)
  const [lastYear, lastMonth] = lastDate.split('-').map(Number)
  while (year < lastYear || (year === lastYear && month <= lastMonth)) {
    const key = `${year}-${String(month).padStart(2, '0')}`
    ticks.push({ month: key, date: ticks.length === 0 ? firstDate : `${key}-01` })
    month += 1
    if (month > 12) {
      month = 1
      year += 1
    }
  }
  return ticks
}
