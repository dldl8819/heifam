import type { Metadata } from 'next'
import { Inter } from 'next/font/google'
import { Analytics } from '@vercel/analytics/next'
import { SpeedInsights } from '@vercel/speed-insights/next'
import { AppShell } from '@/components/app-shell'
import { AuthProvider } from '@/components/auth-session-provider'
import { t } from '@/lib/i18n'
import './globals.css'

const inter = Inter({ subsets: ['latin'] })

export const metadata: Metadata = {
  title: t('meta.title'),
  description: t('meta.description'),
  icons: {
    icon: [
      { url: '/logo.jpg', type: 'image/jpeg' },
      { url: '/icon.jpg', type: 'image/jpeg' },
    ],
    shortcut: '/logo.jpg',
    apple: '/logo.jpg',
  },
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="ko">
      <body className={inter.className}>
        <AuthProvider>
          <AppShell>{children}</AppShell>
        </AuthProvider>
        <Analytics />
        <SpeedInsights />
      </body>
    </html>
  )
}
