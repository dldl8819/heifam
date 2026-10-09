'use client'

import { useCallback, useState } from 'react'
import { ApiRequestError, apiClient } from '@/lib/api'
import { summarizeNoticeVote } from '@/lib/notice-vote'
import { t } from '@/lib/i18n'
import type { NoticeDetail, NoticeVote, NoticeVoteChoice } from '@/types/api'

// Where the page scrolls to from the "there is a vote" line above a long notice.
export const NOTICE_VOTE_ANCHOR = 'notice-vote'

type NoticeVoteViewProps = {
  vote: NoticeVote
  // Left out, nothing can be pressed: the form shows the vote this way before it exists.
  onPress?: (choice: NoticeVoteChoice) => void
  onWithdraw?: () => void
  busy?: boolean
}

type Side = {
  choice: NoticeVoteChoice
  label: string
  count: number
  percent: number
  dot: string
  chosen: string
  hover: string
  bar: string
}

/**
 * A vote for or against, read at a glance: two large buttons that say what each side is and how
 * many chose it, green for and red against, and one bar split between them. The reader's side is
 * filled and checked. Choosing works like a pair of radio buttons, so pressing the chosen side
 * again does nothing; taking the vote back is its own, smaller button. Who chose what is not shown.
 */
export function NoticeVoteView({ vote, onPress, onWithdraw, busy = false }: NoticeVoteViewProps) {
  const open = vote.status === 'OPEN'
  const summary = summarizeNoticeVote(vote)
  const canPress = open && Boolean(onPress) && !busy
  const sides: Side[] = [
    {
      choice: 'AGREE',
      label: t('notices.posts.voteAgree'),
      count: vote.agreeCount,
      percent: summary.agreePercent,
      dot: 'bg-emerald-500',
      chosen:
        'cursor-default border-emerald-500 bg-emerald-50 text-emerald-900 dark:border-emerald-500 dark:bg-emerald-950/50 dark:text-emerald-100',
      hover: 'enabled:hover:border-emerald-400 enabled:hover:bg-emerald-50/60 dark:enabled:hover:bg-emerald-950/30',
      bar: 'bg-emerald-500',
    },
    {
      choice: 'DISAGREE',
      label: t('notices.posts.voteDisagree'),
      count: vote.disagreeCount,
      percent: summary.disagreePercent,
      dot: 'bg-rose-500',
      chosen:
        'cursor-default border-rose-500 bg-rose-50 text-rose-900 dark:border-rose-500 dark:bg-rose-950/50 dark:text-rose-100',
      hover: 'enabled:hover:border-rose-400 enabled:hover:bg-rose-50/60 dark:enabled:hover:bg-rose-950/30',
      bar: 'bg-rose-500',
    },
  ]

  return (
    <section
      id={NOTICE_VOTE_ANCHOR}
      aria-label={t('notices.posts.voteTitle')}
      className="scroll-mt-24 space-y-3 rounded-xl border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-900"
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

      <div className="grid grid-cols-2 gap-2">
        {sides.map((side) => {
          const chosen = vote.myChoice === side.choice
          return (
            <button
              key={side.choice}
              type="button"
              // Chosen already: pressing it again changes nothing, as with a radio button.
              onClick={() => (chosen ? undefined : onPress?.(side.choice))}
              disabled={!canPress}
              aria-pressed={chosen}
              className={`min-h-[3.5rem] rounded-lg border-2 px-3 py-2 text-left transition-colors disabled:cursor-default ${
                chosen
                  ? side.chosen
                  : `border-slate-200 bg-white text-slate-800 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 ${side.hover}`
              }`}
            >
              <span className="flex items-center gap-1.5 text-sm font-semibold">
                {chosen ? (
                  <svg viewBox="0 0 24 24" className="h-4 w-4 shrink-0" fill="none" stroke="currentColor" strokeWidth="3" aria-hidden="true">
                    <path d="M5 12.5l4.5 4.5L19 7.5" />
                  </svg>
                ) : (
                  <span className={`h-2.5 w-2.5 shrink-0 rounded-full ${side.dot}`} aria-hidden="true" />
                )}
                {side.label}
                {chosen && <span className="sr-only"> ({t('notices.posts.voteMine')})</span>}
              </span>
              <span className={`mt-0.5 block text-xs ${chosen ? 'opacity-80' : 'text-slate-500 dark:text-slate-400'}`}>
                {summary.total > 0
                  ? t('notices.posts.voteShare', { count: side.count, percent: side.percent })
                  : t('notices.posts.voteNoShare')}
              </span>
            </button>
          )
        })}
      </div>

      {/* One bar for both sides: who is ahead shows without reading a number. */}
      <div className="flex h-2 gap-0.5 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800" aria-hidden="true">
        {summary.total > 0 &&
          sides
            .filter((side) => side.percent > 0)
            .map((side) => <span key={side.choice} className={`h-full ${side.bar}`} style={{ width: `${side.percent}%` }} />)}
      </div>

      <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
        <p className="text-xs text-slate-500 dark:text-slate-400">{t('notices.posts.voteFooter', { total: summary.total })}</p>
        {open && vote.myChoice && onWithdraw && (
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
  // Every vote answers with the notice as it now is.
  onChange: (notice: NoticeDetail) => void
  // The vote was closed while the page was open: the page reads the notice again.
  onClosed: () => void
}

/** The vote on a notice, live: choosing a side casts or changes the reader's vote. */
export function NoticeVotePanel({ groupId, noticeId, vote, onChange, onClosed }: NoticeVotePanelProps) {
  const [busy, setBusy] = useState<boolean>(false)
  const [error, setError] = useState<string | null>(null)

  const send = useCallback(
    async (choice: NoticeVoteChoice | null) => {
      setBusy(true)
      setError(null)
      try {
        onChange(await apiClient.setNoticeVote(groupId, noticeId, choice))
      } catch (caught) {
        if (caught instanceof ApiRequestError && caught.status === 409) {
          setError(t('notices.posts.voteClosedHint'))
          onClosed()
        } else {
          setError(t('notices.loadError'))
        }
      } finally {
        setBusy(false)
      }
    },
    [groupId, noticeId, onChange, onClosed]
  )

  return (
    <div className="space-y-2">
      <NoticeVoteView
        vote={vote}
        onPress={(choice) => void send(choice)}
        onWithdraw={() => void send(null)}
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
