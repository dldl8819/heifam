'use client'

import { useEffect, useMemo, useState } from 'react'
import { Alert, AlertContent, AlertDescription, AlertIcon, AlertTitle } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { apiClient, isApiForbiddenError, isApiUnauthorizedError } from '@/lib/api'
import { t } from '@/lib/i18n'
import { useAdminAuth } from '@/lib/admin-auth'
import type { OperationAuditLogItem } from '@/types/api'

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
    case 'MATCH_DELETED':
      return t('audit.actions.matchDeleted')
    default:
      return action
  }
}

function getTargetTypeLabel(targetType: string): string {
  switch (targetType) {
    case 'PLAYER':
      return t('audit.targetTypes.player')
    case 'MATCH':
      return t('audit.targetTypes.match')
    default:
      return targetType
  }
}

export default function OperationAuditPage() {
  const { isSuperAdmin, isLoading } = useAdminAuth()
  const [logs, setLogs] = useState<OperationAuditLogItem[]>([])
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (isLoading) {
      return
    }

    if (!isSuperAdmin) {
      setLoading(false)
      setError(t('audit.superOnly'))
      return
    }

    let active = true
    const loadLogs = async () => {
      setLoading(true)
      setError(null)
      try {
        const response = await apiClient.getOperationAuditLogs(100)
        if (active) {
          setLogs(response)
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
  }, [isLoading, isSuperAdmin])

  const latestLogs = useMemo(
    () => logs.slice().sort((left, right) => right.createdAt.localeCompare(left.createdAt)),
    [logs]
  )

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
            {t('audit.count', { count: latestLogs.length })}
          </span>
        </div>

        {loading ? (
          <div className="mt-6 flex justify-center">
            <LoadingIndicator label={t('common.loading')} />
          </div>
        ) : latestLogs.length === 0 ? (
          <p className="mt-4 rounded-lg border border-slate-200 bg-slate-50 px-3 py-3 text-sm text-slate-600">
            {t('audit.empty')}
          </p>
        ) : (
          <div className="mt-4 overflow-x-auto">
            <table className="min-w-full divide-y divide-slate-200 text-left text-sm">
              <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-3 py-2">{t('audit.table.createdAt')}</th>
                  <th className="px-3 py-2">{t('audit.table.action')}</th>
                  <th className="px-3 py-2">{t('audit.table.actor')}</th>
                  <th className="px-3 py-2">{t('audit.table.target')}</th>
                  <th className="px-3 py-2">{t('audit.table.summary')}</th>
                  <th className="px-3 py-2">{t('audit.table.details')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {latestLogs.map((log) => (
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
                    <td className="px-3 py-2 text-slate-700">
                      {getTargetTypeLabel(log.targetType)} {formatTarget(log)}
                    </td>
                    <td className="px-3 py-2 text-slate-800">{log.summary}</td>
                    <td className="px-3 py-2 text-xs text-slate-500">{log.details ?? '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </article>
    </section>
  )
}
