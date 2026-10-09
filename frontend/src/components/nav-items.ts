import type { NavItem } from '@/types/navigation'
import { t } from '@/lib/i18n'

type NavVisibilityContext = {
  isLoggedIn: boolean
  canAccess: boolean
  isAdmin: boolean
  isSuperAdmin: boolean
}

/** The boards a member reads and writes, gathered under one menu. */
function boardsMenu(): NavItem {
  return {
    label: t('nav.boards'),
    href: '/boards',
    children: [
      { label: t('nav.notices'), href: '/notices' },
      { label: t('nav.freeBoard'), href: '/boards/free' },
      { label: t('nav.anonymousBoard'), href: '/boards/anonymous' },
      { label: t('nav.nicknameRequests'), href: '/boards/nickname' },
    ],
  }
}

export function getVisibleNavItems(context: NavVisibilityContext): NavItem[] {
  if (!context.isLoggedIn) {
    return [
      { label: t('nav.home'), href: '/' },
      // Visitors see notice titles only; the notice page shows them nothing else.
      { label: t('nav.notices'), href: '/notices' },
      { label: t('nav.events'), href: '/events' },
      { label: t('nav.ads'), href: '/ads' },
      { label: t('nav.results'), href: '/results' },
    ]
  }

  if (!context.canAccess) {
    return []
  }

  if (context.isAdmin) {
    const adminItems: NavItem[] = [
      { label: t('nav.players'), href: '/players' },
      { label: t('nav.ranking'), href: '/ranking' },
      { label: t('nav.balance'), href: '/balance' },
      { label: t('nav.captainDraft'), href: '/captain-draft' },
      { label: t('nav.multiBalance'), href: '/balance/multi' },
      { label: t('nav.tournaments'), href: '/tournaments' },
      boardsMenu(),
      { label: t('nav.points'), href: '/points' },
      { label: t('nav.predictions'), href: '/predictions' },
      { label: t('nav.events'), href: '/events' },
      { label: t('nav.ads'), href: '/ads' },
      { label: t('nav.results'), href: '/results' },
    ]

    if (context.isSuperAdmin) {
      adminItems.splice(adminItems.length - 1, 0, { label: t('nav.accessControl'), href: '/admin/access' })
    }
    adminItems.splice(adminItems.length - 1, 0, { label: t('nav.auditLogs'), href: '/admin/audit' })

    return adminItems
  }

  return [
    { label: t('nav.players'), href: '/players' },
    { label: t('nav.ranking'), href: '/ranking' },
    { label: t('nav.balance'), href: '/balance' },
    { label: t('nav.multiBalance'), href: '/balance/multi' },
    boardsMenu(),
    { label: t('nav.points'), href: '/points' },
    { label: t('nav.predictions'), href: '/predictions' },
    { label: t('nav.events'), href: '/events' },
    { label: t('nav.ads'), href: '/ads' },
    { label: t('nav.results'), href: '/results' },
  ]
}

function isUnder(pathname: string, href: string): boolean {
  if (href === '/') {
    return pathname === '/'
  }

  return pathname === href || pathname.startsWith(`${href}/`)
}

/**
 * The href of the one item the page belongs to: of all the links the page is under, the longest,
 * so /balance/multi lights "multi balance" and not "balance" too. Links inside a menu count; the
 * menu itself counts for a page under its path that none of its links covers.
 */
export function findActiveNavHref(pathname: string, items: NavItem[]): string | undefined {
  const candidates = items.flatMap((item) => [item, ...(item.children ?? [])])

  return candidates
    .filter((item) => isUnder(pathname, item.href))
    .sort((a, b) => b.href.length - a.href.length)[0]?.href
}

/** Whether a top-level item is the one to light up: itself, or for a menu any link inside it. */
export function isNavItemActive(item: NavItem, activeHref: string | undefined): boolean {
  if (activeHref === undefined) {
    return false
  }

  return item.href === activeHref || (item.children ?? []).some((child) => child.href === activeHref)
}
