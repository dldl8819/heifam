/** A win rate as the balance pages show it: a 0-1 ratio (or a percentage) with two decimals. */
export function formatPercent(value: number): string {
  const percent = value <= 1 ? value * 100 : value
  return `${percent.toFixed(2)}%`
}
