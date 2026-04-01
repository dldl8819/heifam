'use client'

import { useEffect, useMemo, useState } from 'react'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { getVisibleNavItems } from '@/components/nav-items'
import { useAdminAuth } from '@/lib/admin-auth'

function isActive(pathname: string, href: string): boolean {
  if (href === '/') {
    return pathname === '/'
  }

  return pathname === href || pathname.startsWith(`${href}/`)
}

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

export function TopNavDesktop() {
  const { pathname, isLoading, navItems } = useVisibleNavItems()

  if (isLoading) {
    return null
  }

  if (navItems.length === 0) {
    return null
  }

  const activeHref = [...navItems]
    .filter((item) => isActive(pathname, item.href))
    .sort((a, b) => b.href.length - a.href.length)[0]?.href

  return (
    <nav className="hidden overflow-x-auto md:block">
      <ul className="flex min-w-max gap-2 py-3">
        {navItems.map((item) => {
          const active = item.href === activeHref

          return (
            <li key={item.href}>
              <Link
                href={item.href}
                className={`inline-flex rounded-lg px-4 py-2 text-sm font-medium transition-colors ${
                  active
                    ? 'bg-amber-500 text-slate-950'
                    : 'text-slate-200 hover:bg-slate-700 hover:text-white'
                }`}
              >
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

  const activeHref = [...navItems]
    .filter((item) => isActive(pathname, item.href))
    .sort((a, b) => b.href.length - a.href.length)[0]?.href

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
        <nav className="absolute right-0 z-40 mt-2 w-52 overflow-hidden rounded-xl border border-slate-700 bg-slate-900/95 p-2 shadow-xl">
          <ul className="space-y-1">
            {navItems.map((item) => {
              const active = item.href === activeHref
              return (
                <li key={`mobile-nav-${item.href}`}>
                  <Link
                    href={item.href}
                    className={`block rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                      active
                        ? 'bg-amber-500 text-slate-950'
                        : 'text-slate-100 hover:bg-slate-800'
                    }`}
                  >
                    {item.label}
                  </Link>
                </li>
              )
            })}
          </ul>
        </nav>
      )}
    </div>
  )
}
