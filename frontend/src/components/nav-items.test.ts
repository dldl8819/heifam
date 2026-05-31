import { describe, expect, it } from 'vitest'
import { getVisibleNavItems } from '@/components/nav-items'

describe('navigation items', () => {
  it('shows dashboard for admins', () => {
    const items = getVisibleNavItems({
      isLoggedIn: true,
      canAccess: true,
      isAdmin: true,
      isSuperAdmin: false,
    })

    expect(items.map((item) => item.href)).toContain('/dashboard')
    expect(items.map((item) => item.href)).not.toContain('/admin/access')
    expect(items.map((item) => item.href)).not.toContain('/admin/audit')
  })
})
