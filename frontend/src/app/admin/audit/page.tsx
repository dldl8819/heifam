'use client'

import { FormEvent, useEffect, useState } from 'react'
import { Alert, AlertContent, AlertDescription, AlertIcon, AlertTitle } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { apiClient, isApiForbiddenError, isApiUnauthorizedError } from '@/lib/api'
import { t } from '@/lib/i18n'
import { useAdminAuth } from '@/lib/admin-auth'
import type { OperationAuditLogFilters, OperationAuditLogItem } from '@/types/api'

const AUDIT_INITIAL_LIMIT = 10
const AUDIT_LOAD_MORE_LIMIT = 10
const AUDIT_MAX_VISIBLE_LOGS = 20
const AUDIT_ACTION_FILTER_OPTIONS = [
  'ALL',
  'PLAYER_REGISTERED',
  'PLAYER_REGISTRATION_UPDATED',
  'PLAYER_REACTIVATED_BY_REGISTRATION',
  'PLAYER_TIER_UPDATED',
  'PLAYER_DEACTIVATED',
  'PLAYER_REACTIVATED',
  'MATCH_DELETED',
  'MATCH_RESULT_UPDATED',
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
    case 'PLAYER_REACTIVATED_BY_REGISTRATION':
      return t('audit.actions.playerReactivatedByRegistration')
    case 'PLAYER_TIER_UPDATED':
      return t('audit.actions.playerTierUpdated')
    case 'PLAYER_DEACTIVATED':
      return t('audit.actions.playerDeactivated')
    case 'PLAYER_REACTIVATED':
      return t('audit.actions.playerReactivated')
    case 'MATCH_DELETED':
      return t('audit.actions.matchDeleted')
    case 'MATCH_RESULT_UPDATED':
      return t('audit.actions.matchResultUpdated')
    default:
      return action
  }
}

