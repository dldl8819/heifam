'use client'

import { useEffect, useMemo, useState } from 'react'
import { useAdminAuth } from '@/lib/admin-auth'
import { apiClient } from '@/lib/api'
import { Alert, AlertContent, AlertDescription, AlertIcon, AlertTitle } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { t } from '@/lib/i18n'
import { useMmrVisibility } from '@/lib/mmr-visibility'
import type {
  GroupDashboardMyTeammateStat,
  GroupDashboardResponse,
  GroupPlayerTierBoardItem,
  PlayerTierStatus,
} from '@/types/api'

const TEMP_GROUP_ID = 1
const TIER_BOARD_COLUMNS: PlayerTierStatus[] = [
  'S',
  'A+',
  'A',
  'A-',
  'B+',
  'B',
  'B-',
  'C+',
  'C',
  'C-',
  'UNASSIGNED',
]
const TIER_BOARD_MIN_ROWS = 5
const TIER_BOARD_HEADER_CLASS: Record<PlayerTierStatus, string> = {
  S: 'bg-white text-slate-950',
  'A+': 'bg-[#bdd7ee] text-slate-950',
  A: 'bg-[#bdd7ee] text-slate-950',
  'A-': 'bg-[#bdd7ee] text-slate-950',
  'B+': 'bg-[#fff2cc] text-slate-950',
  B: 'bg-[#fff2cc] text-slate-950',
  'B-': 'bg-[#fff2cc] text-slate-950',
  'C+': 'bg-[#c6e0b4] text-slate-950',
  C: 'bg-[#c6e0b4] text-slate-950',
  'C-': 'bg-[#c6e0b4] text-slate-950',
  UNASSIGNED: 'bg-[#f4b183] text-slate-950',
}

function formatWinRate(value: number): string {
  const percentage = value <= 1 ? value * 100 : value
  return `${percentage.toFixed(2)}%`
}

function resolveKstDateParts(value = new Date()): { year: number; month: number; day: number } {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(value)
  return {
    year: Number(parts.find((part) => part.type === 'year')?.value ?? '0'),
    month: Number(parts.find((part) => part.type === 'month')?.value ?? '0'),
    day: Number(parts.find((part) => part.type === 'day')?.value ?? '0'),
  }
}

function resolveTierBoardPeriod(value = new Date()): string {
  const { year, month, day } = resolveKstDateParts(value)
  const lastDayOfMonth = new Date(Date.UTC(year, month, 0)).getUTCDate()
  const useNextMonth = day === lastDayOfMonth
  const targetMonth = useNextMonth ? month + 1 : month
  const targetYear = targetMonth > 12 ? year + 1 : year
  const normalizedMonth = targetMonth > 12 ? 1 : targetMonth
  return `${targetYear}-${String(normalizedMonth).padStart(2, '0')}`
}

function resolveTierBoardLabel(tier: PlayerTierStatus): string {
  return tier === 'UNASSIGNED' ? t('common.tierBoard.unassigned') : tier
}

function buildTierBoardBuckets(
  rows: GroupPlayerTierBoardItem[]
): Record<PlayerTierStatus, GroupPlayerTierBoardItem[]> {
  const buckets = TIER_BOARD_COLUMNS.reduce<Record<PlayerTierStatus, GroupPlayerTierBoardItem[]>>(
    (accumulator, tier) => ({
      ...accumulator,
      [tier]: [],
    }),
    {
      S: [],
      'A+': [],
      A: [],
      'A-': [],
      'B+': [],
      B: [],
      'B-': [],
      'C+': [],
      C: [],
      'C-': [],
      UNASSIGNED: [],
    }
  )

  rows
    .filter((row) => row.active !== false)
    .forEach((row) => {
      const projectedTier = row.liveTier ?? row.tier ?? 'UNASSIGNED'
      buckets[projectedTier].push(row)
    })

  TIER_BOARD_COLUMNS.forEach((tier) => {
    buckets[tier].sort((a, b) => a.nickname.localeCompare(b.nickname, 'ko-KR'))
  })

  return buckets
}

async function loadTierBoardRows(): Promise<GroupPlayerTierBoardItem[]> {
  return apiClient.getGroupPlayerTierBoard(TEMP_GROUP_ID)
}

