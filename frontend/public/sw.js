/* Web Push for HeiFam: shows each notification and opens its page when it is tapped. */

self.addEventListener('install', () => {
  self.skipWaiting()
})

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim())
})

self.addEventListener('push', (event) => {
  let data = {}
  try {
    data = event.data ? event.data.json() : {}
  } catch (error) {
    data = {}
  }
  const title = typeof data.title === 'string' && data.title.length > 0 ? data.title : '헤이팸'
  // Only paths inside the site are opened.
  const link = typeof data.link === 'string' && data.link.startsWith('/') && !data.link.startsWith('//') ? data.link : '/'
  event.waitUntil(
    self.registration.showNotification(title, {
      body: typeof data.body === 'string' ? data.body : '',
      icon: '/icons/icon-192x192.png',
      badge: '/icons/icon-192x192.png',
      tag: typeof data.tag === 'string' ? data.tag : undefined,
      data: { link },
    }),
  )
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const link = (event.notification.data && event.notification.data.link) || '/'
  const target = new URL(link, self.location.origin).href
  event.waitUntil(
    (async () => {
      const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
      for (const client of windows) {
        if (new URL(client.url).origin === self.location.origin && 'focus' in client) {
          await client.focus()
          if ('navigate' in client) {
            await client.navigate(target)
          }
          return
        }
      }
      await self.clients.openWindow(target)
    })(),
  )
})
