'use client'

import { t } from '@/lib/i18n'
import {
  MAX_ROUND_COUNT,
  canAddSet,
  nextSetSize,
  setLayoutSummary,
  setSizeChoices,
} from '@/lib/captain-draft'

type CaptainDraftRoundSettingsProps = {
  // Players a team will have; null until the attendance is an even eight or more.
  playersPerTeam: number | null
  setSizes: number[]
  roundCount: number
  onSetSizesChange: (sizes: number[]) => void
  onRoundCountChange: (count: number) => void
}

const SELECT_CLASS =
  'w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-slate-400 focus:ring-2 focus:ring-slate-200 disabled:cursor-not-allowed disabled:bg-slate-100 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100 dark:focus:border-slate-500 dark:focus:ring-slate-700 dark:disabled:bg-slate-800'

/**
 * How a draft is played: how many rounds, and the sets of a round as 3:3, 2:2 and 1:1 games in any
 * mix the teams can fill (five a side: 3:3 + 2:2, 3:3 + 1:1 + 1:1, 3:3 + 1:1 with two resting, ...).
 */
export function CaptainDraftRoundSettings({
  playersPerTeam,
  setSizes,
  roundCount,
  onSetSizesChange,
  onRoundCountChange,
}: CaptainDraftRoundSettingsProps) {
  const ready = playersPerTeam !== null && setSizes.length > 0
  const summary = playersPerTeam === null ? null : setLayoutSummary(setSizes, playersPerTeam)

  return (
    <div className="mt-4 space-y-3">
      <h4 className="text-xs font-semibold text-slate-700 dark:text-slate-300">{t('captainDraft.settings.title')}</h4>

      <label className="block text-xs font-medium text-slate-500 dark:text-slate-400">
        {t('captainDraft.settings.roundCount')}
        <select
          value={roundCount}
          onChange={(event) => onRoundCountChange(Number(event.target.value))}
          className={`mt-1 ${SELECT_CLASS}`}
        >
          {Array.from({ length: MAX_ROUND_COUNT }, (_, index) => index + 1).map((count) => (
            <option key={`rounds-${count}`} value={count}>
              {t('captainDraft.settings.roundOption', { count })}
            </option>
          ))}
        </select>
        <span className="mt-1 block font-normal">{t('captainDraft.settings.roundHelper')}</span>
      </label>

      <div className="space-y-1.5">
        <p className="text-xs font-medium text-slate-500 dark:text-slate-400">{t('captainDraft.settings.layout')}</p>
        {!ready || playersPerTeam === null ? (
          <p className="rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-500 dark:bg-slate-800/60 dark:text-slate-400">
            {t('captainDraft.settings.needAttendance')}
          </p>
        ) : (
          <>
            <ol className="space-y-1.5">
              {setSizes.map((size, index) => (
                <li key={`set-${index}`} className="flex items-center gap-2">
                  <span className="w-12 shrink-0 text-xs font-medium text-slate-600 dark:text-slate-300">
                    {t('captainDraft.settings.setLabel', { set: index + 1 })}
                  </span>
                  <select
                    value={size}
                    onChange={(event) =>
                      onSetSizesChange(setSizes.map((current, position) => (position === index ? Number(event.target.value) : current)))
                    }
                    aria-label={t('captainDraft.settings.setLabel', { set: index + 1 })}
                    className={SELECT_CLASS}
                  >
                    {setSizeChoices(setSizes, index, playersPerTeam).map((choice) => (
                      <option key={`size-${index}-${choice}`} value={choice}>
                        {t('captainDraft.settings.sizeOption', { size: choice })}
                      </option>
                    ))}
                  </select>
                  <button
                    type="button"
                    onClick={() => onSetSizesChange(setSizes.filter((_, position) => position !== index))}
                    disabled={setSizes.length <= 1}
                    aria-label={t('captainDraft.settings.removeSet', { set: index + 1 })}
                    title={t('captainDraft.settings.removeSet', { set: index + 1 })}
                    className="shrink-0 rounded px-2 py-1 text-sm text-slate-400 hover:bg-slate-100 hover:text-rose-600 disabled:cursor-not-allowed disabled:opacity-30 dark:hover:bg-slate-800 dark:hover:text-rose-400"
                  >
                    ×
                  </button>
                </li>
              ))}
            </ol>
            <div className="flex items-center justify-between gap-2">
              <span className="text-xs text-slate-500 dark:text-slate-400">
                {summary && summary.resting > 0
                  ? t('captainDraft.settings.summaryResting', { playing: summary.playing, resting: summary.resting })
                  : t('captainDraft.settings.summaryAll', { playing: summary?.playing ?? 0 })}
              </span>
              <button
                type="button"
                onClick={() => onSetSizesChange([...setSizes, nextSetSize(setSizes, playersPerTeam)])}
                disabled={!canAddSet(setSizes, playersPerTeam)}
                className="shrink-0 rounded-md border border-slate-300 px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
              >
                {t('captainDraft.settings.addSet')}
              </button>
            </div>
            <p className="text-xs text-slate-500 dark:text-slate-400">{t('captainDraft.settings.helper')}</p>
          </>
        )}
      </div>
    </div>
  )
}
