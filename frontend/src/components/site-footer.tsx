import Link from 'next/link'
import { t } from '@/lib/i18n'

const YOUTUBE_URL = 'https://www.youtube.com/@Hei-minsik'
const INSTAGRAM_URL = 'https://www.instagram.com/hei.minsik'

export function SiteFooter() {
  return (
    <footer className="border-t border-slate-200 bg-slate-100 dark:border-slate-800 dark:bg-slate-950">
      <div className="mx-auto max-w-screen-2xl px-4 py-6 sm:px-6">
        <div className="rounded-xl border border-slate-200 bg-white px-4 py-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
          <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
            <div className="space-y-2 text-sm text-slate-700 dark:text-slate-300">
              <p>
                <span className="font-semibold text-slate-900 dark:text-slate-100">{t('footer.supportLabel')}</span>
                <span className="ml-2">{t('footer.supportAccount')}</span>
              </p>
              <p>
                <span className="font-semibold text-slate-900 dark:text-slate-100">{t('footer.youtubeLabel')}</span>
                <a
                  href={YOUTUBE_URL}
                  target="_blank"
                  rel="noreferrer"
                  className="ml-2 text-slate-700 underline-offset-2 hover:text-slate-900 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500 dark:text-slate-300 dark:hover:text-white"
                >
                  {t('footer.youtubeLinkText')}
                </a>
              </p>
              <p>
                <span className="font-semibold text-slate-900 dark:text-slate-100">{t('footer.instagramLabel')}</span>
                <a
                  href={INSTAGRAM_URL}
                  target="_blank"
                  rel="noreferrer"
                  className="ml-2 text-slate-700 underline-offset-2 hover:text-slate-900 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500 dark:text-slate-300 dark:hover:text-white"
                >
                  {t('footer.instagramLinkText')}
                </a>
              </p>
              <p>
                <span className="font-semibold text-slate-900 dark:text-slate-100">{t('footer.contactLabel')}</span>
                <span className="ml-2">{t('footer.contactName')}</span>
              </p>
            </div>

            <div className="flex flex-col items-start gap-2 md:items-end">
              <p className="text-xs text-slate-500 dark:text-slate-400">
                {t('footer.copyright', { year: new Date().getFullYear() })}
              </p>
              <div className="flex items-center gap-3 text-sm">
                <Link
                  href="/privacy"
                  className="font-medium text-slate-600 transition-colors hover:text-slate-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500 dark:text-slate-300 dark:hover:text-white"
                >
                  {t('footer.privacy')}
                </Link>
                <Link
                  href="/terms"
                  className="font-medium text-slate-600 transition-colors hover:text-slate-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500 dark:text-slate-300 dark:hover:text-white"
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
