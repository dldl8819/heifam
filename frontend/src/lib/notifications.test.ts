import { describe, expect, it } from 'vitest'
import {
  freshNotifications,
  newestNotificationId,
  relativeTime,
  safeNotificationLink,
  unreadBadge,
} from './notifications'
import { needsHomeScreenInstall, urlBase64ToUint8Array } from './push'
import type { NotificationItem } from '@/types/api'

function item(id: number, read: boolean): NotificationItem {
  return {
    id,
    kind: 'NOTICE',
    title: '새 공지사항',
    body: `YOUR_TITLE_${id}`,
    link: `/notices/${id}`,
    createdAt: '2026-10-04T12:00:00Z',
    read,
  }
}

describe('unreadBadge', () => {
  it('shows nothing, the count, or 9+', () => {
    expect(unreadBadge(0)).toBeNull()
    expect(unreadBadge(3)).toBe('3')
    expect(unreadBadge(12)).toBe('9+')
  })
})

describe('freshNotifications', () => {
  const list = [item(9, false), item(8, true), item(7, false)]

  it('pops nothing up on the first load', () => {
    expect(freshNotifications(null, list)).toEqual([])
  })

  it('pops up unread ones newer than the last check', () => {
    expect(freshNotifications(7, list).map((notification) => notification.id)).toEqual([9])
  })

  it('remembers the newest id', () => {
    expect(newestNotificationId(list, null)).toBe(9)
    expect(newestNotificationId([], 4)).toBe(4)
  })
})

describe('safeNotificationLink', () => {
  it('follows only paths inside the site', () => {
    expect(safeNotificationLink('/notices/5')).toBe('/notices/5')
    expect(safeNotificationLink('//example.com')).toBe('/')
    expect(safeNotificationLink('https://example.com')).toBe('/')
    expect(safeNotificationLink(null)).toBe('/')
  })
})

describe('relativeTime', () => {
  const now = new Date('2026-10-04T12:00:00Z')

  it('says just now, minutes, hours, then the date', () => {
    expect(relativeTime('2026-10-04T11:59:40Z', now)).toEqual({ key: 'justNow', count: 0 })
    expect(relativeTime('2026-10-04T11:45:00Z', now)).toEqual({ key: 'minutesAgo', count: 15 })
    expect(relativeTime('2026-10-04T09:00:00Z', now)).toEqual({ key: 'hoursAgo', count: 3 })
    expect(relativeTime('2026-10-01T09:00:00Z', now).key).toBe('date')
  })
})

describe('push helpers', () => {
  it('turns a base64url key into bytes', () => {
    expect(Array.from(urlBase64ToUint8Array('AQID_w'))).toEqual([1, 2, 3, 255])
  })

  it('tells iPhone users outside the home-screen app to install it first', () => {
    const iphone = 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)'
    expect(needsHomeScreenInstall(iphone, false)).toBe(true)
    expect(needsHomeScreenInstall(iphone, true)).toBe(false)
    expect(needsHomeScreenInstall('Mozilla/5.0 (Linux; Android 14)', false)).toBe(false)
  })
})
