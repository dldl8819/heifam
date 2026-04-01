'use client'

import { FormEvent, useCallback, useEffect, useState } from 'react'
import { AdminOnlyContent } from '@/components/admin-only-content'
import { Alert, AlertContent, AlertDescription, AlertIcon, AlertTitle } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { useAdminAuth } from '@/lib/admin-auth'
import { apiClient, isApiForbiddenError } from '@/lib/api'
import { t } from '@/lib/i18n'
import type {
  AccessAdminListResponse,
  AccessAllowedEmailListResponse,
} from '@/types/api'

function normalizeEmail(value: string): string {
  return value.trim().toLowerCase()
}

function normalizeNickname(value: string): string {
  return value.trim()
}

function isEmailFormatValid(value: string): boolean {
  const normalized = normalizeEmail(value)
  return normalized.length > 3 && normalized.includes('@')
}

function isNicknameFormatValid(value: string): boolean {
  const normalized = normalizeNickname(value)
  return normalized.length > 0 && normalized.length <= 100
}

export default function AccessControlPage() {
  const { email, nickname, role, canAccess, isAdmin, isSuperAdmin, refreshAccess } = useAdminAuth()
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  const [adminList, setAdminList] = useState<AccessAdminListResponse | null>(null)
  const [allowedList, setAllowedList] = useState<AccessAllowedEmailListResponse | null>(null)

  const [newAdminEmail, setNewAdminEmail] = useState<string>('')
  const [newAdminNickname, setNewAdminNickname] = useState<string>('')
  const [newAllowedEmail, setNewAllowedEmail] = useState<string>('')
  const [newAllowedNickname, setNewAllowedNickname] = useState<string>('')
  const [saving, setSaving] = useState<boolean>(false)

  const loadLists = useCallback(async () => {
    if (!isAdmin) {
      setLoading(false)
      return
    }

    setLoading(true)
    setError(null)

    try {
      const [admins, allowed] = await Promise.all([
        apiClient.getAdminEmailList(),
        apiClient.getAllowedEmailList(),
      ])
      setAdminList(admins)
      setAllowedList(allowed)
    } catch {
      setError(t('access.loadError'))
    } finally {
      setLoading(false)
    }
  }, [isAdmin])

  useEffect(() => {
    void loadLists()
  }, [loadLists])

  const handleAddAdmin = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError(null)
    setMessage(null)

    const normalized = normalizeEmail(newAdminEmail)
    const normalizedNickname = normalizeNickname(newAdminNickname)
    if (!isEmailFormatValid(normalized)) {
      setError(t('access.messages.invalidEmail'))
      return
    }
    if (!isNicknameFormatValid(normalizedNickname)) {
      setError(t('access.messages.invalidNickname'))
      return
    }
    if (!isSuperAdmin) {
      setError(t('access.superOnly'))
      return
    }

    setSaving(true)
    try {
      const response = await apiClient.addAdminEmail(normalized, normalizedNickname)
      setAdminList(response)
      setNewAdminEmail('')
      setNewAdminNickname('')
      setMessage(t('access.messages.adminSaved'))
      await refreshAccess()
    } catch (requestError) {
      if (isApiForbiddenError(requestError)) {
        setError(t('access.superOnly'))
      } else {
        setError(t('access.loadError'))
      }
    } finally {
      setSaving(false)
    }
  }

  const handleRemoveAdmin = async (targetEmail: string) => {
    setError(null)
    setMessage(null)
    if (!isSuperAdmin) {
      setError(t('access.superOnly'))
      return
    }

    setSaving(true)
    try {
      const response = await apiClient.removeAdminEmail(targetEmail)
      setAdminList(response)
      setMessage(t('access.messages.removeSaved'))
      await refreshAccess()
    } catch (requestError) {
      if (isApiForbiddenError(requestError)) {
        setError(t('access.superOnly'))
      } else {
        setError(t('access.loadError'))
      }
    } finally {
      setSaving(false)
    }
  }

  const handleAddAllowed = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError(null)
    setMessage(null)

    const normalized = normalizeEmail(newAllowedEmail)
    const normalizedNickname = normalizeNickname(newAllowedNickname)
    if (!isEmailFormatValid(normalized)) {
      setError(t('access.messages.invalidEmail'))
      return
    }
    if (!isNicknameFormatValid(normalizedNickname)) {
      setError(t('access.messages.invalidNickname'))
      return
    }

    setSaving(true)
    try {
      const response = await apiClient.addAllowedEmail(normalized, normalizedNickname)
      setAllowedList(response)
      setNewAllowedEmail('')
      setNewAllowedNickname('')
      setMessage(t('access.messages.allowedSaved'))
    } catch {
      setError(t('access.loadError'))
    } finally {
      setSaving(false)
    }
  }

  const handleRemoveAllowed = async (targetEmail: string) => {
    setError(null)
    setMessage(null)

    setSaving(true)
    try {
      const response = await apiClient.removeAllowedEmail(targetEmail)
      setAllowedList(response)
      setMessage(t('access.messages.removeSaved'))
    } catch {
      setError(t('access.loadError'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="space-y-6">
      <header className="space-y-1 rounded-xl border border-slate-200 bg-white px-5 py-4 shadow-sm">
        <h2 className="text-2xl font-semibold tracking-tight">{t('access.title')}</h2>
        <p className="text-sm text-slate-600">{t('access.description')}</p>
      </header>

      <AdminOnlyContent>
        {error && (
          <Alert variant="destructive" appearance="light">
            <AlertIcon icon="destructive">!</AlertIcon>
            <AlertContent>
              <AlertTitle>{t('common.errorPrefix')}</AlertTitle>
              <AlertDescription>{error}</AlertDescription>
            </AlertContent>
          </Alert>
        )}
        {message && (
          <div className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
            {message}
          </div>
        )}

        {loading && <LoadingIndicator label={t('common.loading')} />}

        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <h3 className="text-sm font-semibold text-slate-900">{t('access.sections.me')}</h3>
          <div className="mt-3 grid gap-2 text-sm text-slate-700 sm:grid-cols-4">
            <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
              <p className="text-xs text-slate-500">{t('access.me.email')}</p>
              <p className="mt-1 font-medium">{email ?? '-'}</p>
            </div>
            <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
              <p className="text-xs text-slate-500">{t('access.me.nickname')}</p>
              <p className="mt-1 font-medium">{nickname ?? t('access.labels.nicknameNotSet')}</p>
            </div>
            <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
              <p className="text-xs text-slate-500">{t('access.me.role')}</p>
              <p className="mt-1 font-medium">{role}</p>
            </div>
            <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
              <p className="text-xs text-slate-500">{t('access.me.access')}</p>
              <p className="mt-1 font-medium">
                {canAccess ? t('access.me.yes') : t('access.me.no')}
              </p>
            </div>
          </div>
        </article>

        <div className="grid gap-4 xl:grid-cols-2">
          <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <h3 className="text-sm font-semibold text-slate-900">{t('access.sections.superAdmins')}</h3>
            <ul className="mt-3 space-y-2">
              {(adminList?.superAdmins ?? []).map((superAdminUser) => (
                <li
                  key={`super-admin-${superAdminUser.email}`}
                  className="flex items-center justify-between rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-800"
                >
                  <span>
                    <span className="font-medium">{superAdminUser.nickname ?? t('access.labels.nicknameNotSet')}</span>
                    <span className="ml-2 text-xs text-slate-500">({superAdminUser.email})</span>
                  </span>
                  <span className="rounded-md bg-slate-900 px-2 py-0.5 text-xs font-medium text-white">
                    {t('access.actions.fixed')}
                  </span>
                </li>
              ))}
            </ul>
          </article>

          <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <h3 className="text-sm font-semibold text-slate-900">{t('access.sections.admins')}</h3>
            {!isSuperAdmin && (
              <p className="mt-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
                {t('access.superOnly')}
              </p>
            )}
            {isSuperAdmin && (
              <form className="mt-3 grid gap-2 sm:grid-cols-[1fr_1fr_auto]" onSubmit={handleAddAdmin}>
                <input
                  type="email"
                  value={newAdminEmail}
                  onChange={(event) => setNewAdminEmail(event.target.value)}
                  placeholder={t('access.form.emailPlaceholder')}
                  className="min-w-0 flex-1 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
                />
                <input
                  type="text"
                  value={newAdminNickname}
                  onChange={(event) => setNewAdminNickname(event.target.value)}
                  placeholder={t('access.form.nicknamePlaceholder')}
                  className="min-w-0 flex-1 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
                />
                <button
                  type="submit"
                  disabled={saving}
                  className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300"
                >
                  {saving ? t('access.actions.saving') : t('access.form.addAdmin')}
                </button>
              </form>
            )}

            <ul className="mt-3 space-y-2">
              {(adminList?.admins ?? []).map((adminUser) => (
                <li
                  key={`admin-${adminUser.email}`}
                  className="flex items-center justify-between rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800"
                >
                  <span>
                    <span className="font-medium">{adminUser.nickname ?? t('access.labels.nicknameNotSet')}</span>
                    <span className="ml-2 text-xs text-slate-500">({adminUser.email})</span>
                  </span>
                  {isSuperAdmin ? (
                    <button
                      type="button"
                      disabled={saving}
                      onClick={() => handleRemoveAdmin(adminUser.email)}
                      className="rounded-md border border-rose-300 bg-white px-2 py-1 text-xs font-medium text-rose-700 transition-colors hover:border-rose-500 hover:bg-rose-50 disabled:cursor-not-allowed disabled:border-slate-200 disabled:text-slate-400"
                    >
                      {t('access.actions.remove')}
                    </button>
                  ) : (
                    <span className="rounded-md bg-slate-100 px-2 py-0.5 text-xs text-slate-500">
                      {t('common.readOnly')}
                    </span>
                  )}
                </li>
              ))}
            </ul>
          </article>
        </div>

        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <h3 className="text-sm font-semibold text-slate-900">{t('access.sections.allowlist')}</h3>
          <form className="mt-3 grid gap-2 sm:grid-cols-[1fr_1fr_auto]" onSubmit={handleAddAllowed}>
            <input
              type="email"
              value={newAllowedEmail}
              onChange={(event) => setNewAllowedEmail(event.target.value)}
              placeholder={t('access.form.emailPlaceholder')}
              className="min-w-0 flex-1 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
            />
            <input
              type="text"
              value={newAllowedNickname}
              onChange={(event) => setNewAllowedNickname(event.target.value)}
              placeholder={t('access.form.nicknamePlaceholder')}
              className="min-w-0 flex-1 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
            />
            <button
              type="submit"
              disabled={saving || loading}
              className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300"
            >
              {saving ? t('access.actions.saving') : t('access.form.addAllowed')}
            </button>
          </form>

          <ul className="mt-3 space-y-2">
            {(allowedList?.allowedUsers ?? []).map((allowedUser) => (
              <li
                key={`allowed-${allowedUser.email}`}
                className="flex items-center justify-between rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800"
              >
                <span>
                  <span className="font-medium">{allowedUser.nickname ?? t('access.labels.nicknameNotSet')}</span>
                  <span className="ml-2 text-xs text-slate-500">({allowedUser.email})</span>
                </span>
                <button
                  type="button"
                  disabled={saving}
                  onClick={() => handleRemoveAllowed(allowedUser.email)}
                  className="rounded-md border border-rose-300 bg-white px-2 py-1 text-xs font-medium text-rose-700 transition-colors hover:border-rose-500 hover:bg-rose-50 disabled:cursor-not-allowed disabled:border-slate-200 disabled:text-slate-400"
                >
                  {t('access.actions.remove')}
                </button>
              </li>
            ))}
          </ul>
        </article>
      </AdminOnlyContent>
    </section>
  )
}
