'use client'

import Link from 'next/link'
import { useRouter, useParams } from 'next/navigation'
import { FormEvent, useCallback, useEffect, useState } from 'react'
import { useAdminAuth } from '@/lib/admin-auth'
import { apiClient } from '@/lib/api'
import { Alert, AlertContent, AlertDescription, AlertIcon } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { t } from '@/lib/i18n'
import type { NoticeItem } from '@/types/api'

const TEMP_GROUP_ID = 1
const NOTICE_CONTENT_MAX_LENGTH = 5000

function formatDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

export default function NoticeDetailPage() {
  const router = useRouter()
  const params = useParams<{ id: string }>()
  const noticeId = Number(params.id)
  const { isAdmin } = useAdminAuth()

  const [notice, setNotice] = useState<NoticeItem | null>(null)
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)

  const [editing, setEditing] = useState<boolean>(false)
  const [title, setTitle] = useState<string>('')
  const [content, setContent] = useState<string>('')
  const [saving, setSaving] = useState<boolean>(false)
  const [formError, setFormError] = useState<string | null>(null)

  const loadNotice = useCallback(async () => {
    if (!Number.isFinite(noticeId)) {
      setError(t('notices.posts.notFound'))
      setLoading(false)
      return
    }

    setLoading(true)
    setError(null)
    try {
      const response = await apiClient.getNotice(TEMP_GROUP_ID, noticeId)
      setNotice(response)
      setTitle(response.title)
      setContent(response.content)
    } catch {
      setError(t('notices.posts.notFound'))
    } finally {
      setLoading(false)
    }
  }, [noticeId])

  useEffect(() => {
    void loadNotice()
  }, [loadNotice])

  const handleUpdate = useCallback(
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
        const response = await apiClient.updateNotice(TEMP_GROUP_ID, noticeId, {
          title: title.trim(),
          content: content.trim(),
        })
        setNotice(response)
        setEditing(false)
      } catch {
        setFormError(t('notices.loadError'))
      } finally {
        setSaving(false)
      }
    },
    [content, noticeId, title]
  )

  const handleDelete = useCallback(async () => {
    if (!notice) {
      return
    }
    if (!window.confirm(t('notices.posts.deleteConfirm', { title: notice.title }))) {
      return
    }
    try {
      await apiClient.deleteNotice(TEMP_GROUP_ID, noticeId)
      router.push('/notices')
    } catch {
      setFormError(t('notices.loadError'))
    }
  }, [noticeId, notice, router])

  return (
    <section className="space-y-6">
      <Link href="/notices" className="text-sm text-slate-500 hover:underline dark:text-slate-400">
        {t('notices.posts.backToList')}
      </Link>

      {loading && <LoadingIndicator label={t('common.loading')} />}

      {!loading && error && (
        <Alert variant="destructive" appearance="light">
          <AlertIcon icon="destructive">!</AlertIcon>
          <AlertContent>
            <AlertDescription>{error}</AlertDescription>
          </AlertContent>
        </Alert>
      )}

      {!loading && !error && notice && !editing && (
        <article className="space-y-4 rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900">
          <div className="space-y-1">
            <h1 className="text-lg font-semibold text-slate-900 dark:text-slate-100">{notice.title}</h1>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              {formatDate(notice.createdAt)}
              {notice.authorNickname ? ` · ${notice.authorNickname}` : ''}
              {notice.updatedAt !== notice.createdAt
                ? ` · ${t('notices.posts.updatedAt', { date: formatDate(notice.updatedAt) })}`
                : ''}
            </p>
          </div>
          <p className="whitespace-pre-wrap text-sm text-slate-700 dark:text-slate-200">{notice.content}</p>

          {isAdmin && (
            <div className="flex gap-2 border-t border-slate-100 pt-4 dark:border-slate-800">
              <button
                type="button"
                onClick={() => setEditing(true)}
                className="rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
              >
                {t('notices.posts.editButton')}
              </button>
              <button
                type="button"
                onClick={handleDelete}
                className="rounded-lg border border-rose-300 px-3 py-1.5 text-xs font-medium text-rose-600 hover:bg-rose-50 dark:border-rose-800 dark:text-rose-400 dark:hover:bg-rose-950/40"
              >
                {t('notices.posts.deleteButton')}
              </button>
            </div>
          )}
        </article>
      )}

      {!loading && !error && notice && editing && (
        <form
          onSubmit={handleUpdate}
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
              onClick={() => {
                setEditing(false)
                setTitle(notice.title)
                setContent(notice.content)
                setFormError(null)
              }}
              className="rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
            >
              {t('notices.posts.cancel')}
            </button>
          </div>
        </form>
      )}
    </section>
  )
}
