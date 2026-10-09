'use client'

import { useParams } from 'next/navigation'
import { BoardPost } from '@/components/board-post'

export default function FreeBoardPostPage() {
  const params = useParams<{ id: string }>()
  return <BoardPost board="free" postId={Number(params.id)} />
}
