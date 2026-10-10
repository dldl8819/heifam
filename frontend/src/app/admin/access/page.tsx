import { redirect } from 'next/navigation'

// Access was one page and is three now; an old link leads to the first of them.
export default function AccessControlPage() {
  redirect('/admin/access/admins')
}
