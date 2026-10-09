'use client'

import { useRouter } from 'next/navigation'
import { FormEvent, useEffect, useState } from 'react'
import { BOARD_SEARCH_MAX_LENGTH, boardSearchUrl, validateBoardSearchQuery } from '@/lib/board-search'
import { t } from '@/lib/i18n'

/** The box a member types a word into to search the notices and the free board; it leads to the results page. */
export function BoardSearchBox({ initialQuery = '' }: { initialQuery?: string }) {
  const router = useRouter()
  const [query, setQuery] = useState<string>(initialQuery)
  const [problem, setProblem] = useState<string | null>(null)

  useEffect(() => {
    setQuery(initialQuery)
    setProblem(null)
  }, [initialQuery])

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const found = validateBoardSearchQuery(query)
    if (found) {
      setProblem(t(`boardSearch.${found.key}`, { min: found.min, max: found.max }))
      return
    }
    setProblem(null)
    router.push(boardSearchUrl(query))
  }

  return (
    <form onSubmit={handleSubmit} role="search" className="space-y-1">
      <div className="flex max-w-md gap-2">
        <input
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          maxLength={BOARD_SEARCH_MAX_LENGTH}
          placeholder={t('boardSearch.placeholder')}
          aria-label={t('boardSearch.placeholder')}
          className="min-w-0 flex-1 rounded-md border border-slate-300 px-3 py-1.5 text-sm dark:border-slate-600 dark:bg-slate-900"
        />
        <button
          type="submit"
          className="shrink-0 rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
        >
          {t('boardSearch.submit')}
        </button>
      </div>
      {problem && <p className="text-xs text-rose-600 dark:text-rose-400">{problem}</p>}
    </form>
  )
}
