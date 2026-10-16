import { redirect } from 'next/navigation'

/** The cannon draw moved into the prize draw page, next to the pinball; an old link still gets there. */
export default function CannonPage() {
  redirect('/draws')
}
