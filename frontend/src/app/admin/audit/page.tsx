'use client'

import { FormEvent, useEffect, useRef, useState } from 'react'
import { PageNav, PageSizeSelect } from '@/components/list-pagination'
import { Alert, AlertContent, AlertDescription, AlertIcon, AlertTitle } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { apiClient, isApiForbiddenError, isApiUnauthorizedError } from '@/lib/api'
import { t } from '@/lib/i18n'
import { useAdminAuth } from '@/lib/admin-auth'
import { DEFAULT_PAGE_SIZE, type PageSize } from '@/lib/list-pagination'
import type { OperationAuditLogFilters, OperationAuditLogItem } from '@/types/api'

const AUDIT_ACTION_FILTER_OPTIONS = [
  'ALL',
  'PLAYER_REGISTERED',
  'PLAYER_REGISTRATION_UPDATED',
  'PLAYER_NICKNAME_UPDATED',
  'PLAYER_PROFILE_UPDATED',
  'PLAYER_REACTIVATED_BY_REGISTRATION',
  'PLAYER_TIER_UPDATED',
  'PLAYER_DEACTIVATED',
  'PLAYER_REACTIVATED',
  'PLAYER_DORMANT',
  'PLAYER_WOKEN',
  'MATCH_DELETED',
  'MATCH_RESULT_UPDATED',
  'POINT_ADJUSTED',
  'TOURNAMENT_CREATED',
  'TOURNAMENT_CANCELLED',
  'BALANCE_SERIES_STARTED',
  'BALANCE_SERIES_CANCELLED',
  'PREDICTIONS_CLOSED',
  'PRIZE_EVENT_CREATED',
  'PRIZE_EVENT_CONFIRMED',
  'PRIZE_EVENT_CANCELLED',
  'PRIZE_DRAW_SAVED',
  'PRIZE_DRAW_DELETED',
  'CAPTAIN_DRAFT_DELETED',
  'ACCESS_PEOPLE_GROUPED',
  'ACCESS_PEOPLE_RENAMED',
  'ACCESS_PEOPLE_UNGROUPED',
] as const

type AuditActionFilter = (typeof AUDIT_ACTION_FILTER_OPTIONS)[number]
type AuditFilterForm = {
  fromDate: string
  toDate: string
  actor: string
  action: AuditActionFilter
  content: string
  target: string
}

function createEmptyAuditFilters(): AuditFilterForm {
  return {
    fromDate: '',
    toDate: '',
    actor: '',
    action: 'ALL',
    content: '',
    target: '',
  }
}

function toApiFilters(filters: AuditFilterForm): OperationAuditLogFilters {
  return {
    fromDate: filters.fromDate,
    toDate: filters.toDate,
    actor: filters.actor,
    action: filters.action === 'ALL' ? undefined : filters.action,
    content: filters.content,
    target: filters.target,
  }
}

function formatDateTime(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${year}.${month}.${day} ${hour}:${minute}`
}

function formatActor(log: OperationAuditLogItem): string {
  const nickname = log.actorNickname?.trim() ?? ''
  return nickname.length > 0 ? nickname : '-'
}

function formatTarget(log: OperationAuditLogItem): string {
  if (log.targetLabel) {
    return log.targetLabel
  }
  if (typeof log.targetId === 'number') {
    return `#${log.targetId}`
  }
  return '-'
}

