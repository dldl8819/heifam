'use client'

import Link from 'next/link'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useAdminAuth } from '@/lib/admin-auth'
import { apiClient } from '@/lib/api'
import { t } from '@/lib/i18n'
import {
  freshNotifications,
  newestNotificationId,
  NOTIFICATION_POLL_MS,
  NOTIFICATION_TOAST_MS,
  relativeTime,
  safeNotificationLink,
  unreadBadge,
} from '@/lib/notifications'
import {
  currentPushSubscription,
  isPushSupported,
  isStandaloneDisplay,
  needsHomeScreenInstall,
  PushPermissionError,
  subscribeToPush,
  unsubscribeFromPush,
} from '@/lib/push'
import { startVisiblePolling } from '@/lib/visible-polling'
import type { NotificationItem } from '@/types/api'

const TEMP_GROUP_ID = 1

type PushState = 'checking' | 'unsupported' | 'unavailable' | 'denied' | 'off' | 'on'

function timeLabel(createdAt: string): string {
  const relative = relativeTime(createdAt, new Date())
  return relative.key === 'date' ? relative.date : t(`notifications.${relative.key}`, { count: relative.count })
}

/** The bell in the header, for everyone with service access; the backend enforces the same. */
export function NotificationBell() {
  const { isLoading, isLoggedIn, canAccess } = useAdminAuth()
  if (isLoading || !isLoggedIn || !canAccess) {
    return null
  }
  return <NotificationCenter />
}

