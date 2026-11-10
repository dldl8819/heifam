'use client'

import { t } from '@/lib/i18n'
import { PAGE_SIZE_OPTIONS, type PageSize, toPageSize, visiblePageNumbers } from '@/lib/list-pagination'

const baseClass =
  'rounded-md border py-1.5 text-xs font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-60'
const idleClass =
  'border-slate-300 text-slate-700 hover:border-slate-900 hover:bg-slate-900 hover:text-white dark:border-slate-600 dark:text-slate-200 dark:hover:border-slate-300 dark:hover:bg-slate-100 dark:hover:text-slate-900'
// Sized so the widest row (‹ 1 … 200 201 202 … 426 ›) fits a 360px phone on one line, about
// 296px inside the page gutter and the card; 이전/다음 are spelled out from the sm width up.
const stepButtonClass = `${baseClass} ${idleClass} min-w-7 px-2`
const pageButtonClass = `${baseClass} ${idleClass} min-w-7 px-1.5`
const currentPageButtonClass = `${baseClass} min-w-7 px-1.5 border-slate-900 bg-slate-900 text-white dark:border-slate-100 dark:bg-slate-100 dark:text-slate-900`

/** "10개씩 보기" … "100개씩 보기". */
export function PageSizeSelect({
  value,
  onChange,
  disabled = false,
}: {
  value: PageSize
  onChange: (size: PageSize) => void
  disabled?: boolean
}) {
  return (
    <select
      value={value}
      aria-label={t('common.pagination.pageSize')}
      onChange={(event) => onChange(toPageSize(event.target.value))}
      disabled={disabled}
      className="rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-xs font-medium text-slate-700 outline-hidden transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-200 dark:focus:border-slate-500 dark:focus:ring-slate-700"
    >
      {PAGE_SIZE_OPTIONS.map((size) => (
        <option key={`page-size-${size}`} value={size}>
          {t('common.pagination.pageSizeOption', { count: size })}
        </option>
      ))}
    </select>
  )
}

/** 이전 · 1 … 4 5 6 … 10 · 다음, for a list of more than one page (0-based `page`). */
export function PageNav({
  label,
  page,
  pageCount,
  onPageChange,
  disabled = false,
}: {
  label: string
  page: number
  pageCount: number
  onPageChange: (page: number) => void
  disabled?: boolean
}) {
  if (pageCount <= 1) {
    return null
  }

  return (
    <nav aria-label={label} className="mt-3 flex flex-wrap items-center justify-center gap-1">
      <button
        type="button"
        onClick={() => onPageChange(page - 1)}
        disabled={disabled || page <= 0}
        aria-label={t('common.pagination.previous')}
        className={stepButtonClass}
      >
        <span aria-hidden="true">‹</span>
        <span aria-hidden="true" className="hidden sm:inline"> {t('common.pagination.previous')}</span>
      </button>
      {visiblePageNumbers(page, pageCount).map((number, index) =>
        number === null ? (
          <span key={`page-gap-${index}`} aria-hidden="true" className="px-0.5 text-xs text-slate-400 dark:text-slate-500">
            …
          </span>
        ) : (
          <button
            key={`page-${number}`}
            type="button"
            onClick={() => onPageChange(number)}
            disabled={disabled}
            aria-current={number === page ? 'page' : undefined}
            aria-label={t('common.pagination.page', { page: number + 1 })}
            className={number === page ? currentPageButtonClass : pageButtonClass}
          >
            {number + 1}
          </button>
        ),
      )}
      <button
        type="button"
        onClick={() => onPageChange(page + 1)}
        disabled={disabled || page + 1 >= pageCount}
        aria-label={t('common.pagination.next')}
        className={stepButtonClass}
      >
        <span aria-hidden="true" className="hidden sm:inline">{t('common.pagination.next')} </span>
        <span aria-hidden="true">›</span>
      </button>
    </nav>
  )
}
