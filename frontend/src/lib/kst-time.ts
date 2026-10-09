const MONTH_DAY_TIME = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  month: 'numeric',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

const FULL_DATE_TIME = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

function formatWith(formatter: Intl.DateTimeFormat, value: string | null | undefined): string {
  if (!value) {
    return ''
  }
  const parsed = Date.parse(value)
  return Number.isNaN(parsed) ? '' : formatter.format(new Date(parsed))
}

/** A moment in Korean time as the short "10. 6. 14:30" the pages show; empty for a bad value. */
export function formatKstDateTime(value: string | null | undefined): string {
  return formatWith(MONTH_DAY_TIME, value)
}

/**
 * A moment in Korean time with its year, "2026. 10. 06. 14:30", for what may be read long after it
 * was written, such as a comment; empty for a bad value.
 */
export function formatKstFullDateTime(value: string | null | undefined): string {
  return formatWith(FULL_DATE_TIME, value)
}
