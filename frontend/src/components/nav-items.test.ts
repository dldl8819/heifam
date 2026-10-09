import { describe, expect, it } from 'vitest'
import { findActiveNavHref, getVisibleNavItems, isNavItemActive } from '@/components/nav-items'
import type { NavItem } from '@/types/navigation'

const MEMBER = { isLoggedIn: true, canAccess: true, isAdmin: false, isSuperAdmin: false }
const ADMIN = { isLoggedIn: true, canAccess: true, isAdmin: true, isSuperAdmin: false }
const SUPER_ADMIN = { isLoggedIn: true, canAccess: true, isAdmin: true, isSuperAdmin: true }
const VISITOR = { isLoggedIn: false, canAccess: false, isAdmin: false, isSuperAdmin: false }

/** Every place the navigation leads to, the links inside a menu included. */
function linkHrefs(items: NavItem[]): string[] {
  return items.flatMap((item) => (item.children ? item.children.map((child) => child.href) : [item.href]))
}

describe('navigation items', () => {
  it('shows public ads page to visitors', () => {
    const items = getVisibleNavItems({
      isLoggedIn: false,
      canAccess: false,
      isAdmin: false,
      isSuperAdmin: false,
    })

    expect(items.map((item) => item.href)).toContain('/ads')
    expect(items.map((item) => item.href)).toContain('/events')
  })

  it('hides temporarily disabled dashboard for admins', () => {
    const items = getVisibleNavItems({
      isLoggedIn: true,
      canAccess: true,
      isAdmin: true,
      isSuperAdmin: false,
    })

    expect(items.map((item) => item.href)).not.toContain('/dashboard')
    expect(items.map((item) => item.href)).toContain('/players')
    expect(items.map((item) => item.href)).not.toContain('/stats')
    expect(items.map((item) => item.href)).toContain('/events')
    expect(items.map((item) => item.href)).toContain('/ads')
    expect(items.map((item) => item.href)).not.toContain('/admin/access')
    expect(items.map((item) => item.href)).toContain('/admin/audit')
    expect(linkHrefs(items)).toContain('/notices')
  })

  it('shows multi-balance, points and predictions to members', () => {
    const memberItems = getVisibleNavItems({
      isLoggedIn: true,
      canAccess: true,
      isAdmin: false,
      isSuperAdmin: false,
    })
    const adminItems = getVisibleNavItems({
      isLoggedIn: true,
      canAccess: true,
      isAdmin: true,
      isSuperAdmin: false,
    })

    for (const href of ['/balance/multi', '/points', '/predictions']) {
      expect(memberItems.map((item) => item.href)).toContain(href)
      expect(adminItems.map((item) => item.href)).toContain(href)
    }
  })

  it('gives tournaments their own admin menu next to multi-balance', () => {
    const memberItems = getVisibleNavItems({
      isLoggedIn: true,
      canAccess: true,
      isAdmin: false,
      isSuperAdmin: false,
    })
    const adminHrefs = getVisibleNavItems({
      isLoggedIn: true,
      canAccess: true,
      isAdmin: true,
      isSuperAdmin: false,
    }).map((item) => item.href)

    expect(memberItems.map((item) => item.href)).not.toContain('/tournaments')
    expect(adminHrefs.indexOf('/tournaments')).toBe(adminHrefs.indexOf('/balance/multi') + 1)
  })

  it('hides the audit log from regular members', () => {
    const items = getVisibleNavItems({
      isLoggedIn: true,
      canAccess: true,
      isAdmin: false,
      isSuperAdmin: false,
    })

    expect(items.map((item) => item.href)).not.toContain('/admin/audit')
  })

  it('shows access control and the audit log to super admins', () => {
    const items = getVisibleNavItems({
      isLoggedIn: true,
      canAccess: true,
      isAdmin: true,
      isSuperAdmin: true,
    })

    expect(items.map((item) => item.href)).toEqual(
      expect.arrayContaining(['/admin/access', '/admin/audit'])
    )
    expect(items.filter((item) => item.href === '/admin/audit')).toHaveLength(1)
  })

  it('shows notices to regular members', () => {
    const items = getVisibleNavItems({
      isLoggedIn: true,
      canAccess: true,
      isAdmin: false,
      isSuperAdmin: false,
    })

    expect(linkHrefs(items)).toContain('/notices')
  })

  it('shows notices to visitors who are not signed in', () => {
    const items = getVisibleNavItems({
      isLoggedIn: false,
      canAccess: false,
      isAdmin: false,
      isSuperAdmin: false,
    })

    expect(items.map((item) => item.href)).toContain('/notices')
  })

  it('shows notices to super admins', () => {
    const items = getVisibleNavItems({
      isLoggedIn: true,
      canAccess: true,
      isAdmin: true,
      isSuperAdmin: true,
    })

    expect(linkHrefs(items)).toContain('/notices')
  })

  it('gathers the notices and the member boards under one menu, in that order', () => {
    for (const context of [MEMBER, ADMIN, SUPER_ADMIN]) {
      const items = getVisibleNavItems(context)
      const menus = items.filter((item) => item.children)

      expect(menus).toHaveLength(1)
      expect(menus[0].children?.map((child) => child.href)).toEqual([
        '/notices',
        '/boards/free',
        '/boards/anonymous',
        '/boards/nickname',
      ])
      // The notices are reached through the menu, not beside it as well.
      expect(items.map((item) => item.href)).not.toContain('/notices')
      // Where the notices link used to be: right before the points.
      expect(items.indexOf(menus[0])).toBe(items.findIndex((item) => item.href === '/points') - 1)
    }
  })

  it('leaves visitors the plain notices link and none of the member boards', () => {
    const items = getVisibleNavItems(VISITOR)

    expect(items.some((item) => item.children)).toBe(false)
    expect(items.map((item) => item.href)).toContain('/notices')
    expect(linkHrefs(items).filter((href) => href.startsWith('/boards'))).toEqual([])
  })
})

