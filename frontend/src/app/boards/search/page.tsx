'use client'

import { Suspense } from 'react'
import { BoardSearch } from '@/components/board-search'

export default function BoardSearchPage() {
  // The word searched for is read from the address, which is known only in the browser.
  return (
    <Suspense fallback={null}>
      <BoardSearch />
    </Suspense>
  )
}
