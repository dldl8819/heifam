'use client'

import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { FormEvent, useCallback, useEffect, useState } from 'react'
import { ApiRequestError, apiClient } from '@/lib/api'
import { useAdminAuth } from '@/lib/admin-auth'
import { Alert, AlertContent, AlertDescription, AlertIcon } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { VideoLinkField, YouTubePlayer } from '@/components/board-video'
import {
  BOARD_COMMENT_MAX_LENGTH,
  BOARD_CONTENT_MAX_LENGTH,
  BOARD_TITLE_MAX_LENGTH,
  validateBoardComment,
  validateBoardPost,
  validateBoardVideoLink,
} from '@/lib/boards'
import { youTubeWatchUrl } from '@/lib/youtube'
import { formatKstFullDateTime } from '@/lib/kst-time'
import { t } from '@/lib/i18n'
import type { BoardComment, BoardKind, BoardPostDetail } from '@/types/api'

const TEMP_GROUP_ID = 1

const fieldClass = 'w-full rounded-md border border-slate-300 px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-900'
const primaryButtonClass =
  'rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-medium text-white hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white'
const plainButtonClass =
  'rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800'
const errorClass =
  'rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700 dark:border-rose-800 dark:bg-rose-950/40 dark:text-rose-300'

/**
 * Who a comment is shown as from. On the anonymous board the post's writer has no name, and
 * whoever else comments there is an admin, named if they have a nickname.
 */
function commentAuthor(comment: BoardComment, anonymous: boolean): string {
  if (comment.authorNickname) {
    return comment.authorNickname
  }
  if (anonymous) {
    return comment.byPostAuthor ? t('boards.anonymous.author') : t('boards.anonymous.admin')
  }
  return '-'
}

