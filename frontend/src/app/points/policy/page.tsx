'use client'

import { useEffect, useState } from 'react'
import { apiClient, isApiForbiddenError } from '@/lib/api'
import { LoadingIndicator } from '@/components/ui/loading-indicator'
import { PointErrorAlert, PointPageHeader } from '@/components/point-page-parts'
import { PointPolicyContent } from '@/components/point-policy-content'
import { t } from '@/lib/i18n'
import type { PointPolicyResponse } from '@/types/api'

/** The rules of points: what each activity earns and how often, and when points are corrected or taken back. */
export default function PointPolicyPage() {
  const [policy, setPolicy] = useState<PointPolicyResponse | null>(null)
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    apiClient
      .getPointPolicy()
      .then((response) => {
        if (!cancelled) {
          setPolicy(response)
        }
      })
      .catch((caught: unknown) => {
        if (!cancelled) {
          setError(isApiForbiddenError(caught) ? t('points.forbidden') : t('points.policy.loadError'))
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false)
        }
      })
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <section className="space-y-6">
      <PointPageHeader title={t('points.policy.title')} description={t('points.policy.description')} />
      {loading && <LoadingIndicator label={t('common.loading')} />}
      {error && <PointErrorAlert message={error} />}
      <PointPolicyContent policy={policy} />
    </section>
  )
}