export default function OperationAuditPage() {
  const { isSuperAdmin, isLoading } = useAdminAuth()
  const [logs, setLogs] = useState<OperationAuditLogItem[]>([])
  const [nextPage, setNextPage] = useState<number>(0)
  const [draftFilters, setDraftFilters] = useState<AuditFilterForm>(() => createEmptyAuditFilters())
  const [appliedFilters, setAppliedFilters] = useState<AuditFilterForm>(() => createEmptyAuditFilters())
  const [totalElements, setTotalElements] = useState<number>(0)
  const [hasMoreLogs, setHasMoreLogs] = useState<boolean>(false)
  const [loading, setLoading] = useState<boolean>(true)
  const [loadingMore, setLoadingMore] = useState<boolean>(false)
  const [error, setError] = useState<string | null>(null)

  const loadLogs = async (options: { append?: boolean } = {}) => {
    const append = options.append ?? false
    const currentLoadedCount = append ? logs.length : 0
    const remainingCount = Math.max(0, AUDIT_MAX_VISIBLE_LOGS - currentLoadedCount)
    const requestedSize = Math.min(
      append ? AUDIT_LOAD_MORE_LIMIT : AUDIT_INITIAL_LIMIT,
      remainingCount,
    )

    if (requestedSize <= 0) {
      setHasMoreLogs(false)
      return
    }

    if (append) {
      setLoadingMore(true)
    } else {
      setLoading(true)
    }
    setError(null)

    try {
      const requestedPage = append ? nextPage : 0
      const response = await apiClient.getOperationAuditLogPage({
        page: requestedPage,
        size: requestedSize,
        ...toApiFilters(appliedFilters),
      })
      const nextLogs = append ? [...logs, ...response.items] : response.items
      const nextLoadedCount = nextLogs.length

      setLogs(nextLogs)
      setTotalElements(response.totalElements)
      setNextPage(requestedPage + 1)
      setHasMoreLogs(
        nextLoadedCount < AUDIT_MAX_VISIBLE_LOGS
        && !response.last
        && response.items.length === requestedSize,
      )
    } catch (loadError) {
      if (isApiUnauthorizedError(loadError)) {
        setError(t('common.adminLoginRequired'))
      } else if (isApiForbiddenError(loadError)) {
        setError(t('common.permissionDenied'))
      } else {
        setError(t('audit.loadError'))
      }

      if (!append) {
        setLogs([])
        setTotalElements(0)
        setNextPage(0)
        setHasMoreLogs(false)
      }
    } finally {
      if (append) {
        setLoadingMore(false)
      } else {
        setLoading(false)
      }
    }
  }

  useEffect(() => {
    if (isLoading) {
      return
    }

    if (!isSuperAdmin) {
      setLogs([])
      setTotalElements(0)
      setNextPage(0)
      setHasMoreLogs(false)
      setLoadingMore(false)
      setLoading(false)
      setError(t('audit.superOnly'))
      return
    }

    let active = true
    const loadLogs = async () => {
      setLoading(true)
      setError(null)
      try {
        const response = await apiClient.getOperationAuditLogPage({
          page: 0,
          size: AUDIT_INITIAL_LIMIT,
          ...toApiFilters(appliedFilters),
        })
        if (active) {
          setLogs(response.items)
          setTotalElements(response.totalElements)
          setNextPage(1)
          setHasMoreLogs(
            response.items.length < AUDIT_MAX_VISIBLE_LOGS
            && !response.last
            && response.items.length === AUDIT_INITIAL_LIMIT,
          )
        }
      } catch (loadError) {
        if (!active) {
          return
        }
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
  }, [appliedFilters, isLoading, isSuperAdmin])

  const handleFilterSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setNextPage(0)
    setAppliedFilters({ ...draftFilters })
  }

  const handleFilterReset = () => {
    const emptyFilters = createEmptyAuditFilters()
    setDraftFilters(emptyFilters)
    setAppliedFilters({ ...emptyFilters })
    setNextPage(0)
  }

  const handleLoadMore = () => {
    if (loading || loadingMore || !hasMoreLogs || logs.length >= AUDIT_MAX_VISIBLE_LOGS) {
      return
    }

    void loadLogs({ append: true })
  }

  return (
    <section className="space-y-6">
      <header className="space-y-1 rounded-xl border border-slate-200 bg-white px-5 py-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
        <h2 className="text-2xl font-semibold tracking-tight text-slate-950 dark:text-slate-100">{t('audit.title')}</h2>
        <p className="text-sm text-slate-600 dark:text-slate-300">{t('audit.description')}</p>
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

      <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
        <div className="flex items-center justify-between gap-3">
          <div>
            <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('audit.recentTitle')}</h3>
            <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{t('audit.recentDescription')}</p>
          </div>
          <span className="rounded-md border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs font-semibold text-slate-700 dark:border-slate-700 dark:bg-slate-950/60 dark:text-slate-300">
            {t('audit.count', { count: totalElements })}
          </span>
        </div>

        <form onSubmit={handleFilterSubmit} className="mt-4 grid gap-3 border-t border-slate-100 pt-4 dark:border-slate-800 sm:grid-cols-2 lg:grid-cols-6">
          <label className="space-y-1 text-xs font-medium text-slate-600 dark:text-slate-300">
            <span>{t('audit.filters.fromDate')}</span>
            <input
              type="date"
              value={draftFilters.fromDate}
              onChange={(event) => setDraftFilters((current) => ({ ...current, fromDate: event.target.value }))}
              className="w-full rounded-md border border-slate-200 bg-white px-2 py-1.5 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-600 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-amber-400 dark:focus:ring-amber-400/30"
            />
          </label>
          <label className="space-y-1 text-xs font-medium text-slate-600 dark:text-slate-300">
            <span>{t('audit.filters.toDate')}</span>
            <input
              type="date"
              value={draftFilters.toDate}
              onChange={(event) => setDraftFilters((current) => ({ ...current, toDate: event.target.value }))}
              className="w-full rounded-md border border-slate-200 bg-white px-2 py-1.5 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-600 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-amber-400 dark:focus:ring-amber-400/30"
            />
          </label>
          <label className="space-y-1 text-xs font-medium text-slate-600 dark:text-slate-300">
            <span>{t('audit.filters.actor')}</span>
            <input
              type="search"
              value={draftFilters.actor}
              onChange={(event) => setDraftFilters((current) => ({ ...current, actor: event.target.value }))}
              placeholder={t('audit.filters.actorPlaceholder')}
              className="w-full rounded-md border border-slate-200 bg-white px-2 py-1.5 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-600 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-amber-400 dark:focus:ring-amber-400/30"
            />
          </label>
          <label className="space-y-1 text-xs font-medium text-slate-600 dark:text-slate-300">
            <span>{t('audit.filters.action')}</span>
            <select
              value={draftFilters.action}
              onChange={(event) =>
                setDraftFilters((current) => ({ ...current, action: event.target.value as AuditActionFilter }))
              }
              className="w-full rounded-md border border-slate-200 bg-white px-2 py-1.5 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-600 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-amber-400 dark:focus:ring-amber-400/30"
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
              className="w-full rounded-md border border-slate-200 bg-white px-2 py-1.5 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-600 dark:bg-slate-950 dark:text-slate-100 dark:placeholder:text-slate-500 dark:focus:border-amber-400 dark:focus:ring-amber-400/30"
            />
          </label>
          <label className="space-y-1 text-xs font-medium text-slate-600 dark:text-slate-300">
            <span>{t('audit.filters.target')}</span>
            <input
              type="search"
              value={draftFilters.target}
              onChange={(event) => setDraftFilters((current) => ({ ...current, target: event.target.value }))}
              placeholder={t('audit.filters.targetPlaceholder')}
              className="w-full rounded-md border border-slate-200 bg-white px-2 py-1.5 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 dark:border-slate-600 dark:bg-slate-950 dark:text-slate-100 dark:placeholder:text-slate-500 dark:focus:border-amber-400 dark:focus:ring-amber-400/30"
            />
          </label>
          <div className="flex gap-2 sm:col-span-2 lg:col-span-6">
            <button
              type="submit"
              className="rounded-md bg-slate-900 px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white"
            >
              {t('audit.filters.apply')}
            </button>
            <button
              type="button"
              onClick={handleFilterReset}
              className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 transition hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-300 dark:hover:bg-slate-800"
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
                      <td className="whitespace-nowrap px-3 py-2 text-slate-500 dark:text-slate-400">{index + 1}</td>
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
            {(hasMoreLogs || logs.length > 0) && (
              <div className="mt-4 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  {t('audit.pagination.status', {
                    shown: logs.length,
                    max: Math.min(totalElements, AUDIT_MAX_VISIBLE_LOGS),
                  })}
                </p>
                {hasMoreLogs && (
                  <button
                    type="button"
                    disabled={loading || loadingMore}
                    onClick={handleLoadMore}
                    className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 transition-colors hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-300 dark:hover:bg-slate-800"
                  >
                    {loadingMore ? t('audit.pagination.loadingMore') : t('audit.pagination.loadMore')}
                  </button>
                )}
              </div>
            )}
          </>
        )}
      </article>
    </section>
  )
}
