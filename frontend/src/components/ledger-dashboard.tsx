'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import type { KeyboardEvent, PointerEvent } from 'react'
import { apiClient } from '@/lib/api'
import { Alert, AlertContent, AlertDescription, AlertIcon } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { t } from '@/lib/i18n'
import {
  axisTicks,
  formatLedgerDate,
  formatManwon,
  formatSignedWon,
  formatWon,
  monthLabel,
  monthTicks,
  niceAxis,
} from '@/lib/ledger-dashboard'
import type {
  LedgerDashboardBalancePoint,
  LedgerDashboardCategoryItem,
  LedgerDashboardMonthItem,
  LedgerDashboardResponse,
} from '@/types/api'

// Income/expense series colors: a pair checked for colorblind separation and contrast on both the
// light and dark chart surfaces. Text never uses them; they only mark bars, lines and swatches.
const INCOME_FILL = 'fill-[#2a78d6] dark:fill-[#3987e5]'
const INCOME_STROKE = 'stroke-[#2a78d6] dark:stroke-[#3987e5]'
const INCOME_BG = 'bg-[#2a78d6] dark:bg-[#3987e5]'
const EXPENSE_FILL = 'fill-[#eb6834] dark:fill-[#d95926]'
const EXPENSE_BG = 'bg-[#eb6834] dark:bg-[#d95926]'
const GRID_STROKE = 'stroke-slate-200 dark:stroke-slate-700'
const TICK_TEXT = 'fill-slate-500 text-[11px] tabular-nums dark:fill-slate-400'
const CARD = 'rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900'
const TOOLTIP_WIDTH = 200
const MIN_CHART_WIDTH = 280

type Tone = 'income' | 'expense'

type TooltipRow = {
  tone?: Tone
  value: string
  label: string
}

type TooltipState = {
  x: number
  y: number
  title: string
  rows: TooltipRow[]
}

type LedgerDashboardProps = {
  groupId: number
}

const key = (name: string) => `notices.donations.dashboard.${name}`

function useElementWidth<T extends HTMLElement>() {
  const ref = useRef<T | null>(null)
  const [width, setWidth] = useState(0)

  useEffect(() => {
    const node = ref.current
    if (!node) {
      return
    }
    const update = () => setWidth(node.clientWidth)
    update()
    if (typeof ResizeObserver === 'undefined') {
      return
    }
    const observer = new ResizeObserver(update)
    observer.observe(node)
    return () => observer.disconnect()
  }, [])

  return [ref, width] as const
}

function toneBackground(tone?: Tone): string {
  if (tone === 'income') {
    return INCOME_BG
  }
  if (tone === 'expense') {
    return EXPENSE_BG
  }
  return 'bg-transparent'
}

function ChartTooltip({ tooltip, containerWidth }: { tooltip: TooltipState; containerWidth: number }) {
  const left =
    tooltip.x + 16 + TOOLTIP_WIDTH > containerWidth ? Math.max(0, tooltip.x - 16 - TOOLTIP_WIDTH) : tooltip.x + 16
  return (
    <div
      role="status"
      className="pointer-events-none absolute z-10 rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs shadow-lg dark:border-slate-700 dark:bg-slate-900"
      style={{ left, top: Math.max(0, tooltip.y - 12), width: TOOLTIP_WIDTH }}
    >
      <p className="mb-1 text-slate-500 dark:text-slate-400">{tooltip.title}</p>
      {tooltip.rows.map((row) => (
        <p key={row.label} className="flex items-center gap-2">
          <span aria-hidden className={`h-0.5 w-3 shrink-0 rounded-full ${toneBackground(row.tone)}`} />
          <strong className="font-semibold tabular-nums text-slate-900 dark:text-slate-100">{row.value}</strong>
          <span className="text-slate-500 dark:text-slate-400">{row.label}</span>
        </p>
      ))}
    </div>
  )
}

