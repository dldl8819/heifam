'use client'

import Link from 'next/link'
import { FormEvent, useCallback, useEffect, useState } from 'react'
import { ApiRequestError, apiClient } from '@/lib/api'
import { useAdminAuth } from '@/lib/admin-auth'
import { BoardSearchBox } from '@/components/board-search-box'
import { VideoLinkField, YouTubeThumbnail } from '@/components/board-video'
import { Alert, AlertContent, AlertDescription, AlertIcon } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import {
  BOARD_CONTENT_MAX_LENGTH,
  BOARD_TITLE_MAX_LENGTH,
  boardPageCount,
  validateBoardPost,
  validateBoardVideoLink,
} from '@/lib/boards'
import { formatKstFullDateTime } from '@/lib/kst-time'
import { t } from '@/lib/i18n'
import type { BoardKind, BoardPostList } from '@/types/api'

const TEMP_GROUP_ID = 1

const fieldClass = 'w-full rounded-md border border-slate-300 px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-900'
const primaryButtonClass =
  'rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-medium text-white hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white'
const plainButtonClass =
  'rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800'

/**
 * A member board: its posts, newest first, and the form for a new one. On the anonymous board a
 * member sees only what they wrote themselves and admins see everything, without names. On the
 * video board a post is a YouTube video, and the posts are shown as pictures of their videos.
 */
