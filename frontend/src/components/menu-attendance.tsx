'use client'

import { usePathname } from 'next/navigation'
import { useEffect } from 'react'
import { apiClient } from '@/lib/api'
import { useAdminAuth } from '@/lib/admin-auth'
import {
  attendanceMenuFor,
  isRecorded,
  kstDay,
  loadAttendanceRecord,
  saveAttendanceRecord,
  withRecorded,
} from '@/lib/menu-attendance'

/**
 * Tells the backend when a member opens an attendance menu, once a menu a day for each account.
 * The backend pays the point and keeps the count; nothing shows here, the points page tells how
 * the day's attendance stands.
 */
export function MenuAttendance() {
  const pathname = usePathname()
  const { isLoggedIn, canAccess, email } = useAdminAuth()

  useEffect(() => {
    if (!isLoggedIn || !canAccess || !email || !pathname) {
      return
    }
    const menu = attendanceMenuFor(pathname)
    if (!menu) {
      return
    }
    const day = kstDay()
    if (isRecorded(loadAttendanceRecord(), email, day, menu.key)) {
      return
    }
    apiClient
      .recordMenuVisit(menu.key)
      .then(() => saveAttendanceRecord(withRecorded(loadAttendanceRecord(), email, day, menu.key)))
      .catch(() => {
        // Not remembered, so the next opening of the menu tries again.
      })
  }, [pathname, isLoggedIn, canAccess, email])

  return null
}
