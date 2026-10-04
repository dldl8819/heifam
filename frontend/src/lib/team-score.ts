/** A win rate the backend already rounded to one decimal; '-' when there was nothing to count. */
export function formatScoreRate(value: number | null): string {
  return typeof value === 'number' ? `${value}%` : '-'
}
