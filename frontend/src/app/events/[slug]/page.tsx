import Link from 'next/link'
import { notFound } from 'next/navigation'
import {
  eventStatusLabels,
  events,
  getEventBySlug,
  type EventRecord,
} from '@/features/events/events'

type EventDetailPageProps = {
  params: Promise<{
    slug: string
  }>
}

export function generateStaticParams() {
  return events.map((event) => ({ slug: event.slug }))
}

export async function generateMetadata({ params }: EventDetailPageProps) {
  const { slug } = await params
  const event = getEventBySlug(slug)

  if (!event) {
    return {
      title: '이벤트',
    }
  }

  return {
    title: `${event.title} | Hei`,
    description: event.summary,
  }
}

function DetailList({
  title,
  items,
}: {
  title: string
  items: string[]
}) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900">
      <h3 className="text-sm font-bold text-slate-950 dark:text-slate-100">{title}</h3>
      <ul className="mt-3 space-y-2 text-sm leading-6 text-slate-700 dark:text-slate-300">
        {items.map((item) => (
          <li key={item} className="flex gap-2">
            <span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-cyan-500" />
            <span>{item}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}

function getStatusClass(status: EventRecord['status']): string {
  if (status === 'active') {
    return 'border-emerald-200 bg-emerald-50 text-emerald-800 dark:border-emerald-700/70 dark:bg-emerald-950/40 dark:text-emerald-200'
  }

  if (status === 'completed') {
    return 'border-slate-200 bg-slate-50 text-slate-700 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-300'
  }

  return 'border-amber-200 bg-amber-50 text-amber-800 dark:border-amber-700/70 dark:bg-amber-950/40 dark:text-amber-200'
}

function ScheduleList({ event }: { event: EventRecord }) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900 dark:[&>h3]:text-slate-100">
      <h3 className="text-sm font-bold text-slate-950">일정</h3>
      <dl className="mt-3 space-y-3">
        {event.schedule.map((item) => (
          <div key={`${event.slug}-${item.label}`} className="flex items-start justify-between gap-3">
            <dt className="text-sm text-slate-500 dark:text-slate-400">{item.label}</dt>
            <dd className="text-right text-sm font-semibold text-slate-950 dark:text-slate-100">{item.value}</dd>
          </div>
        ))}
      </dl>
    </section>
  )
}

export default async function EventDetailPage({ params }: EventDetailPageProps) {
  const { slug } = await params
  const event = getEventBySlug(slug)

  if (!event) {
    notFound()
  }

  return (
    <section className="space-y-6">
      <div className="rounded-lg border border-cyan-100 bg-white px-5 py-6 shadow-sm dark:border-cyan-900 dark:bg-slate-900 sm:px-6">
        <Link href="/events" className="text-sm font-semibold text-cyan-700 hover:text-cyan-900 dark:text-cyan-300 dark:hover:text-cyan-200">
          이벤트 목록
        </Link>
        <div className="mt-4 max-w-4xl space-y-3">
          <div className="flex flex-wrap items-center gap-2">
            <span className="rounded-md border border-cyan-200 bg-cyan-50 px-2.5 py-1 text-xs font-semibold text-cyan-800 dark:border-cyan-700/70 dark:bg-cyan-950/40 dark:text-cyan-200">
              {event.category}
            </span>
            <span className={`rounded-md border px-2.5 py-1 text-xs font-semibold ${getStatusClass(event.status)}`}>
              {eventStatusLabels[event.status]}
            </span>
          </div>
          <h2 className="text-3xl font-bold tracking-tight text-slate-950 dark:text-slate-100">{event.title}</h2>
          <p className="text-sm font-semibold text-slate-500 dark:text-slate-400">{event.dateLabel}</p>
          <p className="text-sm leading-6 text-slate-600 dark:text-slate-300">{event.summary}</p>
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(280px,360px)]">
        <div className="space-y-4">
          <DetailList title="진행 조건" items={event.conditions} />
          <DetailList title="참가 규정" items={event.rules} />
          <DetailList title="진행 방식" items={event.format} />
          <DetailList title="시상 내역" items={event.prizes} />
        </div>

        <aside className="space-y-4">
          <ScheduleList event={event} />
          <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900 dark:[&>h3]:text-slate-100">
            <h3 className="text-sm font-bold text-slate-950">팀 편성</h3>
            <p className="mt-3 text-sm leading-6 text-slate-700 dark:text-slate-300">{event.teamAssignmentNote}</p>
          </section>
          {event.sponsors.length > 0 && (
            <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900 dark:[&>h3]:text-slate-100">
              <h3 className="text-sm font-bold text-slate-950">후원</h3>
              <p className="mt-3 text-sm font-semibold text-slate-700 dark:text-slate-300">
                {event.sponsors.join(', ')}
              </p>
            </section>
          )}
          {event.resultNote && (
            <section className="rounded-lg border border-emerald-200 bg-emerald-50 p-5 dark:border-emerald-700/70 dark:bg-emerald-950/40 dark:[&>h3]:text-emerald-200">
              <h3 className="text-sm font-bold text-emerald-900">결과</h3>
              <p className="mt-3 text-sm leading-6 text-emerald-800 dark:text-emerald-200">{event.resultNote}</p>
            </section>
          )}
        </aside>
      </div>
    </section>
  )
}
