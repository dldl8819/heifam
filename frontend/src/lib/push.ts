// Web Push in the browser: the service worker in /sw.js shows the notifications.

export class PushPermissionError extends Error {
  readonly permission: NotificationPermission

  constructor(permission: NotificationPermission) {
    super(`Notification permission is ${permission}`)
    this.permission = permission
  }
}

export function isPushSupported(): boolean {
  return (
    typeof window !== 'undefined' &&
    'serviceWorker' in navigator &&
    'PushManager' in window &&
    'Notification' in window
  )
}

/** iPhones and iPads get push only in the app added to the home screen, not in a Safari tab. */
export function needsHomeScreenInstall(userAgent: string, standalone: boolean): boolean {
  return /iPad|iPhone|iPod/.test(userAgent) && !standalone
}

export function isStandaloneDisplay(): boolean {
  if (typeof window === 'undefined') {
    return false
  }
  const navigatorWithStandalone = navigator as Navigator & { standalone?: boolean }
  return window.matchMedia('(display-mode: standalone)').matches || navigatorWithStandalone.standalone === true
}

/** The VAPID public key (base64url) as the bytes PushManager.subscribe wants. */
export function urlBase64ToUint8Array(value: string): Uint8Array<ArrayBuffer> {
  const padded = `${value}${'='.repeat((4 - (value.length % 4)) % 4)}`.replace(/-/g, '+').replace(/_/g, '/')
  const raw = atob(padded)
  const bytes = new Uint8Array(new ArrayBuffer(raw.length))
  for (let index = 0; index < raw.length; index += 1) {
    bytes[index] = raw.charCodeAt(index)
  }
  return bytes
}

export async function currentPushSubscription(): Promise<PushSubscription | null> {
  if (!isPushSupported()) {
    return null
  }
  const registration = await navigator.serviceWorker.getRegistration('/')
  return registration ? registration.pushManager.getSubscription() : null
}

/** Asks for permission and subscribes this browser; returns what the backend stores. */
export async function subscribeToPush(publicKey: string): Promise<PushSubscriptionJSON> {
  const permission = await Notification.requestPermission()
  if (permission !== 'granted') {
    throw new PushPermissionError(permission)
  }
  await navigator.serviceWorker.register('/sw.js', { scope: '/' })
  const registration = await navigator.serviceWorker.ready
  const existing = await registration.pushManager.getSubscription()
  const subscription =
    existing ??
    (await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(publicKey),
    }))
  return subscription.toJSON()
}

/** Unsubscribes this browser; returns the endpoint the backend should forget, if there was one. */
export async function unsubscribeFromPush(): Promise<string | null> {
  const subscription = await currentPushSubscription()
  if (!subscription) {
    return null
  }
  const endpoint = subscription.endpoint
  await subscription.unsubscribe()
  return endpoint
}
