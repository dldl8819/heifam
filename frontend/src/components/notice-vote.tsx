'use client'

import { FormEvent, useCallback, useState } from 'react'
import { ApiRequestError, apiClient } from '@/lib/api'
import { NOTICE_VOTE_OPTION_MAX_LENGTH, noticeVotePercents, validateNewNoticeVoteOption } from '@/lib/notice-vote'
import { t } from '@/lib/i18n'
import type { NoticeDetail, NoticeVote, NoticeVoteOption } from '@/types/api'

type NoticeVoteViewProps = {
  vote: NoticeVote
  // Left out, nothing can be pressed: the form shows the vote this way before it exists.
  onPress?: (option: NoticeVoteOption) => void
  onWithdraw?: () => void
  // Answers whether the option was added, so the box is emptied only then.
  onAddOption?: (label: string) => Promise<boolean>
  onRemoveOption?: (option: NoticeVoteOption) => void
  busy?: boolean
}

type Tint = {
  dot: string
  bar: string
  chosen: string
  hover: string
}

// Green and red mean for and against, so only 찬성 and 반대 get them. Any other option takes the
// next of ten colours that mean nothing, one for each option a vote can hold.
const AGREE_TINT: Tint = {
  dot: 'bg-emerald-500',
  bar: 'bg-emerald-500',
  chosen: 'border-emerald-500 bg-emerald-50 text-emerald-900 dark:border-emerald-500 dark:bg-emerald-950/50 dark:text-emerald-100',
  hover: 'enabled:hover:border-emerald-400 enabled:hover:bg-emerald-50/60 dark:enabled:hover:bg-emerald-950/30',
}

const DISAGREE_TINT: Tint = {
  dot: 'bg-rose-500',
  bar: 'bg-rose-500',
  chosen: 'border-rose-500 bg-rose-50 text-rose-900 dark:border-rose-500 dark:bg-rose-950/50 dark:text-rose-100',
  hover: 'enabled:hover:border-rose-400 enabled:hover:bg-rose-50/60 dark:enabled:hover:bg-rose-950/30',
}

