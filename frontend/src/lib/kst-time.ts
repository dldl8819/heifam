const MONTH_DAY_TIME = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  month: 'numeric',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

/** A moment in Korean time as the short "10. 6. 14:30" the pages show; empty for a bad value. */
export function formatKstDateTime(value: string | null | undefined): string {
  if (!value) {
    return ''
  }
  const parsed = Date.parse(value)
  return Number.isNaN(parsed) ? '' : MONTH_DAY_TIME.format(new Date(parsed))
}
