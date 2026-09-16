'use client'

import { FormEvent, useCallback, useEffect, useState } from 'react'
import { apiClient } from '@/lib/api'
import { Alert, AlertContent, AlertDescription, AlertIcon } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { t } from '@/lib/i18n'
import { formatWon } from '@/lib/ledger-dashboard'
import { formatUsd, parseOptionalAmount, summarizeServerCosts } from '@/lib/ledger-server-costs'
import type { LedgerServerCost, LedgerServerCostRequest } from '@/types/api'

type ServerCostFormState = {
  id: number | null
  serviceName: string
  billingMonth: string
  chargedDate: string
  usdAmount: string
  krwAmount: string
  paidBy: string
  reimbursedDate: string
  memo: string
}

type LedgerServerCostsProps = {
  groupId: number
  canManage: boolean
}

const DEFAULT_SERVICE_NAME = 'Render'
const BILLING_MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/
const INPUT_CLASS = 'w-full rounded-md border border-slate-300 px-2 py-1.5 text-sm dark:border-slate-600 dark:bg-slate-900'
const LABEL_CLASS = 'block space-y-1 text-xs font-medium text-slate-600 dark:text-slate-300'

const key = (name: string) => `notices.donations.serverCosts.${name}`

function todayIsoDate(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

function emptyForm(): ServerCostFormState {
  const today = todayIsoDate()
  return {
    id: null,
    serviceName: DEFAULT_SERVICE_NAME,
    billingMonth: today.slice(0, 7),
    chargedDate: today,
    usdAmount: '',
    krwAmount: '',
    paidBy: '',
    reimbursedDate: '',
    memo: '',
  }
}

function toForm(cost: LedgerServerCost): ServerCostFormState {
  return {
    id: cost.id,
    serviceName: cost.serviceName,
    billingMonth: cost.billingMonth,
    chargedDate: cost.chargedDate,
    usdAmount: cost.usdAmount === null ? '' : String(cost.usdAmount),
    krwAmount: cost.krwAmount === null ? '' : String(cost.krwAmount),
    paidBy: cost.paidBy ?? '',
    reimbursedDate: cost.reimbursedDate ?? '',
    memo: cost.memo ?? '',
  }
}

function statusLabel(cost: LedgerServerCost): string {
  if (cost.reimbursedDate) {
    return t(key('statusReimbursed'), { date: cost.reimbursedDate })
  }
  return cost.krwAmount === null ? t(key('statusMissingKrw')) : t(key('statusPending'))
}

function SummaryTile({ label, value, foot }: { label: string; value: string; foot: string }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-3 dark:border-slate-700 dark:bg-slate-900">
      <p className="text-xs text-slate-500 dark:text-slate-400">{label}</p>
      <p className="mt-1 text-lg font-semibold text-slate-900 dark:text-slate-100">{value}</p>
      <p className="text-xs text-slate-500 dark:text-slate-400">{foot}</p>
    </div>
  )
}