export function BoardList({ board }: { board: BoardKind }) {
  const { isAdmin } = useAdminAuth()
  const anonymous = board === 'anonymous'
  const video = board === 'video'

  const [list, setList] = useState<BoardPostList | null>(null)
  const [page, setPage] = useState<number>(1)
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)

  const [composing, setComposing] = useState<boolean>(false)
  const [title, setTitle] = useState<string>('')
  const [content, setContent] = useState<string>('')
  const [videoLink, setVideoLink] = useState<string>('')
  const [saving, setSaving] = useState<boolean>(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  const load = useCallback(
    async (targetPage: number) => {
      setLoading(true)
      setError(null)
      try {
        setList(await apiClient.getBoardPosts(TEMP_GROUP_ID, board, targetPage))
      } catch {
        setError(t('boards.loadError'))
      } finally {
        setLoading(false)
      }
    },
    [board]
  )

  useEffect(() => {
    void load(page)
  }, [load, page])

  const handleSubmit = useCallback(
    async (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault()
      const problem = (video ? validateBoardVideoLink(videoLink) : null) ?? validateBoardPost(title, content, { contentOptional: video })
      if (problem) {
        setFormError(t(`boards.${problem.key}`, { max: problem.max }))
        return
      }

      setFormError(null)
      setSaving(true)
      try {
        await apiClient.createBoardPost(TEMP_GROUP_ID, board, {
          title: title.trim(),
          content: content.trim(),
          ...(video ? { videoUrl: videoLink.trim() } : {}),
        })
        setTitle('')
        setContent('')
        setVideoLink('')
        setComposing(false)
        setSuccessMessage(anonymous ? t('boards.anonymous.saveSuccess') : null)
        if (page === 1) {
          await load(1)
        } else {
          setPage(1)
        }
      } catch (caught) {
        // 429: more posts today than one person may write.
        setFormError(
          caught instanceof ApiRequestError && caught.status === 429 ? t('boards.limitReached') : t('boards.saveError')
        )
      } finally {
        setSaving(false)
      }
    },
    [anonymous, board, content, load, page, title, video, videoLink]
  )

  const pages = boardPageCount(list?.total ?? 0, list?.pageSize ?? 20)
  const emptyText = anonymous
    ? t(isAdmin ? 'boards.anonymous.emptyForAdmins' : 'boards.anonymous.empty')
    : t(video ? 'boards.video.empty' : 'boards.free.empty')

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <h1 className="text-lg font-semibold text-slate-900 dark:text-slate-100">{t(`boards.${board}.title`)}</h1>
          <p className="max-w-2xl text-sm text-slate-500 dark:text-slate-400">{t(`boards.${board}.description`)}</p>
        </div>
        <button
          type="button"
          onClick={() => {
            setComposing((previous) => !previous)
            setFormError(null)
          }}
          className={primaryButtonClass}
        >
          {t('boards.write')}
        </button>
      </div>

      {/* The search covers the notices, the free board and the video board; the anonymous board is never in it. */}
      {!anonymous && <BoardSearchBox />}

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
            <button type="submit" disabled={saving} className={primaryButtonClass}>
              {saving ? t('boards.saving') : t('boards.save')}
            </button>
            <button type="button" onClick={() => setComposing(false)} className={plainButtonClass}>
              {t('boards.cancel')}
            </button>
          </div>
        </form>
      )}

      {anonymous && (
        <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
          {t(isAdmin ? 'boards.anonymous.allPosts' : 'boards.anonymous.myPosts')}
        </h2>
      )}

      <div className="rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-900">
        {loading && (
          <div className="px-4 py-3">
            <LoadingIndicator label={t('common.loading')} />
          </div>
        )}
        {!loading && error && (
          <div className="px-4 py-6">
            <Alert variant="destructive" appearance="light">
              <AlertIcon icon="destructive">!</AlertIcon>
              <AlertContent>
                <AlertDescription>{error}</AlertDescription>
              </AlertContent>
            </Alert>
          </div>
        )}
        {!loading && !error && list && list.posts.length === 0 && (
          <p className="px-4 py-8 text-center text-sm text-slate-500 dark:text-slate-400">{emptyText}</p>
        )}
        {!loading && !error && list && list.posts.length > 0 && video && (
          <ul className="grid gap-4 p-4 sm:grid-cols-2 lg:grid-cols-3">
            {list.posts.map((post) => (
              <li key={post.id}>
                <Link
                  href={`/boards/video/${post.id}`}
                  className="block space-y-2 rounded-lg p-1.5 hover:bg-slate-50 dark:hover:bg-slate-800/60"
                >
                  {post.videoId && <YouTubeThumbnail videoId={post.videoId} />}
                  <span className="line-clamp-2 break-all text-sm font-medium text-slate-900 dark:text-slate-100">{post.title}</span>
                  <span className="block text-xs text-slate-500 dark:text-slate-400">
                    {post.authorNickname ?? '-'}
                    {` · ${formatKstFullDateTime(post.createdAt) || post.createdAt}`}
                  </span>
                  <span className="block text-xs text-slate-500 dark:text-slate-400">
                    {t('boards.views', { count: post.viewCount })}
                    {` · ${t('boards.comments', { count: post.commentCount })}`}
                    {` · ${t('boards.likes', { count: post.likeCount })}`}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
        {!loading && !error && list && list.posts.length > 0 && !video && (
          <ul className="divide-y divide-slate-100 dark:divide-slate-800">
            {list.posts.map((post) => (
              <li key={post.id}>
                <Link href={`/boards/${board}/${post.id}`} className="block px-4 py-3 hover:bg-slate-50 dark:hover:bg-slate-800/60">
                  <span className="flex flex-wrap items-center gap-2">
                    <span className="break-all text-sm font-medium text-slate-900 dark:text-slate-100">{post.title}</span>
                    {/* A member's anonymous list holds nothing but their own posts, so no mark there. */}
                    {post.mine && (!anonymous || isAdmin) && (
                      <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-medium text-slate-600 dark:bg-slate-800 dark:text-slate-300">
                        {t('boards.mine')}
                      </span>
                    )}
                  </span>
                  <span className="mt-1 block text-xs text-slate-500 dark:text-slate-400">
                    {anonymous ? t('boards.anonymous.author') : (post.authorNickname ?? '-')}
                    {` · ${formatKstFullDateTime(post.createdAt) || post.createdAt}`}
                    {` · ${t('boards.views', { count: post.viewCount })}`}
                    {` · ${t('boards.comments', { count: post.commentCount })}`}
                    {!anonymous ? ` · ${t('boards.likes', { count: post.likeCount })}` : ''}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>

      {pages > 1 && (
        <div className="flex items-center justify-center gap-3">
          <button
            type="button"
            onClick={() => setPage((previous) => Math.max(1, previous - 1))}
            disabled={loading || page <= 1}
            className={plainButtonClass}
          >
            {t('boards.previous')}
          </button>
          <span className="text-xs text-slate-500 dark:text-slate-400">{t('boards.pageInfo', { page, pages })}</span>
          <button
            type="button"
            onClick={() => setPage((previous) => Math.min(pages, previous + 1))}
            disabled={loading || page >= pages}
            className={plainButtonClass}
          >
            {t('boards.next')}
          </button>
        </div>
      )}
    </section>
  )
}
