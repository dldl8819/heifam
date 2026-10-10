'use client'

import { useCallback, useEffect, useState } from 'react'
import { apiClient } from '@/lib/api'
import { PrizeDrawPanel } from '@/components/prize-draw-panel'
import { Alert, AlertContent, AlertDescription, AlertIcon } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { formatKstFullDateTime } from '@/lib/kst-time'
import { t } from '@/lib/i18n'
import type { PrizeDraw, PrizeDrawList } from '@/types/api'

const TEMP_GROUP_ID = 1

/**
 * Prize draws: every member reads the records of draws that were run; admins also get the pinball
 * to run one on. A draw is watched on the admin's own screen and only its outcome is kept here.
 */
export default function DrawsPage() {
  const [list, setList] = useState<PrizeDrawList | null>(null)
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busy, setBusy] = useState<boolean>(false)

  useEffect(() => {
    let cancelled = false
    apiClient
      .getPrizeDraws(TEMP_GROUP_ID)
      .then((response) => {
        if (!cancelled) {
          setList(response)
        }
      })
      .catch(() => {
        if (!cancelled) {
          setError(t('prizeDraw.loadError'))
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
  }, [])

  const handleDelete = useCallback(async (draw: PrizeDraw) => {
    if (!window.confirm(t('prizeDraw.deleteConfirm', { title: draw.title }))) {
      return
    }
    setBusy(true)
    setActionError(null)
    try {
      setList(await apiClient.deletePrizeDraw(TEMP_GROUP_ID, draw.id))
    } catch {
      setActionError(t('prizeDraw.saveError'))
    } finally {
      setBusy(false)
    }
  }, [])

  return (
    <section className="space-y-6">
      <div className="space-y-1">
        <h1 className="text-lg font-semibold text-slate-900 dark:text-slate-100">{t('prizeDraw.title')}</h1>
        <p className="max-w-2xl text-sm text-slate-500 dark:text-slate-400">{t('prizeDraw.description')}</p>
      </div>

      {loading && <LoadingIndicator label={t('common.loading')} />}

      {!loading && error && (
        <Alert variant="destructive" appearance="light">
          <AlertIcon icon="destructive">!</AlertIcon>
          <AlertContent>
            <AlertDescription>{error}</AlertDescription>
          </AlertContent>
        </Alert>
      )}

      {!loading && !error && list && (
        <>
          {list.canRun && <PrizeDrawPanel groupId={TEMP_GROUP_ID} onSaved={setList} />}

          <div className="space-y-2">
            <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('prizeDraw.historyTitle')}</h2>
            {actionError && (
              <p className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700 dark:border-rose-800 dark:bg-rose-950/40 dark:text-rose-300">
                {actionError}
              </p>
            )}
            <div className="rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-900">
              {list.draws.length === 0 ? (
                <p className="px-4 py-8 text-center text-sm text-slate-500 dark:text-slate-400">{t('prizeDraw.historyEmpty')}</p>
              ) : (
                <ul className="divide-y divide-slate-100 dark:divide-slate-800">
                  {list.draws.map((draw) => (
                    <li key={draw.id} className="space-y-2 px-4 py-3">
                      <div className="flex flex-wrap items-start justify-between gap-2">
                        <div className="space-y-0.5">
                          <p className="break-all text-sm font-semibold text-slate-900 dark:text-slate-100">{draw.title}</p>
                          <p className="text-xs text-slate-500 dark:text-slate-400">
                            {formatKstFullDateTime(draw.createdAt) || draw.createdAt}
                            {` · ${t('prizeDraw.historyEntrants', { count: draw.entrantCount })}`}
                            {` · ${t(draw.mode === 'LAST' ? 'prizeDraw.modeLast' : 'prizeDraw.modeFirst')}`}
                            {draw.savedByNickname ? ` · ${t('prizeDraw.historySavedBy', { name: draw.savedByNickname })}` : ''}
                          </p>
                        </div>
                        {draw.canDelete && (
                          <button
                            type="button"
                            onClick={() => void handleDelete(draw)}
                            disabled={busy}
                            className="rounded-lg border border-rose-300 px-2.5 py-1 text-xs font-medium text-rose-700 hover:bg-rose-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-rose-800 dark:text-rose-300 dark:hover:bg-rose-950/40"
                          >
                            {t('prizeDraw.delete')}
                          </button>
                        )}
                      </div>
                      <ol className="space-y-1">
                        {draw.winners.map((winner) => (
                          <li key={winner.place} className="flex flex-wrap items-baseline gap-x-2 text-sm">
                            <span className="w-10 shrink-0 text-xs font-semibold text-amber-700 dark:text-amber-300">
                              {t('prizeDraw.place', { place: winner.place })}
                            </span>
                            <span className="break-all font-medium text-slate-900 dark:text-slate-100">{winner.name}</span>
                            {winner.prize && <span className="break-all text-xs text-slate-600 dark:text-slate-300">{winner.prize}</span>}
                          </li>
                        ))}
                      </ol>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        </>
      )}
    </section>
  )
}
