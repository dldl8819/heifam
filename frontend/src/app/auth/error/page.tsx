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
      <header className="rounded-xl border border-slate-200 bg-white px-5 py-4 shadow-sm">
        <h2 className="text-2xl font-semibold tracking-tight">{t('auth.error.title')}</h2>
        <p className="mt-1 text-sm text-slate-600">{resolveErrorMessage(errorCode)}</p>
      </header>

      <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
        <p className="text-sm text-slate-700">{t('auth.error.help')}</p>
        <Link
          href="/"
          className="mt-3 inline-flex rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800"
        >
          {t('auth.error.goHome')}
        </Link>
      </article>
    </section>
  )
}
