'use client'

import Link from 'next/link'
import { AdminOnlyContent } from '@/components/admin-only-content'
import { useAdminAuth } from '@/lib/admin-auth'
import { t } from '@/lib/i18n'

export default function ImportPage() {
  const { isSuperAdmin } = useAdminAuth()

  return (
    <section className="space-y-6">
      <header className="space-y-1 rounded-xl border border-slate-200 bg-white px-5 py-4 shadow-sm">
        <h2 className="text-2xl font-semibold tracking-tight">{t('adminGuard.importCenterTitle')}</h2>
        <p className="text-sm text-slate-600">{t('adminGuard.importCenterDescription')}</p>
      </header>

      <AdminOnlyContent>
        <div className="grid gap-4 md:grid-cols-2">
          {isSuperAdmin && (
            <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <h3 className="text-sm font-semibold text-slate-900">{t('players.import.title')}</h3>
              <p className="mt-1 text-xs text-slate-600">{t('adminGuard.playersImportGuide')}</p>
              <Link
                href="/players#player-import"
                className="mt-3 inline-flex rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800"
              >
                {t('adminGuard.goPlayersImport')}
              </Link>
            </article>
          )}

          {isSuperAdmin && (
            <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <h3 className="text-sm font-semibold text-slate-900">{t('results.import.title')}</h3>
              <p className="mt-1 text-xs text-slate-600">{t('adminGuard.matchesImportGuide')}</p>
              <Link
                href="/results#match-import"
                className="mt-3 inline-flex rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800"
              >
                {t('adminGuard.goMatchesImport')}
              </Link>
            </article>
          )}
        </div>
      </AdminOnlyContent>
    </section>
  )
}
