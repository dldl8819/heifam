import { describe, expect, it } from 'vitest'
import { getVisibleNavItems } from '@/components/nav-items'

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
    expect(items.map((item) => item.href)).toContain('/notices')
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

  it('hides notices from regular members', () => {
    const items = getVisibleNavItems({
      isLoggedIn: true,
      canAccess: true,
      isAdmin: false,
      isSuperAdmin: false,
    })

    expect(items.map((item) => item.href)).not.toContain('/notices')
  })

  it('shows notices to super admins', () => {
    const items = getVisibleNavItems({
      isLoggedIn: true,
      canAccess: true,
      isAdmin: true,
      isSuperAdmin: true,
    })

    expect(items.map((item) => item.href)).toContain('/notices')
  })
})
