'use client'

import { useEffect, useRef, type MouseEvent, type ReactNode } from 'react'
import Image from 'next/image'
import { usePathname } from 'next/navigation'
import { AuthControls } from '@/components/auth-controls'
import { AccessGate } from '@/components/access-gate'
import { ContrastToggle } from '@/components/contrast-toggle'
import { MenuAttendance } from '@/components/menu-attendance'
import { pageNameFor } from '@/components/nav-items'
import { NotificationBell } from '@/components/notification-bell'
import { ThemeToggle } from '@/components/theme-toggle'
import { SiteFooter } from '@/components/site-footer'
import { TopNavDesktop, TopNavMobile } from '@/components/top-nav'
import { UserRaceSetupModal } from '@/components/user-race-setup-modal'
import { t } from '@/lib/i18n'
import { MmrVisibilityProvider } from '@/lib/mmr-visibility'

type AppShellProps = {
  children: ReactNode
}

const MAIN_ID = 'main-content'

/** Past the header and its menus, straight to the page: for whoever moves by the keyboard. */
function skipToMain(event: MouseEvent<HTMLAnchorElement>) {
  const main = document.getElementById(MAIN_ID)
  if (main) {
    event.preventDefault()
    // The page starts right under the header, so the top of the window shows its start; jumping
    // to it instead would slide it under the header, which stays on top.
    window.scrollTo({ top: 0 })
    main.focus({ preventScroll: true })
  }
}

export function AppShell({ children }: AppShellProps) {
  const pageName = pageNameFor(usePathname())
  const ownTitleRef = useRef<string | null>(null)

  // Each page its own window title. It is also what a screen reader hears on moving to a page:
  // Next tells it of a new page only when the title changes. Next writes the site's title over it
  // once the page's metadata arrives (on opening a page straight), so it is put back whenever the
  // title turns into the site's again. A page that sets its own title (an event) keeps it.
  useEffect(() => {
    const siteTitle = t('meta.title')
    const title = pageName ? t('a11y.pageTitle', { page: pageName, site: siteTitle }) : siteTitle
    const apply = () => {
      if (document.title !== title && (document.title === siteTitle || document.title === ownTitleRef.current)) {
        document.title = title
        ownTitleRef.current = title
      }
    }
    apply()
    const observer = new MutationObserver(apply)
    observer.observe(document.head, { childList: true, subtree: true, characterData: true })
    return () => observer.disconnect()
  }, [pageName])

  return (
    <MmrVisibilityProvider>
      <div className="min-h-screen bg-slate-100 text-slate-900 transition-colors dark:bg-slate-950 dark:text-slate-100">
        <a
          href={`#${MAIN_ID}`}
          onClick={skipToMain}
          className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded-lg focus:bg-amber-400 focus:px-4 focus:py-2 focus:text-sm focus:font-semibold focus:text-slate-950"
        >
          {t('a11y.skipToContent')}
        </a>
        <header className="sticky top-0 z-30 border-b border-slate-700 bg-slate-900/95 text-white backdrop-blur-sm">
          <div className="mx-auto flex max-w-(--breakpoint-2xl) flex-col gap-3 px-4 py-4 sm:px-6">
            {/* One row at every width: on a narrow phone the name and tagline give way (the tagline
                wraps) so the buttons stay on the right. The row is also what the notification panel
                hangs from on phones. */}
            <div className="relative flex items-start justify-between gap-3">
              <div className="flex min-w-0 flex-1 items-start gap-3">
                <div className="relative h-11 w-11 shrink-0 overflow-hidden rounded-md border border-slate-700/80 bg-slate-950/70">
                  <Image
                    src="/logo.jpg"
                    alt={`${t('brand.name')} logo`}
                    fill
                    className="object-cover"
                    sizes="44px"
                    priority
                  />
                </div>
                <div className="min-w-0 space-y-0.5">
                  {/* The site's name, not the page's: the page's heading is in <main>. */}
                  <p className="text-xl font-bold tracking-tight">{t('brand.name')}</p>
                  <p className="break-keep text-xs text-slate-300">{t('brand.tagline')}</p>
                </div>
              </div>
              <div className="flex items-start gap-2">
                <NotificationBell />
                <ThemeToggle />
                <ContrastToggle />
                <TopNavMobile />
                <div className="hidden w-full max-w-sm md:block">
                  <AuthControls />
                </div>
              </div>
            </div>
            <TopNavDesktop />
            <div className="md:hidden">
              <AuthControls />
            </div>
          </div>
        </header>

        <main id={MAIN_ID} tabIndex={-1} className="mx-auto max-w-(--breakpoint-2xl) p-4 outline-hidden sm:p-6">
          {/* The page's name heads it for a screen reader; the menu shows it on screen. */}
          <h1 className="sr-only">{pageName ?? t('brand.name')}</h1>
          <AccessGate>{children}</AccessGate>
        </main>
        <SiteFooter />
        <UserRaceSetupModal />
        <MenuAttendance />
      </div>
    </MmrVisibilityProvider>
  )
}