function getActionLabel(action: string): string {
  switch (action) {
    case 'PLAYER_REGISTERED':
      return t('audit.actions.playerRegistered')
    case 'PLAYER_REGISTRATION_UPDATED':
      return t('audit.actions.playerRegistrationUpdated')
    case 'PLAYER_NICKNAME_UPDATED':
      return t('audit.actions.playerNicknameUpdated')
    case 'PLAYER_PROFILE_UPDATED':
      return t('audit.actions.playerProfileUpdated')
    case 'PLAYER_REACTIVATED_BY_REGISTRATION':
      return t('audit.actions.playerReactivatedByRegistration')
    case 'PLAYER_TIER_UPDATED':
      return t('audit.actions.playerTierUpdated')
    case 'PLAYER_DEACTIVATED':
      return t('audit.actions.playerDeactivated')
    case 'PLAYER_REACTIVATED':
      return t('audit.actions.playerReactivated')
    case 'ACCESS_PEOPLE_GROUPED':
      return t('audit.actions.accessPeopleGrouped')
    case 'ACCESS_PEOPLE_RENAMED':
      return t('audit.actions.accessPeopleRenamed')
    case 'ACCESS_PEOPLE_UNGROUPED':
      return t('audit.actions.accessPeopleUngrouped')
    case 'PLAYER_DORMANT':
      return t('audit.actions.playerDormant')
    case 'PLAYER_WOKEN':
      return t('audit.actions.playerWoken')
    case 'MATCH_DELETED':
      return t('audit.actions.matchDeleted')
    case 'MATCH_RESULT_UPDATED':
      return t('audit.actions.matchResultUpdated')
    case 'POINT_ADJUSTED':
      return t('audit.actions.pointAdjusted')
    case 'TOURNAMENT_CREATED':
      return t('audit.actions.tournamentCreated')
    case 'TOURNAMENT_CANCELLED':
      return t('audit.actions.tournamentCancelled')
    case 'BALANCE_SERIES_STARTED':
      return t('audit.actions.balanceSeriesStarted')
    case 'BALANCE_SERIES_CANCELLED':
      return t('audit.actions.balanceSeriesCancelled')
    case 'PREDICTIONS_CLOSED':
      return t('audit.actions.predictionsClosed')
    case 'PRIZE_EVENT_CREATED':
      return t('audit.actions.prizeEventCreated')
    case 'PRIZE_EVENT_CONFIRMED':
      return t('audit.actions.prizeEventConfirmed')
    case 'PRIZE_EVENT_CANCELLED':
      return t('audit.actions.prizeEventCancelled')
    case 'PRIZE_EVENT_DELETED':
      return t('audit.actions.prizeEventDeleted')
    case 'PRIZE_DRAW_SAVED':
      return t('audit.actions.prizeDrawSaved')
    case 'PRIZE_DRAW_DELETED':
      return t('audit.actions.prizeDrawDeleted')
    case 'CAPTAIN_DRAFT_DELETED':
      return t('audit.actions.captainDraftDeleted')
    default:
      return action
  }
}

