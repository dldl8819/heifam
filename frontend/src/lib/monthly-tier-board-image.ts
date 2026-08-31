import type { GroupPlayerTierBoardItem, PlayerTierStatus } from '@/types/api'

export type MonthlyTierBoardPlayer = {
  nickname: string
  tier: PlayerTierStatus
}

export type MonthlyTierBoardLabels = {
  title: string
  index: string
  unassigned: string
  tierSuffix: string
  totalSuffix: string
}

export type MonthlyTierBoardModel = {
  periodLabel: string
  fileName: string
  totalCount: number
  rowCount: number
  buckets: Record<PlayerTierStatus, string[]>
}

export type MonthlyTierBoardPng = {
  blob: Blob
  fileName: string
  periodLabel: string
  totalCount: number
}

type TierBoardColumn = {
  tier: PlayerTierStatus
  headerFill: string
}

export const MONTHLY_TIER_BOARD_COLUMNS: TierBoardColumn[] = [
  { tier: 'S', headerFill: '#f7f7f7' },
  { tier: 'A+', headerFill: '#b9d3e8' },
  { tier: 'A', headerFill: '#b9d3e8' },
  { tier: 'A-', headerFill: '#b9d3e8' },
  { tier: 'B+', headerFill: '#fff0c5' },
  { tier: 'B', headerFill: '#fff0c5' },
  { tier: 'B-', headerFill: '#fff0c5' },
  { tier: 'C+', headerFill: '#c8dfb8' },
  { tier: 'C', headerFill: '#c8dfb8' },
  { tier: 'C-', headerFill: '#c8dfb8' },
  { tier: 'D', headerFill: '#dbead3' },
  { tier: 'UNASSIGNED', headerFill: '#f4b17f' },
]

const MINIMUM_ROW_COUNT = 10
const MAXIMUM_ROW_COUNT = 100
const CANVAS_WIDTH = 1680
const OUTER_MARGIN = 12
const TITLE_TOP = 14
const TITLE_BOTTOM = 92
const TABLE_TOP = 112
const INDEX_COLUMN_WIDTH = 60
const REASSIGNMENT_COLUMN_WIDTH = 168
const HEADER_HEIGHT = 54
const ROW_HEIGHT = 36
const FOOTER_HEIGHT = 44
const BOTTOM_MARGIN = 12

function createEmptyBuckets(): Record<PlayerTierStatus, string[]> {
  return {
    S: [],
    'A+': [],
    A: [],
    'A-': [],
    'B+': [],
    B: [],
    'B-': [],
    'C+': [],
    C: [],
    'C-': [],
    D: [],
    UNASSIGNED: [],
  }
}

export function selectMonthlyTierBoardPlayers(
  items: Array<Pick<GroupPlayerTierBoardItem, 'nickname' | 'tier' | 'liveTier' | 'active'>>,
): MonthlyTierBoardPlayer[] {
  return items.flatMap((item) => {
    const nickname = item.nickname.trim()
    if (item.active === false || nickname.length === 0) {
      return []
    }

    return [{ nickname, tier: item.liveTier ?? item.tier }]
  })
}

export function resolveMonthlyTierBoardPeriod(date: Date): {
  periodLabel: string
  fileName: string
} {
  if (Number.isNaN(date.getTime())) {
    throw new Error('Invalid tier board date')
  }

  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).formatToParts(date)
  const part = (type: string) => parts.find((entry) => entry.type === type)?.value
  const year = part('year')
  const month = part('month')
  const day = part('day')
  const rawHour = part('hour')
  const minute = part('minute')
  if (!year || !month || !day || !rawHour || !minute) {
    throw new Error('Unable to resolve tier board period')
  }
  const hour = rawHour === '24' ? '00' : rawHour

  return {
    periodLabel: `${year}-${month}-${day}`,
    fileName: `heifam-tier-board-${year}${month}${day}-${hour}${minute}.png`,
  }
}