const TINTS: Tint[] = [
  {
    dot: 'bg-sky-500',
    bar: 'bg-sky-500',
    chosen: 'border-sky-500 bg-sky-50 text-sky-900 dark:border-sky-500 dark:bg-sky-950/50 dark:text-sky-100',
    hover: 'enabled:hover:border-sky-400 enabled:hover:bg-sky-50/60 dark:enabled:hover:bg-sky-950/30',
  },
  {
    dot: 'bg-amber-500',
    bar: 'bg-amber-500',
    chosen: 'border-amber-500 bg-amber-50 text-amber-900 dark:border-amber-500 dark:bg-amber-950/50 dark:text-amber-100',
    hover: 'enabled:hover:border-amber-400 enabled:hover:bg-amber-50/60 dark:enabled:hover:bg-amber-950/30',
  },
  {
    dot: 'bg-violet-500',
    bar: 'bg-violet-500',
    chosen: 'border-violet-500 bg-violet-50 text-violet-900 dark:border-violet-500 dark:bg-violet-950/50 dark:text-violet-100',
    hover: 'enabled:hover:border-violet-400 enabled:hover:bg-violet-50/60 dark:enabled:hover:bg-violet-950/30',
  },
  {
    dot: 'bg-teal-500',
    bar: 'bg-teal-500',
    chosen: 'border-teal-500 bg-teal-50 text-teal-900 dark:border-teal-500 dark:bg-teal-950/50 dark:text-teal-100',
    hover: 'enabled:hover:border-teal-400 enabled:hover:bg-teal-50/60 dark:enabled:hover:bg-teal-950/30',
  },
  {
    dot: 'bg-orange-500',
    bar: 'bg-orange-500',
    chosen: 'border-orange-500 bg-orange-50 text-orange-900 dark:border-orange-500 dark:bg-orange-950/50 dark:text-orange-100',
    hover: 'enabled:hover:border-orange-400 enabled:hover:bg-orange-50/60 dark:enabled:hover:bg-orange-950/30',
  },
  {
    dot: 'bg-fuchsia-500',
    bar: 'bg-fuchsia-500',
    chosen: 'border-fuchsia-500 bg-fuchsia-50 text-fuchsia-900 dark:border-fuchsia-500 dark:bg-fuchsia-950/50 dark:text-fuchsia-100',
    hover: 'enabled:hover:border-fuchsia-400 enabled:hover:bg-fuchsia-50/60 dark:enabled:hover:bg-fuchsia-950/30',
  },
  {
    dot: 'bg-indigo-500',
    bar: 'bg-indigo-500',
    chosen: 'border-indigo-500 bg-indigo-50 text-indigo-900 dark:border-indigo-500 dark:bg-indigo-950/50 dark:text-indigo-100',
    hover: 'enabled:hover:border-indigo-400 enabled:hover:bg-indigo-50/60 dark:enabled:hover:bg-indigo-950/30',
  },
  {
    dot: 'bg-lime-500',
    bar: 'bg-lime-500',
    chosen: 'border-lime-500 bg-lime-50 text-lime-900 dark:border-lime-500 dark:bg-lime-950/50 dark:text-lime-100',
    hover: 'enabled:hover:border-lime-400 enabled:hover:bg-lime-50/60 dark:enabled:hover:bg-lime-950/30',
  },
  {
    dot: 'bg-cyan-500',
    bar: 'bg-cyan-500',
    chosen: 'border-cyan-500 bg-cyan-50 text-cyan-900 dark:border-cyan-500 dark:bg-cyan-950/50 dark:text-cyan-100',
    hover: 'enabled:hover:border-cyan-400 enabled:hover:bg-cyan-50/60 dark:enabled:hover:bg-cyan-950/30',
  },
  {
    dot: 'bg-pink-500',
    bar: 'bg-pink-500',
    chosen: 'border-pink-500 bg-pink-50 text-pink-900 dark:border-pink-500 dark:bg-pink-950/50 dark:text-pink-100',
    hover: 'enabled:hover:border-pink-400 enabled:hover:bg-pink-50/60 dark:enabled:hover:bg-pink-950/30',
  },
]

function tintOf(label: string, index: number): Tint {
  if (label === '찬성') {
    return AGREE_TINT
  }
  if (label === '반대') {
    return DISAGREE_TINT
  }
  return TINTS[index % TINTS.length]
}

/**
 * A vote read at a glance: one large button per option that says what it is and how many chose
 * it, each in its own colour, and one bar split between them. The reader's option is filled and
 * checked. Choosing works like radio buttons, so pressing the chosen option again does nothing;
 * taking the vote back is its own, smaller button. An anonymous vote shows counts only; a named
 * one lists who chose what underneath. Where the reader may, an option can be added or taken away.
 */
