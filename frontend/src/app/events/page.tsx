'use client'

import { useAdminAuth } from '@/lib/admin-auth'
import { PointPageHeader } from '@/components/point-page-parts'
import { PrizeEventsPanel } from '@/components/prize-events-panel'
import { t } from '@/lib/i18n'

const TEMP_GROUP_ID = 1

/**
 * The prize events by points earned, for members. Admins start one, as they post a notice; super
 * admins confirm or cancel it (the backend enforces the same).
 */
export default function EventsPage() {
  const { isAdmin, isSuperAdmin } = useAdminAuth()

  return (
    <section className="space-y-6">
      <PointPageHeader title={t('nav.events')} description={t('events.description')} />
      <PrizeEventsPanel groupId={TEMP_GROUP_ID} canCreate={isAdmin} canManage={isSuperAdmin} />
    </section>
  )
}