function EquationRow({ label, value, emphasized = false }: { label: string; value: string; emphasized?: boolean }) {
  return (
    <div
      className={`flex items-baseline justify-between gap-3 ${
        emphasized ? 'border-t border-slate-200 pt-1.5 dark:border-slate-700' : ''
      }`}
    >
      <dt className="text-slate-500 dark:text-slate-400">{label}</dt>
      <dd
        className={`tabular-nums ${
          emphasized ? 'font-semibold text-slate-900 dark:text-slate-100' : 'text-slate-700 dark:text-slate-200'
        }`}
      >
        {value}
      </dd>
    </div>
  )
}

function StatTile({ tone, label, value, foot }: { tone: Tone; label: string; value: string; foot: string }) {
  return (
    <div className="rounded-lg border border-slate-200 p-3 dark:border-slate-700">
      <p className="flex items-center gap-1.5 text-xs text-slate-500 dark:text-slate-400">
        <span aria-hidden className={`h-2.5 w-2.5 shrink-0 rounded-sm ${toneBackground(tone)}`} />
        {label}
      </p>
      <p className="mt-1 text-lg font-semibold text-slate-900 dark:text-slate-100">{value}</p>
      <p className="text-xs text-slate-500 dark:text-slate-400">{foot}</p>
    </div>
  )
}

function SectionHeading({ title, hint }: { title: string; hint: string }) {
  return (
    <div className="mb-3">
      <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{title}</h3>
      <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">{hint}</p>
    </div>
  )
}

