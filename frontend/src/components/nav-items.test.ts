import { describe, expect, it } from 'vitest'
import { findActiveNavHref, getVisibleNavItems, isNavItemActive } from '@/components/nav-items'
import type { NavItem } from '@/types/navigation'

const MEMBER = { isLoggedIn: true, canAccess: true, isAdmin: false, isSuperAdmin: false }
const ADMIN = { isLoggedIn: true, canAccess: true, isAdmin: true, isSuperAdmin: false }
const SUPER_ADMIN = { isLoggedIn: true, canAccess: true, isAdmin: true, isSuperAdmin: true }
const VISITOR = { isLoggedIn: false, canAccess: false, isAdmin: false, isSuperAdmin: false }
const PENDING = { isLoggedIn: true, canAccess: false, isAdmin: false, isSuperAdmin: false }

/** Every place the navigation leads to, the links inside a menu included. */
function linkHrefs(items: NavItem[]): string[] {
  return items.flatMap((item) => (item.children ? item.children.map((child) => child.href) : [item.href]))
}

/** The bar as it reads: a plain link by its address, a menu by its name. */
function bar(items: NavItem[]): string[] {
  return items.map((item) => (item.children ? `menu:${item.href}` : item.href))
}

function menu(items: NavItem[], href: string): string[] {
  return (items.find((item) => item.href === href && item.children)?.children ?? []).map((child) => child.href)
}

describe('navigation items', () => {
  it('shows visitors the public pages and the plain notices link, and no menu', () => {
    const items = getVisibleNavItems(VISITOR)

    expect(bar(items)).toEqual(['/', '/notices', '/events', '/ads', '/results'])
  })

  it('shows nothing to a signed-in account without access', () => {
    expect(getVisibleNavItems(PENDING)).toEqual([])
  })

  it('gathers a member bar into four menus and a few links', () => {
    expect(bar(getVisibleNavItems(MEMBER))).toEqual([
      '/players',
      '/ranking',
      'menu:/matches',
      'menu:/boards',
      'menu:/points',
      '/predictions',
      '/events',
      '/ads',
      '/results',
    ])
  })

  it('gives admins the same bar with the admin menu at its end', () => {
    for (const context of [ADMIN, SUPER_ADMIN]) {
      expect(bar(getVisibleNavItems(context))).toEqual([
        '/players',
        '/ranking',
        'menu:/matches',
        'menu:/boards',
        'menu:/points',
        '/predictions',
        '/events',
        '/ads',
        '/results',
        'menu:/admin',
      ])
    }
  })

  it('puts the matches under one menu: balance and multi-balance for members, tournaments and the draft for admins too', () => {
    expect(menu(getVisibleNavItems(MEMBER), '/matches')).toEqual(['/balance', '/balance/multi'])
    for (const context of [ADMIN, SUPER_ADMIN]) {
      expect(menu(getVisibleNavItems(context), '/matches')).toEqual([
        '/balance',
        '/balance/multi',
        '/tournaments',
        '/captain-draft',
      ])
    }
  })

  it('gathers the notices and the member boards under one menu, in that order', () => {
    for (const context of [MEMBER, ADMIN, SUPER_ADMIN]) {
      expect(menu(getVisibleNavItems(context), '/boards')).toEqual([
        '/notices',
        '/boards/free',
        '/boards/anonymous',
        '/boards/nickname',
      ])
    }
  })

  it('puts a member\'s points, the monthly ranking and the prize draws under one menu, with the prize events for admins', () => {
    expect(menu(getVisibleNavItems(MEMBER), '/points')).toEqual(['/points', '/points/ranking', '/draws'])
    for (const context of [ADMIN, SUPER_ADMIN]) {
      expect(menu(getVisibleNavItems(context), '/points')).toEqual(['/points', '/points/ranking', '/points/events', '/draws'])
    }
  })

  it('splits access in three for super admins and gives every admin the operation log', () => {
    expect(menu(getVisibleNavItems(SUPER_ADMIN), '/admin')).toEqual([
      '/admin/access/admins',
      '/admin/access/result-editors',
      '/admin/access/allowed',
      '/admin/audit',
    ])
    expect(menu(getVisibleNavItems(ADMIN), '/admin')).toEqual(['/admin/audit'])
  })

  it('leads every page once, and nowhere a member may not go', () => {
    for (const context of [MEMBER, ADMIN, SUPER_ADMIN]) {
      const hrefs = linkHrefs(getVisibleNavItems(context))

      expect(new Set(hrefs).size).toBe(hrefs.length)
    }
    const memberHrefs = linkHrefs(getVisibleNavItems(MEMBER))
    for (const adminOnly of ['/tournaments', '/captain-draft', '/points/events', '/admin/audit', '/admin/access/admins']) {
      expect(memberHrefs).not.toContain(adminOnly)
    }
    expect(linkHrefs(getVisibleNavItems(MEMBER))).not.toContain('/dashboard')
  })
})

