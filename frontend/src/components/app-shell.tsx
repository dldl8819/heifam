'use client'

import { useEffect, useState, type ReactNode } from 'react'
import Image from 'next/image'
import { AuthControls } from '@/components/auth-controls'
import { AccessGate } from '@/components/access-gate'
import { SiteFooter } from '@/components/site-footer'
import { TopNavDesktop, TopNavMobile } from '@/components/top-nav'
import { UserRaceSetupModal } from '@/components/user-race-setup-modal'
import { t } from '@/lib/i18n'
import { MmrVisibilityProvider } from '@/lib/mmr-visibility'

type AppShellProps = {
  children: ReactNode
}

export function AppShell({ children }: AppShellProps) {
  const [isScrolled, setIsScrolled] = useState<boolean>(false)

  useEffect(() => {
    const handleScroll = () => {
      setIsScrolled(window.scrollY > 18)
    }

    handleScroll()
    window.addEventListener('scroll', handleScroll, { passive: true })
    return () => window.removeEventListener('scroll', handleScroll)
  }, [])

  return (
    <MmrVisibilityProvider>
      <div className="min-h-screen bg-slate-100 text-slate-900">
        <header className="sticky top-0 z-30 border-b border-slate-700 bg-slate-900/95 text-white backdrop-blur">
          <div
            className={`mx-auto flex flex-col gap-3 transition-all duration-300 ${
              isScrolled
                ? 'max-w-screen-2xl px-3 py-2 sm:px-4 md:px-6 md:py-4'
                : 'max-w-screen-2xl px-4 py-4 sm:px-6'
            }`}
          >
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="flex items-start gap-3">
                <div className="relative h-11 w-11 overflow-hidden rounded-md border border-slate-700/80 bg-slate-950/70">
                  <Image
                    src="/logo.jpg"
                    alt={`${t('brand.name')} logo`}
                    fill
                    className="object-cover"
                    sizes="44px"
                    priority
                  />
                </div>
                <div className="space-y-0.5">
                  <h1 className="text-xl font-bold tracking-tight">{t('brand.name')}</h1>
                  <p className="text-xs text-slate-300">{t('brand.tagline')}</p>
                </div>
              </div>
              <div className="flex items-start gap-2">
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

        <main className="mx-auto max-w-screen-2xl p-4 sm:p-6">
          <AccessGate>{children}</AccessGate>
        </main>
        <SiteFooter />
        <UserRaceSetupModal />
      </div>
    </MmrVisibilityProvider>
  )
}
