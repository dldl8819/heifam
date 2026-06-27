'use client'

import { useState } from 'react'
import Link from 'next/link'
import {
  eventStatusLabels,
  filterEvents,
  type EventStatusFilter,
} from '@/features/events/events'

const statusFilters: EventStatusFilter[] = ['all', 'scheduled', 'active', 'completed']

function getStatusClass(status: EventStatusFilter): string {
  if (status === 'scheduled') {
    return 'border-amber-200 bg-amber-50 text-amber-800'
  }

  if (status === 'active') {
    return 'border-emerald-200 bg-emerald-50 text-emerald-800'
  }

  if (status === 'completed') {
    return 'border-slate-200 bg-slate-50 text-slate-700'
  }

  return 'border-cyan-200 bg-cyan-50 text-cyan-800'
}

export default function EventsPage() {
  const [selectedStatus, setSelectedStatus] = useState<EventStatusFilter>('all')
  const filteredEvents = filterEvents(selectedStatus)

  return (
    <section className="space-y-6">
      <div className="rounded-lg border border-cyan-100 bg-white px-5 py-6 shadow-sm sm:px-6">
        <p className="text-xs font-semibold uppercase tracking-[0.2em] text-cyan-700">
          Hei`Fam Events
        </p>
        <div className="mt-3 max-w-3xl space-y-2">
          <h2 className="text-3xl font-bold tracking-tight text-slate-950">이벤트</h2>
          <p className="text-sm leading-6 text-slate-600">
            진행 예정인 이벤트와 지난 이벤트 기록을 모아 확인합니다.
          </p>
        </div>
      </div>

      <div className="flex flex-wrap gap-2">
        {statusFilters.map((status) => {
          const active = selectedStatus === status

          return (
            <button
              key={status}
              type="button"
              onClick={() => setSelectedStatus(status)}
              className={`rounded-md border px-3 py-2 text-sm font-semibold transition-colors ${
                active
                  ? getStatusClass(status)
                  : 'border-slate-200 bg-white text-slate-600 hover:border-cyan-300 hover:bg-cyan-50'
              }`}
            >
              {eventStatusLabels[status]}
            </button>
          )
        })}
      </div>

      {filteredEvents.length === 0 ? (
        <div className="rounded-lg border border-dashed border-slate-300 bg-white px-5 py-10 text-center text-sm text-slate-500">
          해당 상태의 이벤트가 없습니다.
        </div>
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          {filteredEvents.map((event) => (
            <article
              key={event.slug}
              className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm"
            >
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="space-y-2">
                  <span
                    className={`inline-flex rounded-md border px-2.5 py-1 text-xs font-semibold ${getStatusClass(
                      event.status,
                    )}`}
                  >
                    {eventStatusLabels[event.status]}
                  </span>
                  <div>
                    <p className="text-xs font-semibold text-cyan-700">{event.category}</p>
                    <h3 className="mt-1 text-xl font-bold text-slate-950">
                      <Link href={`/events/${event.slug}`} className="hover:text-cyan-700">
                        {event.title}
                      </Link>
                    </h3>
                  </div>
                </div>
                <p className="rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-xs font-semibold text-slate-600">
                  {event.dateLabel}
                </p>
              </div>

              <p className="mt-4 text-sm leading-6 text-slate-600">{event.summary}</p>

              <dl className="mt-5 grid gap-2 sm:grid-cols-3">
                {event.schedule.map((item) => (
                  <div
                    key={`${event.slug}-${item.label}`}
                    className="rounded-md border border-slate-200 bg-slate-50 px-3 py-2"
                  >
                    <dt className="text-[11px] font-semibold text-slate-500">{item.label}</dt>
                    <dd className="mt-1 text-sm font-semibold text-slate-900">{item.value}</dd>
                  </div>
                ))}
              </dl>

              <div className="mt-5 flex flex-wrap items-center gap-2">
                <Link
                  href={`/events/${event.slug}`}
                  className="inline-flex rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-slate-800"
                >
                  상세 보기
                </Link>
              </div>
            </article>
          ))}
        </div>
      )}
    </section>
  )
}
