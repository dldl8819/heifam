'use client'

import { FormEvent, useCallback, useMemo, useState } from 'react'
import { ApiRequestError, apiClient } from '@/lib/api'
import {
  NOTICE_COMMENT_MAX_LENGTH,
  countNoticeComments,
  threadNoticeComments,
  validateNoticeComment,
} from '@/lib/notice-list'
import { formatKstFullDateTime } from '@/lib/kst-time'
import { t } from '@/lib/i18n'
import type { NoticeComment, NoticeDetail } from '@/types/api'

type NoticeCommentsProps = {
  groupId: number
  noticeId: number
  comments: NoticeComment[]
  // Every change answers with the notice as it now is.
  onChange: (notice: NoticeDetail) => void
}

const fieldClass = 'w-full rounded-md border border-slate-300 px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-900'
const primaryButtonClass =
  'rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-medium text-white hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white'
const plainButtonClass =
  'rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800'
const textButtonClass = 'text-xs text-slate-500 hover:underline disabled:opacity-60 dark:text-slate-400'

/** Comments on a notice: answering, editing and removing one's own, and liking other people's. */
export function NoticeComments({ groupId, noticeId, comments, onChange }: NoticeCommentsProps) {
  const [draft, setDraft] = useState<string>('')
  // The comment a reply is being written under; a reply to a reply goes under the same comment.
  const [replyTo, setReplyTo] = useState<number | null>(null)
  const [replyDraft, setReplyDraft] = useState<string>('')
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editDraft, setEditDraft] = useState<string>('')
  const [busy, setBusy] = useState<boolean>(false)
  const [error, setError] = useState<string | null>(null)

  const threads = useMemo(() => threadNoticeComments(comments), [comments])

  const run = useCallback(
    async (action: () => Promise<NoticeDetail>, onDone?: () => void) => {
      setBusy(true)
      setError(null)
      try {
        onChange(await action())
        onDone?.()
      } catch (caught) {
        const status = caught instanceof ApiRequestError ? caught.status : 0
        // 404: someone removed the comment meanwhile. 403: it is not this member's to change or like.
        setError(
          status === 404
            ? t('notices.posts.commentGone')
            : status === 403
              ? t('notices.posts.commentNotAllowed')
              : t('notices.loadError')
        )
      } finally {
        setBusy(false)
      }
    },
    [onChange]
  )

  const checked = useCallback((text: string): boolean => {
    const problem = validateNoticeComment(text)
    if (problem) {
      setError(t(`notices.posts.${problem}`, { max: NOTICE_COMMENT_MAX_LENGTH }))
      return false
    }
    return true
  }, [])

  const handleComment = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (checked(draft)) {
      void run(() => apiClient.addNoticeComment(groupId, noticeId, draft.trim()), () => setDraft(''))
    }
  }

  const handleReply = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (replyTo !== null && checked(replyDraft)) {
      const answered = replyTo
      void run(() => apiClient.addNoticeComment(groupId, noticeId, replyDraft.trim(), answered), () => {
        setReplyTo(null)
        setReplyDraft('')
      })
    }
  }

  const handleEdit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (editingId !== null && checked(editDraft)) {
      const edited = editingId
      void run(() => apiClient.updateNoticeComment(groupId, noticeId, edited, editDraft.trim()), () => {
        setEditingId(null)
        setEditDraft('')
      })
    }
  }

  const handleDelete = (comment: NoticeComment) => {
    if (window.confirm(t('notices.posts.commentDeleteConfirm'))) {
      void run(() => apiClient.deleteNoticeComment(groupId, noticeId, comment.id))
    }
  }

  const handleLike = (comment: NoticeComment) => {
    void run(() => apiClient.setNoticeCommentLike(groupId, noticeId, comment.id, !comment.likedByMe))
  }

  const startReply = (comment: NoticeComment) => {
    setReplyTo(comment.parentId ?? comment.id)
    setReplyDraft('')
    setEditingId(null)
    setError(null)
  }

  const startEdit = (comment: NoticeComment) => {
    setEditingId(comment.id)
    setEditDraft(comment.content)
    setReplyTo(null)
    setError(null)
  }

  const renderComment = (comment: NoticeComment) => {
    if (comment.deleted) {
      return (
        <div className="flex items-center justify-between gap-2">
          <p className="text-sm italic text-slate-400 dark:text-slate-500">{t('notices.posts.commentDeleted')}</p>
          <button type="button" onClick={() => startReply(comment)} disabled={busy} className={textButtonClass}>
            {t('notices.posts.commentReply')}
          </button>
        </div>
      )
    }

    const likeCount = comment.likeCount ?? 0
    return (
      <div className="space-y-1">
        <div className="flex items-center justify-between gap-2">
          <p className="text-xs text-slate-500 dark:text-slate-400">
            <span className="font-medium text-slate-700 dark:text-slate-200">{comment.authorNickname ?? '-'}</span>
            {/* When it was written, to the minute: threads are read as a conversation. */}
            {` · ${formatKstFullDateTime(comment.createdAt) || comment.createdAt}`}
            {comment.edited ? ` · ${t('notices.posts.commentEdited')}` : ''}
          </p>
          <div className="flex shrink-0 items-center gap-2">
            {comment.mine && (
              <button type="button" onClick={() => startEdit(comment)} disabled={busy} className={textButtonClass}>
                {t('notices.posts.commentEdit')}
              </button>
            )}
            {comment.canDelete && (
              <button
                type="button"
                onClick={() => handleDelete(comment)}
                disabled={busy}
                className="text-xs text-rose-600 hover:underline disabled:opacity-60 dark:text-rose-400"
              >
                {t('notices.posts.commentDelete')}
              </button>
            )}
          </div>
        </div>

        {editingId === comment.id ? (
          <form onSubmit={handleEdit} className="space-y-2">
            <textarea
              value={editDraft}
              onChange={(event) => setEditDraft(event.target.value)}
              maxLength={NOTICE_COMMENT_MAX_LENGTH}
              rows={3}
              className={fieldClass}
            />
            <div className="flex items-center justify-end gap-2">
              <span className="mr-auto text-xs text-slate-400 dark:text-slate-500">
                {editDraft.length}/{NOTICE_COMMENT_MAX_LENGTH}
              </span>
              <button type="button" onClick={() => setEditingId(null)} disabled={busy} className={plainButtonClass}>
                {t('notices.posts.commentEditCancel')}
              </button>
              <button type="submit" disabled={busy} className={primaryButtonClass}>
                {t('notices.posts.commentEditSave')}
              </button>
            </div>
          </form>
        ) : (
          <p className="whitespace-pre-wrap break-words text-sm text-slate-700 dark:text-slate-200">{comment.content}</p>
        )}

        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => handleLike(comment)}
            // A member's own comment cannot be liked; the backend refuses it as well.
            disabled={busy || comment.mine}
            aria-pressed={Boolean(comment.likedByMe)}
            aria-label={comment.likedByMe ? t('notices.posts.commentLiked') : t('notices.posts.commentLike')}
            title={comment.mine ? t('notices.posts.commentOwnLike') : undefined}
            className={`rounded-md border px-2 py-0.5 text-xs font-medium disabled:cursor-not-allowed disabled:opacity-60 ${
              comment.likedByMe
                ? 'border-rose-300 bg-rose-50 text-rose-600 dark:border-rose-800 dark:bg-rose-950/40 dark:text-rose-300'
                : 'border-slate-300 text-slate-600 hover:bg-slate-50 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-800'
            }`}
          >
            {comment.likedByMe ? '♥' : '♡'} {likeCount}
          </button>
          <button type="button" onClick={() => startReply(comment)} disabled={busy} className={textButtonClass}>
            {t('notices.posts.commentReply')}
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-3 rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900">
      <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
        {t('notices.posts.commentCount', { count: countNoticeComments(comments) })}
      </h2>

      {error && (
        <p className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700 dark:border-rose-800 dark:bg-rose-950/40 dark:text-rose-300">
          {error}
        </p>
      )}

      {/* Above the list: the newest comment is drawn first, right under this box. */}
      <form onSubmit={handleComment} className="space-y-2">
        <textarea
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          placeholder={t('notices.posts.commentPlaceholder')}
          maxLength={NOTICE_COMMENT_MAX_LENGTH}
          rows={3}
          className={fieldClass}
        />
        <div className="flex items-center justify-between gap-2">
          <span className="text-xs text-slate-400 dark:text-slate-500">
            {draft.length}/{NOTICE_COMMENT_MAX_LENGTH}
          </span>
          <button type="submit" disabled={busy} className={primaryButtonClass}>
            {busy ? t('notices.posts.commentSubmitting') : t('notices.posts.commentSubmit')}
          </button>
        </div>
      </form>

      {threads.length === 0 ? (
        <p className="text-xs text-slate-500 dark:text-slate-400">{t('notices.posts.commentEmpty')}</p>
      ) : (
        <ul className="divide-y divide-slate-100 dark:divide-slate-800">
          {threads.map((thread) => (
            <li key={thread.comment.id} className="space-y-2 py-3">
              {renderComment(thread.comment)}

              {(thread.replies.length > 0 || replyTo === thread.comment.id) && (
                <div className="ml-4 space-y-3 border-l-2 border-slate-100 pl-3 dark:border-slate-800">
                  {thread.replies.map((reply) => (
                    <div key={reply.id}>{renderComment(reply)}</div>
                  ))}

                  {replyTo === thread.comment.id && (
                    <form onSubmit={handleReply} className="space-y-2">
                      <textarea
                        value={replyDraft}
                        onChange={(event) => setReplyDraft(event.target.value)}
                        placeholder={t('notices.posts.commentReplyPlaceholder')}
                        maxLength={NOTICE_COMMENT_MAX_LENGTH}
                        rows={2}
                        autoFocus
                        className={fieldClass}
                      />
                      <div className="flex items-center justify-end gap-2">
                        <span className="mr-auto text-xs text-slate-400 dark:text-slate-500">
                          {replyDraft.length}/{NOTICE_COMMENT_MAX_LENGTH}
                        </span>
                        <button type="button" onClick={() => setReplyTo(null)} disabled={busy} className={plainButtonClass}>
                          {t('notices.posts.commentEditCancel')}
                        </button>
                        <button type="submit" disabled={busy} className={primaryButtonClass}>
                          {t('notices.posts.commentReplySubmit')}
                        </button>
                      </div>
                    </form>
                  )}
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