function TeammateList({
  items,
  emptyText,
  showStreak = false,
}: {
  items: GroupDashboardMyTeammateStat[]
  emptyText: string
  showStreak?: boolean
}) {
  if (items.length === 0) {
    return (
      <p className="rounded-lg border border-dashed border-slate-200 px-3 py-6 text-center text-sm text-slate-500">
        {emptyText}
      </p>
    )
  }

  return (
    <ul className="space-y-2">
      {items.map((item) => (
        <li
          key={`${item.nickname}-${item.games}-${item.currentWinStreak}`}
          className="rounded-lg border border-slate-200 px-3 py-2 text-sm"
        >
          <div className="flex items-center justify-between gap-3">
            <span className="font-semibold text-slate-900">{item.nickname}</span>
            <span className="text-xs font-medium text-slate-500">
              {item.wins}W {item.losses}L
            </span>
          </div>
          <div className="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-xs text-slate-500">
            <span>{t('dashboard.myTeammates.metrics.games', { count: item.games })}</span>
            <span>{t('dashboard.myTeammates.metrics.winRate', { value: formatWinRate(item.winRate) })}</span>
            {showStreak && (
              <span>
                {t('dashboard.myTeammates.metrics.currentWinStreak', {
                  count: item.currentWinStreak,
                })}
              </span>
            )}
          </div>
        </li>
      ))}
    </ul>
  )
}