/** One post of a member board with its comments. The writer changes it; the writer and admins remove it. */
export function BoardPost({ board, postId }: { board: BoardKind; postId: number }) {
  const router = useRouter()
  const { isAdmin } = useAdminAuth()
  const anonymous = board === 'anonymous'
  const video = board === 'video'

  const [post, setPost] = useState<BoardPostDetail | null>(null)
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)

  const [editing, setEditing] = useState<boolean>(false)
  const [title, setTitle] = useState<string>('')
  const [content, setContent] = useState<string>('')
  const [videoLink, setVideoLink] = useState<string>('')
  const [formError, setFormError] = useState<string | null>(null)

  const [commentDraft, setCommentDraft] = useState<string>('')
  const [busy, setBusy] = useState<boolean>(false)
  const [actionError, setActionError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    if (!Number.isFinite(postId)) {
      setError(t('boards.notFound'))
      setLoading(false)
      return
    }
    setLoading(true)
    setError(null)
    apiClient
      .getBoardPost(TEMP_GROUP_ID, board, postId)
      .then((response) => {
        if (!cancelled) {
          setPost(response)
        }
      })
      .catch(() => {
        if (!cancelled) {
          setError(t('boards.notFound'))
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false)
        }
      })
    return () => {
      cancelled = true
    }
  }, [board, postId])

  /** Runs a change that answers with the post as it now is. */
  const run = useCallback(async (action: () => Promise<BoardPostDetail>, onDone?: () => void) => {
    setBusy(true)
    setActionError(null)
    try {
      setPost(await action())
      onDone?.()
    } catch (caught) {
      const status = caught instanceof ApiRequestError ? caught.status : 0
      setActionError(status === 403 ? t('boards.notAllowed') : status === 404 ? t('boards.notFound') : t('boards.saveError'))
    } finally {
      setBusy(false)
    }
  }, [])

  const handleEdit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const problem = (video ? validateBoardVideoLink(videoLink) : null) ?? validateBoardPost(title, content, { contentOptional: video })
    if (problem) {
      setFormError(t(`boards.${problem.key}`, { max: problem.max }))
      return
    }
    setFormError(null)
    void run(
      () =>
        apiClient.updateBoardPost(TEMP_GROUP_ID, board, postId, {
          title: title.trim(),
          content: content.trim(),
          ...(video ? { videoUrl: videoLink.trim() } : {}),
        }),
      () => setEditing(false)
    )
  }

  const handleDelete = async () => {
    if (!window.confirm(t('boards.deleteConfirm'))) {
      return
    }
    setBusy(true)
    setActionError(null)
    try {
      await apiClient.deleteBoardPost(TEMP_GROUP_ID, board, postId)
      router.push(`/boards/${board}`)
    } catch {
      setActionError(t('boards.saveError'))
      setBusy(false)
    }
  }

  const handleComment = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const problem = validateBoardComment(commentDraft)
    if (problem) {
      setActionError(t(`boards.${problem.key}`, { max: problem.max }))
      return
    }
    void run(
      () => apiClient.addBoardComment(TEMP_GROUP_ID, board, postId, commentDraft.trim()),
      () => setCommentDraft('')
    )
  }

  const handleDeleteComment = (comment: BoardComment) => {
    if (window.confirm(t('boards.commentDeleteConfirm'))) {
      void run(() => apiClient.deleteBoardComment(TEMP_GROUP_ID, board, postId, comment.id))
    }
  }

  return (
    <section className="space-y-6">
      <Link href={`/boards/${board}`} className="text-sm text-slate-500 hover:underline dark:text-slate-400">
        {t('boards.backToList')}
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

      {!loading && !error && post && !editing && (
        <>
          <article className="space-y-4 rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900">
            <div className="space-y-1">
              <div className="flex flex-wrap items-center gap-2">
                <h1 className="break-all text-lg font-semibold text-slate-900 dark:text-slate-100">{post.title}</h1>
                {post.mine && (
                  <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-medium text-slate-600 dark:bg-slate-800 dark:text-slate-300">
                    {t('boards.mine')}
                  </span>
                )}
              </div>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                {anonymous ? t('boards.anonymous.author') : (post.authorNickname ?? '-')}
                {` · ${formatKstFullDateTime(post.createdAt) || post.createdAt}`}
                {post.edited ? ` · ${t('boards.edited')}` : ''}
                {` · ${t('boards.views', { count: post.viewCount })}`}
              </p>
            </div>

            {/* On the video board the video is the post: it comes first, any words after it. */}
            {video && post.videoId && <YouTubePlayer videoId={post.videoId} title={post.title} />}
            {post.content.trim().length > 0 && (
              <p className="whitespace-pre-wrap break-words text-sm text-slate-700 dark:text-slate-200">{post.content}</p>
            )}

            <div className="flex flex-wrap items-center gap-2 border-t border-slate-100 pt-4 dark:border-slate-800">
              {!anonymous && (
                <button
                  type="button"
                  onClick={() => void run(() => apiClient.setBoardPostLike(TEMP_GROUP_ID, board, postId, !post.likedByMe))}
                  disabled={busy}
                  aria-pressed={post.likedByMe}
                  aria-label={post.likedByMe ? t('boards.liked') : t('boards.like')}
                  className={`rounded-lg border px-3 py-1.5 text-xs font-medium disabled:cursor-not-allowed disabled:opacity-60 ${
                    post.likedByMe
                      ? 'border-rose-300 bg-rose-50 text-rose-600 dark:border-rose-800 dark:bg-rose-950/40 dark:text-rose-300'
                      : 'border-slate-300 text-slate-700 hover:bg-slate-50 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800'
                  }`}
                >
                  {post.likedByMe ? '♥' : '♡'} {t('boards.likes', { count: post.likeCount })}
                </button>
              )}
              {post.canEdit && (
                <button
                  type="button"
                  onClick={() => {
                    setTitle(post.title)
                    setContent(post.content)
                    setVideoLink(post.videoId ? youTubeWatchUrl(post.videoId) : '')
                    setFormError(null)
                    setEditing(true)
                  }}
                  disabled={busy}
                  className={plainButtonClass}
                >
                  {t('boards.edit')}
                </button>
              )}
              {post.canDelete && (
                <button
                  type="button"
                  onClick={() => void handleDelete()}
                  disabled={busy}
                  className="rounded-lg border border-rose-300 px-3 py-1.5 text-xs font-medium text-rose-600 hover:bg-rose-50 disabled:opacity-60 dark:border-rose-800 dark:text-rose-400 dark:hover:bg-rose-950/40"
                >
                  {t('boards.delete')}
                </button>
              )}
            </div>
          </article>

          <div className="space-y-3 rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900">
            <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
              {t('boards.comments', { count: post.comments.length })}
            </h2>

            {actionError && <p className={errorClass}>{actionError}</p>}

            <form onSubmit={handleComment} className="space-y-2">
              <textarea
                value={commentDraft}
                onChange={(event) => setCommentDraft(event.target.value)}
                // On the anonymous board an admin's comment is the answer the writer is waiting for.
                placeholder={anonymous && isAdmin && !post.mine ? t('boards.answerPlaceholder') : t('boards.commentPlaceholder')}
                maxLength={BOARD_COMMENT_MAX_LENGTH}
                rows={3}
                className={fieldClass}
              />
              <div className="flex items-center justify-between gap-2">
                <span className="text-xs text-slate-400 dark:text-slate-500">
                  {commentDraft.length}/{BOARD_COMMENT_MAX_LENGTH}
                </span>
                <button type="submit" disabled={busy} className={primaryButtonClass}>
                  {t('boards.commentSubmit')}
                </button>
              </div>
            </form>

            {post.comments.length === 0 ? (
              <p className="text-xs text-slate-500 dark:text-slate-400">{t('boards.commentEmpty')}</p>
            ) : (
              <ul className="divide-y divide-slate-100 dark:divide-slate-800">
                {post.comments.map((comment) => (
                  <li key={comment.id} className="space-y-1 py-3">
                    <div className="flex items-center justify-between gap-2">
                      <p className="text-xs text-slate-500 dark:text-slate-400">
                        <span className="font-medium text-slate-700 dark:text-slate-200">{commentAuthor(comment, anonymous)}</span>
                        {comment.byPostAuthor && (
                          <span className="ml-1.5 rounded-full bg-slate-100 px-1.5 py-0.5 text-[10px] font-medium text-slate-600 dark:bg-slate-800 dark:text-slate-300">
                            {t('boards.postAuthorTag')}
                          </span>
                        )}
                        {` · ${formatKstFullDateTime(comment.createdAt) || comment.createdAt}`}
                      </p>
                      {comment.canDelete && (
                        <button
                          type="button"
                          onClick={() => handleDeleteComment(comment)}
                          disabled={busy}
                          className="shrink-0 text-xs text-rose-600 hover:underline disabled:opacity-60 dark:text-rose-400"
                        >
                          {t('boards.commentDelete')}
                        </button>
                      )}
                    </div>
                    <p className="whitespace-pre-wrap break-words text-sm text-slate-700 dark:text-slate-200">{comment.content}</p>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </>
      )}

      {!loading && !error && post && editing && (
        <form
          onSubmit={handleEdit}
          className="space-y-3 rounded-xl border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-800/60"
        >
          {formError && <p className={errorClass}>{formError}</p>}
          {actionError && <p className={errorClass}>{actionError}</p>}
          {video && <VideoLinkField value={videoLink} onChange={setVideoLink} />}
          <input
            type="text"
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            maxLength={BOARD_TITLE_MAX_LENGTH}
            placeholder={t('boards.titlePlaceholder')}
            className={fieldClass}
          />
          <textarea
            value={content}
            onChange={(event) => setContent(event.target.value)}
            placeholder={video ? t('boards.video.contentPlaceholder') : t('boards.contentPlaceholder')}
            rows={8}
            className={fieldClass}
          />
          <p className="text-xs text-slate-500 dark:text-slate-400">
            {t('boards.contentLimitHint', { max: BOARD_CONTENT_MAX_LENGTH })}
          </p>
          <div className="flex gap-2">
            <button type="submit" disabled={busy} className={primaryButtonClass}>
              {t('boards.saveEdit')}
            </button>
            <button
              type="button"
              onClick={() => {
                setEditing(false)
                setFormError(null)
                setActionError(null)
              }}
              className={plainButtonClass}
            >
              {t('boards.cancel')}
            </button>
          </div>
        </form>
      )}
    </section>
  )
}
