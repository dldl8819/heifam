import { t } from '@/lib/i18n'

/**
 * The ten menus whose first opening each day earns an attendance point, by the key the backend
 * knows them by (AttendanceMenu). A page under a menu's path counts as that menu, so a post opened
 * from a link counts for its board.
 */
export const ATTENDANCE_MENUS = [
  { key: 'notices', href: '/notices', labelKey: 'nav.notices' },
  { key: 'free-board', href: '/boards/free', labelKey: 'nav.freeBoard' },
  { key: 'anonymous-board', href: '/boards/anonymous', labelKey: 'nav.anonymousBoard' },
  { key: 'video-board', href: '/boards/video', labelKey: 'nav.videoBoard' },
  { key: 'players', href: '/players', labelKey: 'nav.players' },
  { key: 'ranking', href: '/ranking', labelKey: 'nav.ranking' },
  { key: 'predictions', href: '/predictions', labelKey: 'nav.predictions' },
  { key: 'partners', href: '/ads', labelKey: 'nav.ads' },
  { key: 'results', href: '/results', labelKey: 'nav.results' },
  { key: 'point-policy', href: '/points/policy', labelKey: 'nav.pointPolicy' },
] as const

export type AttendanceMenu = (typeof ATTENDANCE_MENUS)[number]

const KST_OFFSET_MS = 9 * 60 * 60 * 1000
const STORAGE_KEY = 'heifam.menuAttendance'

/** The attendance menu a page belongs to, or null for any other page. */
export function attendanceMenuFor(pathname: string): AttendanceMenu | null {
  return ATTENDANCE_MENUS.find((menu) => pathname === menu.href || pathname.startsWith(`${menu.href}/`)) ?? null
}

/** The day in Korea, as YYYY-MM-DD: attendance starts over at 0:00 KST, like every daily cap. */
export function kstDay(now: Date = new Date()): string {
  return new Date(now.getTime() + KST_OFFSET_MS).toISOString().slice(0, 10)
}

export function attendanceMenuLabel(menu: AttendanceMenu): string {
  return t(menu.labelKey)
}

/** The menus that have not paid today, in menu order. */
export function menusLeft(visited: readonly string[]): AttendanceMenu[] {
  return ATTENDANCE_MENUS.filter((menu) => !visited.includes(menu.key))
}

/** The menus this browser has already told the backend about today, for one account. */
export type AttendanceRecord = {
  email: string
  day: string
  menus: string[]
}

export function isRecorded(record: AttendanceRecord | null, email: string, day: string, key: string): boolean {
  return record !== null && record.email === email && record.day === day && record.menus.includes(key)
}

/** The record with one more menu; another account or another day starts it over. */
export function withRecorded(record: AttendanceRecord | null, email: string, day: string, key: string): AttendanceRecord {
  const current = record && record.email === email && record.day === day ? record.menus : []
  return { email, day, menus: current.includes(key) ? current : [...current, key] }
}

function parseRecord(raw: string | null): AttendanceRecord | null {
  if (!raw) {
    return null
  }
  try {
    const value = JSON.parse(raw) as Partial<AttendanceRecord>
    if (typeof value.email === 'string' && typeof value.day === 'string' && Array.isArray(value.menus)) {
      return { email: value.email, day: value.day, menus: value.menus.filter((menu) => typeof menu === 'string') }
    }
  } catch {
    // A record that cannot be read is no record: the backend pays a menu once a day anyway.
  }
  return null
}

// Kept in memory as well, for browsers where storage is off or throws.
let memoryRecord: AttendanceRecord | null = null

export function loadAttendanceRecord(): AttendanceRecord | null {
  try {
    return parseRecord(window.localStorage.getItem(STORAGE_KEY)) ?? memoryRecord
  } catch {
    return memoryRecord
  }
}

export function saveAttendanceRecord(record: AttendanceRecord): void {
  memoryRecord = record
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(record))
  } catch {
    // Memory alone then; at worst a menu is reported again and the backend pays nothing more.
  }
}
