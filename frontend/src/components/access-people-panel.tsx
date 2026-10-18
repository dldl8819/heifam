'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import { AdminOnlyContent } from '@/components/admin-only-content'
import { Alert, AlertContent, AlertDescription, AlertIcon, AlertTitle } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { useAdminAuth } from '@/lib/admin-auth'
import { apiClient, isApiBadRequestError, isApiForbiddenError } from '@/lib/api'
import { t } from '@/lib/i18n'
import type { AccessPeopleOverview, AccessPersonMember } from '@/types/api'

const CARD_CLASS = 'rounded-xl border border-slate-200 bg-white p-4 shadow-xs dark:border-slate-700 dark:bg-slate-900'
const FIELD_CLASS =
  'rounded-md border border-slate-300 px-2.5 py-1.5 text-sm dark:border-slate-600 dark:bg-slate-900'
const PRIMARY_BUTTON_CLASS =
  'rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-medium text-white hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white'
const PLAIN_BUTTON_CLASS =
  'rounded-lg border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800'

function memberLabel(member: { email: string; nickname: string | null }): string {
  return member.nickname ? `${member.nickname} · ${member.email}` : member.email
}

/**
 * 같은 사람 묶기, for super admins: emails one person signs in with, grouped so that votes, likes,
 * predictions and points count once for them and their name shows as one. Access stays with each
 * email. Nicknames that differ by a trailing "(number)" come up as suggestions; nothing is grouped
 * without a click.
 */
