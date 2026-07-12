import type { Metadata, Viewport } from 'next'
import { Inter } from 'next/font/google'
import { Analytics } from '@vercel/analytics/next'
import { SpeedInsights } from '@vercel/speed-insights/next'
import { AppShell } from '@/components/app-shell'
import { AuthProvider } from '@/components/auth-session-provider'
import { t } from '@/lib/i18n'
import { THEME_STORAGE_KEY } from '@/lib/theme'
import './globals.css'

const inter = Inter({ subsets: ['latin'] })

const themeInitializationScript = String.raw`
  (() => {
    const storageKey = '__THEME_STORAGE_KEY__';
    const root = document.documentElement;
    let storedTheme = null;

    try {
      storedTheme = window.localStorage.getItem(storageKey);
    } catch {
      storedTheme = null;
    }

    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    const theme = storedTheme === 'light' || storedTheme === 'dark'
      ? storedTheme
      : prefersDark ? 'dark' : 'light';

    root.classList.toggle('dark', theme === 'dark');
    root.style.colorScheme = theme;
  })();
`.replace('__THEME_STORAGE_KEY__', THEME_STORAGE_KEY)

export const metadata: Metadata = {
  applicationName: '헤이팸',
  title: t('meta.title'),
  description: t('meta.description'),
  manifest: '/manifest.webmanifest',
  appleWebApp: {
    capable: true,
    title: '헤이팸',
    statusBarStyle: 'black',
  },
  other: {
    'apple-mobile-web-app-capable': 'yes',
  },
  icons: {
    icon: [
      {
        url: '/icons/icon-192x192.png',
        type: 'image/png',
        sizes: '192x192',
      },
    ],
    apple: [
      {
        url: '/apple-touch-icon.png',
        type: 'image/png',
        sizes: '180x180',
      },
    ],
  },
}

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  themeColor: '#0f172a',
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="ko" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeInitializationScript }} />
      </head>
      <body className={inter.className}>
        <AuthProvider>
          <AppShell>{children}</AppShell>
        </AuthProvider>
        <Analytics />
        <SpeedInsights sampleRate={0.5} />
      </body>
    </html>
  )
}