describe('findActiveNavHref', () => {
  const items = getVisibleNavItems(SUPER_ADMIN)

  it('picks the link the page is under, inside a menu too', () => {
    expect(findActiveNavHref('/players', items)).toBe('/players')
    expect(findActiveNavHref('/notices/12', items)).toBe('/notices')
    expect(findActiveNavHref('/boards/free/7', items)).toBe('/boards/free')
    expect(findActiveNavHref('/tournaments', items)).toBe('/tournaments')
    expect(findActiveNavHref('/points/ranking', items)).toBe('/points/ranking')
    expect(findActiveNavHref('/admin/access/allowed', items)).toBe('/admin/access/allowed')
    expect(findActiveNavHref('/admin/audit', items)).toBe('/admin/audit')
  })

  it('picks the longer of two links a page is under', () => {
    expect(findActiveNavHref('/balance/multi', items)).toBe('/balance/multi')
    expect(findActiveNavHref('/balance', items)).toBe('/balance')
    expect(findActiveNavHref('/points', items)).toBe('/points')
    expect(findActiveNavHref('/points/events', items)).toBe('/points/events')
  })

  it('falls back to the menu for a page under it that no link covers', () => {
    expect(findActiveNavHref('/boards/search', items)).toBe('/boards')
    expect(findActiveNavHref('/admin/access', items)).toBe('/admin')
  })

  it('finds nothing for a page outside the navigation', () => {
    expect(findActiveNavHref('/privacy', items)).toBeUndefined()
    // "/" belongs to the home link only, never to everything.
    expect(findActiveNavHref('/players', getVisibleNavItems(VISITOR))).toBeUndefined()
    expect(findActiveNavHref('/', getVisibleNavItems(VISITOR))).toBe('/')
  })
})

describe('isNavItemActive', () => {
  const items = getVisibleNavItems(SUPER_ADMIN)
  const byHref = (href: string) => items.find((item) => item.href === href) as NavItem

  it('lights a menu for any of its links', () => {
    expect(isNavItemActive(byHref('/matches'), '/captain-draft')).toBe(true)
    expect(isNavItemActive(byHref('/boards'), '/notices')).toBe(true)
    expect(isNavItemActive(byHref('/points'), '/draws')).toBe(true)
    expect(isNavItemActive(byHref('/admin'), '/admin/access/result-editors')).toBe(true)
    expect(isNavItemActive(byHref('/admin'), '/admin')).toBe(true)
  })

  it('lights one item only, and nothing when no link is active', () => {
    expect(isNavItemActive(byHref('/players'), '/players')).toBe(true)
    expect(isNavItemActive(byHref('/matches'), '/players')).toBe(false)
    expect(isNavItemActive(byHref('/points'), '/balance')).toBe(false)
    expect(isNavItemActive(byHref('/matches'), undefined)).toBe(false)
  })
})
