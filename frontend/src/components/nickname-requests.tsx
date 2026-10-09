'use client'

import { FormEvent, useCallback, useEffect, useState } from 'react'
import { ApiRequestError, apiClient } from '@/lib/api'
import { Alert, AlertContent, AlertDescription, AlertIcon } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { formatKstFullDateTime } from '@/lib/kst-time'
import { t } from '@/lib/i18n'
import {
  NICKNAME_REQUEST_NICKNAME_MAX_LENGTH,
  NICKNAME_REQUEST_NOTE_MAX_LENGTH,
  NICKNAME_REQUEST_REASON_MAX_LENGTH,
  nicknameRequestStatusClass,
  validateNicknameDecisionNote,
  validateNicknameRequest,
} from '@/lib/nickname-requests'
import type { NicknameRequest, NicknameRequestList } from '@/types/api'

const TEMP_GROUP_ID = 1

const fieldClass = 'w-full rounded-md border border-slate-300 px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-900'
const primaryButtonClass =
  'rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-medium text-white hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white'
const plainButtonClass =
  'rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800'
const errorClass =
  'rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700 dark:border-rose-800 dark:bg-rose-950/40 dark:text-rose-300'
const cardClass = 'rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-900'

/** Why a change was not taken, by what the backend answered. */
function failureMessage(caught: unknown, conflictKey: string): string {
  const status = caught instanceof ApiRequestError ? caught.status : 0
  if (status === 409) {
    return t(conflictKey)
  }
  if (status === 429) {
    return t('nicknameRequests.limitReached')
  }
  if (status === 400) {
    return t('nicknameRequests.invalid')
  }
  return t('nicknameRequests.saveError')
}

