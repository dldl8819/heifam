import Link from 'next/link'
import { t } from '@/lib/i18n'

const sections = [
  {
    title: t('legal.privacy.sections.collect.title'),
    items: [
      t('legal.privacy.sections.collect.items.one'),
      t('legal.privacy.sections.collect.items.two'),
      t('legal.privacy.sections.collect.items.three'),
    ],
  },
  {
    title: t('legal.privacy.sections.purpose.title'),
    items: [
      t('legal.privacy.sections.purpose.items.one'),
      t('legal.privacy.sections.purpose.items.two'),
      t('legal.privacy.sections.purpose.items.three'),
    ],
  },
  {
    title: t('legal.privacy.sections.retention.title'),
    items: [
      t('legal.privacy.sections.retention.items.one'),
      t('legal.privacy.sections.retention.items.two'),
      t('legal.privacy.sections.retention.items.three'),
    ],
  },
  {
    title: t('legal.privacy.sections.thirdParty.title'),
    items: [
      t('legal.privacy.sections.thirdParty.items.one'),
      t('legal.privacy.sections.thirdParty.items.two'),
    ],
  },
  {
    title: t('legal.privacy.sections.changes.title'),
    items: [
      t('legal.privacy.sections.changes.items.one'),
      t('legal.privacy.sections.changes.items.two'),
    ],
  },
]

export default function PrivacyPage() {
  return (
    <section className="space-y-6">
      <header className="rounded-xl border border-slate-200 bg-white px-5 py-4 shadow-sm">
        <h2 className="text-2xl font-semibold tracking-tight">{t('legal.privacy.title')}</h2>
        <p className="mt-1 text-sm text-slate-600">
          {t('legal.privacy.description')}
        </p>
        <p className="mt-2 text-xs text-slate-500">
          {t('legal.common.updatedAt', { date: '2026-04-01' })}
        </p>
      </header>

      <article className="space-y-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
        {sections.map((section) => (
          <section key={section.title} className="space-y-2">
            <h3 className="text-sm font-semibold text-slate-900">{section.title}</h3>
            <ul className="space-y-1 text-sm text-slate-700">
              {section.items.map((item) => (
                <li key={`${section.title}-${item}`}>{`- ${item}`}</li>
              ))}
            </ul>
          </section>
        ))}
      </article>

      <div className="flex flex-wrap items-center gap-2">
        <Link
          href="/terms"
          className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 transition-colors hover:border-slate-400 hover:bg-slate-50"
        >
          {t('legal.privacy.links.terms')}
        </Link>
        <Link
          href="/"
          className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800"
        >
          {t('legal.common.goHome')}
        </Link>
      </div>
    </section>
  )
}
