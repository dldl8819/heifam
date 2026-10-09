import { redirect } from 'next/navigation'

// The boards have no page of their own: /boards leads to the free board.
export default function BoardsPage() {
  redirect('/boards/free')
}