export default function OperationAuditPage() {
  const { isAdmin, isSuperAdmin, isLoading } = useAdminAuth()
  const [logs, setLogs] = useState<OperationAuditLogItem[]>([])
  const [page, setPage] = useState<number>(0)
  const [pageSize, setPageSize] = useState<PageSize>(DEFAULT_PAGE_SIZE)
  const [totalPages, setTotalPages] = useState<number>(0)
  const [draftFilters, setDraftFilters] = useState<AuditFilterForm>(() => createEmptyAuditFilters())
  const [appliedFilters, setAppliedFilters] = useState<AuditFilterForm>(() => createEmptyAuditFilters())
  const [totalElements, setTotalElements] = useState<number>(0)
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)
  const listTopRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    if (isLoading) {
      return
    }

    // Admins get what admins and match result editors did; the backend decides the scope.
    if (!isAdmin) {
      setLogs([])
      setTotalElements(0)
      setTotalPages(0)
      setLoading(false)
      setError(t('audit.adminOnly'))
      return
    }

    let active = true
    const loadLogs = async () => {
      setLoading(true)
      setError(null)
      try {
        const response = await apiClient.getOperationAuditLogPage({
          page,
          size: pageSize,
          ...toApiFilters(appliedFilters),
        })
        if (!active) {
          return
        }
        if (response.items.length === 0 && page > 0 && response.totalPages > 0) {
          // Fewer pages than before (a narrower search, say): go to the last one there is.
          setPage(Math.min(page, response.totalPages) - 1)
          return
        }
        setLogs(response.items)
        setTotalElements(response.totalElements)
        setTotalPages(response.totalPages)
      } catch (loadError) {
        if (!active) {
          return
        }
        setLogs([])
        setTotalElements(0)
        setTotalPages(0)
        if (isApiUnauthorizedError(loadError)) {
          setError(t('common.adminLoginRequired'))
        } else if (isApiForbiddenError(loadError)) {
          setError(t('common.permissionDenied'))
        } else {
          setError(t('audit.loadError'))
        }
      } finally {
        if (active) {
          setLoading(false)
        }
      }
    }

    void loadLogs()
    return () => {
      active = false
    }
  }, [appliedFilters, isAdmin, isLoading, isSuperAdmin, page, pageSize])

  const pageCount = Math.max(totalPages, 1)

  const handleFilterSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setPage(0)
    setAppliedFilters({ ...draftFilters })
  }

  const handleFilterReset = () => {
    const emptyFilters = createEmptyAuditFilters()
    setDraftFilters(emptyFilters)
    setAppliedFilters({ ...emptyFilters })
    setPage(0)
  }

  const handlePageSizeChange = (size: PageSize) => {
    setPageSize(size)
    setPage(0)
  }

  const handleGoToPage = (target: number) => {
    const next = Math.min(Math.max(0, target), pageCount - 1)
    if (next === page) {
      return
    }
    setPage(next)
    // Pressed at the bottom of a long page: bring the top of the list back into view.
    const top = listTopRef.current
    if (top && top.getBoundingClientRect().top < 0) {
      top.scrollIntoView({ block: 'start' })
    }
  }

  return (
    <section className="space-y-6">
      <header className="space-y-1 rounded-xl border border-slate-200 bg-white px-5 py-4 shadow-xs dark:border-slate-700 dark:bg-slate-900">
        <h2 className="text-2xl font-semibold tracking-tight text-slate-950 dark:text-slate-100">{t('audit.title')}</h2>
        <p className="text-sm text-slate-600 dark:text-slate-300">
          {isSuperAdmin ? t('audit.description') : t('audit.adminDescription')}
        </p>
      </header>

      {error && (
        <Alert variant="destructive" appearance="light">
          <AlertIcon icon="destructive">!</AlertIcon>
          <AlertContent>
            <AlertTitle>{t('common.errorPrefix')}</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </AlertContent>
        </Alert>
      )}

      <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-xs dark:border-slate-700 dark:bg-slate-900">
        <div ref={listTopRef} className="flex scroll-mt-4 flex-wrap items-center justify-between gap-3">
          <div>
            <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('audit.recentTitle')}</h3>
            <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{t('audit.recentDescription')}</p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <span className="rounded-md border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs font-semibold text-slate-700 dark:border-slate-700 dark:bg-slate-950/60 dark:text-slate-300">
              {t('audit.count', { count: totalElements })}
            </span>
            <PageSizeSelect value={pageSize} onChange={handlePageSizeChange} disabled={loading} />
          </div>
        </div>

        <form onSubmit={handleFilterSubmit} className="mt-4 grid gap-3 border-t border-slate-100 pt-4 dark:border-slate-800 sm:grid-cols-2 lg:grid-cols-6">
          <label className="space-y-1 text-xs font-medium text-slate-600 dark:text-slate-300">
            <span>{t('audit.filters.fromDate')}</span>
            <input
              type="date"
              value={draftFilters.fromDate}
              onChange={(event) => setDraftFilters((current) => ({ ...current, fromDate: event.target.value }))}
              className="w-full rounded-md border border-slate-200 bg-white px-2 py-1.5 text-sm text-slate-800 outline-hidden transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-600 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-amber-400 dark:focus:ring-amber-400/30"
            />
          </label>
          <label className="space-y-1 text-xs font-medium text-slate-600 dark:text-slate-300">
            <span>{t('audit.filters.toDate')}</span>
            <input
              type="date"
              value={draftFilters.toDate}
              onChange={(event) => setDraftFilters((current) => ({ ...current, toDate: event.target.value }))}
              className="w-full rounded-md border border-slate-200 bg-white px-2 py-1.5 text-sm text-slate-800 outline-hidden transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-600 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-amber-400 dark:focus:ring-amber-400/30"
            />
          </label>
          <label className="space-y-1 text-xs font-medium text-slate-600 dark:text-slate-300">
            <span>{t('audit.filters.actor')}</span>
            <input
              type="search"
              value={draftFilters.actor}
              onChange={(event) => setDraftFilters((current) => ({ ...current, actor: event.target.value }))}
              placeholder={t('audit.filters.actorPlaceholder')}
              className="w-full rounded-md border border-slate-200 bg-white px-2 py-1.5 text-sm text-slate-800 outline-hidden transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-600 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-amber-400 dark:focus:ring-amber-400/30"
            />
          </label>
          <label className="space-y-1 text-xs font-medium text-slate-600 dark:text-slate-300">
            <span>{t('audit.filters.action')}</span>
            <select
              value={draftFilters.action}
              onChange={(event) =>
                setDraftFilters((current) => ({ ...current, action: event.target.value as AuditActionFilter }))
              }
              className="w-full rounded-md border border-slate-200 bg-white px-2 py-1.5 text-sm text-slate-800 outline-hidden transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-600 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-amber-400 dark:focus:ring-amber-400/30"
            >
              {AUDIT_ACTION_FILTER_OPTIONS.map((action) => (
                <option key={action} value={action}>
                  {action === 'ALL' ? t('audit.filters.actionAll') : getActionLabel(action)}
                </option>
              ))}
            </select>
          </label>
          <label className="space-y-1 text-xs font-medium text-slate-600 dark:text-slate-300">
            <span>{t('audit.filters.content')}</span>
            <input
              type="search"
              value={draftFilters.content}
              onChange={(event) => setDraftFilters((current) => ({ ...current, content: event.target.value }))}
              placeholder={t('audit.filters.contentPlaceholder')}
              className="w-full rounded-md border border-slate-200 bg-white px-2 py-1.5 text-sm text-slate-800 outline-hidden transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-600 dark:bg-slate-950 dark:text-slate-100 dark:placeholder:text-slate-500 dark:focus:border-amber-400 dark:focus:ring-amber-400/30"
            />
          </label>
          <label className="space-y-1 text-xs font-medium text-slate-600 dark:text-slate-300">
            <span>{t('audit.filters.target')}</span>
            <input
              type="search"
              value={draftFilters.target}
              onChange={(event) => setDraftFilters((current) => ({ ...current, target: event.target.value }))}
              placeholder={t('audit.filters.targetPlaceholder')}
              className="w-full rounded-md border border-slate-200 bg-white px-2 py-1.5 text-sm text-slate-800 outline-hidden transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-600 dark:bg-slate-950 dark:text-slate-100 dark:placeholder:text-slate-500 dark:focus:border-amber-400 dark:focus:ring-amber-400/30"
            />
          </label>
          <div className="flex gap-2 sm:col-span-2 lg:col-span-6">
            <button
              type="submit"
              className="rounded-md bg-slate-900 px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-slate-700 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-amber-500 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white"
            >
              {t('audit.filters.apply')}
            </button>
            <button
              type="button"
              onClick={handleFilterReset}
              className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 transition hover:bg-slate-50 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-amber-500 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-300 dark:hover:bg-slate-800"
            >
              {t('audit.filters.reset')}
            </button>
          </div>
        </form>

        {loading ? (
          <div className="mt-6 flex justify-center">
            <LoadingIndicator label={t('common.loading')} />
          </div>
        ) : logs.length === 0 ? (
          <p className="mt-4 rounded-lg border border-slate-200 bg-slate-50 px-3 py-3 text-sm text-slate-600 dark:border-slate-700 dark:bg-slate-950/60 dark:text-slate-300">
            {t('audit.empty')}
          </p>
        ) : (
          <>
            <div className="mt-4 overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-200 text-left text-sm dark:divide-slate-700">
                <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500 dark:bg-slate-950/60 dark:text-slate-400">
                  <tr>
                    <th className="px-3 py-2">{t('audit.table.seq')}</th>
                    <th className="px-3 py-2">{t('audit.table.createdAt')}</th>
                    <th className="px-3 py-2">{t('audit.table.action')}</th>
                    <th className="px-3 py-2">{t('audit.table.actor')}</th>
                    <th className="px-3 py-2">{t('audit.table.target')}</th>
                    <th className="px-3 py-2">{t('audit.table.details')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                  {logs.map((log, index) => (
                    <tr key={log.id} className="align-top">
                      <td className="whitespace-nowrap px-3 py-2 text-slate-500 dark:text-slate-400">
                        {page * pageSize + index + 1}
                      </td>
                      <td className="whitespace-nowrap px-3 py-2 text-slate-600 dark:text-slate-300">
                        {formatDateTime(log.createdAt)}
                      </td>
                      <td className="whitespace-nowrap px-3 py-2">
                        <span className="rounded-full bg-slate-100 px-2 py-1 text-xs font-semibold text-slate-700 dark:bg-slate-800 dark:text-slate-300">
                          {getActionLabel(log.action)}
                        </span>
                      </td>
                      <td className="px-3 py-2 text-slate-700 dark:text-slate-300">{formatActor(log)}</td>
                      <td className="px-3 py-2 text-slate-700 dark:text-slate-300">{formatTarget(log)}</td>
                      <td className="px-3 py-2 text-xs text-slate-500 dark:text-slate-400">{log.details ?? '-'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="mt-3 text-center text-xs text-slate-500 dark:text-slate-400">
              {t('common.pagination.status', { page: page + 1, totalPages: pageCount })}
            </p>
          </>
        )}
        {/* Kept outside the table so it stays put while the next page loads. */}
        {!error && (logs.length > 0 || page > 0) && (
          <PageNav
            label={t('audit.pagination.label')}
            page={page}
            pageCount={pageCount}
            onPageChange={handleGoToPage}
            disabled={loading}
          />
        )}
      </article>
    </section>
  )
}