function BalanceChart({ points }: { points: LedgerDashboardBalancePoint[] }) {
  const [containerRef, containerWidth] = useElementWidth<HTMLDivElement>()
  const svgRef = useRef<SVGSVGElement | null>(null)
  const [activeIndex, setActiveIndex] = useState<number | null>(null)
  const [pointer, setPointer] = useState<{ x: number; y: number } | null>(null)

  if (points.length === 0) {
    return null
  }

  const width = Math.max(containerWidth, MIN_CHART_WIDTH)
  const height = 240
  const margin = { top: 12, right: 92, bottom: 28, left: 44 }
  const plotWidth = width - margin.left - margin.right
  const plotHeight = height - margin.top - margin.bottom
  const first = points[0]
  const last = points[points.length - 1]
  const firstTime = Date.parse(first.date)
  const lastTime = Date.parse(last.date)
  const x = (date: string) =>
    lastTime === firstTime
      ? margin.left + plotWidth / 2
      : margin.left + ((Date.parse(date) - firstTime) / (lastTime - firstTime)) * plotWidth
  const balances = points.map((point) => point.balance)
  const scale = niceAxis(Math.min(...balances), Math.max(...balances))
  const y = (value: number) => margin.top + (1 - (value - scale.min) / (scale.max - scale.min)) * plotHeight

  const path = points
    .map((point, index) =>
      index === 0 ? `M${x(point.date)},${y(point.balance)}` : `H${x(point.date)}V${y(point.balance)}`
    )
    .join('')

  // Skip month labels that would collide on a long range.
  const visibleMonthTicks: { month: string; date: string }[] = []
  for (const tick of monthTicks(first.date, last.date)) {
    const previous = visibleMonthTicks[visibleMonthTicks.length - 1]
    if (!previous || x(tick.date) - x(previous.date) >= 36) {
      visibleMonthTicks.push(tick)
    }
  }

  const clear = () => {
    setActiveIndex(null)
    setPointer(null)
  }

  const handlePointerMove = (event: PointerEvent<SVGRectElement>) => {
    const svgRect = svgRef.current?.getBoundingClientRect()
    const containerRect = containerRef.current?.getBoundingClientRect()
    if (!svgRect || !containerRect || svgRect.width === 0) {
      return
    }
    const plotX = ((event.clientX - svgRect.left) * width) / svgRect.width
    let nearest = 0
    points.forEach((point, index) => {
      if (Math.abs(x(point.date) - plotX) < Math.abs(x(points[nearest].date) - plotX)) {
        nearest = index
      }
    })
    setActiveIndex(nearest)
    setPointer({ x: event.clientX - containerRect.left, y: event.clientY - containerRect.top })
  }

  const handleKeyDown = (event: KeyboardEvent<SVGSVGElement>) => {
    const direction = event.key === 'ArrowLeft' ? -1 : event.key === 'ArrowRight' ? 1 : 0
    if (direction === 0) {
      return
    }
    event.preventDefault()
    setPointer(null)
    setActiveIndex((current) =>
      Math.min(points.length - 1, Math.max(0, (current ?? points.length - 1) + direction))
    )
  }

  const active = activeIndex === null ? null : points[activeIndex]
  const tooltip: TooltipState | null = active
    ? {
        x: pointer?.x ?? x(active.date),
        y: pointer?.y ?? y(active.balance),
        title: formatLedgerDate(active.date),
        rows: [
          { tone: 'income', value: formatWon(active.balance), label: t(key('balanceTooltipBalance')) },
          { value: formatSignedWon(active.change), label: t(key('balanceTooltipChange')) },
        ],
      }
    : null

  return (
    <div ref={containerRef} className="relative">
      <svg
        ref={svgRef}
        viewBox={`0 0 ${width} ${height}`}
        className="block h-auto w-full overflow-visible rounded focus:outline-none focus-visible:ring-2 focus-visible:ring-slate-400"
        role="img"
        tabIndex={0}
        aria-label={t(key('balanceAria'), { balance: formatWon(last.balance) })}
        onKeyDown={handleKeyDown}
        onFocus={() => setActiveIndex((current) => current ?? points.length - 1)}
        onBlur={clear}
      >
        {axisTicks(scale).map((tick) => (
          <g key={tick}>
            <line x1={margin.left} x2={margin.left + plotWidth} y1={y(tick)} y2={y(tick)} className={GRID_STROKE} strokeWidth={1} />
            <text x={margin.left - 8} y={y(tick) + 4} textAnchor="end" className={TICK_TEXT}>
              {formatManwon(tick)}
            </text>
          </g>
        ))}
        {visibleMonthTicks.map((tick, index) => (
          <text
            key={tick.month}
            x={x(tick.date)}
            y={height - 8}
            textAnchor={index === 0 ? 'start' : 'middle'}
            className={TICK_TEXT}
          >
            {monthLabel(tick.month)}
          </text>
        ))}
        <path d={path} fill="none" className={INCOME_STROKE} strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" />
        {active && (
          <line
            x1={x(active.date)}
            x2={x(active.date)}
            y1={margin.top}
            y2={margin.top + plotHeight}
            className="stroke-slate-300 dark:stroke-slate-600"
            strokeWidth={1}
          />
        )}
        <circle
          cx={x(last.date)}
          cy={y(last.balance)}
          r={4}
          className={`${INCOME_FILL} stroke-white dark:stroke-slate-900`}
          strokeWidth={2}
        />
        {active && (
          <circle
            cx={x(active.date)}
            cy={y(active.balance)}
            r={5}
            className={`${INCOME_FILL} stroke-white dark:stroke-slate-900`}
            strokeWidth={2}
          />
        )}
        <text
          x={x(last.date) + 10}
          y={y(last.balance) + 4}
          className="fill-slate-900 text-xs font-semibold dark:fill-slate-100"
        >
          {formatWon(last.balance)}
        </text>
        <rect
          x={margin.left - 6}
          y={margin.top}
          width={plotWidth + 12}
          height={plotHeight}
          fill="transparent"
          onPointerMove={handlePointerMove}
          onPointerLeave={clear}
        />
      </svg>
      {tooltip && <ChartTooltip tooltip={tooltip} containerWidth={containerWidth || width} />}
    </div>
  )
}

function columnPath(left: number, top: number, barWidth: number, baseline: number): string {
  const radius = Math.min(4, barWidth / 2, Math.max(0, baseline - top))
  return [
    `M${left},${baseline}`,
    `V${top + radius}`,
    `Q${left},${top} ${left + radius},${top}`,
    `H${left + barWidth - radius}`,
    `Q${left + barWidth},${top} ${left + barWidth},${top + radius}`,
    `V${baseline}Z`,
  ].join('')
}

