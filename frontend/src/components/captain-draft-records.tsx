'use client'

import { t } from '@/lib/i18n'
import { draftRecordState, type DraftRecordState } from '@/lib/captain-draft'
import { formatKstDateTime } from '@/lib/kst-time'
import type { CaptainDraftSummary } from '@/types/api'

type CaptainDraftRecordsProps = {
  records: CaptainDraftSummary[] | null
  loading: boolean
  error: string | null
  currentDraftId: number | null
  // The draft being removed, while its request is out; every delete button waits for it.
  deletingDraftId: number | null
  onOpen: (draftId: number) => void
  onDelete: (record: CaptainDraftSummary) => void
}

const STATE_STYLES: Record<DraftRecordState, string> = {
  drafting: 'bg-amber-100 text-amber-800 dark:bg-amber-900/50 dark:text-amber-200',
  playing: 'bg-sky-100 text-sky-800 dark:bg-sky-900/50 dark:text-sky-200',
  finished: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/50 dark:text-emerald-200',
}

/**
 * The regular drafts played so far, newest first, with each one's score. Their results are kept
 * here only: a regular draft is not balanced, so it never moves anyone's MMR. A draft made by
 * mistake or to try the page out can be removed, with everything in it.
 */
export function CaptainDraftRecords({
  records,
  loading,
  error,
  currentDraftId,
  deletingDraftId,
  onOpen,
  onDelete,
}: CaptainDraftRecordsProps) {
  return (
    <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-xs dark:border-slate-700 dark:bg-slate-900">
      <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('captainDraft.records.title')}</h3>
      <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{t('captainDraft.records.description')}</p>

      {error && <p className="mt-3 text-sm text-rose-600 dark:text-rose-400">{error}</p>}
      {loading && !records && <p className="mt-3 text-sm text-slate-500 dark:text-slate-400">{t('common.loading')}</p>}
      {records && records.length === 0 && (
        <p className="mt-3 rounded-lg border border-dashed border-slate-200 px-3 py-6 text-center text-xs text-slate-500 dark:border-slate-700 dark:text-slate-400">
          {t('captainDraft.records.empty')}
        </p>
      )}
      {records && records.length > 0 && (
        <ul className="mt-3 divide-y divide-slate-100 dark:divide-slate-800">
          {records.map((record) => {
            const state = draftRecordState(record)
            const current = record.draftId === currentDraftId
            return (
              <li key={record.draftId} className="flex flex-wrap items-center gap-x-4 gap-y-1 py-2">
                <div className="min-w-0 flex-1">
                  <p className="flex flex-wrap items-center gap-2 text-sm font-medium text-slate-900 dark:text-slate-100">
                    <span className="truncate">{record.title}</span>
                    <span className={`rounded-sm px-1.5 text-[11px] font-semibold ${STATE_STYLES[state]}`}>
                      {state === 'playing'
                        ? t('captainDraft.records.state.playing', { decided: record.decidedSets, total: record.totalSets })
                        : t(`captainDraft.records.state.${state}`)}
                    </span>
                  </p>
                  <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
                    {t('captainDraft.records.meta', {
                      date: formatKstDateTime(record.createdAt),
                      home: record.homeCaptainNickname,
                      away: record.awayCaptainNickname,
                      count: record.participantCount,
                    })}
                  </p>
                </div>
                <p className="text-sm text-slate-700 dark:text-slate-200">
                  <span className="text-sky-700 dark:text-sky-300">{t('captainDraft.scoreboard.homeHeader')}</span>{' '}
                  <span className="font-semibold">{t('captainDraft.records.rounds', { home: record.homeRoundWins, away: record.awayRoundWins })}</span>{' '}
                  <span className="text-rose-700 dark:text-rose-300">{t('captainDraft.scoreboard.awayHeader')}</span>
                  <span className="ml-2 text-xs text-slate-500 dark:text-slate-400">
                    {t('captainDraft.records.sets', { home: record.homeSetWins, away: record.awaySetWins })}
                  </span>
                </p>
                <button
                  type="button"
                  onClick={() => onOpen(record.draftId)}
                  disabled={current}
                  className="shrink-0 rounded-md border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-default disabled:border-transparent disabled:bg-slate-100 disabled:text-slate-500 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800 dark:disabled:bg-slate-800"
                >
                  {current ? t('captainDraft.records.viewing') : t('captainDraft.records.open')}
                </button>
                <button
                  type="button"
                  onClick={() => onDelete(record)}
                  disabled={deletingDraftId !== null}
                  aria-label={t('captainDraft.records.deleteLabel', { title: record.title })}
                  className="shrink-0 rounded-md border border-rose-300 px-2.5 py-1 text-xs font-medium text-rose-700 hover:bg-rose-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-rose-800 dark:text-rose-300 dark:hover:bg-rose-950/40"
                >
                  {deletingDraftId === record.draftId ? t('captainDraft.records.deleting') : t('captainDraft.records.delete')}
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </article>
  )
}
