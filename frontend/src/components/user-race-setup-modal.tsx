'use client'

import { useState } from 'react'
import { Alert, AlertContent, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { useAdminAuth } from '@/lib/admin-auth'
import { apiClient, isApiForbiddenError } from '@/lib/api'
import { t } from '@/lib/i18n'
import type { PlayerRace } from '@/types/api'

const RACE_OPTIONS: Array<{ value: PlayerRace; label: string }> = [
  { value: 'P', label: 'P (Protoss)' },
  { value: 'T', label: 'T (Terran)' },
  { value: 'Z', label: 'Z (Zerg)' },
  { value: 'PT', label: 'PT' },
  { value: 'PZ', label: 'PZ' },
  { value: 'TZ', label: 'TZ' },
  { value: 'R', label: 'R (PTZ)' },
]

export function UserRaceSetupModal() {
  const { isLoggedIn, canAccess, preferredRace, refreshAccess } = useAdminAuth()
  const [selectedRace, setSelectedRace] = useState<PlayerRace>('P')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const shouldOpen = isLoggedIn && canAccess && !preferredRace

  if (!shouldOpen) {
    return null
  }

  const handleSave = async () => {
    setSubmitting(true)
    setError(null)
    try {
      await apiClient.updateMyPreferredRace(selectedRace)
      await refreshAccess()
    } catch (submitError) {
      if (isApiForbiddenError(submitError)) {
        setError(t('profileRace.errors.forbidden'))
      } else {
        setError(t('profileRace.errors.saveFailed'))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 px-4">
      <article className="w-full max-w-md rounded-xl border border-slate-700 bg-slate-900 p-5 text-white shadow-2xl">
        <h2 className="text-lg font-semibold">{t('profileRace.title')}</h2>
        <p className="mt-1 text-sm text-slate-300">{t('profileRace.description')}</p>

        {error ? (
          <Alert variant="destructive" appearance="light" className="mt-4">
            <AlertContent>
              <AlertTitle>{t('profileRace.errorTitle')}</AlertTitle>
              <AlertDescription>{error}</AlertDescription>
            </AlertContent>
          </Alert>
        ) : null}

        {submitting ? (
          <LoadingIndicator label={t('profileRace.saving')} className="mt-4 py-2" />
        ) : null}

        <div className="mt-4 space-y-2">
          <label className="text-sm font-medium text-slate-200" htmlFor="profile-race-select">
            {t('profileRace.fieldLabel')}
          </label>
          <select
            id="profile-race-select"
            value={selectedRace}
            onChange={(event) => setSelectedRace(event.target.value as PlayerRace)}
            className="w-full rounded-lg border border-slate-600 bg-slate-800 px-3 py-2 text-sm text-white outline-none transition-colors focus:border-amber-400"
            disabled={submitting}
          >
            {RACE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>

        <button
          type="button"
          onClick={handleSave}
          disabled={submitting}
          className="mt-5 w-full rounded-lg bg-amber-500 px-4 py-2 text-sm font-semibold text-slate-950 transition-colors hover:bg-amber-400 disabled:cursor-not-allowed disabled:bg-slate-500 disabled:text-slate-300"
        >
          {submitting ? t('profileRace.saving') : t('profileRace.save')}
        </button>
      </article>
    </div>
  )
}
