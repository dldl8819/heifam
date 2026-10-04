import type { NotificationItem } from '@/types/api'

export const NOTIFICATION_POLL_MS = 60000
export const NOTIFICATION_TOAST_MS = 8000

/** The badge on the bell; nothing when all is read. */
export function unreadBadge(count: number): string | null {
  if (count <= 0) {
    return null
  }
  return count > 9 ? '9+' : String(count)
}

/**
 * Unread notifications that arrived since the previous check, newest first. The first load of a
 * page has nothing to compare with, so it pops nothing up.
 */
export function freshNotifications(previousNewestId: number | null, notifications: NotificationItem[]): NotificationItem[] {
  if (previousNewestId === null) {
    return []
  }
  return notifications.filter((notification) => notification.id > previousNewestId && !notification.read)
}

export function newestNotificationId(notifications: NotificationItem[], fallback: number | null): number | null {
  return notifications.reduce<number | null>(
    (newest, notification) => (newest === null || notification.id > newest ? notification.id : newest),
    fallback,
  )
}

/** Only paths inside the site are followed. */
export function safeNotificationLink(link: string | null): string {
  return link && link.startsWith('/') && !link.startsWith('//') ? link : '/'
}

export type RelativeTime = { key: 'justNow' | 'minutesAgo' | 'hoursAgo'; count: number } | { key: 'date'; date: string }

export function relativeTime(createdAt: string, now: Date): RelativeTime {
  const created = new Date(createdAt)
  const minutes = Math.floor((now.getTime() - created.getTime()) / 60000)
  if (Number.isNaN(minutes) || minutes < 1) {
    return { key: 'justNow', count: 0 }
  }
  if (minutes < 60) {
    return { key: 'minutesAgo', count: minutes }
  }
  if (minutes < 24 * 60) {
    return { key: 'hoursAgo', count: Math.floor(minutes / 60) }
  }
  return { key: 'date', date: created.toLocaleDateString('ko-KR', { month: 'numeric', day: 'numeric' }) }
}
