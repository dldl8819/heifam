'use client'

import { useEffect, useRef, useState } from 'react'
import { t } from '@/lib/i18n'
import { shareLink } from '@/lib/share-link'

type ShareLinkButtonProps = {
  title: string
  // The page's path on this site, such as /notices/5; the link is made from the address in use.
  path: string
}

/**
 * 링크 공유: the system share sheet on a phone (the app added to the home screen has no address bar
 * to copy from), the link copied on a computer. If even copying fails, the link is shown to copy by hand.
 */
export function ShareLinkButton({ title, path }: ShareLinkButtonProps) {
  const [copied, setCopied] = useState<boolean>(false)
  const [failedUrl, setFailedUrl] = useState<string | null>(null)
  const resetTimer = useRef<number | null>(null)

  useEffect(
    () => () => {
      if (resetTimer.current !== null) {
        window.clearTimeout(resetTimer.current)
      }
    },
    [],
  )

  const handleClick = async () => {
    const url = new URL(path, window.location.origin).toString()
    setFailedUrl(null)
    try {
      if ((await shareLink({ title, url })) === 'copied') {
        setCopied(true)
        if (resetTimer.current !== null) {
          window.clearTimeout(resetTimer.current)
        }
        resetTimer.current = window.setTimeout(() => setCopied(false), 2000)
      }
    } catch {
      setFailedUrl(url)
    }
  }

  return (
    <>
      <button
        type="button"
        onClick={() => void handleClick()}
        className="rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 dark:border-slate-600 dark:text-slate-200 dark:hover:bg-slate-800"
      >
        {copied ? t('common.shareLinkCopied') : t('common.shareLink')}
      </button>
      {failedUrl && (
        <span role="alert" className="basis-full text-xs text-rose-700 dark:text-rose-300">
          {t('common.shareLinkFailed')} <span className="break-all select-all">{failedUrl}</span>
        </span>
      )}
    </>
  )
}
