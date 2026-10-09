'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { findActiveNavHref, getVisibleNavItems, isNavItemActive } from '@/components/nav-items'
import { useAdminAuth } from '@/lib/admin-auth'
import type { NavItem } from '@/types/navigation'

// How long the pointer may be off a menu before it closes: long enough to cross the gap between
// the menu's name and its list.
const MENU_CLOSE_DELAY_MS = 150
// The opened list is this wide (w-44) and keeps this far from the edge of the screen.
const MENU_WIDTH_PX = 176
const MENU_EDGE_GAP_PX = 8

function useVisibleNavItems() {
  const pathname = usePathname()
  const { isLoading, isLoggedIn, canAccess, isAdmin, isSuperAdmin } = useAdminAuth()
  const navItems = useMemo(
    () => getVisibleNavItems({ isLoggedIn, canAccess, isAdmin, isSuperAdmin }),
    [isLoggedIn, canAccess, isAdmin, isSuperAdmin]
  )

  return {
    pathname,
    isLoading,
    navItems,
  }
}

const desktopItemClass = (active: boolean) =>
  `inline-flex items-center gap-1 rounded-lg px-4 py-2 text-sm font-medium transition-colors ${
    active ? 'bg-amber-500 text-slate-950' : 'text-slate-200 hover:bg-slate-700 hover:text-white'
  }`

/**
 * A menu in the desktop bar: pointing at its name, or pressing it, opens the list of its links.
 * The list is drawn outside the bar (the bar scrolls sideways and would cut it off), placed
 * under the name by where that is on the screen.
 */
function DesktopMenu({ item, active, activeHref }: { item: NavItem; active: boolean; activeHref?: string }) {
  const pathname = usePathname()
  const [position, setPosition] = useState<{ top: number; left: number } | null>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const listRef = useRef<HTMLUListElement>(null)
  const closeTimer = useRef<number | null>(null)
  const open = position !== null
  const menuId = `nav-menu-${item.href.replace(/[^a-z0-9]+/gi, '-')}`

  const cancelClose = useCallback(() => {
    if (closeTimer.current !== null) {
      window.clearTimeout(closeTimer.current)
      closeTimer.current = null
    }
  }, [])

  const close = useCallback(() => {
    cancelClose()
    setPosition(null)
  }, [cancelClose])

  const show = useCallback(() => {
    cancelClose()
    const rect = triggerRef.current?.getBoundingClientRect()
    if (rect) {
      // Under the name, pulled back in when the bar is scrolled so far that it would hang off the screen.
      const furthestLeft = window.innerWidth - MENU_WIDTH_PX - MENU_EDGE_GAP_PX
      setPosition({ top: rect.bottom, left: Math.max(MENU_EDGE_GAP_PX, Math.min(rect.left, furthestLeft)) })
    }
  }, [cancelClose])

  const closeSoon = useCallback(() => {
    cancelClose()
    closeTimer.current = window.setTimeout(() => setPosition(null), MENU_CLOSE_DELAY_MS)
  }, [cancelClose])

  useEffect(() => {
    close()
  }, [close, pathname])

  useEffect(() => cancelClose, [cancelClose])

  useEffect(() => {
    if (!open) {
      return
    }

    const handleKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        close()
        triggerRef.current?.focus()
      }
    }
    const handlePointer = (event: PointerEvent) => {
      const target = event.target as Node | null
      if (target && !triggerRef.current?.contains(target) && !listRef.current?.contains(target)) {
        close()
      }
    }
    // The list is placed by where the name was when it opened; once anything moves, that is stale.
    const handleMove = () => close()

    document.addEventListener('keydown', handleKey)
    document.addEventListener('pointerdown', handlePointer)
    window.addEventListener('resize', handleMove)
    window.addEventListener('scroll', handleMove, true)
    return () => {
      document.removeEventListener('keydown', handleKey)
      document.removeEventListener('pointerdown', handlePointer)
      window.removeEventListener('resize', handleMove)
      window.removeEventListener('scroll', handleMove, true)
    }
  }, [close, open])

  return (
    <li onMouseEnter={show} onMouseLeave={closeSoon}>
      <button
        ref={triggerRef}
        type="button"
        aria-haspopup="true"
        aria-expanded={open}
        aria-controls={menuId}
        onClick={(event) => {
          show()
          // Opened from the keyboard: go on into the list, which is not next in the tab order.
          if (event.detail === 0) {
            window.setTimeout(() => listRef.current?.querySelector('a')?.focus(), 0)
          }
        }}
        className={desktopItemClass(active)}
      >
        {item.label}
        <svg viewBox="0 0 24 24" className="h-3.5 w-3.5" fill="none" stroke="currentColor" strokeWidth="2.5" aria-hidden="true">
          <path d="M6 9l6 6 6-6" />
        </svg>
      </button>
      {position &&
        createPortal(
          <ul
            ref={listRef}
            id={menuId}
            onMouseEnter={cancelClose}
            onMouseLeave={closeSoon}
            style={{ top: position.top + 4, left: position.left }}
            className="fixed z-50 w-44 space-y-1 rounded-xl border border-slate-700 bg-slate-900 p-2 text-white shadow-xl"
          >
            {(item.children ?? []).map((child) => (
              <li key={child.href}>
                <Link
                  href={child.href}
                  onClick={close}
                  className={`block whitespace-nowrap rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                    child.href === activeHref ? 'bg-amber-500 text-slate-950' : 'text-slate-100 hover:bg-slate-800'
                  }`}
                >
                  {child.label}
                </Link>
              </li>
            ))}
          </ul>,
          document.body
        )}
    </li>
  )
}