export function buildMonthlyTierBoardModel(
  players: MonthlyTierBoardPlayer[],
  date: Date = new Date(),
): MonthlyTierBoardModel {
  const buckets = createEmptyBuckets()
  players.forEach((player) => {
    const nickname = player.nickname.trim()
    if (nickname.length > 0) {
      buckets[player.tier].push(nickname)
    }
  })

  const longestBucket = Math.max(
    0,
    ...MONTHLY_TIER_BOARD_COLUMNS.map((column) => buckets[column.tier].length),
  )
  if (longestBucket > MAXIMUM_ROW_COUNT) {
    throw new Error('Tier board row limit exceeded')
  }

  const period = resolveMonthlyTierBoardPeriod(date)
  return {
    ...period,
    totalCount: players.filter((player) => player.nickname.trim().length > 0).length,
    rowCount: Math.max(MINIMUM_ROW_COUNT, longestBucket),
    buckets,
  }
}

function fitCanvasText(
  context: CanvasRenderingContext2D,
  value: string,
  maxWidth: number,
): string {
  if (context.measureText(value).width <= maxWidth) {
    return value
  }

  const suffix = '…'
  let truncated = value
  while (truncated.length > 0) {
    truncated = truncated.slice(0, -1)
    const candidate = `${truncated}${suffix}`
    if (context.measureText(candidate).width <= maxWidth) {
      return candidate
    }
  }

  return suffix
}

function fillCell(
  context: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  fill: string,
): void {
  context.fillStyle = fill
  context.fillRect(x, y, width, height)
  context.strokeStyle = '#77716a'
  context.lineWidth = 1
  context.strokeRect(x, y, width, height)
}

function drawCellText(
  context: CanvasRenderingContext2D,
  value: string,
  x: number,
  y: number,
  width: number,
  height: number,
  options: { font: string; color?: string; horizontalPadding?: number },
): void {
  context.font = options.font
  context.fillStyle = options.color ?? '#2f2f2f'
  context.textAlign = 'center'
  context.textBaseline = 'middle'
  const horizontalPadding = options.horizontalPadding ?? 14
  context.fillText(
    fitCanvasText(context, value, Math.max(1, width - horizontalPadding * 2)),
    x + width / 2,
    y + height / 2 + 1,
  )
}

