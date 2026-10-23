'use client'

import * as Sentry from '@sentry/nextjs'
import { useEffect } from 'react'
import { t } from '@/lib/i18n'

// Shown instead of the whole site when the root layout itself fails to render. It replaces the
// layout, so it brings its own <html> and plain styles, and tells Sentry what broke.
export default function GlobalError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    Sentry.captureException(error)
  }, [error])

  return (
    <html lang="ko">
      <body style={{ margin: 0, fontFamily: 'system-ui, sans-serif', background: '#f8fafc', color: '#0f172a' }}>
        <main style={{ maxWidth: 480, margin: '15vh auto', padding: '0 16px', textAlign: 'center' }}>
          <h1 style={{ fontSize: 20, fontWeight: 600 }}>{t('common.globalErrorTitle')}</h1>
          <p style={{ fontSize: 14, color: '#475569' }}>{t('common.globalErrorDescription')}</p>
          <button
            type="button"
            onClick={() => reset()}
            style={{ marginTop: 16, padding: '8px 16px', borderRadius: 8, border: 0, background: '#0f172a', color: '#fff', fontSize: 14 }}
          >
            {t('common.globalErrorRetry')}
          </button>
        </main>
      </body>
    </html>
  )
}