function MonthlyChart({ months, spansYears }: { months: LedgerDashboardMonthItem[]; spansYears: boolean }) {
  const [containerRef, containerWidth] = useElementWidth<HTMLDivElement>()
  const [activeIndex, setActiveIndex] = useState<number | null>(null)
  const [pointer, setPointer] = useState<{ x: number; y: number } | null>(null)

  if (months.length === 0) {
    return null
  }

  const width = Math.max(containerWidth, MIN_CHART_WIDTH)
  const height = 220
  const margin = { top: 12, right: 8, bottom: 28, left: 44 }
  const plotWidth = width - margin.left - margin.right
  const plotHeight = height - margin.top - margin.bottom
  const scale = niceAxis(0, Math.max(1, ...months.map((month) => Math.max(month.income, month.totalExpense))))
  const y = (value: number) => margin.top + (1 - (value - scale.min) / (scale.max - scale.min)) * plotHeight
  const baseline = y(0)
  const band = plotWidth / months.length
  const barWidth = Math.max(3, Math.min(24, (band - 18) / 2))
  const labelEvery = Math.max(1, Math.ceil(48 / band))
  const labelFor = (month: LedgerDashboardMonthItem, index: number) =>
    monthLabel(month.month, spansYears && (index === 0 || month.month.endsWith('-01')))

  const clear = () => {
    setActiveIndex(null)
    setPointer(null)
  }

  const active = activeIndex === null ? null : months[activeIndex]
  const tooltip: TooltipState | null =
    active && activeIndex !== null
      ? {
          x: pointer?.x ?? margin.left + band * activeIndex + band / 2,
          y: pointer?.y ?? margin.top + 16,
          title: monthLabel(active.month, spansYears),
          rows: [
            {
              tone: 'income',
              value: formatWon(active.income),
              label: t(key('tooltipIncome'), { count: String(active.incomeCount) }),
            },
            {
              tone: 'expense',
              value: formatWon(active.totalExpense),
              label: t(key('tooltipExpense'), { count: String(active.expenseCount) }),
            },
            ...(active.serverCostReimbursed > 0
              ? [{ value: formatWon(active.serverCostReimbursed), label: t(key('tooltipServerCost')) }]
              : []),
            { value: formatWon(active.endBalance), label: t(key('table.endBalance')) },
          ],
        }
      : null

  const handlePointerMove = (index: number, event: PointerEvent<SVGRectElement>) => {
    const containerRect = containerRef.current?.getBoundingClientRect()
    setActiveIndex(index)
    setPointer(containerRect ? { x: event.clientX - containerRect.left, y: event.clientY - containerRect.top } : null)
  }

  return (
    <div ref={containerRef} className="relative">
      <svg
        viewBox={`0 0 ${width} ${height}`}
        className="block h-auto w-full overflow-visible"
        role="img"
        aria-label={t(key('monthlyAria'))}
      >
        {axisTicks(scale).map((tick) => (
          <g key={tick}>
            <line
              x1={margin.left}
              x2={margin.left + plotWidth}
              y1={y(tick)}
              y2={y(tick)}
              className={tick === 0 ? 'stroke-slate-300 dark:stroke-slate-600' : GRID_STROKE}
              strokeWidth={1}
            />
            <text x={margin.left - 8} y={y(tick) + 4} textAnchor="end" className={TICK_TEXT}>
              {formatManwon(tick)}
            </text>
          </g>
        ))}
        {months.map((month, index) => {
          const center = margin.left + band * index + band / 2
          return (
            <g key={month.month}>
              {activeIndex === index && (
                <rect
                  x={margin.left + band * index}
                  y={margin.top}
                  width={band}
                  height={plotHeight}
                  className="fill-slate-900/5 dark:fill-white/5"
                />
              )}
              {month.income > 0 && (
                <path d={columnPath(center - 1 - barWidth, y(month.income), barWidth, baseline)} className={INCOME_FILL} />
              )}
              {month.totalExpense > 0 && (
                <path d={columnPath(center + 1, y(month.totalExpense), barWidth, baseline)} className={EXPENSE_FILL} />
              )}
              {index % labelEvery === 0 && (
                <text x={center} y={height - 8} textAnchor="middle" className={TICK_TEXT}>
                  {labelFor(month, index)}
                </text>
              )}
            </g>
          )
        })}
        {months.map((month, index) => (
          <rect
            key={`hit-${month.month}`}
            x={margin.left + band * index}
            y={margin.top}
            width={band}
            height={plotHeight}
            fill="transparent"
            tabIndex={0}
            className="focus:outline-none focus-visible:stroke-slate-400"
            aria-label={t(key('monthlyBarAria'), {
              month: monthLabel(month.month, spansYears),
              income: formatWon(month.income),
              expense: formatWon(month.totalExpense),
              balance: formatWon(month.endBalance),
            })}
            onPointerMove={(event) => handlePointerMove(index, event)}
            onPointerLeave={clear}
            onFocus={() => {
              setActiveIndex(index)
              setPointer(null)
            }}
            onBlur={clear}
          />
        ))}
      </svg>
      {tooltip && <ChartTooltip tooltip={tooltip} containerWidth={containerWidth || width} />}
    </div>
  )
}

