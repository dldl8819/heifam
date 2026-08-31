'use client'

import Link from 'next/link'
import { FormEvent, useCallback, useEffect, useState } from 'react'
import { useAdminAuth } from '@/lib/admin-auth'
import { apiClient } from '@/lib/api'
import { Alert, AlertContent, AlertDescription, AlertIcon } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { LedgerSection } from '@/components/ledger-section'
import { t } from '@/lib/i18n'
import type { NoticeItem } from '@/types/api'

const TEMP_GROUP_ID = 1
const NOTICE_CONTENT_MAX_LENGTH = 5000

type TopTab = 'posts' | 'donations'

function formatDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

export default function NoticesPage() {
  const { isAdmin } = useAdminAuth()
  const [topTab, setTopTab] = useState<TopTab>('posts')

  const [notices, setNotices] = useState<NoticeItem[]>([])
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)

  const [composing, setComposing] = useState<boolean>(false)
  const [title, setTitle] = useState<string>('')
  const [content, setContent] = useState<string>('')
  const [saving, setSaving] = useState<boolean>(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  const loadNotices = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const response = await apiClient.getNotices(TEMP_GROUP_ID)
      setNotices(response)
    } catch {
      setError(t('notices.loadError'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadNotices()
  }, [loadNotices])

  const handleSubmit = useCallback(
    async (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault()
      if (!title.trim()) {
        setFormError(t('notices.posts.titleRequired'))
        return
      }
      if (!content.trim()) {
        setFormError(t('notices.posts.contentRequired'))
        return
      }
      if (content.length > NOTICE_CONTENT_MAX_LENGTH) {
        setFormError(t('notices.posts.contentTooLong', { max: NOTICE_CONTENT_MAX_LENGTH }))
        return
      }

      setFormError(null)
      setSaving(true)
      try {
        await apiClient.createNotice(TEMP_GROUP_ID, { title: title.trim(), content: content.trim() })
        setTitle('')
        setContent('')
        setComposing(false)
        setSuccessMessage(t('notices.posts.saveSuccess'))
        await loadNotices()
      } catch {
        setFormError(t('notices.loadError'))
      } finally {
        setSaving(false)
      }
    },
    [content, loadNotices, title]
  )

  return (
    <section className="space-y-6">
      <div className="space-y-1">
        <h1 className="text-lg font-semibold text-slate-900 dark:text-slate-100">{t('notices.title')}</h1>
        <p className="text-sm text-slate-500 dark:text-slate-400">{t('notices.description')}</p>
      </div>

      <div className="flex gap-2 border-b border-slate-200 dark:border-slate-700">
        {(['posts', 'donations'] as TopTab[]).map((tab) => (
          <button
            key={tab}
            type="button"
            onClick={() => setTopTab(tab)}
            className={`px-3 py-2 text-sm font-medium ${
              topTab === tab
                ? 'border-b-2 border-slate-900 text-slate-900 dark:border-slate-100 dark:text-slate-100'
                : 'text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200'
            }`}
          >
            {t(`notices.tabs.${tab}`)}
          </button>
        ))}
      </div>

      {topTab === 'posts' && (
        <div className="space-y-4">
          {isAdmin && (
            <div className="flex items-center justify-end">
              <button
                type="button"
                onClick={() => {
                  setComposing((prev) => !prev)
                  setFormError(null)
                }}
                className="rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-medium text-white hover:bg-slate-800 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white"
              >
                {t('notices.posts.writeButton')}
              </button>
            </div>
          )}

          {successMessage && (
            <p className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300">
              {successMessage}
            </p>
          )}

          {composing && (
            <form
              onSubmit={handleSubmit}
              className="space-y-3 rounded-xl border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-800/60"
            >
              {formError && (
                <p className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700 dark:border-rose-800 dark:bg-rose-950/40 dark:text-rose-300">
                  {formError}
                </p>
              )}
              <input
                type="text"
                value={title}
                onChange={(event) => setTitle(event.target.value)}
                placeholder={t('notices.posts.titlePlaceholder')}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-900"
              />
              <textarea
                value={content}
                onChange={(event) => setContent(event.target.value)}
                placeholder={t('notices.posts.contentPlaceholder')}
                rows={8}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-900"
              />
              <p className="text-xs text-slate-500 dark:text-slate-400">
                {t('notices.posts.contentLimitHint', { max: NOTICE_CONTENT_MAX_LENGTH })}
              </p>
              <div className="flex gap-2">
                <button
                  type="submit"
                  disabled={saving}
                  className="rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-medium text-white hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white"
                >
                  {saving ? t('notices.posts.saving') : t('notices.posts.save')}
                </button>
                <button
                  type="button"
                  onClick={() => setComposing(false)}
                  className="rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
                >
                  {t('notices.posts.cancel')}
                </button>
              </div>
            </form>
          )}

          <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-900">
            <table className="min-w-full text-left text-sm">
              <thead className="bg-slate-50 text-xs tracking-wide text-slate-500 dark:bg-slate-800/80 dark:text-slate-300">
                <tr>
                  <th className="px-4 py-3">{t('notices.posts.titlePlaceholder')}</th>
                  <th className="px-4 py-3">{t('notices.donations.income.table.date')}</th>
                </tr>
              </thead>
              <tbody>
                {loading && (
                  <tr>
                    <td className="px-4 py-3" colSpan={2}>
                      <LoadingIndicator label={t('common.loading')} />
                    </td>
                  </tr>
                )}
                {!loading && error && (
                  <tr>
                    <td className="px-4 py-8 text-center" colSpan={2}>
                      <Alert variant="destructive" appearance="light">
                        <AlertIcon icon="destructive">!</AlertIcon>
                        <AlertContent>
                          <AlertDescription>{error}</AlertDescription>
                        </AlertContent>
                      </Alert>
                    </td>
                  </tr>
                )}
                {!loading && !error && notices.length === 0 && (
                  <tr>
                    <td className="px-4 py-8 text-center text-sm text-slate-500 dark:text-slate-400" colSpan={2}>
                      {t('notices.posts.empty')}
                    </td>
                  </tr>
                )}
                {!loading &&
                  !error &&
                  notices.map((notice) => (
                    <tr key={notice.id} className="border-t border-slate-100 dark:border-slate-800">
                      <td className="px-4 py-3">
                        <Link href={`/notices/${notice.id}`} className="font-medium text-slate-900 hover:underline dark:text-slate-100">
                          {notice.title}
                        </Link>
                      </td>
                      <td className="px-4 py-3 text-slate-500 dark:text-slate-400">{formatDate(notice.createdAt)}</td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {topTab === 'donations' && <LedgerSection />}
    </section>
  )
}
