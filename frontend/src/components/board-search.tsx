'use client'

import Link from 'next/link'
import { useRouter, useSearchParams } from 'next/navigation'
import { useEffect, useState } from 'react'
import { apiClient } from '@/lib/api'
import { BoardSearchBox } from '@/components/board-search-box'
import { Alert, AlertContent, AlertDescription, AlertIcon } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { boardSearchPage, boardSearchResultHref, boardSearchUrl, validateBoardSearchQuery } from '@/lib/board-search'
import { boardPageCount } from '@/lib/boards'
import { formatKstFullDateTime } from '@/lib/kst-time'
import { t } from '@/lib/i18n'
import type { BoardSearchResult } from '@/types/api'

const TEMP_GROUP_ID = 1

const plainButtonClass =
  'rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800'
const badgeClass = 'rounded-full px-2 py-0.5 text-[11px] font-medium'

/**
 * What a word turns up in the notices and on the free board. The word and the page are in the
 * address, so a result can be opened and come back from, and a search can be passed on as a link.
 */
export function BoardSearch() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const query = (searchParams.get('q') ?? '').trim()
  const page = boardSearchPage(searchParams.get('page'))
  const searchable = validateBoardSearchQuery(query) === null

  const [result, setResult] = useState<BoardSearchResult | null>(null)
  const [loading, setLoading] = useState<boolean>(searchable)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!searchable) {
      setResult(null)
      setError(null)
      setLoading(false)
      return
    }
    let cancelled = false
    setLoading(true)
    setError(null)
    apiClient
      .searchBoards(TEMP_GROUP_ID, query, page)
      .then((response) => {
        if (!cancelled) {
          setResult(response)
        }
      })
      .catch(() => {
        if (!cancelled) {
          setResult(null)
          setError(t('boardSearch.loadError'))
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
  }, [page, query, searchable])

  const pages = boardPageCount(result?.total ?? 0, result?.pageSize ?? 20)

  return (
    <section className="space-y-4">
      <div className="space-y-1">
        <h1 className="text-lg font-semibold text-slate-900 dark:text-slate-100">{t('boardSearch.title')}</h1>
        <p className="max-w-2xl text-sm text-slate-500 dark:text-slate-400">{t('boardSearch.description')}</p>
      </div>

      <BoardSearchBox initialQuery={query} />

      {searchable && !loading && !error && result && (
        <p className="text-sm text-slate-600 dark:text-slate-300">
          {t('boardSearch.summary', { query: result.query, total: result.total })}
        </p>
      )}

      <div className="rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-900">
        {!searchable && (
          <p className="px-4 py-8 text-center text-sm text-slate-500 dark:text-slate-400">{t('boardSearch.prompt')}</p>
        )}
        {searchable && loading && (
          <div className="px-4 py-3">
            <LoadingIndicator label={t('common.loading')} />
          </div>
        )}
        {searchable && !loading && error && (
          <div className="px-4 py-6">
            <Alert variant="destructive" appearance="light">
              <AlertIcon icon="destructive">!</AlertIcon>
              <AlertContent>
                <AlertDescription>{error}</AlertDescription>
              </AlertContent>
            </Alert>
          </div>
        )}
        {searchable && !loading && !error && result && result.results.length === 0 && (
          <p className="px-4 py-8 text-center text-sm text-slate-500 dark:text-slate-400">{t('boardSearch.empty')}</p>
        )}
        {searchable && !loading && !error && result && result.results.length > 0 && (
          <ul className="divide-y divide-slate-100 dark:divide-slate-800">
            {result.results.map((item) => (
              <li key={`${item.kind}-${item.id}`}>
                <Link href={boardSearchResultHref(item)} className="block px-4 py-3 hover:bg-slate-50 dark:hover:bg-slate-800/60">
                  <span className="flex flex-wrap items-center gap-2">
                    <span
                      className={`${badgeClass} ${
                        item.kind === 'NOTICE'
                          ? 'bg-sky-100 text-sky-800 dark:bg-sky-900/40 dark:text-sky-300'
                          : 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300'
                      }`}
                    >
                      {t(
                        item.kind === 'NOTICE'
                          ? 'boardSearch.kindNotice'
                          : item.kind === 'VIDEO'
                            ? 'boardSearch.kindVideo'
                            : 'boardSearch.kindFree'
                      )}
                    </span>
                    {item.adminOnly && (
                      <span className={`${badgeClass} bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-300`}>
                        {t('notices.posts.adminOnlyBadge')}
                      </span>
                    )}
                    <span className="break-all text-sm font-medium text-slate-900 dark:text-slate-100">{item.title}</span>
                  </span>
                  {item.snippet && (
                    <span className="mt-1 line-clamp-2 break-all text-sm text-slate-600 dark:text-slate-300">{item.snippet}</span>
                  )}
                  <span className="mt-1 block text-xs text-slate-500 dark:text-slate-400">
                    {item.authorNickname ?? '-'}
                    {` · ${formatKstFullDateTime(item.createdAt) || item.createdAt}`}
                    {` · ${t('boards.views', { count: item.viewCount })}`}
                    {` · ${t('boards.comments', { count: item.commentCount })}`}
                    {` · ${t('boards.likes', { count: item.likeCount })}`}
                    {item.voteCount !== null && item.voteCount !== undefined
                      ? ` · ${t('boardSearch.votes', { count: item.voteCount })}`
                      : ''}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>

      {searchable && pages > 1 && (
        <div className="flex items-center justify-center gap-3">
          <button
            type="button"
            onClick={() => router.push(boardSearchUrl(query, page - 1))}
            disabled={loading || page <= 1}
            className={plainButtonClass}
          >
            {t('boards.previous')}
          </button>
          <span className="text-xs text-slate-500 dark:text-slate-400">{t('boards.pageInfo', { page, pages })}</span>
          <button
            type="button"
            onClick={() => router.push(boardSearchUrl(query, page + 1))}
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
