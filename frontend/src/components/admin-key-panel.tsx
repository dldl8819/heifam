'use client'

import { useEffect, useState } from 'react'
import {
  ADMIN_KEY_STORAGE_KEY,
  clearStoredAdminApiKey,
  getStoredAdminApiKey,
  setStoredAdminApiKey,
} from '@/lib/admin-key'
import { useAdminAuth } from '@/lib/admin-auth'
import { t } from '@/lib/i18n'

export function AdminKeyPanel() {
  const [adminKey, setAdminKey] = useState<string>('')
  const [status, setStatus] = useState<string>(t('common.notSet'))
  const { isAdmin, isLoggedIn, isLoading, canAccess } = useAdminAuth()

  useEffect(() => {
    const current = getStoredAdminApiKey()
    setAdminKey(current)
    setStatus(current.length > 0 ? t('common.savedInBrowser') : t('common.notSet'))

    const handleStorage = (event: StorageEvent) => {
      if (event.key !== ADMIN_KEY_STORAGE_KEY) {
        return
      }

      const next = getStoredAdminApiKey()
      setAdminKey(next)
      setStatus(next.length > 0 ? t('common.savedInBrowser') : t('common.notSet'))
    }

    window.addEventListener('storage', handleStorage)
    return () => window.removeEventListener('storage', handleStorage)
  }, [])

  const handleSave = () => {
    setStoredAdminApiKey(adminKey)
    const saved = getStoredAdminApiKey()
    setAdminKey(saved)
    setStatus(saved.length > 0 ? t('common.savedInBrowser') : t('common.notSet'))
  }

  const handleClear = () => {
    clearStoredAdminApiKey()
    setAdminKey('')
    setStatus(t('common.notSet'))
  }

  if (isLoading) {
    return (
      <div className="w-full max-w-sm rounded-lg border border-slate-700 bg-slate-800/70 p-2 shadow-sm">
        <p className="text-[10px] text-slate-300">{t('auth.loading')}</p>
      </div>
    )
  }

  if (!isLoggedIn) {
    return (
      <div className="w-full max-w-sm rounded-lg border border-slate-700 bg-slate-800/70 p-2 shadow-sm">
        <p className="text-[10px] text-slate-300">{t('auth.notLoggedIn')}</p>
        <p className="mt-1 text-[10px] text-slate-400">{t('common.adminLoginRequired')}</p>
      </div>
    )
  }

  if (!isAdmin) {
    return (
      <div className="w-full max-w-sm rounded-lg border border-slate-700 bg-slate-800/70 p-2 shadow-sm">
        <p className="text-[10px] text-slate-300">{t('auth.loggedIn')}</p>
        <p className="mt-1 text-[10px] text-amber-300">
          {canAccess ? t('common.adminReadOnly') : t('access.blockedTitle')}
        </p>
      </div>
    )
  }

  return (
    <div className="w-full max-w-sm rounded-lg border border-slate-700 bg-slate-800/70 p-2 shadow-sm">
      <p className="text-[10px] text-slate-300">{t('auth.loggedIn')}</p>
      <p className="text-[10px] font-semibold tracking-wide text-slate-300">
        {t('adminKey.label')}
      </p>
      <div className="mt-1 flex gap-2">
        <input
          type="password"
          autoComplete="off"
          value={adminKey}
          onChange={(event) => setAdminKey(event.target.value)}
          placeholder="X-ADMIN-KEY"
          className="min-w-0 flex-1 rounded-md border border-slate-600 bg-slate-900 px-2 py-1.5 text-xs text-slate-100 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-600"
        />
        <button
          type="button"
          onClick={handleSave}
          className="rounded-md bg-amber-500 px-2 py-1.5 text-xs font-semibold text-slate-950 hover:bg-amber-400"
        >
          {t('common.save')}
        </button>
        <button
          type="button"
          onClick={handleClear}
          className="rounded-md border border-slate-600 px-2 py-1.5 text-xs font-medium text-slate-200 hover:bg-slate-700"
        >
          {t('common.clear')}
        </button>
      </div>
      <p className="mt-1 text-[10px] text-slate-400">{status}</p>
    </div>
  )
}
