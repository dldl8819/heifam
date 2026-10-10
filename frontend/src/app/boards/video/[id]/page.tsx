'use client'

import { useParams } from 'next/navigation'
import { BoardPost } from '@/components/board-post'

export default function VideoBoardPostPage() {
  const params = useParams<{ id: string }>()
  return <BoardPost board="video" postId={Number(params.id)} />
}
