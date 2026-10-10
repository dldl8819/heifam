'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { CannonStage } from '@/components/cannon-stage'
import {
  CANNON_DEFAULT_LAST,
  CANNON_HISTORY_SIZE,
  CANNON_MATCHUP_PRESET,
  CANNON_MAX_ITEMS,
  CANNON_MAX_ITEM_LENGTH,
  CANNON_MAX_LAST,
  CANNON_MIN_LAST,
  fireCannon,
  parseCannonItems,
  parseLastNumber,
  validateCannonItems,
  type CannonMode,
  type CannonShot,
} from '@/lib/cannon-draw'
import { t } from '@/lib/i18n'

const CARD_CLASS = 'rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900'
const MODES: CannonMode[] = ['number', 'items']
// The items last written on this device, for the next ace match. Only a convenience: nothing breaks without it.
const ITEMS_STORAGE_KEY = 'heifam.cannon.items'

function shotText(shot: CannonShot): string {
  return shot.label !== null
    ? t('cannonDraw.resultItem', { number: shot.number, label: shot.label })
    : t('cannonDraw.resultNumber', { number: shot.number, last: shot.last })
}

function readStoredItems(): string {
  try {
    return window.localStorage.getItem(ITEMS_STORAGE_KEY) ?? ''
  } catch {
    return ''
  }
}

function storeItems(text: string) {
  try {
    window.localStorage.setItem(ITEMS_STORAGE_KEY, text)
  } catch {
    // Private windows and blocked storage: the list is simply not remembered.
  }
}

/**
 * The cannon draw: a number from 1 to a last number, or one of a list of items written down, such
 * as the race matchups of an ace match. Runs in the browser only and keeps nothing on the server;
 * the results stay on this screen until it is left.
 */
