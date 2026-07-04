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
    expect(items.map((item) => item.href)).toContain('/stats')
    expect(items.map((item) => item.href)).toContain('/events')
    expect(items.map((item) => item.href)).toContain('/ads')
    expect(items.map((item) => item.href)).not.toContain('/admin/access')
    expect(items.map((item) => item.href)).not.toContain('/admin/audit')
  })
})
