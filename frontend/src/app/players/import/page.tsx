import Link from 'next/link'
import { AdminOnlyContent } from '@/components/admin-only-content'
import { t } from '@/lib/i18n'

export default function PlayersImportPage() {
  return (
    <section className="space-y-6">
      <header className="space-y-1 rounded-xl border border-slate-200 bg-white px-5 py-4 shadow-sm">
        <h2 className="text-2xl font-semibold tracking-tight">{t('players.import.title')}</h2>
        <p className="text-sm text-slate-600">{t('players.import.description')}</p>
      </header>

      <AdminOnlyContent>
        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-sm text-slate-700">{t('adminGuard.playersImportGuide')}</p>
          <Link
            href="/players#player-import"
            className="mt-3 inline-flex rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800"
          >
            {t('adminGuard.goPlayersImport')}
          </Link>
        </article>
      </AdminOnlyContent>
    </section>
  )
}