export default function CannonPage() {
  const [mode, setMode] = useState<CannonMode>('number')
  const [lastText, setLastText] = useState<string>(String(CANNON_DEFAULT_LAST))
  const [itemsText, setItemsText] = useState<string>('')
  const [shot, setShot] = useState<CannonShot | null>(null)
  const [flying, setFlying] = useState<boolean>(false)
  const [history, setHistory] = useState<CannonShot[]>([])
  const [announcement, setAnnouncement] = useState<string>('')
  const nextId = useRef<number>(1)

  useEffect(() => {
    setItemsText(readStoredItems())
  }, [])

  const last = parseLastNumber(lastText)
  const items = useMemo(() => parseCannonItems(itemsText), [itemsText])
  const itemsError = validateCannonItems(items)
  const canFire = !flying && (mode === 'items' ? itemsError === null : last !== null)

  const fire = () => {
    if (!canFire) {
      return
    }
    const id = nextId.current
    nextId.current += 1
    setShot(fireCannon(id, mode, last ?? CANNON_MIN_LAST, items))
    setFlying(true)
    setAnnouncement('')
  }

  const handleLanded = useCallback((landed: CannonShot) => {
    setFlying(false)
    setHistory((previous) => [landed, ...previous].slice(0, CANNON_HISTORY_SIZE))
    setAnnouncement(shotText(landed))
  }, [])

  // Drawing from something else clears the last ball from the sky.
  const changeMode = (next: CannonMode) => {
    setMode(next)
    setShot(null)
  }
  const changeLast = (text: string) => {
    setLastText(text)
    setShot(null)
  }
  const changeItems = (text: string) => {
    setItemsText(text)
    setShot(null)
    storeItems(text)
  }

  const range =
    mode === 'items'
      ? itemsError === null
        ? t('cannonDraw.rangeItems', { count: items.length })
        : null
      : last !== null
        ? t('cannonDraw.range', { last })
        : null

  const itemsMessage =
    itemsError === 'tooMany'
      ? t('cannonDraw.itemsTooMany', { max: CANNON_MAX_ITEMS })
      : itemsError === 'tooLong'
        ? t('cannonDraw.itemTooLong', { max: CANNON_MAX_ITEM_LENGTH })
        : itemsError === 'tooFew' && items.length > 0
          ? t('cannonDraw.itemsTooFew')
          : null

  return (
    <section className="space-y-6">
      <div className="space-y-1">
        <h1 className="text-lg font-semibold text-slate-900 dark:text-slate-100">{t('cannonDraw.title')}</h1>
        <p className="max-w-2xl text-sm text-slate-500 dark:text-slate-400">{t('cannonDraw.description')}</p>
      </div>

      <div className="mx-auto w-full max-w-3xl space-y-4">
        <form
          className={`${CARD_CLASS} space-y-3`}
          onSubmit={(event) => {
            event.preventDefault()
            fire()
          }}
        >
          <div className="flex flex-wrap items-center gap-x-4 gap-y-3">
            <div
              className="inline-flex rounded-lg border border-slate-300 p-0.5 dark:border-slate-600"
              role="radiogroup"
              aria-label={t('cannonDraw.modeLabel')}
            >
              {MODES.map((option) => (
                <button
                  key={option}
                  type="button"
                  role="radio"
                  aria-checked={mode === option}
                  disabled={flying}
                  onClick={() => changeMode(option)}
                  className={`rounded-md px-3 py-1.5 text-sm font-medium disabled:cursor-not-allowed ${
                    mode === option
                      ? 'bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900'
                      : 'text-slate-600 hover:bg-slate-50 dark:text-slate-300 dark:hover:bg-slate-800'
                  }`}
                >
                  {t(option === 'items' ? 'cannonDraw.modeItems' : 'cannonDraw.modeNumber')}
                </button>
              ))}
            </div>

            {mode === 'number' ? (
              <label className="flex items-center gap-2 text-sm text-slate-700 dark:text-slate-200">
                {t('cannonDraw.lastNumber')}
                <input
                  type="text"
                  inputMode="numeric"
                  maxLength={3}
                  value={lastText}
                  disabled={flying}
                  onChange={(event) => changeLast(event.target.value)}
                  aria-invalid={last === null}
                  className="w-20 rounded-md border border-slate-300 px-2 py-1.5 text-sm dark:border-slate-600 dark:bg-slate-900"
                />
              </label>
            ) : (
              <button
                type="button"
                disabled={flying}
                onClick={() => changeItems(CANNON_MATCHUP_PRESET.join('\n'))}
                className="rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
              >
                {t('cannonDraw.itemsPreset')}
              </button>
            )}
          </div>

          {mode === 'items' && (
            <div className="space-y-2">
              <textarea
                value={itemsText}
                disabled={flying}
                rows={4}
                onChange={(event) => changeItems(event.target.value)}
                placeholder={t('cannonDraw.itemsPlaceholder')}
                aria-label={t('cannonDraw.itemsLabel')}
                aria-invalid={itemsMessage !== null}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-900"
              />
              {items.length > 0 && (
                <ol className="flex flex-wrap gap-2" aria-label={t('cannonDraw.itemsList')}>
                  {items.map((item, index) => {
                    const picked = !flying && shot !== null && shot.label !== null && shot.number === index + 1
                    return (
                      <li
                        key={`${index}-${item}`}
                        className={`break-all rounded-lg border px-2.5 py-1 text-sm font-semibold ${
                          picked
                            ? 'border-amber-400 bg-amber-50 text-amber-800 dark:border-amber-500 dark:bg-amber-950/40 dark:text-amber-200'
                            : 'border-slate-200 text-slate-700 dark:border-slate-700 dark:text-slate-200'
                        }`}
                      >
                        <span className="mr-1 text-xs font-medium text-slate-500 dark:text-slate-400">{index + 1}</span>
                        {item}
                      </li>
                    )
                  })}
                </ol>
              )}
            </div>
          )}

          {mode === 'number' && last === null ? (
            <p className="text-xs text-rose-600 dark:text-rose-400">
              {t('cannonDraw.lastNumberInvalid', { min: CANNON_MIN_LAST, max: CANNON_MAX_LAST })}
            </p>
          ) : mode === 'items' && itemsMessage !== null ? (
            <p className="text-xs text-rose-600 dark:text-rose-400">{itemsMessage}</p>
          ) : (
            <p className="text-xs text-slate-500 dark:text-slate-400">
              {t(mode === 'items' ? 'cannonDraw.itemsHint' : 'cannonDraw.lastNumberHint')}
            </p>
          )}
        </form>

        <CannonStage shot={shot} onLanded={handleLanded}>
          {range && (
            <span className="absolute left-3 top-3 rounded-md bg-white/85 px-2.5 py-1 text-xs font-semibold text-slate-800 shadow-sm">
              {range}
            </span>
          )}
          <button
            type="button"
            onClick={fire}
            disabled={!canFire}
            className="absolute bottom-2 right-2 rounded-lg bg-white/90 px-3 py-1.5 text-sm font-bold text-slate-900 shadow-md hover:bg-white disabled:cursor-not-allowed disabled:opacity-60 sm:bottom-3 sm:right-3 sm:px-4 sm:py-2 sm:text-base"
          >
            {flying ? t('cannonDraw.flying') : t(history.length > 0 ? 'cannonDraw.fireAgain' : 'cannonDraw.fire')}
          </button>
        </CannonStage>
        <p className="sr-only" aria-live="polite">
          {announcement}
        </p>

        <div className={`${CARD_CLASS} space-y-2`}>
          <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t('cannonDraw.historyTitle')}</h2>
          {history.length === 0 ? (
            <p className="text-sm text-slate-500 dark:text-slate-400">{t('cannonDraw.historyEmpty')}</p>
          ) : (
            <ol className="flex flex-wrap gap-2">
              {history.map((item, index) => (
                <li
                  key={item.id}
                  className={`break-all rounded-lg border px-2.5 py-1 text-sm ${
                    index === 0
                      ? 'border-emerald-300 bg-emerald-50 font-semibold text-emerald-800 dark:border-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-200'
                      : 'border-slate-200 text-slate-700 dark:border-slate-700 dark:text-slate-300'
                  }`}
                >
                  {shotText(item)}
                </li>
              ))}
            </ol>
          )}
          <p className="text-xs text-slate-500 dark:text-slate-400">{t('cannonDraw.historyNote')}</p>
        </div>
      </div>
    </section>
  )
}