function CategoryBars({ categories, totalExpense }: { categories: LedgerDashboardCategoryItem[]; totalExpense: number }) {
  if (categories.length === 0) {
    return <p className="text-sm text-slate-500 dark:text-slate-400">{t(key('noExpense'))}</p>
  }

  const largest = Math.max(...categories.map((category) => category.amount), 1)
  return (
    <ul className="divide-y divide-slate-100 dark:divide-slate-800">
      {categories.map((category) => {
        const share = totalExpense > 0 ? Math.round((category.amount / totalExpense) * 100) : 0
        return (
          <li key={category.category} className="py-2">
            <div className="flex justify-between gap-2 text-xs text-slate-500 dark:text-slate-400">
              <span className="truncate">{category.category || '-'}</span>
              <span className="shrink-0">{t(key('count'), { count: String(category.count) })}</span>
            </div>
            <div className="mt-1 flex items-center gap-2">
              <div
                className={`h-5 shrink-0 rounded-r ${EXPENSE_BG}`}
                style={{ width: `calc((100% - 9.5rem) * ${category.amount / largest})` }}
              />
              <span className="whitespace-nowrap text-xs font-semibold tabular-nums text-slate-900 dark:text-slate-100">
                {formatWon(category.amount)} · {share}%
              </span>
            </div>
          </li>
        )
      })}
    </ul>
  )
}

function amountCell(value: number): string {
  return value === 0 ? '-' : formatWon(value)
}