describe('findActiveNavHref', () => {
  const items = getVisibleNavItems(ADMIN)

  it('picks the link the page is under, inside the menu too', () => {
    expect(findActiveNavHref('/players', items)).toBe('/players')
    expect(findActiveNavHref('/notices', items)).toBe('/notices')
    expect(findActiveNavHref('/notices/12', items)).toBe('/notices')
    expect(findActiveNavHref('/boards/free/7', items)).toBe('/boards/free')
    expect(findActiveNavHref('/boards/anonymous', items)).toBe('/boards/anonymous')
    expect(findActiveNavHref('/boards/nickname', items)).toBe('/boards/nickname')
  })

  it('picks the longer of two links a page is under', () => {
    expect(findActiveNavHref('/balance/multi', items)).toBe('/balance/multi')
    expect(findActiveNavHref('/balance', items)).toBe('/balance')
  })

  it('falls back to the menu for a page under it that no link covers', () => {
    expect(findActiveNavHref('/boards/search', items)).toBe('/boards')
  })

  it('finds nothing for a page outside the navigation', () => {
    expect(findActiveNavHref('/privacy', items)).toBeUndefined()
    // "/" belongs to the home link only, never to everything.
    expect(findActiveNavHref('/players', getVisibleNavItems(VISITOR))).toBeUndefined()
    expect(findActiveNavHref('/', getVisibleNavItems(VISITOR))).toBe('/')
  })
})

describe('isNavItemActive', () => {
  const items = getVisibleNavItems(MEMBER)
  const menu = items.find((item) => item.children) as NavItem
  const players = items.find((item) => item.href === '/players') as NavItem

  it('lights the menu for any of its links and for its own path', () => {
    for (const href of ['/notices', '/boards/free', '/boards/anonymous', '/boards/nickname', '/boards']) {
      expect(isNavItemActive(menu, href)).toBe(true)
      expect(isNavItemActive(players, href)).toBe(false)
    }
  })

  it('lights a plain link only for itself, and nothing when no link is active', () => {
    expect(isNavItemActive(players, '/players')).toBe(true)
    expect(isNavItemActive(menu, '/players')).toBe(false)
    expect(isNavItemActive(menu, undefined)).toBe(false)
    expect(isNavItemActive(players, undefined)).toBe(false)
  })
})