export default function DashboardPage() {
  const { isAdmin, canViewMmr } = useAdminAuth()
  const { mmrVisible } = useMmrVisibility()
  const showMmr = canViewMmr && mmrVisible
  const [dashboard, setDashboard] = useState<GroupDashboardResponse | null>(null)
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)
  const [tierBoardRows, setTierBoardRows] = useState<GroupPlayerTierBoardItem[]>([])
  const [tierBoardLoading, setTierBoardLoading] = useState<boolean>(false)
  const [tierBoardError, setTierBoardError] = useState<string | null>(null)
  const myRaceSummary = dashboard?.myRaceSummary
  const myGameTypeSummary = dashboard?.myGameTypeSummary
  const myTeammateSummary = dashboard?.myTeammateSummary
  const tierBoardBuckets = useMemo(() => buildTierBoardBuckets(tierBoardRows), [tierBoardRows])
  const tierBoardRowCount = useMemo(
    () => Math.max(
      TIER_BOARD_MIN_ROWS,
      ...TIER_BOARD_COLUMNS.map((tier) => tierBoardBuckets[tier].length)
    ),
    [tierBoardBuckets]
  )
  const tierBoardTotalCount = useMemo(
    () => TIER_BOARD_COLUMNS.reduce((total, tier) => total + tierBoardBuckets[tier].length, 0),
    [tierBoardBuckets]
  )
  const tierBoardPeriod = useMemo(() => resolveTierBoardPeriod(), [])

  useEffect(() => {
    let active = true

    const run = async () => {
      setLoading(true)
      setError(null)

      try {
        const response = await apiClient.getGroupDashboard(TEMP_GROUP_ID)
        if (!active) {
          return
        }
        setDashboard(response)
      } catch {
        if (!active) {
          return
        }
        setDashboard(null)
        setError(t('dashboard.loadError'))
      } finally {
        if (active) {
          setLoading(false)
        }
      }
    }

    void run()

    return () => {
      active = false
    }
  }, [])

  useEffect(() => {
    let active = true

    if (!isAdmin) {
      setTierBoardRows([])
      setTierBoardLoading(false)
      setTierBoardError(null)
      return () => {
        active = false
      }
    }

    const run = async () => {
      setTierBoardLoading(true)
      setTierBoardError(null)

      try {
        const response = await loadTierBoardRows()
        if (!active) {
          return
        }
        setTierBoardRows(response)
      } catch {
        if (!active) {
          return
        }
        setTierBoardRows([])
        setTierBoardError(t('dashboard.tierBoard.loadError'))
      } finally {
        if (active) {
          setTierBoardLoading(false)
        }
      }
    }

    void run()

    return () => {
      active = false
    }
  }, [isAdmin])

  return (
    <section className="space-y-6">
      <header className="space-y-1 rounded-xl border border-slate-200 bg-white px-5 py-4 shadow-sm">
        <h2 className="text-2xl font-semibold tracking-tight">{t('dashboard.title')}</h2>
        <p className="text-sm text-slate-600">{t('dashboard.description')}</p>
      </header>

      {error && (
        <Alert variant="destructive" appearance="light">
          <AlertIcon icon="destructive">!</AlertIcon>
          <AlertContent>
            <AlertTitle>{t('common.errorPrefix')}</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </AlertContent>
        </Alert>
      )}

      {isAdmin && (
        <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          {tierBoardError && (
            <Alert variant="destructive" appearance="light">
              <AlertIcon icon="destructive">!</AlertIcon>
              <AlertContent>
                <AlertTitle>{t('common.errorPrefix')}</AlertTitle>
                <AlertDescription>{tierBoardError}</AlertDescription>
              </AlertContent>
            </Alert>
          )}

          {tierBoardLoading ? (
            <div className="mt-4 rounded-lg border border-dashed border-slate-200 px-4 py-10">
              <LoadingIndicator label={t('common.loading')} />
            </div>
          ) : (
            <div className="mt-4 overflow-x-auto rounded-lg border border-[#7b6d5b] bg-[#ded8cc] p-3">
              <div className="min-w-[980px]">
                <div className="border-y-2 border-[#4f4636] py-4 text-center">
                  <h4 className="text-4xl font-semibold tracking-normal text-[#4f4636]">
                    {t('common.tierBoard.title')}
                  </h4>
                </div>

                <table className="mt-4 w-full border-collapse bg-white text-center text-sm">
                  <thead>
                    <tr>
                      <th className="w-12 border border-slate-900 bg-white px-2 py-3 text-base font-semibold">
                        {t('common.tierBoard.index')}
                      </th>
                      {TIER_BOARD_COLUMNS.map((tier) => (
                        <th
                          key={`tier-board-head-${tier}`}
                          className={`min-w-20 border border-slate-900 px-3 py-3 text-xl font-bold ${TIER_BOARD_HEADER_CLASS[tier]}`}
                        >
                          {resolveTierBoardLabel(tier)}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {Array.from({ length: tierBoardRowCount }, (_, rowIndex) => (
                      <tr key={`tier-board-row-${rowIndex}`}>
                        <th className="border border-slate-900 bg-white px-2 py-2 font-normal text-slate-900">
                          {rowIndex + 1}
                        </th>
                        {TIER_BOARD_COLUMNS.map((tier) => {
                          const player = tierBoardBuckets[tier][rowIndex] ?? null
                          return (
                            <td
                              key={`tier-board-cell-${tier}-${rowIndex}`}
                              className="h-9 border border-dotted border-slate-500 bg-white px-2 py-1 align-middle font-semibold text-slate-950"
                            >
                              {player ? (
                                <span>{player.nickname}</span>
                              ) : ''}
                            </td>
                          )
                        })}
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr>
                      <td
                        className="border border-slate-900 bg-white px-2 py-2 text-right text-xs font-semibold text-slate-900"
                        colSpan={TIER_BOARD_COLUMNS.length}
                      >
                        {t('dashboard.tierBoard.period', { period: tierBoardPeriod })}
                      </td>
                      <td className="border border-slate-900 bg-[#c6e0b4] px-2 py-2 text-xs font-semibold text-slate-900">
                        {t('dashboard.tierBoard.total', { count: tierBoardTotalCount })}
                      </td>
                    </tr>
                  </tfoot>
                </table>
              </div>
            </div>
          )}
        </section>
      )}

      <section className={`grid gap-4 sm:grid-cols-2 ${showMmr ? 'xl:grid-cols-4' : 'xl:grid-cols-2'}`}>
        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs tracking-wide text-slate-500">{t('dashboard.kpi.totalPlayers')}</p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">
            {loading ? '...' : dashboard?.kpiSummary.totalPlayers ?? 0}
          </p>
        </article>

        {showMmr && (
          <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <p className="text-xs tracking-wide text-slate-500">{t('dashboard.kpi.topMmr')}</p>
            <p className="mt-2 text-2xl font-semibold text-slate-900">
              {loading ? '...' : dashboard?.kpiSummary.topMmr ?? 0}
            </p>
          </article>
        )}

        {showMmr && (
          <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <p className="text-xs tracking-wide text-slate-500">{t('dashboard.kpi.averageMmr')}</p>
            <p className="mt-2 text-2xl font-semibold text-slate-900">
              {loading ? '...' : dashboard?.kpiSummary.averageMmr ?? 0}
            </p>
          </article>
        )}

        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs tracking-wide text-slate-500">{t('dashboard.kpi.totalGames')}</p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">
            {loading ? '...' : dashboard?.kpiSummary.totalGames ?? 0}
          </p>
        </article>
      </section>

      <section className="grid gap-4 xl:grid-cols-3">
        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm xl:col-span-2">
          <div className="flex flex-col gap-1 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <h3 className="text-sm font-semibold text-slate-900">{t('dashboard.myTeammates.title')}</h3>
              <p className="mt-1 text-xs text-slate-500">{t('dashboard.myTeammates.description')}</p>
            </div>
            <span className="rounded-md border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs font-semibold text-slate-600">
              {t('dashboard.myTeammates.minGames', {
                count: myTeammateSummary?.minGames ?? 10,
              })}
            </span>
          </div>

          {loading && (
            <div className="mt-4">
              <LoadingIndicator label={t('common.loading')} />
            </div>
          )}

          {!loading && dashboard && !myTeammateSummary?.linked && (
            <div className="mt-4 rounded-lg border border-dashed border-slate-200 px-3 py-8 text-center text-sm text-slate-500">
              {t('dashboard.myTeammates.notLinked')}
            </div>
          )}

          {!loading && myTeammateSummary?.linked && (
            <div className="mt-4 grid gap-4 lg:grid-cols-3">
              <section className="space-y-3">
                <h4 className="text-sm font-semibold text-slate-900">
                  {t('dashboard.myTeammates.bestDuos.title')}
                </h4>
                <TeammateList
                  items={myTeammateSummary.bestDuos}
                  emptyText={t('dashboard.myTeammates.bestDuos.empty')}
                />
              </section>

              <section className="space-y-3">
                <h4 className="text-sm font-semibold text-slate-900">
                  {t('dashboard.myTeammates.frequentTeammates.title')}
                </h4>
                <TeammateList
                  items={myTeammateSummary.frequentTeammates}
                  emptyText={t('dashboard.myTeammates.frequentTeammates.empty')}
                />
              </section>

              <section className="space-y-3">
                <h4 className="text-sm font-semibold text-slate-900">
                  {t('dashboard.myTeammates.streakPartners.title')}
                </h4>
                <TeammateList
                  items={myTeammateSummary.streakPartners}
                  emptyText={t('dashboard.myTeammates.streakPartners.empty')}
                  showStreak
                />
              </section>
            </div>
          )}
        </article>

        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <h3 className="text-sm font-semibold text-slate-900">{t('dashboard.myRace.title')}</h3>
          <p className="mt-1 text-xs text-slate-500">{t('dashboard.myRace.description')}</p>

          {loading && (
            <div className="mt-3">
              <LoadingIndicator label={t('common.loading')} />
            </div>
          )}

          {!loading && dashboard && !myRaceSummary?.linked && (
            <div className="mt-3 rounded-lg border border-dashed border-slate-200 px-3 py-3 text-sm text-slate-500">
              {t('dashboard.myRace.notLinked')}
            </div>
          )}

          {!loading && myRaceSummary && myRaceSummary.linked && (
            <>
              <div className="mt-3 rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700">
                {t('dashboard.myRace.total')}:&nbsp;
                <span className="font-semibold text-slate-900">
                  {myRaceSummary.wins}W {myRaceSummary.losses}L ({myRaceSummary.games})
                </span>
                &nbsp;·&nbsp;
                <span className="font-semibold text-slate-900">
                  {formatWinRate(myRaceSummary.winRate)}
                </span>
              </div>

              <div className="mt-3 overflow-x-auto rounded-lg border border-slate-200">
                <table className="min-w-full text-left text-sm">
                  <thead className="bg-slate-50 text-xs tracking-wide text-slate-500">
                    <tr>
                      <th className="px-3 py-2">{t('dashboard.myRace.headers.race')}</th>
                      <th className="px-3 py-2">{t('dashboard.myRace.headers.wins')}</th>
                      <th className="px-3 py-2">{t('dashboard.myRace.headers.losses')}</th>
                      <th className="px-3 py-2">{t('dashboard.myRace.headers.games')}</th>
                      <th className="px-3 py-2">{t('dashboard.myRace.headers.winRate')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {myRaceSummary.byRace.map((item) => (
                      <tr key={`my-race-${item.race}`} className="border-t border-slate-100">
                        <td className="px-3 py-2 font-medium text-slate-900">{item.race}</td>
                        <td className="px-3 py-2 text-slate-700">{item.wins}</td>
                        <td className="px-3 py-2 text-slate-700">{item.losses}</td>
                        <td className="px-3 py-2 text-slate-700">{item.games}</td>
                        <td className="px-3 py-2 text-slate-700">{formatWinRate(item.winRate)}</td>
                      </tr>
                    ))}
                    {myRaceSummary.byRace.length === 0 && (
                      <tr className="border-t border-slate-100">
                        <td className="px-3 py-4 text-center text-sm text-slate-500" colSpan={5}>
                          {t('dashboard.myRace.empty')}
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </>
          )}

          <div className="mt-4 border-t border-slate-200 pt-4">
            <h4 className="text-sm font-semibold text-slate-900">{t('dashboard.myGameType.title')}</h4>
            <p className="mt-1 text-xs text-slate-500">{t('dashboard.myGameType.description')}</p>

            {!loading && dashboard && !myGameTypeSummary?.linked && (
              <div className="mt-3 rounded-lg border border-dashed border-slate-200 px-3 py-3 text-sm text-slate-500">
                {t('dashboard.myGameType.notLinked')}
              </div>
            )}

            {!loading && myGameTypeSummary && myGameTypeSummary.linked && (
              <>
                <div className="mt-3 rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700">
                  {t('dashboard.myGameType.total')}:&nbsp;
                  <span className="font-semibold text-slate-900">
                    {myGameTypeSummary.wins}W {myGameTypeSummary.losses}L ({myGameTypeSummary.games})
                  </span>
                  &nbsp;·&nbsp;
                  <span className="font-semibold text-slate-900">
                    {formatWinRate(myGameTypeSummary.winRate)}
                  </span>
                </div>

                <div className="mt-3 overflow-x-auto rounded-lg border border-slate-200">
                  <table className="min-w-full text-left text-sm">
                    <thead className="bg-slate-50 text-xs tracking-wide text-slate-500">
                      <tr>
                        <th className="px-3 py-2">{t('dashboard.myGameType.headers.gameType')}</th>
                        <th className="px-3 py-2">{t('dashboard.myGameType.headers.wins')}</th>
                        <th className="px-3 py-2">{t('dashboard.myGameType.headers.losses')}</th>
                        <th className="px-3 py-2">{t('dashboard.myGameType.headers.games')}</th>
                        <th className="px-3 py-2">{t('dashboard.myGameType.headers.winRate')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {myGameTypeSummary.byGameType.map((item) => (
                        <tr key={`my-game-type-${item.gameType}`} className="border-t border-slate-100">
                          <td className="px-3 py-2 font-medium text-slate-900">{item.gameType}</td>
                          <td className="px-3 py-2 text-slate-700">{item.wins}</td>
                          <td className="px-3 py-2 text-slate-700">{item.losses}</td>
                          <td className="px-3 py-2 text-slate-700">{item.games}</td>
                          <td className="px-3 py-2 text-slate-700">{formatWinRate(item.winRate)}</td>
                        </tr>
                      ))}
                      {myGameTypeSummary.byGameType.length === 0 && (
                        <tr className="border-t border-slate-100">
                          <td className="px-3 py-4 text-center text-sm text-slate-500" colSpan={5}>
                            {t('dashboard.myGameType.empty')}
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
              </>
            )}
          </div>

          <div className="mt-4 border-t border-slate-200 pt-4">
            <h4 className="text-sm font-semibold text-slate-900">{t('dashboard.notes.title')}</h4>
            <ul className="mt-3 space-y-2 text-sm text-slate-600">
              <li>{t('dashboard.notes.items.one')}</li>
              <li>{t('dashboard.notes.items.two')}</li>
            </ul>
          </div>
        </article>
      </section>
    </section>
  )
}
