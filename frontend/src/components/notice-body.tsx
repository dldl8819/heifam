'use client'

import { useEffect, useMemo, useState } from 'react'
import { apiClient } from '@/lib/api'
import { splitNoticeContent } from '@/lib/notice-images'
import { t } from '@/lib/i18n'

type NoticeBodyProps = {
  groupId: number
  content: string
}

/** A notice's text with its images drawn where the text names them. */
export function NoticeBody({ groupId, content }: NoticeBodyProps) {
  const parts = useMemo(() => splitNoticeContent(content), [content])
  // The same image may be named twice; its place among the images keeps each one's key steady
  // while the text around it is edited, so a preview does not load it again on every keystroke.
  let imageCount = 0

  return (
    <div className="space-y-3">
      {parts.map((part, index) => {
        if (part.kind === 'text') {
          return (
            <p
              key={`text-${index}`}
              className="whitespace-pre-wrap break-words text-sm text-slate-700 dark:text-slate-200"
            >
              {part.text}
            </p>
          )
        }
        imageCount += 1
        return <NoticeImage key={`image-${imageCount}-${part.imageId}`} groupId={groupId} imageId={part.imageId} />
      })}
    </div>
  )
}

type ImageState = { status: 'loading' } | { status: 'ready'; url: string } | { status: 'error' }

function NoticeImage({ groupId, imageId }: { groupId: number; imageId: number }) {
  const [state, setState] = useState<ImageState>({ status: 'loading' })
  const [attempt, setAttempt] = useState<number>(0)

  useEffect(() => {
    const controller = new AbortController()
    let objectUrl: string | null = null
    setState({ status: 'loading' })

    apiClient
      .getNoticeImage(groupId, imageId, controller.signal)
      .then((image) => {
        if (controller.signal.aborted) {
          return
        }
        objectUrl = URL.createObjectURL(image)
        setState({ status: 'ready', url: objectUrl })
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setState({ status: 'error' })
        }
      })

    return () => {
      controller.abort()
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl)
      }
    }
  }, [attempt, groupId, imageId])

  if (state.status === 'ready') {
    return (
      // The file comes through the API with the member's sign-in, so there is no address for
      // next/image to load; what is drawn is the Blob the page already holds.
      // eslint-disable-next-line @next/next/no-img-element
      <img
        src={state.url}
        alt={t('notices.posts.imageAlt')}
        className="h-auto max-w-full rounded-lg border border-slate-200 dark:border-slate-700"
      />
    )
  }

  return (
    <div className="flex min-h-24 flex-wrap items-center justify-center gap-2 rounded-lg border border-dashed border-slate-300 px-3 py-6 text-xs text-slate-500 dark:border-slate-600 dark:text-slate-400">
      {state.status === 'loading' ? (
        t('notices.posts.imageLoading')
      ) : (
        <>
          <span>{t('notices.posts.imageLoadError')}</span>
          <button
            type="button"
            onClick={() => setAttempt((previous) => previous + 1)}
            className="rounded-md border border-slate-300 px-2 py-1 font-medium text-slate-700 hover:bg-slate-50 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
          >
            {t('notices.posts.imageRetry')}
          </button>
        </>
      )}
    </div>
  )
}