function MonthlyTable({ dashboard, spansYears }: { dashboard: LedgerDashboardResponse; spansYears: boolean }) {
  // The server cost column only appears once something has been paid back, so the table stays narrow until then.
  const showServerCost = dashboard.serverCostReimbursed > 0
  const headers = [
    'month',
    'income',
    'fixedExpense',
    'variableExpense',
    'totalExpense',
    ...(showServerCost ? ['serverCost'] : []),
    'net',
    'endBalance',
  ]
  const numberCell = 'whitespace-nowrap px-3 py-2 text-right tabular-nums'
  return (
    <div className="overflow-x-auto">
      <table className="min-w-full text-left text-sm">
        <thead className="text-xs text-slate-500 dark:text-slate-400">
          <tr>
            {headers.map((header, index) => (
              <th key={header} className={`whitespace-nowrap px-3 py-2 font-medium ${index === 0 ? '' : 'text-right'}`}>
                {t(key(`table.${header}`))}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="text-slate-700 dark:text-slate-200">
          {dashboard.months.map((month) => (
            <tr key={month.month} className="border-t border-slate-100 dark:border-slate-800">
              <td className="whitespace-nowrap px-3 py-2">{monthLabel(month.month, spansYears)}</td>
              <td className={numberCell}>{amountCell(month.income)}</td>
              <td className={numberCell}>{amountCell(month.fixedExpense)}</td>
              <td className={numberCell}>{amountCell(month.variableExpense)}</td>
              <td className={numberCell}>{amountCell(month.totalExpense)}</td>
              {showServerCost && <td className={numberCell}>{amountCell(month.serverCostReimbursed)}</td>}
              <td className={numberCell}>{formatSignedWon(month.net)}</td>
              <td className={numberCell}>{formatWon(month.endBalance)}</td>
            </tr>
          ))}
        </tbody>
        <tfoot className="font-semibold text-slate-900 dark:text-slate-100">
          <tr className="border-t border-slate-200 dark:border-slate-700">
            <td className="whitespace-nowrap px-3 py-2">{t(key('table.total'))}</td>
            <td className={numberCell}>{amountCell(dashboard.totalIncome)}</td>
            <td className={numberCell}>{amountCell(dashboard.totalFixedExpense)}</td>
            <td className={numberCell}>{amountCell(dashboard.totalVariableExpense)}</td>
            <td className={numberCell}>{amountCell(dashboard.totalExpense)}</td>
            {showServerCost && <td className={numberCell}>{amountCell(dashboard.serverCostReimbursed)}</td>}
            <td className={numberCell}>
              {formatSignedWon(dashboard.totalIncome - dashboard.totalExpense - dashboard.serverCostReimbursed)}
            </td>
            <td className={numberCell}>{formatWon(dashboard.currentBalance)}</td>
          </tr>
        </tfoot>
      </table>
    </div>
  )
}

export function LedgerDashboard({ groupId }: LedgerDashboardProps) {
  const [dashboard, setDashboard] = useState<LedgerDashboardResponse | null>(null)
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setDashboard(await apiClient.getLedgerDashboard(groupId))
    } catch {
      setError(t('notices.loadError'))
    } finally {
      setLoading(false)
    }
  }, [groupId])

  useEffect(() => {
    void load()
  }, [load])

  if (loading && !dashboard) {
    return <LoadingIndicator label={t('common.loading')} />
  }

  if (error) {
    return (
      <Alert variant="destructive" appearance="light">
        <AlertIcon icon="destructive">!</AlertIcon>
        <AlertContent>
          <AlertDescription>{error}</AlertDescription>
        </AlertContent>
      </Alert>
    )
  }

  if (!dashboard) {
    return null
  }

  return <LedgerDashboardContent dashboard={dashboard} />
}