function renderMonthlyTierBoardCanvas(
  model: MonthlyTierBoardModel,
  labels: MonthlyTierBoardLabels,
): HTMLCanvasElement {
  const canvas = document.createElement('canvas')
  canvas.width = CANVAS_WIDTH
  canvas.height =
    TABLE_TOP +
    HEADER_HEIGHT +
    model.rowCount * ROW_HEIGHT +
    FOOTER_HEIGHT +
    BOTTOM_MARGIN

  const context = canvas.getContext('2d', { alpha: false })
  if (!context) {
    throw new Error('Canvas is not supported')
  }

  context.fillStyle = '#e5ded3'
  context.fillRect(0, 0, canvas.width, canvas.height)
  context.strokeStyle = '#3e3e3e'
  context.lineWidth = 2
  context.beginPath()
  context.moveTo(10, TITLE_TOP)
  context.lineTo(canvas.width - 10, TITLE_TOP)
  context.moveTo(10, TITLE_BOTTOM)
  context.lineTo(canvas.width - 10, TITLE_BOTTOM)
  context.stroke()

  context.font = '700 44px "Noto Sans KR", "Apple SD Gothic Neo", sans-serif'
  context.fillStyle = '#303030'
  context.textAlign = 'center'
  context.textBaseline = 'middle'
  context.fillText(labels.title, canvas.width / 2, 57)

  const tableWidth = canvas.width - OUTER_MARGIN * 2
  const regularTierColumnWidth =
    (tableWidth - INDEX_COLUMN_WIDTH - REASSIGNMENT_COLUMN_WIDTH) /
    (MONTHLY_TIER_BOARD_COLUMNS.length - 1)
  const headerY = TABLE_TOP
  fillCell(
    context,
    OUTER_MARGIN,
    headerY,
    INDEX_COLUMN_WIDTH,
    HEADER_HEIGHT,
    '#f7f7f7',
  )
  drawCellText(context, labels.index, OUTER_MARGIN, headerY, INDEX_COLUMN_WIDTH, HEADER_HEIGHT, {
    font: '700 23px "Noto Sans KR", "Apple SD Gothic Neo", sans-serif',
    horizontalPadding: 4,
  })

  MONTHLY_TIER_BOARD_COLUMNS.forEach((column, columnIndex) => {
    const x = OUTER_MARGIN + INDEX_COLUMN_WIDTH + columnIndex * regularTierColumnWidth
    const width =
      column.tier === 'UNASSIGNED' ? REASSIGNMENT_COLUMN_WIDTH : regularTierColumnWidth
    fillCell(context, x, headerY, width, HEADER_HEIGHT, column.headerFill)
    drawCellText(
      context,
      column.tier === 'UNASSIGNED' ? labels.unassigned : column.tier,
      x,
      headerY,
      width,
      HEADER_HEIGHT,
      {
        font: '700 24px "Noto Sans KR", "Apple SD Gothic Neo", sans-serif',
        horizontalPadding: 8,
      },
    )
  })

  Array.from({ length: model.rowCount }, (_, rowIndex) => {
    const y = TABLE_TOP + HEADER_HEIGHT + rowIndex * ROW_HEIGHT
    fillCell(context, OUTER_MARGIN, y, INDEX_COLUMN_WIDTH, ROW_HEIGHT, '#fbfbfb')
    drawCellText(
      context,
      String(rowIndex + 1),
      OUTER_MARGIN,
      y,
      INDEX_COLUMN_WIDTH,
      ROW_HEIGHT,
      { font: '600 17px "Noto Sans KR", "Apple SD Gothic Neo", sans-serif' },
    )

    MONTHLY_TIER_BOARD_COLUMNS.forEach((column, columnIndex) => {
      const x = OUTER_MARGIN + INDEX_COLUMN_WIDTH + columnIndex * regularTierColumnWidth
      const width =
        column.tier === 'UNASSIGNED' ? REASSIGNMENT_COLUMN_WIDTH : regularTierColumnWidth
      fillCell(context, x, y, width, ROW_HEIGHT, '#fbfbfb')
      const nickname = model.buckets[column.tier][rowIndex]
      if (nickname) {
        drawCellText(context, nickname, x, y, width, ROW_HEIGHT, {
          font: '600 18px "Noto Sans KR", "Apple SD Gothic Neo", sans-serif',
          horizontalPadding: 8,
        })
      }
    })
  })

  const footerY = TABLE_TOP + HEADER_HEIGHT + model.rowCount * ROW_HEIGHT
  fillCell(context, OUTER_MARGIN, footerY, INDEX_COLUMN_WIDTH, FOOTER_HEIGHT, '#fbfbfb')
  MONTHLY_TIER_BOARD_COLUMNS.forEach((column, columnIndex) => {
    const x = OUTER_MARGIN + INDEX_COLUMN_WIDTH + columnIndex * regularTierColumnWidth
    const width =
      column.tier === 'UNASSIGNED' ? REASSIGNMENT_COLUMN_WIDTH : regularTierColumnWidth
    const isTotalCell = column.tier === 'UNASSIGNED'
    fillCell(context, x, footerY, width, FOOTER_HEIGHT, isTotalCell ? '#b7d7a8' : '#fbfbfb')

    if (column.tier === 'D') {
      drawCellText(
        context,
        model.periodLabel,
        x,
        footerY,
        width,
        FOOTER_HEIGHT,
        { font: '600 16px "Noto Sans KR", "Apple SD Gothic Neo", sans-serif', horizontalPadding: 4 },
      )
    } else if (isTotalCell) {
      drawCellText(
        context,
        `${model.totalCount} ${labels.totalSuffix}`,
        x,
        footerY,
        width,
        FOOTER_HEIGHT,
        { font: '700 17px "Noto Sans KR", "Apple SD Gothic Neo", sans-serif' },
      )
    }
  })

  return canvas
}

export async function createMonthlyTierBoardPng(
  players: MonthlyTierBoardPlayer[],
  labels: MonthlyTierBoardLabels,
  date: Date = new Date(),
): Promise<MonthlyTierBoardPng> {
  if (typeof document === 'undefined') {
    throw new Error('Tier board download requires a browser')
  }

  const model = buildMonthlyTierBoardModel(players, date)
  if (model.totalCount === 0) {
    throw new Error('Tier board is empty')
  }

  if ('fonts' in document) {
    await document.fonts.ready
  }
  const canvas = renderMonthlyTierBoardCanvas(model, labels)
  const blob = await new Promise<Blob>((resolve, reject) => {
    canvas.toBlob((value) => {
      if (value) {
        resolve(value)
      } else {
        reject(new Error('Unable to encode tier board image'))
      }
    }, 'image/png')
  })

  return {
    blob,
    fileName: model.fileName,
    periodLabel: model.periodLabel,
    totalCount: model.totalCount,
  }
}