export function AccessPeoplePanel() {
  const { isSuperAdmin, isLoading } = useAdminAuth()
  const [overview, setOverview] = useState<AccessPeopleOverview | null>(null)
  const [loading, setLoading] = useState<boolean>(false)
  const [busy, setBusy] = useState<boolean>(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [suggestionNames, setSuggestionNames] = useState<Record<string, string>>({})
  const [renaming, setRenaming] = useState<{ id: number; name: string } | null>(null)
  const [chosen, setChosen] = useState<Set<string>>(new Set())
  const [manualName, setManualName] = useState<string>('')
  const [search, setSearch] = useState<string>('')

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setOverview(await apiClient.getAccessPeople())
    } catch (caught) {
      setError(isApiForbiddenError(caught) ? t('common.permissionDenied') : t('accessPeople.loadError'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!isLoading && isSuperAdmin) {
      void load()
    }
  }, [isLoading, isSuperAdmin, load])

  const run = async (action: () => Promise<AccessPeopleOverview>, done: string) => {
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      setOverview(await action())
      setMessage(done)
      return true
    } catch (caught) {
      setError(
        isApiBadRequestError(caught) && caught instanceof Error && caught.message.trim().length > 0
          ? caught.message
          : isApiForbiddenError(caught)
            ? t('common.permissionDenied')
            : t('accessPeople.actionError')
      )
      return false
    } finally {
      setBusy(false)
    }
  }

  const group = async (members: AccessPersonMember[] | string[], name: string) => {
    const emails = members.map((member) => (typeof member === 'string' ? member : member.email))
    if (!window.confirm(t('accessPeople.groupConfirm', { count: emails.length, name: name.trim() || t('accessPeople.defaultName') }))) {
      return false
    }
    return run(() => apiClient.groupAccessPeople(emails, name.trim()), t('accessPeople.grouped', { name: name.trim() || t('accessPeople.defaultName') }))
  }

  const ungroup = async (member: AccessPersonMember) => {
    if (!window.confirm(t('accessPeople.ungroupConfirm', { email: memberLabel(member) }))) {
      return
    }
    await run(() => apiClient.ungroupAccessEmail(member.email), t('accessPeople.ungrouped'))
  }

  const rename = async () => {
    if (!renaming) {
      return
    }
    if (await run(() => apiClient.renameAccessPerson(renaming.id, renaming.name.trim()), t('accessPeople.renamed'))) {
      setRenaming(null)
    }
  }

  const shownCandidates = useMemo(() => {
    const word = search.trim().toLowerCase()
    const candidates = overview?.candidates ?? []
    return word.length === 0
      ? candidates
      : candidates.filter(
          (candidate) =>
            candidate.email.toLowerCase().includes(word) || (candidate.nickname ?? '').toLowerCase().includes(word)
        )
  }, [overview, search])
  const personNames = useMemo(
    () => new Map((overview?.people ?? []).map((person) => [person.id, person.displayName])),
    [overview]
  )

  const toggle = (email: string) =>
    setChosen((previous) => {
      const next = new Set(previous)
      if (next.has(email)) {
        next.delete(email)
      } else {
        next.add(email)
      }
      return next
    })

  return (
    <section className="space-y-6">
      <header className="space-y-1 rounded-xl border border-slate-200 bg-white px-5 py-4 shadow-xs dark:border-slate-700 dark:bg-slate-900">
        <h2 className="text-2xl font-semibold tracking-tight">{t('accessPeople.title')}</h2>
        <p className="text-sm text-slate-600 dark:text-slate-300">{t('accessPeople.description')}</p>
      </header>

      <AdminOnlyContent>
        {!isLoading && !isSuperAdmin ? (
          <p className="text-sm text-slate-600 dark:text-slate-300">{t('accessPeople.superAdminOnly')}</p>
        ) : (
          <div className="space-y-6">
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
              <div role="status" className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300">
                {message}
              </div>
            )}
            {loading && <LoadingIndicator label={t('common.loading')} />}

            {overview && (
              <>
                <article className={`${CARD_CLASS} space-y-3`}>
                  <div className="space-y-1">
                    <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('accessPeople.suggestionsTitle')}</h3>
                    <p className="text-xs text-slate-500 dark:text-slate-400">{t('accessPeople.suggestionsHint')}</p>
                  </div>
                  {overview.suggestions.length === 0 ? (
                    <p className="text-sm text-slate-500 dark:text-slate-400">{t('accessPeople.suggestionsEmpty')}</p>
                  ) : (
                    <ul className="grid gap-3 md:grid-cols-2">
                      {overview.suggestions.map((suggestion) => {
                        const key = suggestion.members.map((member) => member.email).join(',')
                        const name = suggestionNames[key] ?? suggestion.displayName
                        return (
                          <li key={key} className="space-y-2 rounded-lg border border-amber-200 bg-amber-50/60 p-3 dark:border-amber-800 dark:bg-amber-950/30">
                            <ul className="space-y-1 text-xs text-slate-700 dark:text-slate-200">
                              {suggestion.members.map((member) => (
                                <li key={member.email} className="break-all">
                                  {memberLabel(member)}
                                </li>
                              ))}
                            </ul>
                            {suggestion.personId !== null && (
                              <p className="text-[11px] text-slate-500 dark:text-slate-400">
                                {t('accessPeople.addHint', { name: personNames.get(suggestion.personId) ?? suggestion.displayName })}
                              </p>
                            )}
                            <div className="flex flex-wrap items-center gap-2">
                              <label className="flex items-center gap-1.5 text-xs text-slate-600 dark:text-slate-300">
                                {t('accessPeople.nameLabel')}
                                <input
                                  value={name}
                                  maxLength={100}
                                  disabled={busy}
                                  onChange={(event) =>
                                    setSuggestionNames((previous) => ({ ...previous, [key]: event.target.value }))
                                  }
                                  className={`${FIELD_CLASS} w-32`}
                                />
                              </label>
                              <button
                                type="button"
                                disabled={busy}
                                onClick={() => void group(suggestion.members, name)}
                                className={PRIMARY_BUTTON_CLASS}
                              >
                                {suggestion.personId !== null ? t('accessPeople.addToPerson') : t('accessPeople.group')}
                              </button>
                            </div>
                          </li>
                        )
                      })}
                    </ul>
                  )}
                </article>

                <article className={`${CARD_CLASS} space-y-3`}>
                  <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('accessPeople.peopleTitle')}</h3>
                  {overview.people.length === 0 ? (
                    <p className="text-sm text-slate-500 dark:text-slate-400">{t('accessPeople.peopleEmpty')}</p>
                  ) : (
                    <ul className="space-y-3">
                      {overview.people.map((person) => (
                        <li key={person.id} className="space-y-2 rounded-lg border border-slate-200 p-3 dark:border-slate-700">
                          <div className="flex flex-wrap items-center gap-2">
                            {renaming?.id === person.id ? (
                              <>
                                <input
                                  value={renaming.name}
                                  maxLength={100}
                                  disabled={busy}
                                  aria-label={t('accessPeople.nameLabel')}
                                  onChange={(event) => setRenaming({ id: person.id, name: event.target.value })}
                                  className={`${FIELD_CLASS} w-40`}
                                />
                                <button type="button" disabled={busy} onClick={() => void rename()} className={PRIMARY_BUTTON_CLASS}>
                                  {t('accessPeople.save')}
                                </button>
                                <button type="button" disabled={busy} onClick={() => setRenaming(null)} className={PLAIN_BUTTON_CLASS}>
                                  {t('accessPeople.cancel')}
                                </button>
                              </>
                            ) : (
                              <>
                                <span className="text-sm font-semibold text-slate-900 dark:text-slate-100">{person.displayName}</span>
                                <button
                                  type="button"
                                  disabled={busy}
                                  onClick={() => setRenaming({ id: person.id, name: person.displayName })}
                                  className={PLAIN_BUTTON_CLASS}
                                >
                                  {t('accessPeople.rename')}
                                </button>
                              </>
                            )}
                          </div>
                          <ul className="space-y-1.5">
                            {person.members.map((member) => (
                              <li key={member.email} className="flex flex-wrap items-center justify-between gap-2 text-xs text-slate-700 dark:text-slate-200">
                                <span className="break-all">
                                  {memberLabel(member)}
                                  {!member.hasAccess && (
                                    <span className="ml-2 rounded-sm border border-slate-300 px-1.5 py-0.5 text-[11px] text-slate-500 dark:border-slate-600 dark:text-slate-400">
                                      {t('accessPeople.noAccess')}
                                    </span>
                                  )}
                                </span>
                                <button type="button" disabled={busy} onClick={() => void ungroup(member)} className={PLAIN_BUTTON_CLASS}>
                                  {t('accessPeople.ungroup')}
                                </button>
                              </li>
                            ))}
                          </ul>
                        </li>
                      ))}
                    </ul>
                  )}
                </article>

                <article className={`${CARD_CLASS} space-y-3`}>
                  <div className="space-y-1">
                    <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('accessPeople.manualTitle')}</h3>
                    <p className="text-xs text-slate-500 dark:text-slate-400">{t('accessPeople.manualHint')}</p>
                  </div>
                  <input
                    type="search"
                    value={search}
                    onChange={(event) => setSearch(event.target.value)}
                    placeholder={t('accessPeople.searchPlaceholder')}
                    aria-label={t('accessPeople.searchPlaceholder')}
                    className={`${FIELD_CLASS} w-full sm:w-72`}
                  />
                  <ul className="max-h-72 space-y-1 overflow-y-auto rounded-lg border border-slate-200 p-2 dark:border-slate-700">
                    {shownCandidates.map((candidate) => (
                      <li key={candidate.email}>
                        <label className="flex cursor-pointer items-start gap-2 rounded-sm px-1.5 py-1 text-xs text-slate-700 hover:bg-slate-50 dark:text-slate-200 dark:hover:bg-slate-800">
                          <input type="checkbox" checked={chosen.has(candidate.email)} disabled={busy} onChange={() => toggle(candidate.email)} />
                          <span className="break-all">
                            {memberLabel(candidate)}
                            {candidate.personId !== null && (
                              <span className="ml-2 text-slate-500 dark:text-slate-400">
                                {t('accessPeople.inPerson', { name: personNames.get(candidate.personId) ?? '' })}
                              </span>
                            )}
                          </span>
                        </label>
                      </li>
                    ))}
                  </ul>
                  <div className="flex flex-wrap items-center gap-2">
                    <label className="flex items-center gap-1.5 text-xs text-slate-600 dark:text-slate-300">
                      {t('accessPeople.nameLabel')}
                      <input
                        value={manualName}
                        maxLength={100}
                        disabled={busy}
                        placeholder={t('accessPeople.defaultName')}
                        onChange={(event) => setManualName(event.target.value)}
                        className={`${FIELD_CLASS} w-40`}
                      />
                    </label>
                    <button
                      type="button"
                      disabled={busy || chosen.size < 2}
                      onClick={async () => {
                        if (await group([...chosen], manualName)) {
                          setChosen(new Set())
                          setManualName('')
                        }
                      }}
                      className={PRIMARY_BUTTON_CLASS}
                    >
                      {t('accessPeople.groupChosen', { count: chosen.size })}
                    </button>
                  </div>
                </article>
              </>
            )}
          </div>
        )}
      </AdminOnlyContent>
    </section>
  )
}