/** Renders already-loaded dashboard data; kept apart from fetching so it can be rendered from data directly. */
export function LedgerDashboardContent({ dashboard }: { dashboard: LedgerDashboardResponse }) {
  if (dashboard.asOfDate === null) {
    return (
      <p className="rounded-xl border border-dashed border-slate-300 px-4 py-8 text-center text-sm text-slate-500 dark:border-slate-700 dark:text-slate-400">
        {t(key('empty'))}
      </p>
    )
  }

  const currentMonth = dashboard.months[dashboard.months.length - 1]
  const spansYears = new Set(dashboard.months.map((month) => month.month.slice(0, 4))).size > 1
  const currentMonthLabel = currentMonth ? monthLabel(currentMonth.month, spansYears) : ''

  return (
    <div className="space-y-4">
      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.5fr)]">
        <section className={CARD}>
          <p className="text-sm text-slate-500 dark:text-slate-400">{t(key('balanceLabel'))}</p>
          <p className="mt-1 text-4xl font-semibold tracking-tight text-slate-900 dark:text-slate-100 sm:text-5xl">
            {formatWon(dashboard.currentBalance)}
          </p>
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
            {t(key('asOf'), { date: formatLedgerDate(dashboard.asOfDate) })}
          </p>
          <dl className="mt-4 space-y-1.5 text-sm">
            <EquationRow
              label={
                dashboard.startingBalanceDate
                  ? t(key('startingBalance'), { date: formatLedgerDate(dashboard.startingBalanceDate) })
                  : t(key('startingBalanceNoDate'))
              }
              value={formatWon(dashboard.startingBalance)}
            />
            <EquationRow label={t(key('plusIncome'))} value={formatWon(dashboard.totalIncome)} />
            <EquationRow label={t(key('minusExpense'))} value={formatWon(dashboard.totalExpense)} />
            {dashboard.serverCostReimbursed > 0 && (
              <EquationRow label={t(key('minusServerCost'))} value={formatWon(dashboard.serverCostReimbursed)} />
            )}
            <EquationRow label={t(key('equalsBalance'))} value={formatWon(dashboard.currentBalance)} emphasized />
          </dl>
          {(dashboard.serverCostPending > 0 || dashboard.serverCostMissingKrwCount > 0) && (
            <p className="mt-3 rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-600 dark:bg-slate-800/60 dark:text-slate-300">
              {t(key('serverCostPending'), { amount: formatWon(dashboard.serverCostPending) })}
              {dashboard.serverCostMissingKrwCount > 0 &&
                ` ${t(key('serverCostMissingKrw'), { count: String(dashboard.serverCostMissingKrwCount) })}`}
            </p>
          )}
        </section>

        <section className={CARD}>
          <SectionHeading title={t(key('overviewTitle'))} hint={t(key('overviewHint'))} />
          <div className="grid grid-cols-2 gap-3">
            <StatTile
              tone="income"
              label={t(key('totalIncome'))}
              value={formatWon(dashboard.totalIncome)}
              foot={t(key('count'), { count: String(dashboard.incomeCount) })}
            />
            <StatTile
              tone="expense"
              label={t(key('totalExpense'))}
              value={formatWon(dashboard.totalExpense)}
              foot={t(key('expenseSplit'), {
                count: String(dashboard.expenseCount),
                fixed: formatWon(dashboard.totalFixedExpense),
                variable: formatWon(dashboard.totalVariableExpense),
              })}
            />
            {currentMonth && (
              <>
                <StatTile
                  tone="income"
                  label={t(key('monthIncome'), { month: currentMonthLabel })}
                  value={formatWon(currentMonth.income)}
                  foot={t(key('count'), { count: String(currentMonth.incomeCount) })}
                />
                <StatTile
                  tone="expense"
                  label={t(key('monthExpense'), { month: currentMonthLabel })}
                  value={formatWon(currentMonth.totalExpense)}
                  foot={t(key('count'), { count: String(currentMonth.expenseCount) })}
                />
              </>
            )}
          </div>
        </section>
      </div>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1.5fr)_minmax(0,1fr)]">
        <section className={CARD}>
          <SectionHeading title={t(key('balanceTitle'))} hint={t(key('balanceHint'))} />
          <BalanceChart points={dashboard.balanceTimeline} />
        </section>
        <section className={CARD}>
          <SectionHeading title={t(key('categoryTitle'))} hint={t(key('categoryHint'))} />
          <CategoryBars categories={dashboard.expenseCategories} totalExpense={dashboard.totalExpense} />
        </section>
      </div>

      <section className={CARD}>
        <SectionHeading title={t(key('monthlyTitle'))} hint={t(key('monthlyHint'))} />
        <div className="mb-2 flex flex-wrap gap-4 text-xs text-slate-600 dark:text-slate-300">
          <span className="inline-flex items-center gap-1.5">
            <span aria-hidden className={`h-2.5 w-2.5 rounded-sm ${INCOME_BG}`} />
            {t(key('legendIncome'))}
          </span>
          <span className="inline-flex items-center gap-1.5">
            <span aria-hidden className={`h-2.5 w-2.5 rounded-sm ${EXPENSE_BG}`} />
            {t(key('legendExpense'))}
          </span>
        </div>
        <MonthlyChart months={dashboard.months} spansYears={spansYears} />
        <div className="mt-4">
          <MonthlyTable dashboard={dashboard} spansYears={spansYears} />
        </div>
        {dashboard.startingBalanceDate && (
          <p className="mt-2 text-xs text-slate-500 dark:text-slate-400">
            {t(key('endBalanceCaption'), {
              date: formatLedgerDate(dashboard.startingBalanceDate),
              amount: formatWon(dashboard.startingBalance),
            })}
          </p>
        )}
      </section>
    </div>
  )
}