/** One request: from which nickname to which, where it stands, and what was said about it. */
function RequestRow({
  request,
  busy,
  onCancel,
  onDecide,
}: {
  request: NicknameRequest
  busy: boolean
  onCancel: (request: NicknameRequest) => void
  onDecide: (request: NicknameRequest, status: 'APPROVED' | 'REJECTED', note: string) => void
}) {
  const [note, setNote] = useState<string>('')

  return (
    <li className="space-y-2 px-4 py-3">
      <div className="flex flex-wrap items-center gap-2">
        <span className="break-all text-sm font-medium text-slate-900 dark:text-slate-100">
          {request.currentNickname || t('nicknameRequests.noNickname')}
          <span className="mx-1.5 text-slate-400" aria-hidden="true">
            →
          </span>
          {request.desiredNickname}
        </span>
        <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${nicknameRequestStatusClass(request.status)}`}>
          {t(`nicknameRequests.status.${request.status}`)}
        </span>
      </div>
      <p className="text-xs text-slate-500 dark:text-slate-400">
        {t('nicknameRequests.requestedAt', { time: formatKstFullDateTime(request.createdAt) || request.createdAt })}
        {request.processedAt && request.status !== 'CANCELED'
          ? ` · ${t('nicknameRequests.processedAt', {
              time: formatKstFullDateTime(request.processedAt) || request.processedAt,
              by: request.processedByNickname || t('nicknameRequests.admin'),
            })}`
          : ''}
      </p>
      {request.reason && (
        <p className="whitespace-pre-wrap break-words text-sm text-slate-700 dark:text-slate-200">
          <span className="mr-1.5 text-xs text-slate-500 dark:text-slate-400">{t('nicknameRequests.reasonLabel')}</span>
          {request.reason}
        </p>
      )}
      {request.adminNote && (
        <p className="whitespace-pre-wrap break-words rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700 dark:bg-slate-800/60 dark:text-slate-200">
          <span className="mr-1.5 text-xs text-slate-500 dark:text-slate-400">{t('nicknameRequests.noteLabel')}</span>
          {request.adminNote}
        </p>
      )}
      {request.canDecide && (
        <div className="flex flex-wrap items-center gap-2">
          <input
            type="text"
            value={note}
            onChange={(event) => setNote(event.target.value)}
            maxLength={NICKNAME_REQUEST_NOTE_MAX_LENGTH}
            placeholder={t('nicknameRequests.notePlaceholder')}
            className={`${fieldClass} min-w-0 flex-1 basis-48`}
          />
          <button type="button" disabled={busy} onClick={() => onDecide(request, 'APPROVED', note)} className={primaryButtonClass}>
            {t('nicknameRequests.approve')}
          </button>
          <button type="button" disabled={busy} onClick={() => onDecide(request, 'REJECTED', note)} className={plainButtonClass}>
            {t('nicknameRequests.reject')}
          </button>
        </div>
      )}
      {request.canCancel && (
        <button type="button" disabled={busy} onClick={() => onCancel(request)} className={plainButtonClass}>
          {t('nicknameRequests.cancelRequest')}
        </button>
      )}
    </li>
  )
}

/**
 * Where a member asks for a nickname change and follows what became of it, and where admins work
 * through the requests. Marking a request changes no nickname: an admin changes it by hand first.
 */
export function NicknameRequests() {
  const [list, setList] = useState<NicknameRequestList | null>(null)
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)

  const [desiredNickname, setDesiredNickname] = useState<string>('')
  const [reason, setReason] = useState<string>('')
  const [formError, setFormError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [busy, setBusy] = useState<boolean>(false)

  useEffect(() => {
    let cancelled = false
    apiClient
      .getNicknameRequests(TEMP_GROUP_ID)
      .then((response) => {
        if (!cancelled) {
          setList(response)
        }
      })
      .catch(() => {
        if (!cancelled) {
          setError(t('nicknameRequests.loadError'))
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

  const handleSubmit = useCallback(
    async (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault()
      const problem = validateNicknameRequest(desiredNickname, reason, list?.currentNickname)
      if (problem) {
        setFormError(t(`nicknameRequests.${problem.key}`, { max: problem.max }))
        return
      }
      setFormError(null)
      setSuccessMessage(null)
      setBusy(true)
      try {
        setList(
          await apiClient.createNicknameRequest(TEMP_GROUP_ID, {
            desiredNickname: desiredNickname.trim(),
            reason: reason.trim(),
          })
        )
        setDesiredNickname('')
        setReason('')
        setSuccessMessage(t('nicknameRequests.saveSuccess'))
      } catch (caught) {
        setFormError(failureMessage(caught, 'nicknameRequests.alreadyPending'))
      } finally {
        setBusy(false)
      }
    },
    [desiredNickname, list?.currentNickname, reason]
  )

  /** Runs a change that answers with the lists as they now are. */
  const run = useCallback(async (action: () => Promise<NicknameRequestList>) => {
    setBusy(true)
    setActionError(null)
    setSuccessMessage(null)
    try {
      setList(await action())
    } catch (caught) {
      setActionError(failureMessage(caught, 'nicknameRequests.alreadyProcessed'))
      // Someone else got there first: show the lists as they are now.
      if (caught instanceof ApiRequestError && (caught.status === 409 || caught.status === 404)) {
        try {
          setList(await apiClient.getNicknameRequests(TEMP_GROUP_ID))
        } catch {
          // The message above already says the change was not taken.
        }
      }
    } finally {
      setBusy(false)
    }
  }, [])

  const handleCancel = (request: NicknameRequest) => {
    if (window.confirm(t('nicknameRequests.cancelConfirm'))) {
      void run(() => apiClient.cancelNicknameRequest(TEMP_GROUP_ID, request.id))
    }
  }

  const handleDecide = (request: NicknameRequest, status: 'APPROVED' | 'REJECTED', note: string) => {
    const problem = validateNicknameDecisionNote(note)
    if (problem) {
      setActionError(t(`nicknameRequests.${problem.key}`, { max: problem.max }))
      return
    }
    const question = t(status === 'APPROVED' ? 'nicknameRequests.approveConfirm' : 'nicknameRequests.rejectConfirm', {
      nickname: request.desiredNickname,
    })
    if (window.confirm(question)) {
      void run(() => apiClient.decideNicknameRequest(TEMP_GROUP_ID, request.id, { status, note: note.trim() }))
    }
  }

  const waiting = list?.mine.some((request) => request.status === 'PENDING') ?? false
  const received = list?.received ?? []
  const receivedWaiting = received.filter((request) => request.status === 'PENDING').length

  return (
    <section className="space-y-6">
      <div className="space-y-1">
        <h1 className="text-lg font-semibold text-slate-900 dark:text-slate-100">{t('nicknameRequests.title')}</h1>
        <p className="max-w-2xl text-sm text-slate-500 dark:text-slate-400">{t('nicknameRequests.description')}</p>
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
          {successMessage && (
            <p className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300">
              {successMessage}
            </p>
          )}

          {actionError && <p className={errorClass}>{actionError}</p>}

          {/* Admins come here to work through what was asked: that list first, their own request after. */}
          {list.admin && (
            <div className="space-y-2">
              <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
                {t('nicknameRequests.receivedTitle', { count: receivedWaiting })}
              </h2>
              <p className="text-xs text-slate-500 dark:text-slate-400">{t('nicknameRequests.receivedHint')}</p>
              <div className={cardClass}>
                {received.length === 0 ? (
                  <p className="px-4 py-8 text-center text-sm text-slate-500 dark:text-slate-400">
                    {t('nicknameRequests.receivedEmpty')}
                  </p>
                ) : (
                  <ul className="divide-y divide-slate-100 dark:divide-slate-800">
                    {received.map((request) => (
                      <RequestRow
                        key={request.id}
                        request={{ ...request, canCancel: false }}
                        busy={busy}
                        onCancel={handleCancel}
                        onDecide={handleDecide}
                      />
                    ))}
                  </ul>
                )}
              </div>
            </div>
          )}

          {waiting ? (
            <p className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600 dark:border-slate-700 dark:bg-slate-800/60 dark:text-slate-300">
              {t('nicknameRequests.waitingNotice')}
            </p>
          ) : (
            <form
              onSubmit={handleSubmit}
              className="space-y-3 rounded-xl border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-800/60"
            >
              {formError && <p className={errorClass}>{formError}</p>}
              <p className="text-sm text-slate-700 dark:text-slate-200">
                <span className="mr-1.5 text-xs text-slate-500 dark:text-slate-400">{t('nicknameRequests.currentLabel')}</span>
                {list.currentNickname || t('nicknameRequests.noNickname')}
              </p>
              <input
                type="text"
                value={desiredNickname}
                onChange={(event) => setDesiredNickname(event.target.value)}
                maxLength={NICKNAME_REQUEST_NICKNAME_MAX_LENGTH}
                placeholder={t('nicknameRequests.nicknamePlaceholder')}
                className={fieldClass}
              />
              <textarea
                value={reason}
                onChange={(event) => setReason(event.target.value)}
                maxLength={NICKNAME_REQUEST_REASON_MAX_LENGTH}
                placeholder={t('nicknameRequests.reasonPlaceholder')}
                rows={3}
                className={fieldClass}
              />
              <button type="submit" disabled={busy} className={primaryButtonClass}>
                {busy ? t('nicknameRequests.saving') : t('nicknameRequests.submit')}
              </button>
            </form>
          )}

          <div className="space-y-2">
            <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('nicknameRequests.mineTitle')}</h2>
            <div className={cardClass}>
              {list.mine.length === 0 ? (
                <p className="px-4 py-8 text-center text-sm text-slate-500 dark:text-slate-400">{t('nicknameRequests.mineEmpty')}</p>
              ) : (
                <ul className="divide-y divide-slate-100 dark:divide-slate-800">
                  {list.mine.map((request) => (
                    // Deciding happens in the list below, so a row here only offers calling it off.
                    <RequestRow
                      key={request.id}
                      request={{ ...request, canDecide: false }}
                      busy={busy}
                      onCancel={handleCancel}
                      onDecide={handleDecide}
                    />
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
