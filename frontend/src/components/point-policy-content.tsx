import { POINT_CARD_CLASS } from '@/components/point-page-parts'
import { t } from '@/lib/i18n'
import { formatPolicyPoints, pointPolicyGroups } from '@/lib/point-policy'
import type { PointPolicyResponse } from '@/types/api'

const RULE_KEYS = ['one', 'two', 'three', 'four', 'five'] as const

function RuleList({ section }: { section: 'corrections' | 'basics' }) {
  return (
    <div className={`${POINT_CARD_CLASS} space-y-2`}>
      <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t(`points.policy.${section}.title`)}</h2>
      <ul className="list-disc space-y-1 pl-4 text-sm text-slate-600 dark:text-slate-300">
        {RULE_KEYS.map((key) => (
          <li key={key}>{t(`points.policy.${section}.${key}`)}</li>
        ))}
      </ul>
    </div>
  )
}

/** The policy page's body: the ways to earn once the amounts are in, and the rules that hold anyway. */
export function PointPolicyContent({ policy }: { policy: PointPolicyResponse | null }) {
  return (
    <>
      {policy && (
        <div className="space-y-3">
          <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('points.policy.earnTitle')}</h2>
          <div className="grid gap-4 lg:grid-cols-2">
            {pointPolicyGroups(policy).map((group) => (
              <div key={group.title} className={POINT_CARD_CLASS}>
                <h3 className="text-xs font-semibold text-slate-700 dark:text-slate-200">{group.title}</h3>
                <ul className="mt-1 divide-y divide-slate-100 dark:divide-slate-800">
                  {group.rows.map((row) => (
                    <li key={row.name} className="flex items-start justify-between gap-3 py-2">
                      <div className="min-w-0">
                        <p className="text-sm font-medium text-slate-800 dark:text-slate-100">{row.name}</p>
                        {row.detail && <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">{row.detail}</p>}
                      </div>
                      <div className="shrink-0 text-right">
                        <p
                          className={`text-sm font-semibold ${
                            row.points > 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-slate-400 dark:text-slate-500'
                          }`}
                        >
                          {formatPolicyPoints(row.points)}
                        </p>
                        <p className="text-xs text-slate-500 dark:text-slate-400">{row.limit}</p>
                      </div>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
          <p className="text-xs text-slate-500 dark:text-slate-400">{t('points.policy.noPoints')}</p>
        </div>
      )}

      <div className="grid gap-4 lg:grid-cols-2">
        <RuleList section="corrections" />
        <RuleList section="basics" />
      </div>
    </>
  )
}
