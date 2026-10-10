'use client'

import { useAdminAuth } from '@/lib/admin-auth'
import { AdminOnlyContent } from '@/components/admin-only-content'
import { PointPageHeader } from '@/components/point-page-parts'
import { PrizeEventsPanel } from '@/components/prize-events-panel'
import { t } from '@/lib/i18n'

const TEMP_GROUP_ID = 1

/** Prize events by points earned: admins see them, super admins run them (the backend enforces the same). */
export default function PointEventsPage() {
  const { isSuperAdmin } = useAdminAuth()

  return (
    <section className="space-y-6">
      <PointPageHeader title={t('nav.pointEvents')} description={t('points.events.description')} />
      <AdminOnlyContent>
        <PrizeEventsPanel groupId={TEMP_GROUP_ID} canManage={isSuperAdmin} />
      </AdminOnlyContent>
    </section>
  )
}
