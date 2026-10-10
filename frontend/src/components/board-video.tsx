'use client'

import { t } from '@/lib/i18n'
import { parseYouTubeVideoId, youTubeEmbedUrl, youTubeThumbnailUrl, youTubeWatchUrl } from '@/lib/youtube'

const fieldClass = 'w-full rounded-md border border-slate-300 px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-900'

/**
 * A YouTube video played in the page. A video whose uploader does not let it be shown elsewhere
 * says so in the player; the link under it opens it on YouTube.
 */
export function YouTubePlayer({ videoId, title }: { videoId: string; title: string }) {
  return (
    <div className="space-y-1.5">
      <div className="relative aspect-video w-full overflow-hidden rounded-lg bg-black">
        <iframe
          src={youTubeEmbedUrl(videoId)}
          title={title}
          className="absolute inset-0 h-full w-full"
          allow="accelerometer; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
          allowFullScreen
          referrerPolicy="strict-origin-when-cross-origin"
        />
      </div>
      <a
        href={youTubeWatchUrl(videoId)}
        target="_blank"
        rel="noopener noreferrer"
        className="inline-block text-xs text-slate-500 underline-offset-2 hover:underline dark:text-slate-400"
      >
        {t('boards.video.openOnYouTube')}
      </a>
    </div>
  )
}

/** The picture of a video, with a play mark over it; it fills the box it is put in. */
export function YouTubeThumbnail({ videoId }: { videoId: string }) {
  return (
    <span className="relative block aspect-video w-full overflow-hidden rounded-lg bg-slate-900">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src={youTubeThumbnailUrl(videoId)} alt="" loading="lazy" className="h-full w-full object-cover" />
      <span className="absolute inset-0 flex items-center justify-center" aria-hidden="true">
        <span className="flex h-10 w-14 items-center justify-center rounded-xl bg-red-600/90 shadow-lg">
          <svg viewBox="0 0 24 24" className="h-5 w-5 fill-white">
            <path d="M8 5v14l11-7z" />
          </svg>
        </span>
      </span>
    </span>
  )
}

/** Where the writer pastes the link: it says at once whether the link is one video, and shows which. */
export function VideoLinkField({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const videoId = parseYouTubeVideoId(value)
  return (
    <div className="space-y-2">
      <input
        type="url"
        inputMode="url"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        maxLength={500}
        placeholder={t('boards.video.linkPlaceholder')}
        aria-label={t('boards.video.linkPlaceholder')}
        className={fieldClass}
      />
      {value.trim().length > 0 && !videoId && (
        <p className="text-xs text-rose-600 dark:text-rose-400">{t('boards.video.linkInvalid')}</p>
      )}
      {videoId && (
        <div className="w-48 max-w-full">
          <YouTubeThumbnail videoId={videoId} />
        </div>
      )}
    </div>
  )
}
