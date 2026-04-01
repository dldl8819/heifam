import Link from 'next/link'
import { t } from '@/lib/i18n'

const YOUTUBE_URL = 'https://www.youtube.com/@hei1749'

export function SiteFooter() {
  return (
    <footer className="border-t border-slate-200 bg-slate-100">
      <div className="mx-auto max-w-screen-2xl px-4 py-6 sm:px-6">
        <div className="rounded-xl border border-slate-200 bg-white px-4 py-4 shadow-sm">
          <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
            <div className="space-y-2 text-sm text-slate-700">
              <p>
                <span className="font-semibold text-slate-900">{t('footer.supportLabel')}</span>
                <span className="ml-2">{t('footer.supportAccount')}</span>
              </p>
              <p>
                <span className="font-semibold text-slate-900">{t('footer.youtubeLabel')}</span>
                <a
                  href={YOUTUBE_URL}
                  target="_blank"
                  rel="noreferrer"
                  className="ml-2 text-slate-700 underline-offset-2 hover:text-slate-900 hover:underline"
                >
                  {t('footer.youtubeLinkText')}
                </a>
              </p>
              <p>
                <span className="font-semibold text-slate-900">{t('footer.contactLabel')}</span>
                <span className="ml-2">{t('footer.contactName')}</span>
              </p>
            </div>

            <div className="flex flex-col items-start gap-2 md:items-end">
              <p className="text-xs text-slate-500">
                {t('footer.copyright', { year: new Date().getFullYear() })}
              </p>
              <div className="flex items-center gap-3 text-sm">
                <Link
                  href="/privacy"
                  className="font-medium text-slate-600 transition-colors hover:text-slate-900"
                >
                  {t('footer.privacy')}
                </Link>
                <Link
                  href="/terms"
                  className="font-medium text-slate-600 transition-colors hover:text-slate-900"
                >
                  {t('footer.terms')}
                </Link>
              </div>
            </div>
          </div>
        </div>
      </div>
    </footer>
  )
}
