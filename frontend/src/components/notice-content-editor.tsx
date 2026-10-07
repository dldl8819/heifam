'use client'

import { ChangeEvent, ClipboardEvent, useCallback, useEffect, useRef, useState } from 'react'
import { ApiRequestError, apiClient } from '@/lib/api'
import { NoticeBody } from '@/components/notice-body'
import {
  NOTICE_IMAGE_ACCEPTED_TYPES,
  NOTICE_IMAGE_MAX_BYTES,
  NOTICE_IMAGE_MAX_COUNT,
  NoticeImageError,
  insertNoticeImage,
  noticeImageIds,
  prepareNoticeImage,
} from '@/lib/notice-images'
import { t } from '@/lib/i18n'

type NoticeContentEditorProps = {
  groupId: number
  value: string
  onChange: (value: string) => void
  placeholder: string
  // The form holds its save back while an image is still on its way up.
  onUploadingChange?: (uploading: boolean) => void
}

function uploadErrorMessage(error: unknown): string {
  const maxMegabytes = NOTICE_IMAGE_MAX_BYTES / (1024 * 1024)
  if (error instanceof NoticeImageError) {
    return error.reason === 'tooLarge'
      ? t('notices.posts.imageTooLarge', { max: maxMegabytes })
      : t(error.reason === 'unsupported' ? 'notices.posts.imageUnsupported' : 'notices.posts.imageUnreadable')
  }
  if (error instanceof ApiRequestError) {
    if (error.status === 413) {
      return t('notices.posts.imageTooLarge', { max: maxMegabytes })
    }
    if (error.status === 415) {
      return t('notices.posts.imageUnsupported')
    }
    if (error.status === 409) {
      return t('notices.posts.imageTooManyWaiting')
    }
  }
  return t('notices.posts.imageUploadFailed')
}

/**
 * The text box of a notice with its images: a picked or pasted image is uploaded and a marker for
 * it lands on its own line at the caret. Below, the notice is drawn as readers will see it.
 */
export function NoticeContentEditor({
  groupId,
  value,
  onChange,
  placeholder,
  onUploadingChange,
}: NoticeContentEditorProps) {
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  // Uploads finish after the render that started them; these hold the text and caret as they are now.
  const valueRef = useRef<string>(value)
  const pendingCaretRef = useRef<number | null>(null)
  // Until the writer has been in the box it has no caret worth following: images go to the end.
  const caretSeenRef = useRef<boolean>(false)
  const [uploading, setUploading] = useState<boolean>(false)
  const [uploadError, setUploadError] = useState<string | null>(null)

  valueRef.current = value

  useEffect(() => {
    const caret = pendingCaretRef.current
    if (caret !== null && textareaRef.current) {
      pendingCaretRef.current = null
      textareaRef.current.setSelectionRange(caret, caret)
    }
  }, [value])

  const addImages = useCallback(
    async (files: File[]) => {
      if (files.length === 0) {
        return
      }
      setUploadError(null)
      setUploading(true)
      onUploadingChange?.(true)
      try {
        for (const file of files) {
          if (noticeImageIds(valueRef.current).length >= NOTICE_IMAGE_MAX_COUNT) {
            setUploadError(t('notices.posts.imageTooMany', { max: NOTICE_IMAGE_MAX_COUNT }))
            break
          }
          const prepared = await prepareNoticeImage(file)
          const uploaded = await apiClient.uploadNoticeImage(groupId, prepared)

          const textarea = caretSeenRef.current ? textareaRef.current : null
          const current = valueRef.current
          const caret = pendingCaretRef.current
          const next = insertNoticeImage(
            current,
            caret ?? textarea?.selectionStart ?? current.length,
            caret ?? textarea?.selectionEnd ?? current.length,
            uploaded.id
          )
          valueRef.current = next.content
          pendingCaretRef.current = next.caret
          caretSeenRef.current = true
          onChange(next.content)
        }
      } catch (error) {
        setUploadError(uploadErrorMessage(error))
      } finally {
        setUploading(false)
        onUploadingChange?.(false)
      }
    },
    [groupId, onChange, onUploadingChange]
  )

  const handlePick = useCallback(
    (event: ChangeEvent<HTMLInputElement>) => {
      const files = Array.from(event.target.files ?? [])
      // Cleared so that picking the same file again still counts as a change.
      event.target.value = ''
      void addImages(files)
    },
    [addImages]
  )

  const handlePaste = useCallback(
    (event: ClipboardEvent<HTMLTextAreaElement>) => {
      const images = Array.from(event.clipboardData.files).filter((file) => file.type.startsWith('image/'))
      // Text copied from a document may come with a picture of itself; that paste stays text.
      if (images.length === 0 || event.clipboardData.getData('text/plain').length > 0) {
        return
      }
      event.preventDefault()
      void addImages(images)
    },
    [addImages]
  )

  const hasImages = noticeImageIds(value).length > 0

  return (
    <div className="space-y-2">
      <textarea
        ref={textareaRef}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        onFocus={() => {
          caretSeenRef.current = true
        }}
        onPaste={handlePaste}
        placeholder={placeholder}
        rows={8}
        className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-900"
      />
      <div className="flex flex-wrap items-center gap-2">
        <input
          ref={fileInputRef}
          type="file"
          accept={NOTICE_IMAGE_ACCEPTED_TYPES.join(',')}
          multiple
          onChange={handlePick}
          className="hidden"
        />
        <button
          type="button"
          onClick={() => fileInputRef.current?.click()}
          disabled={uploading}
          className="rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
        >
          {uploading ? t('notices.posts.imageUploading') : t('notices.posts.imageAdd')}
        </button>
        <p className="text-xs text-slate-500 dark:text-slate-400">
          {t('notices.posts.imageHint', { max: NOTICE_IMAGE_MAX_COUNT })}
        </p>
      </div>
      {uploadError && (
        <p className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700 dark:border-rose-800 dark:bg-rose-950/40 dark:text-rose-300">
          {uploadError}
        </p>
      )}
      {hasImages && (
        <div className="space-y-2 rounded-lg border border-slate-200 bg-white p-3 dark:border-slate-700 dark:bg-slate-900">
          <p className="text-xs text-slate-500 dark:text-slate-400">{t('notices.posts.imageMarkerHint')}</p>
          <p className="text-xs font-semibold text-slate-700 dark:text-slate-200">{t('notices.posts.imagePreview')}</p>
          <NoticeBody groupId={groupId} content={value} />
        </div>
      )}
    </div>
  )
}