function NotificationCenter() {
  const [notifications, setNotifications] = useState<NotificationItem[]>([])
  const [unreadCount, setUnreadCount] = useState<number>(0)
  const [loadError, setLoadError] = useState<boolean>(false)
  const [open, setOpen] = useState<boolean>(false)
  const [toast, setToast] = useState<NotificationItem | null>(null)
  const [pushState, setPushState] = useState<PushState>('checking')
  const [pushPublicKey, setPushPublicKey] = useState<string | null>(null)
  const [pushBusy, setPushBusy] = useState<boolean>(false)
  const [pushError, setPushError] = useState<boolean>(false)
  const [showInstallHint, setShowInstallHint] = useState<boolean>(false)
  const newestSeenId = useRef<number | null>(null)
  const panelRef = useRef<HTMLDivElement | null>(null)

  const load = useCallback(async () => {
    try {
      const response = await apiClient.getNotifications(TEMP_GROUP_ID)
      const fresh = freshNotifications(newestSeenId.current, response.notifications)
      newestSeenId.current = newestNotificationId(response.notifications, newestSeenId.current)
      setNotifications(response.notifications)
      setUnreadCount(response.unreadCount)
      setLoadError(false)
      if (fresh.length > 0) {
        setToast(fresh[0])
      }
    } catch {
      setLoadError(true)
    }
  }, [])

  useEffect(() => {
    void load()
    return startVisiblePolling(() => {
      void load()
    }, NOTIFICATION_POLL_MS)
  }, [load])

  useEffect(() => {
    if (!toast) {
      return
    }
    const timer = window.setTimeout(() => setToast(null), NOTIFICATION_TOAST_MS)
    return () => window.clearTimeout(timer)
  }, [toast])

  // Close the panel on an outside click.
  useEffect(() => {
    if (!open) {
      return
    }
    const handlePointerDown = (event: PointerEvent) => {
      if (panelRef.current && !panelRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('pointerdown', handlePointerDown)
    return () => document.removeEventListener('pointerdown', handlePointerDown)
  }, [open])

  useEffect(() => {
    let active = true
    const checkPush = async () => {
      if (!isPushSupported()) {
        setPushState('unsupported')
        setShowInstallHint(needsHomeScreenInstall(navigator.userAgent, isStandaloneDisplay()))
        return
      }
      try {
        const config = await apiClient.getPushConfig()
        if (!active) {
          return
        }
        if (!config.enabled || !config.publicKey) {
          setPushState('unavailable')
          return
        }
        setPushPublicKey(config.publicKey)
        if (Notification.permission === 'denied') {
          setPushState('denied')
          return
        }
        const subscription = await currentPushSubscription()
        if (!active) {
          return
        }
        if (subscription) {
          // Registers it again for whoever is signed in now on this browser.
          await apiClient.savePushSubscription(subscription.toJSON())
        }
        setPushState(subscription ? 'on' : 'off')
      } catch {
        if (active) {
          setPushState('unavailable')
        }
      }
    }
    void checkPush()
    return () => {
      active = false
    }
  }, [])

  const handleToggle = async () => {
    const nextOpen = !open
    setOpen(nextOpen)
    if (nextOpen && unreadCount > 0) {
      try {
        const response = await apiClient.markNotificationsRead(TEMP_GROUP_ID)
        newestSeenId.current = newestNotificationId(response.notifications, newestSeenId.current)
        setUnreadCount(response.unreadCount)
        // The list keeps showing what was new until the panel is opened again.
      } catch {
        setLoadError(true)
      }
    } else if (!nextOpen) {
      setNotifications((previous) => previous.map((notification) => ({ ...notification, read: true })))
    }
  }

  const handlePush = async () => {
    if (pushBusy) {
      return
    }
    setPushBusy(true)
    setPushError(false)
    try {
      if (pushState === 'on') {
        const endpoint = await unsubscribeFromPush()
        if (endpoint) {
          await apiClient.removePushSubscription(endpoint)
        }
        setPushState('off')
      } else if (pushPublicKey) {
        const subscription = await subscribeToPush(pushPublicKey)
        await apiClient.savePushSubscription(subscription)
        setPushState('on')
      }
    } catch (error) {
      if (error instanceof PushPermissionError && error.permission === 'denied') {
        setPushState('denied')
      } else {
        setPushError(true)
      }
    } finally {
      setPushBusy(false)
    }
  }

  const badge = unreadBadge(unreadCount)

  return (
    // On phones the panel hangs from the header row instead of the bell, so it never runs off the left edge.
    <div ref={panelRef} className="sm:relative">
      <button
        type="button"
        onClick={() => void handleToggle()}
        aria-label={badge ? t('notifications.unreadLabel', { count: unreadCount }) : t('notifications.open')}
        aria-expanded={open}
        className="relative inline-flex h-10 w-10 items-center justify-center rounded-lg border border-slate-700 bg-slate-800/80 text-slate-100 transition-colors hover:bg-slate-700"
      >
        <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
          <path d="M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
          <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
        </svg>
        {badge && (
          <span className="absolute -right-1 -top-1 min-w-[1.25rem] rounded-full bg-rose-500 px-1 text-center text-[11px] font-bold leading-5 text-white">
            {badge}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 z-40 mt-2 w-80 max-w-[calc(100vw-2rem)] overflow-hidden rounded-xl border border-slate-200 bg-white text-slate-900 shadow-xl dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100">
          <p className="border-b border-slate-100 px-4 py-2 text-sm font-semibold dark:border-slate-800">
            {t('notifications.title')}
          </p>
          <div className="max-h-80 overflow-y-auto">
            {loadError && notifications.length === 0 && (
              <p className="px-4 py-6 text-center text-xs text-rose-600 dark:text-rose-300">{t('notifications.loadError')}</p>
            )}
            {!loadError && notifications.length === 0 && (
              <p className="px-4 py-6 text-center text-xs text-slate-500 dark:text-slate-400">{t('notifications.empty')}</p>
            )}
            <ul className="divide-y divide-slate-100 dark:divide-slate-800">
              {notifications.map((notification) => (
                <li key={notification.id}>
                  <Link
                    href={safeNotificationLink(notification.link)}
                    onClick={() => setOpen(false)}
                    className="block px-4 py-3 hover:bg-slate-50 dark:hover:bg-slate-800"
                  >
                    <div className="flex items-baseline justify-between gap-2">
                      <span className={`text-xs ${notification.read ? 'text-slate-500 dark:text-slate-400' : 'font-semibold text-indigo-700 dark:text-indigo-300'}`}>
                        {notification.title}
                      </span>
                      <span className="shrink-0 text-[11px] text-slate-400">{timeLabel(notification.createdAt)}</span>
                    </div>
                    {notification.body && (
                      <p className={`mt-0.5 text-sm ${notification.read ? 'text-slate-600 dark:text-slate-300' : 'font-semibold'}`}>
                        {notification.body}
                      </p>
                    )}
                  </Link>
                </li>
              ))}
            </ul>
          </div>
          <div className="space-y-2 border-t border-slate-100 bg-slate-50 px-4 py-3 dark:border-slate-800 dark:bg-slate-800/60">
            <p className="text-xs font-semibold">{t('notifications.push.title')}</p>
            {pushState === 'off' || pushState === 'on' ? (
              <>
                <p className="text-[11px] text-slate-500 dark:text-slate-400">
                  {pushState === 'on' ? t('notifications.push.on') : t('notifications.push.description')}
                </p>
                <button
                  type="button"
                  onClick={() => void handlePush()}
                  disabled={pushBusy}
                  className="rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-medium text-white hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-white"
                >
                  {pushBusy
                    ? t('notifications.push.working')
                    : pushState === 'on'
                      ? t('notifications.push.disable')
                      : t('notifications.push.enable')}
                </button>
              </>
            ) : (
              pushState !== 'checking' && (
                <p className="text-[11px] text-slate-500 dark:text-slate-400">
                  {showInstallHint ? t('notifications.push.iosHint') : t(`notifications.push.${pushState}`)}
                </p>
              )
            )}
            {pushError && <p className="text-[11px] text-rose-600 dark:text-rose-300">{t('notifications.push.error')}</p>}
          </div>
        </div>
      )}

      {toast && !open && (
        <div
          role="status"
          className="fixed bottom-4 left-4 right-4 z-50 rounded-xl border border-indigo-200 bg-white p-4 text-slate-900 shadow-2xl sm:left-auto sm:w-80 dark:border-indigo-800 dark:bg-slate-900 dark:text-slate-100"
        >
          <p className="text-xs font-semibold text-indigo-700 dark:text-indigo-300">{toast.title}</p>
          {toast.body && <p className="mt-1 text-sm font-semibold">{toast.body}</p>}
          <div className="mt-3 flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setToast(null)}
              className="rounded-lg border border-slate-300 px-3 py-1 text-xs text-slate-600 hover:bg-slate-50 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-800"
            >
              {t('notifications.toast.close')}
            </button>
            <Link
              href={safeNotificationLink(toast.link)}
              onClick={() => setToast(null)}
              className="rounded-lg bg-indigo-600 px-3 py-1 text-xs font-medium text-white hover:bg-indigo-500"
            >
              {t('notifications.toast.view')}
            </Link>
          </div>
        </div>
      )}
    </div>
  )
}