export function LedgerServerCosts({ groupId, canManage }: LedgerServerCostsProps) {
  const [costs, setCosts] = useState<LedgerServerCost[]>([])
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)
  const [form, setForm] = useState<ServerCostFormState | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [actionSuccess, setActionSuccess] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setCosts(await apiClient.getLedgerServerCosts(groupId))
    } catch {
      setError(t('notices.loadError'))
    } finally {
      setLoading(false)
    }
  }, [groupId])

  useEffect(() => {
    void load()
  }, [load])

  const handleSubmit = useCallback(
    async (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault()
      if (!form) {
        return
      }
      if (!form.serviceName.trim()) {
        setActionError(t(key('serviceRequired')))
        return
      }
      if (!BILLING_MONTH_PATTERN.test(form.billingMonth)) {
        setActionError(t(key('billingMonthRequired')))
        return
      }
      if (!form.chargedDate) {
        setActionError(t('notices.donations.dateRequired'))
        return
      }
      const usdAmount = parseOptionalAmount(form.usdAmount)
      const krwAmount = parseOptionalAmount(form.krwAmount)
      if (
        usdAmount === 'invalid' ||
        krwAmount === 'invalid' ||
        (krwAmount !== null && (krwAmount <= 0 || !Number.isInteger(krwAmount)))
      ) {
        setActionError(t('notices.donations.amountRequired'))
        return
      }
      if (form.reimbursedDate && krwAmount === null) {
        setActionError(t(key('reimbursedNeedsKrw')))
        return
      }

      setActionError(null)
      const payload: LedgerServerCostRequest = {
        serviceName: form.serviceName.trim(),
        billingMonth: form.billingMonth,
        chargedDate: form.chargedDate,
        usdAmount,
        krwAmount,
        paidBy: form.paidBy.trim() || null,
        reimbursedDate: form.reimbursedDate || null,
        memo: form.memo.trim() || null,
      }
      try {
        if (form.id === null) {
          await apiClient.createLedgerServerCost(groupId, payload)
        } else {
          await apiClient.updateLedgerServerCost(groupId, form.id, payload)
        }
        setForm(null)
        setActionSuccess(t('notices.donations.saveSuccess'))
        await load()
      } catch {
        setActionError(t('notices.loadError'))
      }
    },
    [form, groupId, load]
  )

  const handleDelete = useCallback(
    async (cost: LedgerServerCost) => {
      if (!window.confirm(t('notices.donations.deleteConfirm'))) {
        return
      }
      try {
        await apiClient.deleteLedgerServerCost(groupId, cost.id)
        setActionSuccess(t('notices.donations.deleteSuccess'))
        await load()
      } catch {
        setActionError(t('notices.loadError'))
      }
    },
    [groupId, load]
  )

  const summary = summarizeServerCosts(costs)
  const columnCount = canManage ? 10 : 9

  return (
    <div className="space-y-4">
      <p className="text-xs text-slate-500 dark:text-slate-400">{t(key('hint'))}</p>

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <SummaryTile
          label={t(key('totalKrw'))}
          value={formatWon(summary.totalKrw)}
          foot={t(key('totalUsd'), { amount: formatUsd(summary.totalUsd) })}
        />
        <SummaryTile
          label={t(key('reimbursed'))}
          value={formatWon(summary.reimbursedKrw)}
          foot={t(key('count'), { count: String(summary.reimbursedCount) })}
        />
        <SummaryTile
          label={t(key('pending'))}
          value={formatWon(summary.pendingKrw)}
          foot={t(key('count'), { count: String(summary.pendingCount) })}
        />
        <SummaryTile
          label={t(key('missingKrw'))}
          value={t(key('count'), { count: String(summary.missingKrwCount) })}
          foot={t(key('missingKrwHint'))}
        />
      </div>

      {canManage && (
        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            onClick={() => setForm(emptyForm())}
            className="rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-medium text-white hover:bg-slate-800 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white"
          >
            {t(key('addButton'))}
          </button>
        </div>
      )}

      {(actionError || actionSuccess) && (
        <p
          className={`rounded-lg border px-3 py-2 text-xs ${
            actionError
              ? 'border-rose-200 bg-rose-50 text-rose-700 dark:border-rose-800 dark:bg-rose-950/40 dark:text-rose-300'
              : 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300'
          }`}
        >
          {actionError ?? actionSuccess}
        </p>
      )}

      {canManage && form && (
        <form
          onSubmit={handleSubmit}
          className="space-y-3 rounded-xl border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-800/60"
        >
          <div className="grid gap-3 sm:grid-cols-2">
            <label className={LABEL_CLASS}>
              {t(key('table.service'))}
              <input
                type="text"
                value={form.serviceName}
                placeholder={t(key('placeholders.service'))}
                onChange={(event) => setForm({ ...form, serviceName: event.target.value })}
                className={INPUT_CLASS}
              />
            </label>
            <label className={LABEL_CLASS}>
              {t(key('table.billingMonth'))}
              <input
                type="month"
                value={form.billingMonth}
                onChange={(event) => setForm({ ...form, billingMonth: event.target.value })}
                className={INPUT_CLASS}
              />
            </label>
            <label className={LABEL_CLASS}>
              {t(key('table.chargedDate'))}
              <input
                type="date"
                value={form.chargedDate}
                onChange={(event) => setForm({ ...form, chargedDate: event.target.value })}
                className={INPUT_CLASS}
              />
            </label>
            <label className={LABEL_CLASS}>
              {t(key('table.usdAmount'))}
              <input
                type="number"
                min={0}
                step="0.01"
                value={form.usdAmount}
                onChange={(event) => setForm({ ...form, usdAmount: event.target.value })}
                className={INPUT_CLASS}
              />
            </label>
            <label className={LABEL_CLASS}>
              {t(key('table.krwAmount'))}
              <input
                type="number"
                min={1}
                step="1"
                value={form.krwAmount}
                onChange={(event) => setForm({ ...form, krwAmount: event.target.value })}
                className={INPUT_CLASS}
              />
            </label>
            <label className={LABEL_CLASS}>
              {t(key('table.paidBy'))}
              <input
                type="text"
                value={form.paidBy}
                placeholder={t(key('placeholders.paidBy'))}
                onChange={(event) => setForm({ ...form, paidBy: event.target.value })}
                className={INPUT_CLASS}
              />
            </label>
            <label className={LABEL_CLASS}>
              {t(key('reimbursedDate'))}
              <input
                type="date"
                value={form.reimbursedDate}
                onChange={(event) => setForm({ ...form, reimbursedDate: event.target.value })}
                className={INPUT_CLASS}
              />
            </label>
            <label className={LABEL_CLASS}>
              {t(key('table.memo'))}
              <input
                type="text"
                value={form.memo}
                placeholder={t(key('placeholders.memo'))}
                onChange={(event) => setForm({ ...form, memo: event.target.value })}
                className={INPUT_CLASS}
              />
            </label>
          </div>
          <div className="flex gap-2">
            <button
              type="submit"
              className="rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-medium text-white hover:bg-slate-800 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white"
            >
              {t('notices.donations.save')}
            </button>
            <button
              type="button"
              onClick={() => setForm(null)}
              className="rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
            >
              {t('notices.donations.cancel')}
            </button>
          </div>
        </form>
      )}

      <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-900">
        <table className="min-w-full text-left text-sm">
          <thead className="bg-slate-50 text-xs tracking-wide text-slate-500 dark:bg-slate-800/80 dark:text-slate-300">
            <tr>
              <th className="whitespace-nowrap px-4 py-3">{t(key('table.billingMonth'))}</th>
              <th className="whitespace-nowrap px-4 py-3">{t(key('table.service'))}</th>
              <th className="whitespace-nowrap px-4 py-3">{t(key('table.chargedDate'))}</th>
              <th className="whitespace-nowrap px-4 py-3 text-right">{t(key('table.usdAmount'))}</th>
              <th className="whitespace-nowrap px-4 py-3 text-right">{t(key('table.krwAmount'))}</th>
              <th className="whitespace-nowrap px-4 py-3">{t(key('table.paidBy'))}</th>
              <th className="whitespace-nowrap px-4 py-3">{t(key('table.status'))}</th>
              <th className="whitespace-nowrap px-4 py-3">{t(key('table.memo'))}</th>
              <th className="whitespace-nowrap px-4 py-3">{t(key('table.author'))}</th>
              {canManage && <th className="px-4 py-3" />}
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr>
                <td className="px-4 py-3" colSpan={columnCount}>
                  <LoadingIndicator label={t('common.loading')} />
                </td>
              </tr>
            )}
            {!loading && error && (
              <tr>
                <td className="px-4 py-8 text-center" colSpan={columnCount}>
                  <Alert variant="destructive" appearance="light">
                    <AlertIcon icon="destructive">!</AlertIcon>
                    <AlertContent>
                      <AlertDescription>{error}</AlertDescription>
                    </AlertContent>
                  </Alert>
                </td>
              </tr>
            )}
            {!loading && !error && costs.length === 0 && (
              <tr>
                <td className="px-4 py-8 text-center text-sm text-slate-500 dark:text-slate-400" colSpan={columnCount}>
                  {t(key('empty'))}
                </td>
              </tr>
            )}
            {!loading &&
              !error &&
              costs.map((cost) => (
                <tr key={cost.id} className="border-t border-slate-100 dark:border-slate-800">
                  <td className="whitespace-nowrap px-4 py-3">{cost.billingMonth}</td>
                  <td className="px-4 py-3">{cost.serviceName}</td>
                  <td className="whitespace-nowrap px-4 py-3">{cost.chargedDate}</td>
                  <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">{formatUsd(cost.usdAmount)}</td>
                  <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">
                    {cost.krwAmount === null ? '-' : formatWon(cost.krwAmount)}
                  </td>
                  <td className="px-4 py-3">{cost.paidBy}</td>
                  <td className="whitespace-nowrap px-4 py-3">{statusLabel(cost)}</td>
                  <td className="px-4 py-3">{cost.memo}</td>
                  <td className="px-4 py-3">{cost.authorNickname}</td>
                  {canManage && (
                    <td className="whitespace-nowrap px-4 py-3 text-right">
                      <button
                        type="button"
                        onClick={() => setForm(toForm(cost))}
                        className="mr-2 text-xs font-medium text-slate-600 hover:underline dark:text-slate-300"
                      >
                        {t('notices.donations.editButton')}
                      </button>
                      <button
                        type="button"
                        onClick={() => handleDelete(cost)}
                        className="text-xs font-medium text-rose-600 hover:underline dark:text-rose-400"
                      >
                        {t('notices.donations.deleteButton')}
                      </button>
                    </td>
                  )}
                </tr>
              ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
