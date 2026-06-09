'use client'

import { FormEvent, useEffect, useState } from 'react'
import { Alert, AlertContent, AlertDescription, AlertIcon, AlertTitle } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { apiClient, isApiForbiddenError, isApiUnauthorizedError } from '@/lib/api'
import { t } from '@/lib/i18n'
import { useAdminAuth } from '@/lib/admin-auth'
import type { OperationAuditLogFilters, OperationAuditLogItem } from '@/types/api'

const AUDIT_PAGE_SIZE = 20
const AUDIT_ACTION_FILTER_OPTIONS = [
  'ALL',
  'PLAYER_REGISTERED',
  'PLAYER_REGISTRATION_UPDATED',
  'PLAYER_REACTIVATED_BY_REGISTRATION',
  'PLAYER_TIER_UPDATED',
  'MATCH_DELETED',
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
  if (log.actorNickname && log.actorEmail) {
    return `${log.actorNickname} (${log.actorEmail})`
  }
  return log.actorNickname ?? log.actorEmail ?? '-'
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
    case 'MATCH_DELETED':
      return t('audit.actions.matchDeleted')
    default:
      return action
  }
}

export default function OperationAuditPage() {
  const { isSuperAdmin, isLoading } = useAdminAuth()
  const [logs, setLogs] = useState<OperationAuditLogItem[]>([])
  const [page, setPage] = useState<number>(0)
  const [draftFilters, setDraftFilters] = useState<AuditFilterForm>(() => createEmptyAuditFilters())
  const [appliedFilters, setAppliedFilters] = useState<AuditFilterForm>(() => createEmptyAuditFilters())
  const [totalElements, setTotalElements] = useState<number>(0)
  const [totalPages, setTotalPages] = useState<number>(0)
  const [isFirstPage, setIsFirstPage] = useState<boolean>(true)
  const [isLastPage, setIsLastPage] = useState<boolean>(true)
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (isLoading) {
      return
    }

    if (!isSuperAdmin) {
      setLogs([])
      setTotalElements(0)
      setTotalPages(0)
      setIsFirstPage(true)
      setIsLastPage(true)
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
          page,
          size: AUDIT_PAGE_SIZE,
          ...toApiFilters(appliedFilters),
        })
        if (active) {
          setLogs(response.items)
          setTotalElements(response.totalElements)
          setTotalPages(response.totalPages)
          setIsFirstPage(response.first)
          setIsLastPage(response.last)
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
  }, [appliedFilters, isLoading, isSuperAdmin, page])

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

  return (
    <section className="space-y-6">
      <header className="space-y-1 rounded-xl border border-slate-200 bg-white px-5 py-4 shadow-sm">
        <h2 className="text-2xl font-semibold tracking-tight text-slate-950">{t('audit.title')}</h2>
        <p className="text-sm text-slate-600">{t('audit.description')}</p>
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

      <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
        <div className="flex items-center justify-between gap-3">
          <div>
            <h3 className="text-sm font-semibold text-slate-900">{t('audit.recentTitle')}</h3>
            <p className="mt-1 text-xs text-slate-500">{t('audit.recentDescription')}</p>
          </div>
          <span className="rounded-md border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs font-semibold text-slate-700">
            {t('audit.count', { count: totalElements })}
          </span>
        </div>

        <form onSubmit={handleFilterSubmit} className="mt-4 grid gap-3 border-t border-slate-100 pt-4 sm:grid-cols-2 lg:grid-cols-6">
          <label className="space-y-1 text-xs font-medium text-slate-600">
            <span>{t('audit.filters.fromDate')}</span>
            <input
              type="date"
              value={draftFilters.fromDate}
              onChange={(event) => setDraftFilters((current) => ({ ...current, fromDate: event.target.value }))}
              className="w-full rounded-md border border-slate-200 px-2 py-1.5 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
            />
          </label>
          <label className="space-y-1 text-xs font-medium text-slate-600">
            <span>{t('audit.filters.toDate')}</span>
            <input
              type="date"
              value={draftFilters.toDate}
              onChange={(event) => setDraftFilters((current) => ({ ...current, toDate: event.target.value }))}
              className="w-full rounded-md border border-slate-200 px-2 py-1.5 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
            />
          </label>
          <label className="space-y-1 text-xs font-medium text-slate-600">
            <span>{t('audit.filters.actor')}</span>
            <input
              type="search"
              value={draftFilters.actor}
              onChange={(event) => setDraftFilters((current) => ({ ...current, actor: event.target.value }))}
              placeholder={t('audit.filters.actorPlaceholder')}
              className="w-full rounded-md border border-slate-200 px-2 py-1.5 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
            />
          </label>
          <label className="space-y-1 text-xs font-medium text-slate-600">
            <span>{t('audit.filters.action')}</span>
            <select
              value={draftFilters.action}
              onChange={(event) =>
                setDraftFilters((current) => ({ ...current, action: event.target.value as AuditActionFilter }))
              }
              className="w-full rounded-md border border-slate-200 px-2 py-1.5 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
            >
              {AUDIT_ACTION_FILTER_OPTIONS.map((action) => (
                <option key={action} value={action}>
                  {action === 'ALL' ? t('audit.filters.actionAll') : getActionLabel(action)}
                </option>
              ))}
            </select>
          </label>
          <label className="space-y-1 text-xs font-medium text-slate-600">
            <span>{t('audit.filters.content')}</span>
            <input
              type="search"
              value={draftFilters.content}
              onChange={(event) => setDraftFilters((current) => ({ ...current, content: event.target.value }))}
              placeholder={t('audit.filters.contentPlaceholder')}
              className="w-full rounded-md border border-slate-200 px-2 py-1.5 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
            />
          </label>
          <label className="space-y-1 text-xs font-medium text-slate-600">
            <span>{t('audit.filters.target')}</span>
            <input
              type="search"
              value={draftFilters.target}
              onChange={(event) => setDraftFilters((current) => ({ ...current, target: event.target.value }))}
              placeholder={t('audit.filters.targetPlaceholder')}
              className="w-full rounded-md border border-slate-200 px-2 py-1.5 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
            />
          </label>
          <div className="flex gap-2 sm:col-span-2 lg:col-span-6">
            <button
              type="submit"
              className="rounded-md bg-slate-900 px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-slate-700"
            >
              {t('audit.filters.apply')}
            </button>
            <button
              type="button"
              onClick={handleFilterReset}
              className="rounded-md border border-slate-300 px-3 py-1.5 text-xs font-semibold text-slate-700 transition hover:bg-slate-50"
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
          <p className="mt-4 rounded-lg border border-slate-200 bg-slate-50 px-3 py-3 text-sm text-slate-600">
            {t('audit.empty')}
          </p>
        ) : (
          <>
            <div className="mt-4 overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-200 text-left text-sm">
                <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="px-3 py-2">{t('audit.table.createdAt')}</th>
                    <th className="px-3 py-2">{t('audit.table.action')}</th>
                    <th className="px-3 py-2">{t('audit.table.actor')}</th>
                    <th className="px-3 py-2">{t('audit.table.target')}</th>
                    <th className="px-3 py-2">{t('audit.table.details')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {logs.map((log) => (
                    <tr key={log.id} className="align-top">
                      <td className="whitespace-nowrap px-3 py-2 text-slate-600">
                        {formatDateTime(log.createdAt)}
                      </td>
                      <td className="whitespace-nowrap px-3 py-2">
                        <span className="rounded-full bg-slate-100 px-2 py-1 text-xs font-semibold text-slate-700">
                          {getActionLabel(log.action)}
                        </span>
                      </td>
                      <td className="px-3 py-2 text-slate-700">{formatActor(log)}</td>
                      <td className="px-3 py-2 text-slate-700">{formatTarget(log)}</td>
                      <td className="px-3 py-2 text-xs text-slate-500">{log.details ?? '-'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {totalPages > 1 && (
              <div className="mt-4 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <p className="text-xs text-slate-500">
                  {t('audit.pagination.status', { page: page + 1, totalPages })}
                </p>
                <div className="flex gap-2">
                  <button
                    type="button"
                    disabled={loading || isFirstPage}
                    onClick={() => setPage((currentPage) => Math.max(0, currentPage - 1))}
                    className="rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {t('audit.pagination.previous')}
                  </button>
                  <button
                    type="button"
                    disabled={loading || isLastPage}
                    onClick={() => setPage((currentPage) => currentPage + 1)}
                    className="rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 transition-colors hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {t('audit.pagination.next')}
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </article>
    </section>
  )
}
