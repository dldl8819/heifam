'use client'

import { useCallback, useState } from 'react'
import { ApiRequestError, apiClient } from '@/lib/api'
import { nextNoticeVote, summarizeNoticeVote } from '@/lib/notice-vote'
import { t } from '@/lib/i18n'
import type { NoticeDetail, NoticeVote, NoticeVoteChoice } from '@/types/api'

type NoticeVotePanelProps = {
  groupId: number
  noticeId: number
  vote: NoticeVote
  // Every vote answers with the notice as it now is.
  onChange: (notice: NoticeDetail) => void
  // The vote was closed while the page was open: the page reads the notice again.
  onClosed: () => void
}

/**
 * The vote on a notice: for or against, with how many chose each. Pressing the side already chosen
 * takes the vote back. Who chose what is not shown to anyone.
 */
export function NoticeVotePanel({ groupId, noticeId, vote, onChange, onClosed }: NoticeVotePanelProps) {
  const [busy, setBusy] = useState<boolean>(false)
  const [error, setError] = useState<string | null>(null)
  const open = vote.status === 'OPEN'
  const summary = summarizeNoticeVote(vote)

  const press = useCallback(
    async (pressed: NoticeVoteChoice) => {
      setBusy(true)
      setError(null)
      try {
        onChange(await apiClient.setNoticeVote(groupId, noticeId, nextNoticeVote(vote.myChoice, pressed)))
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
    [groupId, noticeId, onChange, onClosed, vote.myChoice]
  )

  const sideButton = (choice: NoticeVoteChoice, label: string, count: number, chosenClass: string) => {
    const chosen = vote.myChoice === choice
    return (
      <button
        type="button"
        onClick={() => void press(choice)}
        disabled={busy || !open}
        aria-pressed={chosen}
        className={`flex-1 rounded-lg border px-3 py-2 text-sm font-medium disabled:cursor-not-allowed ${
          chosen
            ? chosenClass
            : 'border-slate-300 text-slate-700 hover:bg-slate-50 disabled:opacity-70 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800'
        }`}
      >
        {label} {count}
      </button>
    )
  }

  return (
    <div className="space-y-2 rounded-lg border border-slate-200 bg-slate-50 p-3 dark:border-slate-700 dark:bg-slate-800/60">
      <div className="flex items-center gap-2">
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

      <div className="flex gap-2">
        {sideButton(
          'AGREE',
          t('notices.posts.voteAgree'),
          vote.agreeCount,
          'border-emerald-400 bg-emerald-50 text-emerald-700 dark:border-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300'
        )}
        {sideButton(
          'DISAGREE',
          t('notices.posts.voteDisagree'),
          vote.disagreeCount,
          'border-rose-400 bg-rose-50 text-rose-700 dark:border-rose-700 dark:bg-rose-950/40 dark:text-rose-300'
        )}
      </div>

      {summary.total > 0 ? (
        <>
          <div className="flex h-2 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700" aria-hidden="true">
            <div className="bg-emerald-500" style={{ width: `${summary.agreePercent}%` }} />
            <div className="bg-rose-500" style={{ width: `${summary.disagreePercent}%` }} />
          </div>
          <p className="text-xs text-slate-600 dark:text-slate-300">
            {t('notices.posts.voteSummary', {
              total: summary.total,
              agree: summary.agreePercent,
              disagree: summary.disagreePercent,
            })}
          </p>
        </>
      ) : (
        <p className="text-xs text-slate-500 dark:text-slate-400">{t('notices.posts.voteEmpty')}</p>
      )}

      <p className="text-xs text-slate-500 dark:text-slate-400">
        {open ? t('notices.posts.voteOpenHint') : t('notices.posts.voteClosedHint')}
      </p>

      {error && (
        <p className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700 dark:border-rose-800 dark:bg-rose-950/40 dark:text-rose-300">
          {error}
        </p>
      )}
    </div>
  )
}
