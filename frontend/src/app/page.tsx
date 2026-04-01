import Link from 'next/link'
import { GridBackground } from '@/components/grid-background'
import { GoogleLoginButton } from '@/components/google-login-button'
import { t } from '@/lib/i18n'

export default function Home() {
  return (
    <section className="relative overflow-hidden rounded-2xl border border-slate-800/80 px-6 py-16 sm:px-10 sm:py-24">
      <GridBackground />
      <div className="relative z-10 mx-auto flex max-w-3xl flex-col items-center text-center">
        <p className="rounded-full border border-white/30 bg-white/10 px-3 py-1 text-xs font-semibold tracking-[0.22em] text-white">
          HEI`FAM
        </p>
        <h2 className="mt-5 text-4xl font-bold tracking-tight text-white sm:text-5xl">
          {t('landing.title')}
        </h2>
        <p className="mt-4 text-sm text-slate-200 sm:text-base">{t('landing.description')}</p>
        <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
          <Link
            href="/results"
            className="rounded-lg bg-white px-5 py-2.5 text-sm font-semibold text-slate-900 transition-colors hover:bg-slate-100"
          >
            {t('landing.actions.toResults')}
          </Link>
          <GoogleLoginButton
            className="rounded-lg border border-white/40 bg-white/10 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-white/20"
          >
            {t('landing.actions.toLogin')}
          </GoogleLoginButton>
        </div>
      </div>
    </section>
  )
}
