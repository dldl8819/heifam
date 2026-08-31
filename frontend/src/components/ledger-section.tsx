'use client'

import { ChangeEvent, FormEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useAdminAuth } from '@/lib/admin-auth'
import { apiClient } from '@/lib/api'
import { Alert, AlertContent, AlertDescription, AlertIcon } from '@/components/ui/alert'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { t } from '@/lib/i18n'
import { escapeCsvCell, triggerBlobDownload } from '@/lib/csv-download'
import { normalizeLedgerTargetInput } from '@/lib/ledger-target'
import type {
  LedgerExpenseEntry,
  LedgerExpenseType,
  LedgerIncomeEntry,
  LedgerMonthlySummaryItem,
  PlayerRosterItem,
} from '@/types/api'

const TEMP_GROUP_ID = 1

type LedgerSubTab = 'income' | 'expense' | 'summary'

function todayIsoDate(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

function fileDateSuffix(): string {
  const now = new Date()
  return `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}`
}

function formatAmount(amount: number): string {
  return amount.toLocaleString('ko-KR')
}

type IncomeFormState = {
  id: number | null
  entryDate: string
  category: string
  amount: string
  memo: string
}

function emptyIncomeForm(): IncomeFormState {
  return { id: null, entryDate: todayIsoDate(), category: '', amount: '', memo: '' }
}

type ExpenseFormState = {
  id: number | null
  entryDate: string
  expenseType: LedgerExpenseType
  category: string
  target: string
  amount: string
  memo: string
}

function emptyExpenseForm(expenseType: LedgerExpenseType): ExpenseFormState {
  return { id: null, entryDate: todayIsoDate(), expenseType, category: '', target: '', amount: '', memo: '' }
}

export function LedgerSection() {
  const { isAdmin } = useAdminAuth()
  const [subTab, setSubTab] = useState<LedgerSubTab>('income')

  const [roster, setRoster] = useState<PlayerRosterItem[]>([])

  const [incomeEntries, setIncomeEntries] = useState<LedgerIncomeEntry[]>([])
  const [incomeCategories, setIncomeCategories] = useState<string[]>([])
  const [incomeLoading, setIncomeLoading] = useState<boolean>(true)
  const [incomeError, setIncomeError] = useState<string | null>(null)
  const [incomeForm, setIncomeForm] = useState<IncomeFormState | null>(null)
  const [incomeActionError, setIncomeActionError] = useState<string | null>(null)
  const [incomeActionSuccess, setIncomeActionSuccess] = useState<string | null>(null)
  const [incomeUploading, setIncomeUploading] = useState<boolean>(false)
  const incomeFileInputRef = useRef<HTMLInputElement | null>(null)

  const [expenseTypeFilter, setExpenseTypeFilter] = useState<LedgerExpenseType | 'ALL'>('ALL')
  const [expenseEntries, setExpenseEntries] = useState<LedgerExpenseEntry[]>([])
  const [expenseCategories, setExpenseCategories] = useState<string[]>([])
  const [expenseLoading, setExpenseLoading] = useState<boolean>(true)
  const [expenseError, setExpenseError] = useState<string | null>(null)
  const [expenseForm, setExpenseForm] = useState<ExpenseFormState | null>(null)
  const [expenseActionError, setExpenseActionError] = useState<string | null>(null)
  const [expenseActionSuccess, setExpenseActionSuccess] = useState<string | null>(null)
  const [expenseUploadType, setExpenseUploadType] = useState<LedgerExpenseType>('FIXED')
  const [expenseUploading, setExpenseUploading] = useState<boolean>(false)
  const expenseFileInputRef = useRef<HTMLInputElement | null>(null)

  const [summaryYear, setSummaryYear] = useState<number>(new Date().getFullYear())
  const [summaryMonths, setSummaryMonths] = useState<LedgerMonthlySummaryItem[]>([])
  const [summaryLoading, setSummaryLoading] = useState<boolean>(true)
  const [summaryError, setSummaryError] = useState<string | null>(null)

  const loadIncome = useCallback(async () => {
    setIncomeLoading(true)
    setIncomeError(null)
    try {
      const [entries, categories] = await Promise.all([
        apiClient.getLedgerIncome(TEMP_GROUP_ID),
        apiClient.getLedgerIncomeCategories(TEMP_GROUP_ID).catch(() => ({ categories: [] })),
      ])
      setIncomeEntries(entries)
      setIncomeCategories(categories.categories)
    } catch {
      setIncomeError(t('notices.loadError'))
    } finally {
      setIncomeLoading(false)
    }
  }, [])

  const loadExpense = useCallback(async (typeFilter: LedgerExpenseType | 'ALL') => {
    setExpenseLoading(true)
    setExpenseError(null)
    try {
      const [entries, categories] = await Promise.all([
        apiClient.getLedgerExpense(TEMP_GROUP_ID, typeFilter === 'ALL' ? undefined : typeFilter),
        apiClient
          .getLedgerExpenseCategories(TEMP_GROUP_ID, typeFilter === 'ALL' ? 'FIXED' : typeFilter)
          .catch(() => ({ categories: [] })),
      ])
      setExpenseEntries(entries)
      setExpenseCategories(categories.categories)
    } catch {
      setExpenseError(t('notices.loadError'))
    } finally {
      setExpenseLoading(false)
    }
  }, [])

  const loadSummary = useCallback(async (year: number) => {
    setSummaryLoading(true)
    setSummaryError(null)
    try {
      const response = await apiClient.getLedgerSummary(TEMP_GROUP_ID, year)
      setSummaryMonths(response.months)
    } catch {
      setSummaryError(t('notices.loadError'))
    } finally {
      setSummaryLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadIncome()
  }, [loadIncome])

  useEffect(() => {
    void loadExpense(expenseTypeFilter)
  }, [loadExpense, expenseTypeFilter])

  useEffect(() => {
    void loadSummary(summaryYear)
  }, [loadSummary, summaryYear])

  useEffect(() => {
    if (!isAdmin) {
      return
    }
    let active = true
    apiClient
      .getGroupPlayers(TEMP_GROUP_ID)
      .then((players) => {
        if (active) {
          setRoster(players)
        }
      })
      .catch(() => undefined)
    return () => {
      active = false
    }
  }, [isAdmin])

  const handleIncomeSubmit = useCallback(
    async (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault()
      if (!incomeForm) {
        return
      }

      const amount = Number(incomeForm.amount)
      if (!incomeForm.entryDate) {
        setIncomeActionError(t('notices.donations.dateRequired'))
        return
      }
      if (!incomeForm.category.trim()) {
        setIncomeActionError(t('notices.donations.categoryRequired'))
        return
      }
      if (!Number.isFinite(amount) || amount <= 0) {
        setIncomeActionError(t('notices.donations.amountRequired'))
        return
      }

      setIncomeActionError(null)
      const payload = {
        entryDate: incomeForm.entryDate,
        category: incomeForm.category.trim(),
        amount,
        memo: incomeForm.memo.trim() || undefined,
      }

      try {
        if (incomeForm.id === null) {
          await apiClient.createLedgerIncomeEntry(TEMP_GROUP_ID, payload)
        } else {
          await apiClient.updateLedgerIncomeEntry(TEMP_GROUP_ID, incomeForm.id, payload)
        }
        setIncomeForm(null)
        setIncomeActionSuccess(t('notices.donations.saveSuccess'))
        await loadIncome()
      } catch {
        setIncomeActionError(t('notices.loadError'))
      }
    },
    [incomeForm, loadIncome]
  )

  const handleIncomeDelete = useCallback(
    async (entry: LedgerIncomeEntry) => {
      if (!window.confirm(t('notices.donations.deleteConfirm'))) {
        return
      }
      try {
        await apiClient.deleteLedgerIncomeEntry(TEMP_GROUP_ID, entry.id)
        setIncomeActionSuccess(t('notices.donations.deleteSuccess'))
        await loadIncome()
      } catch {
        setIncomeActionError(t('notices.loadError'))
      }
    },
    [loadIncome]
  )

  const handleIncomeFileSelected = useCallback(
    async (event: ChangeEvent<HTMLInputElement>) => {
      const file = event.target.files?.[0]
      event.target.value = ''
      if (!file) {
        return
      }
      setIncomeUploading(true)
      setIncomeActionError(null)
      try {
        const csvContent = await file.text()
        const response = await apiClient.importLedgerIncomeEntries(TEMP_GROUP_ID, csvContent)
        setIncomeActionSuccess(
          `${t('notices.donations.importSuccess', { count: response.importedCount })}${
            response.skippedRows.length > 0
              ? ` ${t('notices.donations.importSkipped', { count: response.skippedRows.length })}`
              : ''
          }`
        )
        await loadIncome()
      } catch {
        setIncomeActionError(t('notices.donations.importError'))
      } finally {
        setIncomeUploading(false)
      }
    },
    [loadIncome]
  )

  const handleIncomeDownload = useCallback(() => {
    const header = [
      t('notices.donations.income.table.date'),
      t('notices.donations.income.table.category'),
      t('notices.donations.income.table.amount'),
      t('notices.donations.income.table.memo'),
      t('notices.donations.income.table.author'),
    ]
    const lines = [
      header.map(escapeCsvCell).join(','),
      ...incomeEntries.map((entry) =>
        [entry.entryDate, entry.category, String(entry.amount), entry.memo ?? '', entry.authorNickname ?? '']
          .map(escapeCsvCell)
          .join(',')
      ),
    ]
    const blob = new Blob([`\uFEFF${lines.join('\n')}`], { type: 'text/csv;charset=utf-8;' })
    triggerBlobDownload(blob, `heifam-income-${fileDateSuffix()}.csv`)
  }, [incomeEntries])

  const handleExpenseSubmit = useCallback(
    async (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault()
      if (!expenseForm) {
        return
      }

      const amount = Number(expenseForm.amount)
      if (!expenseForm.entryDate) {
        setExpenseActionError(t('notices.donations.dateRequired'))
        return
      }
      if (!expenseForm.category.trim()) {
        setExpenseActionError(t('notices.donations.categoryRequired'))
        return
      }
      if (!Number.isFinite(amount) || amount <= 0) {
        setExpenseActionError(t('notices.donations.amountRequired'))
        return
      }

      setExpenseActionError(null)
      const payload = {
        entryDate: expenseForm.entryDate,
        expenseType: expenseForm.expenseType,
        category: expenseForm.category.trim(),
        target: normalizeLedgerTargetInput(expenseForm.target, roster) || undefined,
        amount,
        memo: expenseForm.memo.trim() || undefined,
      }

      try {
        if (expenseForm.id === null) {
          await apiClient.createLedgerExpenseEntry(TEMP_GROUP_ID, payload)
        } else {
          await apiClient.updateLedgerExpenseEntry(TEMP_GROUP_ID, expenseForm.id, payload)
        }
        setExpenseForm(null)
        setExpenseActionSuccess(t('notices.donations.saveSuccess'))
        await loadExpense(expenseTypeFilter)
      } catch {
        setExpenseActionError(t('notices.loadError'))
      }
    },
    [expenseForm, expenseTypeFilter, loadExpense, roster]
  )

  const handleExpenseDelete = useCallback(
    async (entry: LedgerExpenseEntry) => {
      if (!window.confirm(t('notices.donations.deleteConfirm'))) {
        return
      }
      try {
        await apiClient.deleteLedgerExpenseEntry(TEMP_GROUP_ID, entry.id)
        setExpenseActionSuccess(t('notices.donations.deleteSuccess'))
        await loadExpense(expenseTypeFilter)
      } catch {
        setExpenseActionError(t('notices.loadError'))
      }
    },
    [expenseTypeFilter, loadExpense]
  )

  const handleExpenseFileSelected = useCallback(
    async (event: ChangeEvent<HTMLInputElement>) => {
      const file = event.target.files?.[0]
      event.target.value = ''
      if (!file) {
        return
      }
      setExpenseUploading(true)
      setExpenseActionError(null)
      try {
        const csvContent = await file.text()
        const response = await apiClient.importLedgerExpenseEntries(TEMP_GROUP_ID, csvContent, expenseUploadType)
        setExpenseActionSuccess(
          `${t('notices.donations.importSuccess', { count: response.importedCount })}${
            response.skippedRows.length > 0
              ? ` ${t('notices.donations.importSkipped', { count: response.skippedRows.length })}`
              : ''
          }`
        )
        await loadExpense(expenseTypeFilter)
      } catch {
        setExpenseActionError(t('notices.donations.importError'))
      } finally {
        setExpenseUploading(false)
      }
    },
    [expenseTypeFilter, expenseUploadType, loadExpense]
  )

  const handleExpenseDownload = useCallback(() => {
    const header = [
      t('notices.donations.expense.table.date'),
      t('notices.donations.expense.table.type'),
      t('notices.donations.expense.table.category'),
      t('notices.donations.expense.table.target'),
      t('notices.donations.expense.table.amount'),
      t('notices.donations.expense.table.memo'),
      t('notices.donations.expense.table.author'),
    ]
    const lines = [
      header.map(escapeCsvCell).join(','),
      ...expenseEntries.map((entry) =>
        [
          entry.entryDate,
          entry.expenseType === 'FIXED'
            ? t('notices.donations.expense.typeFixed')
            : t('notices.donations.expense.typeVariable'),
          entry.category,
          entry.target ?? '',
          String(entry.amount),
          entry.memo ?? '',
          entry.authorNickname ?? '',
        ]
          .map(escapeCsvCell)
          .join(',')
      ),
    ]
    const blob = new Blob([`\uFEFF${lines.join('\n')}`], { type: 'text/csv;charset=utf-8;' })
    triggerBlobDownload(blob, `heifam-expense-${fileDateSuffix()}.csv`)
  }, [expenseEntries])

  const rosterNicknames = useMemo(() => roster.map((player) => player.nickname), [roster])
  const yearOptions = useMemo(() => {
    const currentYear = new Date().getFullYear()
    return Array.from({ length: 6 }, (_, index) => currentYear - index)
  }, [])

  return (
    <div className="space-y-6">
      <div className="flex gap-2 border-b border-slate-200 dark:border-slate-700">
        {(['income', 'expense', 'summary'] as LedgerSubTab[]).map((tab) => (
          <button
            key={tab}
            type="button"
            onClick={() => setSubTab(tab)}
            className={`px-3 py-2 text-sm font-medium ${
              subTab === tab
                ? 'border-b-2 border-slate-900 text-slate-900 dark:border-slate-100 dark:text-slate-100'
                : 'text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200'
            }`}
          >
            {t(`notices.donations.subTabs.${tab}`)}
          </button>
        ))}
      </div>

      <p className="text-xs text-slate-500 dark:text-slate-400">{t('notices.donations.memoHint')}</p>

      {subTab === 'income' && (
        <div className="space-y-4">
          {isAdmin && (
            <div className="flex flex-wrap items-center gap-2">
              <button
                type="button"
                onClick={() => setIncomeForm(emptyIncomeForm())}
                className="rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-medium text-white hover:bg-slate-800 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white"
              >
                {t('notices.donations.addButton')}
              </button>
              <button
                type="button"
                onClick={handleIncomeDownload}
                className="rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
              >
                {t('notices.donations.income.downloadCsv')}
              </button>
              <button
                type="button"
                onClick={() => incomeFileInputRef.current?.click()}
                disabled={incomeUploading}
                className="rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
              >
                {t('notices.donations.uploadButton')}
              </button>
              <input
                ref={incomeFileInputRef}
                type="file"
                accept=".csv,text/csv"
                className="hidden"
                onChange={handleIncomeFileSelected}
              />
            </div>
          )}

          {!isAdmin && (
            <button
              type="button"
              onClick={handleIncomeDownload}
              className="rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
            >
              {t('notices.donations.income.downloadCsv')}
            </button>
          )}

          {(incomeActionError || incomeActionSuccess) && (
            <p
              className={`rounded-lg border px-3 py-2 text-xs ${
                incomeActionError
                  ? 'border-rose-200 bg-rose-50 text-rose-700 dark:border-rose-800 dark:bg-rose-950/40 dark:text-rose-300'
                  : 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300'
              }`}
            >
              {incomeActionError ?? incomeActionSuccess}
            </p>
          )}

          {incomeForm && (
            <form
              onSubmit={handleIncomeSubmit}
              className="space-y-3 rounded-xl border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-800/60"
            >
              <div className="grid gap-3 sm:grid-cols-2">
                <label className="block space-y-1 text-xs font-medium text-slate-600 dark:text-slate-300">
                  {t('notices.donations.income.table.date')}
                  <input
                    type="date"
                    value={incomeForm.entryDate}
                    onChange={(event) => setIncomeForm({ ...incomeForm, entryDate: event.target.value })}
                    className="w-full rounded-md border border-slate-300 px-2 py-1.5 text-sm dark:border-slate-600 dark:bg-slate-900"
                  />
                </label>
                <label className="block space-y-1 text-xs font-medium text-slate-600 dark:text-slate-300">
                  {t('notices.donations.income.table.category')}
                  <input
                    type="text"
                    list="income-category-suggestions"
                    value={incomeForm.category}
                    placeholder={t('notices.donations.income.categoryPlaceholder')}
                    onChange={(event) => setIncomeForm({ ...incomeForm, category: event.target.value })}
                    className="w-full rounded-md border border-slate-300 px-2 py-1.5 text-sm dark:border-slate-600 dark:bg-slate-900"
                  />
                  <datalist id="income-category-suggestions">
                    {incomeCategories.map((category) => (
                      <option key={category} value={category} />
                    ))}
                  </datalist>
                </label>
                <label className="block space-y-1 text-xs font-medium text-slate-600 dark:text-slate-300">
                  {t('notices.donations.income.table.amount')}
                  <input
                    type="number"
                    min={1}
                    value={incomeForm.amount}
                    placeholder={t('notices.donations.income.amountPlaceholder')}
                    onChange={(event) => setIncomeForm({ ...incomeForm, amount: event.target.value })}
                    className="w-full rounded-md border border-slate-300 px-2 py-1.5 text-sm dark:border-slate-600 dark:bg-slate-900"
                  />
                </label>
                <label className="block space-y-1 text-xs font-medium text-slate-600 dark:text-slate-300">
                  {t('notices.donations.income.table.memo')}
                  <input
                    type="text"
                    value={incomeForm.memo}
                    placeholder={t('notices.donations.income.memoPlaceholder')}
                    onChange={(event) => setIncomeForm({ ...incomeForm, memo: event.target.value })}
                    className="w-full rounded-md border border-slate-300 px-2 py-1.5 text-sm dark:border-slate-600 dark:bg-slate-900"
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
                  onClick={() => setIncomeForm(null)}
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
                  <th className="px-4 py-3">{t('notices.donations.income.table.date')}</th>
                  <th className="px-4 py-3">{t('notices.donations.income.table.category')}</th>
                  <th className="px-4 py-3">{t('notices.donations.income.table.amount')}</th>
                  <th className="px-4 py-3">{t('notices.donations.income.table.memo')}</th>
                  <th className="px-4 py-3">{t('notices.donations.income.table.author')}</th>
                  {isAdmin && <th className="px-4 py-3" />}
                </tr>
              </thead>
              <tbody>
                {incomeLoading && (
                  <tr>
                    <td className="px-4 py-3" colSpan={isAdmin ? 6 : 5}>
                      <LoadingIndicator label={t('common.loading')} />
                    </td>
                  </tr>
                )}
                {!incomeLoading && incomeError && (
                  <tr>
                    <td className="px-4 py-8 text-center" colSpan={isAdmin ? 6 : 5}>
                      <Alert variant="destructive" appearance="light">
                        <AlertIcon icon="destructive">!</AlertIcon>
                        <AlertContent>
                          <AlertDescription>{incomeError}</AlertDescription>
                        </AlertContent>
                      </Alert>
                    </td>
                  </tr>
                )}
                {!incomeLoading && !incomeError && incomeEntries.length === 0 && (
                  <tr>
                    <td className="px-4 py-8 text-center text-sm text-slate-500 dark:text-slate-400" colSpan={isAdmin ? 6 : 5}>
                      {t('notices.donations.income.empty')}
                    </td>
                  </tr>
                )}
                {!incomeLoading &&
                  !incomeError &&
                  incomeEntries.map((entry) => (
                    <tr key={entry.id} className="border-t border-slate-100 dark:border-slate-800">
                      <td className="px-4 py-3">{entry.entryDate}</td>
                      <td className="px-4 py-3">{entry.category}</td>
                      <td className="px-4 py-3">{formatAmount(entry.amount)}</td>
                      <td className="px-4 py-3">{entry.memo}</td>
                      <td className="px-4 py-3">{entry.authorNickname}</td>
                      {isAdmin && (
                        <td className="whitespace-nowrap px-4 py-3 text-right">
                          <button
                            type="button"
                            onClick={() =>
                              setIncomeForm({
                                id: entry.id,
                                entryDate: entry.entryDate,
                                category: entry.category,
                                amount: String(entry.amount),
                                memo: entry.memo ?? '',
                              })
                            }
                            className="mr-2 text-xs font-medium text-slate-600 hover:underline dark:text-slate-300"
                          >
                            {t('notices.donations.editButton')}
                          </button>
                          <button
                            type="button"
                            onClick={() => handleIncomeDelete(entry)}
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
      )}

      {subTab === 'expense' && (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center gap-2">
            {(['ALL', 'FIXED', 'VARIABLE'] as const).map((type) => (
              <button
                key={type}
                type="button"
                onClick={() => setExpenseTypeFilter(type)}
                className={`rounded-full px-3 py-1 text-xs font-medium ${
                  expenseTypeFilter === type
                    ? 'bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900'
                    : 'border border-slate-300 text-slate-600 dark:border-slate-600 dark:text-slate-300'
                }`}
              >
                {type === 'ALL'
                  ? t('notices.donations.expense.typeAll')
                  : type === 'FIXED'
                    ? t('notices.donations.expense.typeFixed')
                    : t('notices.donations.expense.typeVariable')}
              </button>
            ))}
          </div>

          {isAdmin && (
            <div className="flex flex-wrap items-center gap-2">
              <button
                type="button"
                onClick={() => setExpenseForm(emptyExpenseForm(expenseTypeFilter === 'ALL' ? 'FIXED' : expenseTypeFilter))}
                className="rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-medium text-white hover:bg-slate-800 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white"
              >
                {t('notices.donations.addButton')}
              </button>
              <button
                type="button"
                onClick={handleExpenseDownload}
                className="rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
              >
                {t('notices.donations.expense.downloadCsv')}
              </button>
              <label className="flex items-center gap-1 text-xs text-slate-600 dark:text-slate-300">
                {t('notices.donations.expense.uploadTypeLabel')}
                <select
                  value={expenseUploadType}
                  onChange={(event) => setExpenseUploadType(event.target.value as LedgerExpenseType)}
                  className="rounded-md border border-slate-300 px-1.5 py-1 text-xs dark:border-slate-600 dark:bg-slate-900"
                >
                  <option value="FIXED">{t('notices.donations.expense.typeFixed')}</option>
                  <option value="VARIABLE">{t('notices.donations.expense.typeVariable')}</option>
                </select>
              </label>
              <button
                type="button"
                onClick={() => expenseFileInputRef.current?.click()}
                disabled={expenseUploading}
                className="rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
              >
                {t('notices.donations.uploadButton')}
              </button>
              <input
                ref={expenseFileInputRef}
                type="file"
                accept=".csv,text/csv"
                className="hidden"
                onChange={handleExpenseFileSelected}
              />
            </div>
          )}

          {!isAdmin && (
            <button
              type="button"
              onClick={handleExpenseDownload}
              className="rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
            >
              {t('notices.donations.expense.downloadCsv')}
            </button>
          )}

          {(expenseActionError || expenseActionSuccess) && (
            <p
              className={`rounded-lg border px-3 py-2 text-xs ${
                expenseActionError
                  ? 'border-rose-200 bg-rose-50 text-rose-700 dark:border-rose-800 dark:bg-rose-950/40 dark:text-rose-300'
                  : 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300'
              }`}
            >
              {expenseActionError ?? expenseActionSuccess}
            </p>
          )}

          {expenseForm && (
            <form
              onSubmit={handleExpenseSubmit}
              className="space-y-3 rounded-xl border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-800/60"
            >
              <div className="grid gap-3 sm:grid-cols-2">
                <label className="block space-y-1 text-xs font-medium text-slate-600 dark:text-slate-300">
                  {t('notices.donations.expense.table.date')}
                  <input
                    type="date"
                    value={expenseForm.entryDate}
                    onChange={(event) => setExpenseForm({ ...expenseForm, entryDate: event.target.value })}
                    className="w-full rounded-md border border-slate-300 px-2 py-1.5 text-sm dark:border-slate-600 dark:bg-slate-900"
                  />
                </label>
                <label className="block space-y-1 text-xs font-medium text-slate-600 dark:text-slate-300">
                  {t('notices.donations.expense.table.type')}
                  <select
                    value={expenseForm.expenseType}
                    onChange={(event) =>
                      setExpenseForm({ ...expenseForm, expenseType: event.target.value as LedgerExpenseType })
                    }
                    className="w-full rounded-md border border-slate-300 px-2 py-1.5 text-sm dark:border-slate-600 dark:bg-slate-900"
                  >
                    <option value="FIXED">{t('notices.donations.expense.typeFixed')}</option>
                    <option value="VARIABLE">{t('notices.donations.expense.typeVariable')}</option>
                  </select>
                </label>
                <label className="block space-y-1 text-xs font-medium text-slate-600 dark:text-slate-300">
                  {t('notices.donations.expense.table.category')}
                  <input
                    type="text"
                    list="expense-category-suggestions"
                    value={expenseForm.category}
                    placeholder={t('notices.donations.expense.categoryPlaceholder')}
                    onChange={(event) => setExpenseForm({ ...expenseForm, category: event.target.value })}
                    className="w-full rounded-md border border-slate-300 px-2 py-1.5 text-sm dark:border-slate-600 dark:bg-slate-900"
                  />
                  <datalist id="expense-category-suggestions">
                    {expenseCategories.map((category) => (
                      <option key={category} value={category} />
                    ))}
                  </datalist>
                </label>
                <label className="block space-y-1 text-xs font-medium text-slate-600 dark:text-slate-300">
                  {t('notices.donations.expense.table.target')}
                  <input
                    type="text"
                    list="ledger-target-suggestions"
                    value={expenseForm.target}
                    placeholder={t('notices.donations.expense.targetPlaceholder')}
                    onChange={(event) => setExpenseForm({ ...expenseForm, target: event.target.value })}
                    onBlur={(event) =>
                      setExpenseForm((current) =>
                        current ? { ...current, target: normalizeLedgerTargetInput(event.target.value, roster) } : current
                      )
                    }
                    className="w-full rounded-md border border-slate-300 px-2 py-1.5 text-sm dark:border-slate-600 dark:bg-slate-900"
                  />
                  <datalist id="ledger-target-suggestions">
                    {rosterNicknames.map((nickname) => (
                      <option key={nickname} value={nickname} />
                    ))}
                  </datalist>
                </label>
                <label className="block space-y-1 text-xs font-medium text-slate-600 dark:text-slate-300">
                  {t('notices.donations.expense.table.amount')}
                  <input
                    type="number"
                    min={1}
                    value={expenseForm.amount}
                    placeholder={t('notices.donations.expense.amountPlaceholder')}
                    onChange={(event) => setExpenseForm({ ...expenseForm, amount: event.target.value })}
                    className="w-full rounded-md border border-slate-300 px-2 py-1.5 text-sm dark:border-slate-600 dark:bg-slate-900"
                  />
                </label>
                <label className="block space-y-1 text-xs font-medium text-slate-600 dark:text-slate-300">
                  {t('notices.donations.expense.table.memo')}
                  <input
                    type="text"
                    value={expenseForm.memo}
                    placeholder={t('notices.donations.expense.memoPlaceholder')}
                    onChange={(event) => setExpenseForm({ ...expenseForm, memo: event.target.value })}
                    className="w-full rounded-md border border-slate-300 px-2 py-1.5 text-sm dark:border-slate-600 dark:bg-slate-900"
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
                  onClick={() => setExpenseForm(null)}
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
                  <th className="px-4 py-3">{t('notices.donations.expense.table.date')}</th>
                  <th className="px-4 py-3">{t('notices.donations.expense.table.type')}</th>
                  <th className="px-4 py-3">{t('notices.donations.expense.table.category')}</th>
                  <th className="px-4 py-3">{t('notices.donations.expense.table.target')}</th>
                  <th className="px-4 py-3">{t('notices.donations.expense.table.amount')}</th>
                  <th className="px-4 py-3">{t('notices.donations.expense.table.memo')}</th>
                  <th className="px-4 py-3">{t('notices.donations.expense.table.author')}</th>
                  {isAdmin && <th className="px-4 py-3" />}
                </tr>
              </thead>
              <tbody>
                {expenseLoading && (
                  <tr>
                    <td className="px-4 py-3" colSpan={isAdmin ? 8 : 7}>
                      <LoadingIndicator label={t('common.loading')} />
                    </td>
                  </tr>
                )}
                {!expenseLoading && expenseError && (
                  <tr>
                    <td className="px-4 py-8 text-center" colSpan={isAdmin ? 8 : 7}>
                      <Alert variant="destructive" appearance="light">
                        <AlertIcon icon="destructive">!</AlertIcon>
                        <AlertContent>
                          <AlertDescription>{expenseError}</AlertDescription>
                        </AlertContent>
                      </Alert>
                    </td>
                  </tr>
                )}
                {!expenseLoading && !expenseError && expenseEntries.length === 0 && (
                  <tr>
                    <td className="px-4 py-8 text-center text-sm text-slate-500 dark:text-slate-400" colSpan={isAdmin ? 8 : 7}>
                      {t('notices.donations.expense.empty')}
                    </td>
                  </tr>
                )}
                {!expenseLoading &&
                  !expenseError &&
                  expenseEntries.map((entry) => (
                    <tr key={entry.id} className="border-t border-slate-100 dark:border-slate-800">
                      <td className="px-4 py-3">{entry.entryDate}</td>
                      <td className="px-4 py-3">
                        {entry.expenseType === 'FIXED'
                          ? t('notices.donations.expense.typeFixed')
                          : t('notices.donations.expense.typeVariable')}
                      </td>
                      <td className="px-4 py-3">{entry.category}</td>
                      <td className="px-4 py-3">{entry.target}</td>
                      <td className="px-4 py-3">{formatAmount(entry.amount)}</td>
                      <td className="px-4 py-3">{entry.memo}</td>
                      <td className="px-4 py-3">{entry.authorNickname}</td>
                      {isAdmin && (
                        <td className="whitespace-nowrap px-4 py-3 text-right">
                          <button
                            type="button"
                            onClick={() =>
                              setExpenseForm({
                                id: entry.id,
                                entryDate: entry.entryDate,
                                expenseType: entry.expenseType,
                                category: entry.category,
                                target: entry.target ?? '',
                                amount: String(entry.amount),
                                memo: entry.memo ?? '',
                              })
                            }
                            className="mr-2 text-xs font-medium text-slate-600 hover:underline dark:text-slate-300"
                          >
                            {t('notices.donations.editButton')}
                          </button>
                          <button
                            type="button"
                            onClick={() => handleExpenseDelete(entry)}
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
      )}

      {subTab === 'summary' && (
        <div className="space-y-4">
          <div className="flex items-center gap-2">
            <label className="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-300">
              {t('notices.donations.summary.yearLabel')}
              <select
                value={summaryYear}
                onChange={(event) => setSummaryYear(Number(event.target.value))}
                className="rounded-md border border-slate-300 px-2 py-1 text-sm dark:border-slate-600 dark:bg-slate-900"
              >
                {yearOptions.map((year) => (
                  <option key={year} value={year}>
                    {year}
                  </option>
                ))}
              </select>
            </label>
          </div>

          <p className="text-xs text-slate-500 dark:text-slate-400">{t('notices.donations.summary.cumulativeHint')}</p>

          <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-900">
            <table className="min-w-full text-left text-sm">
              <thead className="bg-slate-50 text-xs tracking-wide text-slate-500 dark:bg-slate-800/80 dark:text-slate-300">
                <tr>
                  <th className="px-4 py-3">{t('notices.donations.summary.table.month')}</th>
                  <th className="px-4 py-3">{t('notices.donations.summary.table.income')}</th>
                  <th className="px-4 py-3">{t('notices.donations.summary.table.fixedExpense')}</th>
                  <th className="px-4 py-3">{t('notices.donations.summary.table.variableExpense')}</th>
                  <th className="px-4 py-3">{t('notices.donations.summary.table.totalExpense')}</th>
                  <th className="px-4 py-3">{t('notices.donations.summary.table.net')}</th>
                  <th className="px-4 py-3">{t('notices.donations.summary.table.cumulativeBalance')}</th>
                </tr>
              </thead>
              <tbody>
                {summaryLoading && (
                  <tr>
                    <td className="px-4 py-3" colSpan={7}>
                      <LoadingIndicator label={t('common.loading')} />
                    </td>
                  </tr>
                )}
                {!summaryLoading && summaryError && (
                  <tr>
                    <td className="px-4 py-8 text-center" colSpan={7}>
                      <Alert variant="destructive" appearance="light">
                        <AlertIcon icon="destructive">!</AlertIcon>
                        <AlertContent>
                          <AlertDescription>{summaryError}</AlertDescription>
                        </AlertContent>
                      </Alert>
                    </td>
                  </tr>
                )}
                {!summaryLoading &&
                  !summaryError &&
                  summaryMonths.map((month) => (
                    <tr key={month.month} className="border-t border-slate-100 dark:border-slate-800">
                      <td className="px-4 py-3">{month.month}</td>
                      <td className="px-4 py-3">{formatAmount(month.totalIncome)}</td>
                      <td className="px-4 py-3">{formatAmount(month.totalFixedExpense)}</td>
                      <td className="px-4 py-3">{formatAmount(month.totalVariableExpense)}</td>
                      <td className="px-4 py-3">{formatAmount(month.totalExpense)}</td>
                      <td className="px-4 py-3">{formatAmount(month.net)}</td>
                      <td className="px-4 py-3">{formatAmount(month.cumulativeBalance)}</td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  )
}
