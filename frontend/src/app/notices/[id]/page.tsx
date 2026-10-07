'use client'

import Link from 'next/link'
import { useRouter, useParams } from 'next/navigation'
import { FormEvent, useCallback, useEffect, useState } from 'react'
import { useAdminAuth } from '@/lib/admin-auth'
import { ApiRequestError, apiClient } from '@/lib/api'
import { Alert, AlertContent, AlertDescription, AlertIcon } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { NoticeBody } from '@/components/notice-body'
import { NoticeContentEditor } from '@/components/notice-content-editor'
import { NOTICE_IMAGE_MAX_COUNT, noticeImageIds } from '@/lib/notice-images'
import { NOTICE_COMMENT_MAX_LENGTH, validateNoticeComment } from '@/lib/notice-list'
import { t } from '@/lib/i18n'
import type { NoticeDetail } from '@/types/api'

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
  const { isAdmin, isSuperAdmin } = useAdminAuth()

  const [notice, setNotice] = useState<NoticeDetail | null>(null)
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)

  const [editing, setEditing] = useState<boolean>(false)
  const [title, setTitle] = useState<string>('')
  const [content, setContent] = useState<string>('')
  const [adminOnly, setAdminOnly] = useState<boolean>(false)
  // Off for every edit: announcing again is a choice, not something a typo fix should do.
  const [notifyAgain, setNotifyAgain] = useState<boolean>(false)
  const [saving, setSaving] = useState<boolean>(false)
  const [imageUploading, setImageUploading] = useState<boolean>(false)
  const [formError, setFormError] = useState<string | null>(null)

  const [commentDraft, setCommentDraft] = useState<string>('')
  const [commentBusy, setCommentBusy] = useState<boolean>(false)
  const [likeBusy, setLikeBusy] = useState<boolean>(false)
  const [engagementError, setEngagementError] = useState<string | null>(null)

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
      setAdminOnly(response.adminOnly)
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
      if (noticeImageIds(content).length > NOTICE_IMAGE_MAX_COUNT) {
        setFormError(t('notices.posts.imageTooMany', { max: NOTICE_IMAGE_MAX_COUNT }))
        return
      }

      setFormError(null)
      setSaving(true)
      try {
        const response = await apiClient.updateNotice(TEMP_GROUP_ID, noticeId, {
          title: title.trim(),
          content: content.trim(),
          adminOnly,
          notify: notifyAgain,
        })
        setNotice((prev) =>
          prev
            ? {
                ...prev,
                title: response.title,
                content: response.content,
                updatedAt: response.updatedAt,
                adminOnly: response.adminOnly,
              }
            : prev
        )
        setEditing(false)
        setNotifyAgain(false)
      } catch (error) {
        // 409: the text names an image the notice cannot show; nothing was saved.
        setFormError(
          error instanceof ApiRequestError && error.status === 409
            ? t('notices.posts.imageUnavailable')
            : t('notices.loadError')
        )
      } finally {
        setSaving(false)
      }
    },
    [adminOnly, content, noticeId, notifyAgain, title]
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

  const handleLike = useCallback(async () => {
    if (!notice || likeBusy) {
      return
    }
    setLikeBusy(true)
    setEngagementError(null)
    try {
      setNotice(await apiClient.setNoticeLike(TEMP_GROUP_ID, noticeId, !notice.likedByMe))
    } catch {
      setEngagementError(t('notices.loadError'))
    } finally {
      setLikeBusy(false)
    }
  }, [likeBusy, notice, noticeId])

  const handleComment = useCallback(
    async (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault()
      const problem = validateNoticeComment(commentDraft)
      if (problem) {
        setEngagementError(t(`notices.posts.${problem}`, { max: NOTICE_COMMENT_MAX_LENGTH }))
        return
      }
      setCommentBusy(true)
      setEngagementError(null)
      try {
        setNotice(await apiClient.addNoticeComment(TEMP_GROUP_ID, noticeId, commentDraft.trim()))
        setCommentDraft('')
      } catch {
        setEngagementError(t('notices.loadError'))
      } finally {
        setCommentBusy(false)
      }
    },
    [commentDraft, noticeId]
  )

  const handleDeleteComment = useCallback(
    async (commentId: number) => {
      if (!window.confirm(t('notices.posts.commentDeleteConfirm'))) {
        return
      }
      setCommentBusy(true)
      setEngagementError(null)
      try {
        setNotice(await apiClient.deleteNoticeComment(TEMP_GROUP_ID, noticeId, commentId))
      } catch {
        setEngagementError(t('notices.loadError'))
      } finally {
        setCommentBusy(false)
      }
    },
    [noticeId]
  )

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
        <>
          <article className="space-y-4 rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900">
            <div className="space-y-1">
              <div className="flex flex-wrap items-center gap-2">
                <h1 className="text-lg font-semibold text-slate-900 dark:text-slate-100">{notice.title}</h1>
                {notice.adminOnly && (
                  <span className="rounded-full bg-amber-100 px-2 py-0.5 text-[11px] font-medium text-amber-800 dark:bg-amber-900/40 dark:text-amber-300">
                    {t('notices.posts.adminOnlyBadge')}
                  </span>
                )}
              </div>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                {formatDate(notice.createdAt)}
                {notice.authorNickname ? ` · ${notice.authorNickname}` : ''}
                {notice.updatedAt !== notice.createdAt
                  ? ` · ${t('notices.posts.updatedAt', { date: formatDate(notice.updatedAt) })}`
                  : ''}
              </p>
            </div>
            <NoticeBody groupId={TEMP_GROUP_ID} content={notice.content} />

            <div className="flex flex-wrap items-center gap-2 border-t border-slate-100 pt-4 dark:border-slate-800">
              <button
                type="button"
                onClick={handleLike}
                disabled={likeBusy}
                aria-pressed={notice.likedByMe}
                aria-label={notice.likedByMe ? t('notices.posts.liked') : t('notices.posts.like')}
                className={`rounded-lg border px-3 py-1.5 text-xs font-medium disabled:cursor-not-allowed disabled:opacity-60 ${
                  notice.likedByMe
                    ? 'border-rose-300 bg-rose-50 text-rose-600 dark:border-rose-800 dark:bg-rose-950/40 dark:text-rose-300'
                    : 'border-slate-300 text-slate-700 hover:bg-slate-50 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800'
                }`}
              >
                {notice.likedByMe ? '♥' : '♡'} {t('notices.posts.likeCount', { count: notice.likeCount })}
              </button>

              {isAdmin && (
                <>
                  <button
                    type="button"
                    onClick={() => setEditing(true)}
                    className="rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
                  >
                    {t('notices.posts.editButton')}
                  </button>
                  {isSuperAdmin && (
                    <button
                      type="button"
                      onClick={handleDelete}
                      className="rounded-lg border border-rose-300 px-3 py-1.5 text-xs font-medium text-rose-600 hover:bg-rose-50 dark:border-rose-800 dark:text-rose-400 dark:hover:bg-rose-950/40"
                    >
                      {t('notices.posts.deleteButton')}
                    </button>
                  )}
                </>
              )}
            </div>
          </article>

          <div className="space-y-3 rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900">
            <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
              {t('notices.posts.commentCount', { count: notice.comments.length })}
            </h2>

            {engagementError && (
              <p className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700 dark:border-rose-800 dark:bg-rose-950/40 dark:text-rose-300">
                {engagementError}
              </p>
            )}

            {notice.comments.length === 0 ? (
              <p className="text-xs text-slate-500 dark:text-slate-400">{t('notices.posts.commentEmpty')}</p>
            ) : (
              <ul className="divide-y divide-slate-100 dark:divide-slate-800">
                {notice.comments.map((comment) => (
                  <li key={comment.id} className="space-y-1 py-2">
                    <div className="flex items-center justify-between gap-2">
                      <p className="text-xs text-slate-500 dark:text-slate-400">
                        <span className="font-medium text-slate-700 dark:text-slate-200">
                          {comment.authorNickname ?? '-'}
                        </span>
                        {` · ${formatDate(comment.createdAt)}`}
                      </p>
                      {comment.canDelete && (
                        <button
                          type="button"
                          onClick={() => void handleDeleteComment(comment.id)}
                          disabled={commentBusy}
                          className="text-xs text-rose-600 hover:underline disabled:opacity-60 dark:text-rose-400"
                        >
                          {t('notices.posts.commentDelete')}
                        </button>
                      )}
                    </div>
                    <p className="whitespace-pre-wrap text-sm text-slate-700 dark:text-slate-200">{comment.content}</p>
                  </li>
                ))}
              </ul>
            )}

            <form onSubmit={handleComment} className="space-y-2">
              <textarea
                value={commentDraft}
                onChange={(event) => setCommentDraft(event.target.value)}
                placeholder={t('notices.posts.commentPlaceholder')}
                maxLength={NOTICE_COMMENT_MAX_LENGTH}
                rows={3}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-900"
              />
              <div className="flex items-center justify-between gap-2">
                <span className="text-xs text-slate-400 dark:text-slate-500">
                  {commentDraft.length}/{NOTICE_COMMENT_MAX_LENGTH}
                </span>
                <button
                  type="submit"
                  disabled={commentBusy}
                  className="rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-medium text-white hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white"
                >
                  {commentBusy ? t('notices.posts.commentSubmitting') : t('notices.posts.commentSubmit')}
                </button>
              </div>
            </form>
          </div>
        </>
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
          <NoticeContentEditor
            groupId={TEMP_GROUP_ID}
            value={content}
            onChange={setContent}
            placeholder={t('notices.posts.contentPlaceholder')}
            onUploadingChange={setImageUploading}
          />
          <p className="text-xs text-slate-500 dark:text-slate-400">
            {t('notices.posts.contentLimitHint', { max: NOTICE_CONTENT_MAX_LENGTH })}
          </p>
          <label className="flex items-center gap-2 text-xs text-slate-700 dark:text-slate-200">
            <input type="checkbox" checked={adminOnly} onChange={(event) => setAdminOnly(event.target.checked)} />
            {t('notices.posts.adminOnlyLabel')}
          </label>
          <div className="space-y-1">
            <label className="flex items-center gap-2 text-xs text-slate-700 dark:text-slate-200">
              <input type="checkbox" checked={notifyAgain} onChange={(event) => setNotifyAgain(event.target.checked)} />
              {t('notices.posts.notifyAgainLabel')}
            </label>
            <p className="pl-5 text-xs text-slate-500 dark:text-slate-400">{t('notices.posts.notifyAgainHint')}</p>
          </div>
          <div className="flex gap-2">
            <button
              type="submit"
              disabled={saving || imageUploading}
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
                setAdminOnly(notice.adminOnly)
                setNotifyAgain(false)
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
