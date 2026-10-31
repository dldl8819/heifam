import { redirect } from 'next/navigation'

/** The prize events moved to the events page; an old link still gets there. */
export default function PointEventsPage() {
  redirect('/events')
}
