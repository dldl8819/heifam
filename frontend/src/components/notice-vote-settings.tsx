'use client'

import {
  NOTICE_VOTE_DEFAULT_OPTIONS,
  NOTICE_VOTE_MAX_OPTIONS,
  NOTICE_VOTE_MIN_OPTIONS,
  NOTICE_VOTE_OPTION_MAX_LENGTH,
} from '@/lib/notice-vote'
import { t } from '@/lib/i18n'

type NoticeVoteSettingsProps = {
  options: string[]
  onOptionsChange: (options: string[]) => void
  // Somebody has voted: the options are what they voted on and can no longer be rewritten here.
  optionsLocked?: boolean
  anonymous: boolean
  onAnonymousChange: (anonymous: boolean) => void
  // An anonymous vote that has votes stays anonymous.
  anonymousLocked?: boolean
  allowAdditions: boolean
  onAllowAdditionsChange: (allowAdditions: boolean) => void
}

const smallButtonClass =
  'rounded-lg border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800'

/**
 * What the writer of a notice sets about its vote: the options to choose between, whether voters
 * may add options of their own, and whether the vote is anonymous. Used when a notice is written
 * and when it is edited.
 */
export function NoticeVoteSettings({
  options,
  onOptionsChange,
  optionsLocked = false,
  anonymous,
  onAnonymousChange,
  anonymousLocked = false,
  allowAdditions,
  onAllowAdditionsChange,
}: NoticeVoteSettingsProps) {
  const setOption = (index: number, value: string) =>
    onOptionsChange(options.map((option, position) => (position === index ? value : option)))

  return (
    <div className="space-y-3 rounded-xl border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-900">
      <div className="space-y-2">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="text-xs font-semibold text-slate-900 dark:text-slate-100">{t('notices.posts.voteOptionsLabel')}</p>
          {!optionsLocked && (
            <button type="button" onClick={() => onOptionsChange([...NOTICE_VOTE_DEFAULT_OPTIONS])} className={smallButtonClass}>
              {t('notices.posts.voteOptionsPreset')}
            </button>
          )}
        </div>
        <ul className="space-y-2">
          {options.map((option, index) => (
            // Rows have no identity of their own: the position is what the writer sees and edits.
            <li key={index} className="flex gap-2">
              <input
                type="text"
                value={option}
                onChange={(event) => setOption(index, event.target.value)}
                maxLength={NOTICE_VOTE_OPTION_MAX_LENGTH}
                disabled={optionsLocked}
                placeholder={t('notices.posts.voteOptionPlaceholder', { number: index + 1 })}
                aria-label={t('notices.posts.voteOptionPlaceholder', { number: index + 1 })}
                className="min-w-0 flex-1 rounded-md border border-slate-300 px-3 py-1.5 text-sm disabled:bg-slate-100 disabled:text-slate-500 dark:border-slate-600 dark:bg-slate-900 dark:disabled:bg-slate-800"
              />
              {!optionsLocked && (
                <button
                  type="button"
                  onClick={() => onOptionsChange(options.filter((_, position) => position !== index))}
                  disabled={options.length <= NOTICE_VOTE_MIN_OPTIONS}
                  className={smallButtonClass}
                >
                  {t('notices.posts.voteOptionRowRemove')}
                </button>
              )}
            </li>
          ))}
        </ul>
        {optionsLocked ? (
          <p className="text-xs text-slate-500 dark:text-slate-400">{t('notices.posts.voteOptionsLockedHint')}</p>
        ) : (
          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={() => onOptionsChange([...options, ''])}
              disabled={options.length >= NOTICE_VOTE_MAX_OPTIONS}
              className={smallButtonClass}
            >
              {t('notices.posts.voteOptionRowAdd')}
            </button>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              {t('notices.posts.voteOptionsHint', { min: NOTICE_VOTE_MIN_OPTIONS, max: NOTICE_VOTE_MAX_OPTIONS })}
            </p>
          </div>
        )}
      </div>

      <div className="space-y-1">
        <label className="flex items-center gap-2 text-xs text-slate-700 dark:text-slate-200">
          <input type="checkbox" checked={allowAdditions} onChange={(event) => onAllowAdditionsChange(event.target.checked)} />
          {t('notices.posts.voteAllowAdditionsLabel')}
        </label>
        <p className="pl-5 text-xs text-slate-500 dark:text-slate-400">{t('notices.posts.voteAllowAdditionsHint')}</p>
      </div>

      <div className="space-y-1">
        <label className="flex items-center gap-2 text-xs text-slate-700 dark:text-slate-200">
          <input
            type="checkbox"
            checked={anonymous}
            disabled={anonymousLocked}
            onChange={(event) => onAnonymousChange(event.target.checked)}
          />
          {t('notices.posts.voteAnonymousLabel')}
        </label>
        <p className="pl-5 text-xs text-slate-500 dark:text-slate-400">
          {t(
            anonymousLocked
              ? 'notices.posts.voteAnonymousLockedHint'
              : anonymous
                ? 'notices.posts.voteAnonymousOnHint'
                : 'notices.posts.voteAnonymousOffHint'
          )}
        </p>
      </div>
    </div>
  )
}
