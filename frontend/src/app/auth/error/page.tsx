'use client'

import Link from 'next/link'
import { useSearchParams } from 'next/navigation'
import { t } from '@/lib/i18n'

function resolveErrorMessage(errorCode: string | null): string {
  if (!errorCode) {
    return t('auth.error.default')
  }

  if (errorCode === 'Configuration') {
    return t('auth.error.configuration')
  }

  if (errorCode === 'AccessDenied') {
    return t('auth.error.accessDenied')
  }

  return t('auth.error.default')
}

export default function AuthErrorPage() {
  const searchParams = useSearchParams()
  const errorCode = searchParams.get('error')

  return (
    <section className="space-y-6">
      <header className="rounded-xl border border-slate-200 bg-white px-5 py-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
        <h2 className="text-2xl font-semibold tracking-tight">{t('auth.error.title')}</h2>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-300">{resolveErrorMessage(errorCode)}</p>
      </header>

      <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
        <p className="text-sm text-slate-700 dark:text-slate-300">{t('auth.error.help')}</p>
        <Link
          href="/"
          className="mt-3 inline-flex rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white"
        >
          {t('auth.error.goHome')}
        </Link>
      </article>
    </section>
  )
}
