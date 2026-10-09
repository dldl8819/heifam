'use client'

import { useParams } from 'next/navigation'
import { BoardPost } from '@/components/board-post'

export default function AnonymousBoardPostPage() {
  const params = useParams<{ id: string }>()
  return <BoardPost board="anonymous" postId={Number(params.id)} />
}
