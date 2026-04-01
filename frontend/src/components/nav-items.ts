import type { NavItem } from '@/types/navigation'
import { t } from '@/lib/i18n'

type NavVisibilityContext = {
  isLoggedIn: boolean
  canAccess: boolean
  isAdmin: boolean
  isSuperAdmin: boolean
}

export function getVisibleNavItems(context: NavVisibilityContext): NavItem[] {
  if (!context.isLoggedIn) {
    return [
      { label: t('nav.home'), href: '/' },
      { label: t('nav.results'), href: '/results' },
    ]
  }

  if (!context.canAccess) {
    return []
  }

  if (context.isAdmin) {
    const adminItems: NavItem[] = [
      { label: t('nav.dashboard'), href: '/dashboard' },
      { label: t('nav.players'), href: '/players' },
      { label: t('nav.balance'), href: '/balance' },
      { label: t('nav.captainDraft'), href: '/captain-draft' },
      { label: t('nav.multiBalance'), href: '/balance/multi' },
      { label: t('nav.results'), href: '/results' },
      { label: t('nav.ranking'), href: '/ranking' },
    ]

    if (context.isSuperAdmin) {
      adminItems.splice(5, 0, { label: t('nav.accessControl'), href: '/admin/access' })
    }

    return adminItems
  }

  return [
    { label: t('nav.dashboard'), href: '/dashboard' },
    { label: t('nav.results'), href: '/results' },
    { label: t('nav.ranking'), href: '/ranking' },
  ]
}