export function TopNavDesktop() {
  const { pathname, isLoading, navItems } = useVisibleNavItems()

  if (isLoading) {
    return null
  }

  if (navItems.length === 0) {
    return null
  }

  const activeHref = findActiveNavHref(pathname, navItems)

  return (
    <nav className="hidden overflow-x-auto md:block">
      <ul className="flex min-w-max gap-2 py-3">
        {navItems.map((item) => {
          const active = isNavItemActive(item, activeHref)

          if (item.children) {
            return <DesktopMenu key={item.href} item={item} active={active} activeHref={activeHref} />
          }

          return (
            <li key={item.href}>
              <Link href={item.href} className={desktopItemClass(active)}>
                {item.label}
              </Link>
            </li>
          )
        })}
      </ul>
    </nav>
  )
}

export function TopNavMobile() {
  const { pathname, isLoading, navItems } = useVisibleNavItems()
  const [open, setOpen] = useState<boolean>(false)

  useEffect(() => {
    setOpen(false)
  }, [pathname])

  if (isLoading || navItems.length === 0) {
    return null
  }

  const activeHref = findActiveNavHref(pathname, navItems)
  const linkClass = (href: string) =>
    `block rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
      href === activeHref ? 'bg-amber-500 text-slate-950' : 'text-slate-100 hover:bg-slate-800'
    }`

  return (
    <div className="relative md:hidden">
      <button
        type="button"
        onClick={() => setOpen((prev) => !prev)}
        aria-label="메뉴 열기"
        aria-expanded={open}
        className="inline-flex h-10 w-10 items-center justify-center rounded-lg border border-slate-700 bg-slate-800/80 text-slate-100 transition-colors hover:bg-slate-700"
      >
        {open ? (
          <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M6 6l12 12M18 6L6 18" />
          </svg>
        ) : (
          <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M4 7h16M4 12h16M4 17h16" />
          </svg>
        )}
      </button>

      {open && (
        <nav className="absolute right-0 z-40 mt-2 max-h-[calc(100vh-5rem)] w-52 overflow-y-auto rounded-xl border border-slate-700 bg-slate-900/95 p-2 shadow-xl">
          <ul className="space-y-1">
            {navItems.map((item) =>
              item.children ? (
                // A menu is shown opened out: its name as a heading, its links set in under it.
                <li key={`mobile-nav-${item.href}`}>
                  <p className="px-3 pb-1 pt-2 text-xs font-semibold text-slate-400">{item.label}</p>
                  <ul className="space-y-1 border-l border-slate-700 pl-2">
                    {item.children.map((child) => (
                      <li key={`mobile-nav-${child.href}`}>
                        <Link href={child.href} className={linkClass(child.href)}>
                          {child.label}
                        </Link>
                      </li>
                    ))}
                  </ul>
                </li>
              ) : (
                <li key={`mobile-nav-${item.href}`}>
                  <Link href={item.href} className={linkClass(item.href)}>
                    {item.label}
                  </Link>
                </li>
              )
            )}
          </ul>
        </nav>
      )}
    </div>
  )
}
