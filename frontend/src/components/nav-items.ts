import type { NavItem } from '@/types/navigation'
import { t } from '@/lib/i18n'

type NavVisibilityContext = {
  isLoggedIn: boolean
  canAccess: boolean
  isAdmin: boolean
  isSuperAdmin: boolean
}

/**
 * The matches: setting one up for members, and the tournaments and regular draft for admins.
 * Its links share no path; "/matches" only names the menu.
 */
function matchesMenu(isAdmin: boolean): NavItem {
  return {
    label: t('nav.matches'),
    href: '/matches',
    children: [
      { label: t('nav.balance'), href: '/balance' },
      { label: t('nav.multiBalance'), href: '/balance/multi' },
      ...(isAdmin
        ? [
            { label: t('nav.tournaments'), href: '/tournaments' },
            { label: t('nav.captainDraft'), href: '/captain-draft' },
          ]
        : []),
    ],
  }
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

/** A member's points, the month's ranking, the prize draws; and for admins the prize events. */
function pointsMenu(isAdmin: boolean): NavItem {
  return {
    label: t('nav.points'),
    href: '/points',
    children: [
      { label: t('nav.myPoints'), href: '/points' },
      { label: t('nav.pointRanking'), href: '/points/ranking' },
      ...(isAdmin ? [{ label: t('nav.pointEvents'), href: '/points/events' }] : []),
      // Members read the records of prize draws there; admins also run the draws.
      { label: t('nav.draws'), href: '/draws' },
    ],
  }
}

/** What runs the site: access for super admins, the operation log for every admin. */
function adminMenu(isSuperAdmin: boolean): NavItem {
  return {
    label: t('nav.admin'),
    href: '/admin',
    children: [
      ...(isSuperAdmin
        ? [
            { label: t('nav.accessAdmins'), href: '/admin/access/admins' },
            { label: t('nav.accessResultEditors'), href: '/admin/access/result-editors' },
            { label: t('nav.accessAllowed'), href: '/admin/access/allowed' },
          ]
        : []),
      { label: t('nav.auditLogs'), href: '/admin/audit' },
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

  const items: NavItem[] = [
    { label: t('nav.players'), href: '/players' },
    { label: t('nav.ranking'), href: '/ranking' },
    matchesMenu(context.isAdmin),
    boardsMenu(),
    pointsMenu(context.isAdmin),
    { label: t('nav.predictions'), href: '/predictions' },
    { label: t('nav.events'), href: '/events' },
    { label: t('nav.ads'), href: '/ads' },
    { label: t('nav.results'), href: '/results' },
  ]
  if (context.isAdmin) {
    items.push(adminMenu(context.isSuperAdmin))
  }
  return items
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