export function NoticeVoteView({ vote, onPress, onWithdraw, onAddOption, onRemoveOption, busy = false }: NoticeVoteViewProps) {
  const [draft, setDraft] = useState<string>('')
  const [draftError, setDraftError] = useState<string | null>(null)

  const open = vote.status === 'OPEN'
  // Named only when the vote says so: every vote was anonymous before it could say anything.
  const named = vote.anonymous === false
  // An answer from before votes had options carries none: show the frame and no buttons.
  const options = vote.options ?? []
  const percents = noticeVotePercents(options.map((option) => option.count))
  const total = options.reduce((sum, option) => sum + Math.max(0, option.count), 0)
  const canPress = open && Boolean(onPress) && !busy
  const canRemove = open && vote.canRemoveOptions && Boolean(onRemoveOption)
  const myOption = options.find((option) => option.mine)

  const handleAdd = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const problem = validateNewNoticeVoteOption(draft, options.map((option) => option.label))
    if (problem) {
      setDraftError(t(`notices.posts.${problem.key}`, { min: problem.min, max: problem.max, length: problem.length }))
      return
    }
    setDraftError(null)
    if (await onAddOption?.(draft.trim())) {
      setDraft('')
    }
  }

  return (
    <section
      aria-label={t('notices.posts.voteTitle')}
      className="space-y-3 rounded-xl border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-900"
    >
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('notices.posts.voteTitle')}</h2>
        <span
          className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${
            open
              ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-300'
              : 'bg-slate-200 text-slate-700 dark:bg-slate-700 dark:text-slate-200'
          }`}
        >
          {open ? t('notices.posts.voteOpenBadge') : t('notices.posts.voteClosedBadge')}
        </span>
      </div>

      {/* Said before anyone chooses: on a named vote the choice will be seen. */}
      {named && (
        <p className="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800 dark:bg-amber-950/40 dark:text-amber-200">
          {t('notices.posts.voteNamedNotice')}
        </p>
      )}

      {/* Two options sit side by side as a vote for or against always did; more of them wrap. */}
      <div className={`grid gap-2 ${options.length === 2 ? 'grid-cols-2' : 'grid-cols-1 sm:grid-cols-2'}`}>
        {options.map((option, index) => {
          const tint = tintOf(option.label, index)
          return (
            <div key={option.id} className="relative">
              <button
                type="button"
                // Chosen already: pressing it again changes nothing, as with a radio button.
                onClick={() => (option.mine ? undefined : onPress?.(option))}
                disabled={!canPress}
                aria-pressed={option.mine}
                className={`min-h-[3.5rem] w-full rounded-lg border-2 px-3 py-2 text-left transition-colors disabled:cursor-default ${
                  canRemove ? 'pr-9' : ''
                } ${
                  option.mine
                    ? `cursor-default ${tint.chosen}`
                    : `border-slate-200 bg-white text-slate-800 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 ${tint.hover}`
                }`}
              >
                <span className="flex items-start gap-1.5 text-sm font-semibold">
                  {option.mine ? (
                    <svg viewBox="0 0 24 24" className="mt-0.5 h-4 w-4 shrink-0" fill="none" stroke="currentColor" strokeWidth="3" aria-hidden="true">
                      <path d="M5 12.5l4.5 4.5L19 7.5" />
                    </svg>
                  ) : (
                    <span className={`mt-[0.3rem] h-2.5 w-2.5 shrink-0 rounded-full ${tint.dot}`} aria-hidden="true" />
                  )}
                  <span className="min-w-0 break-words">{option.label}</span>
                  {option.mine && <span className="sr-only"> ({t('notices.posts.voteMine')})</span>}
                </span>
                <span className={`mt-0.5 block text-xs ${option.mine ? 'opacity-80' : 'text-slate-500 dark:text-slate-400'}`}>
                  {total > 0
                    ? t('notices.posts.voteShare', { count: option.count, percent: percents[index] })
                    : t('notices.posts.voteNoShare')}
                </span>
              </button>
              {canRemove && (
                <button
                  type="button"
                  onClick={() => onRemoveOption?.(option)}
                  disabled={busy}
                  aria-label={t('notices.posts.voteOptionRemove', { label: option.label })}
                  title={t('notices.posts.voteOptionRemove', { label: option.label })}
                  className="absolute right-1.5 top-1.5 inline-flex h-6 w-6 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100 hover:text-rose-600 disabled:opacity-60 dark:hover:bg-slate-800"
                >
                  <svg viewBox="0 0 24 24" className="h-3.5 w-3.5" fill="none" stroke="currentColor" strokeWidth="2.5" aria-hidden="true">
                    <path d="M6 6l12 12M18 6L6 18" />
                  </svg>
                </button>
              )}
            </div>
          )
        })}
      </div>

      {/* One bar for all options: who is ahead shows without reading a number. */}
      <div className="flex h-2 gap-0.5 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800" aria-hidden="true">
        {total > 0 &&
          options.map((option, index) =>
            percents[index] > 0 ? (
              <span key={option.id} className={`h-full ${tintOf(option.label, index).bar}`} style={{ width: `${percents[index]}%` }} />
            ) : null
          )}
      </div>

      {/* A named vote says who chose what; an anonymous one is never sent the names at all. */}
      {named && total > 0 && (
        <ul className="space-y-1 rounded-lg bg-slate-50 px-3 py-2 text-xs dark:bg-slate-800/60">
          {options
            .filter((option) => (option.voters ?? []).length > 0)
            .map((option) => (
              <li key={option.id} className="break-words text-slate-600 dark:text-slate-300">
                <span className="mr-1.5 font-semibold text-slate-900 dark:text-slate-100">{option.label}</span>
                {(option.voters ?? []).map((voter) => voter ?? t('notices.posts.voteVoterUnnamed')).join(', ')}
              </li>
            ))}
        </ul>
      )}

      {open && vote.canAddOption && onAddOption && (
        <form onSubmit={handleAdd} className="space-y-1">
          <div className="flex gap-2">
            <input
              type="text"
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              maxLength={NOTICE_VOTE_OPTION_MAX_LENGTH}
              placeholder={t('notices.posts.voteOptionAddPlaceholder')}
              aria-label={t('notices.posts.voteOptionAddPlaceholder')}
              className="min-w-0 flex-1 rounded-md border border-slate-300 px-3 py-1.5 text-sm dark:border-slate-600 dark:bg-slate-900"
            />
            <button
              type="submit"
              disabled={busy}
              className="shrink-0 rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
            >
              {t('notices.posts.voteOptionAdd')}
            </button>
          </div>
          {draftError && <p className="text-xs text-rose-600 dark:text-rose-400">{draftError}</p>}
        </form>
      )}

      <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
        <p className="text-xs text-slate-500 dark:text-slate-400">
          {t(named ? 'notices.posts.voteFooterNamed' : 'notices.posts.voteFooterAnonymous', { total })}
        </p>
        {open && myOption && onWithdraw && (
          <button
            type="button"
            onClick={onWithdraw}
            disabled={busy}
            className="text-xs font-medium text-slate-600 underline underline-offset-2 hover:text-slate-900 disabled:opacity-60 dark:text-slate-300 dark:hover:text-white"
          >
            {t('notices.posts.voteWithdraw')}
          </button>
        )}
      </div>
    </section>
  )
}

type NoticeVotePanelProps = {
  groupId: number
  noticeId: number
  vote: NoticeVote
  // Every change answers with the notice as it now is.
  onChange: (notice: NoticeDetail) => void
  // The vote was closed or its options changed while the page was open: the page reads the notice again.
  onClosed: () => void
}

/** The vote on a notice, live: choosing an option casts or moves the reader's vote. */
export function NoticeVotePanel({ groupId, noticeId, vote, onChange, onClosed }: NoticeVotePanelProps) {
  const [busy, setBusy] = useState<boolean>(false)
  const [error, setError] = useState<string | null>(null)

  /** Runs a change to the vote; answers whether it was taken. */
  const run = useCallback(
    async (action: () => Promise<NoticeDetail>): Promise<boolean> => {
      setBusy(true)
      setError(null)
      try {
        onChange(await action())
        return true
      } catch (caught) {
        const status = caught instanceof ApiRequestError ? caught.status : 0
        if (status === 409 || status === 403) {
          // Closed, an option gone or already there, or additions turned off since the page was drawn.
          setError(t(status === 409 ? 'notices.posts.voteChangedHint' : 'notices.posts.voteAddNotAllowed'))
          onClosed()
        } else {
          setError(t('notices.loadError'))
        }
        return false
      } finally {
        setBusy(false)
      }
    },
    [onChange, onClosed]
  )

  const handleRemove = (option: NoticeVoteOption) => {
    if (window.confirm(t('notices.posts.voteOptionRemoveConfirm', { label: option.label, count: option.count }))) {
      void run(() => apiClient.removeNoticeVoteOption(groupId, noticeId, option.id))
    }
  }

  return (
    <div className="space-y-2">
      <NoticeVoteView
        vote={vote}
        onPress={(option) => void run(() => apiClient.setNoticeVote(groupId, noticeId, option.id))}
        onWithdraw={() => void run(() => apiClient.setNoticeVote(groupId, noticeId, null))}
        onAddOption={(label) => run(() => apiClient.addNoticeVoteOption(groupId, noticeId, label))}
        onRemoveOption={handleRemove}
        busy={busy}
      />
      {error && (
        <p className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700 dark:border-rose-800 dark:bg-rose-950/40 dark:text-rose-300">
          {error}
        </p>
      )}
    </div>
  )
}
