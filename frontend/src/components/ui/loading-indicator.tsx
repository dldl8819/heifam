'use client'

import * as React from 'react'
import { cn } from '@/lib/utils'

type LoadingIndicatorProps = {
  label?: string
  className?: string
}

export function LoadingIndicator({ label = 'Loading…', className }: LoadingIndicatorProps) {
  return (
    <div className={cn('flex w-full justify-center py-4', className)}>
      <div className="pl" role="status" aria-live="polite" aria-label={label}>
        {Array.from({ length: 12 }).map((_, index) => (
          <div key={`loading-dot-${index}`} className="pl__dot" style={{ ['--i' as string]: index + 1 }} />
        ))}
        <div className="pl__text">{label}</div>
      </div>
    </div>
  )
}

