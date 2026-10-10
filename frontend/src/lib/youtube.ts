/**
 * YouTube links as members paste them. Only a video's id is ever kept (eleven letters, digits, '-'
 * and '_'), and every address a page loads is built from that id alone. The backend
 * (YouTubeVideoIds) reads links the same way and is what decides.
 */

const VIDEO_ID = /^[A-Za-z0-9_-]{11}$/
const MAX_LINK_LENGTH = 500
const WATCH_HOSTS = new Set([
  'youtube.com',
  'www.youtube.com',
  'm.youtube.com',
  'music.youtube.com',
  'youtube-nocookie.com',
  'www.youtube-nocookie.com',
])
// Paths under which the next segment is the id: /shorts/ID, /embed/ID, /live/ID, /v/ID.
const ID_PATHS = new Set(['shorts', 'embed', 'live', 'v'])

export function isYouTubeVideoId(value: string | null | undefined): value is string {
  return typeof value === 'string' && VIDEO_ID.test(value)
}

/** The video a link points at, or null when it is not a link to one YouTube video. */
export function parseYouTubeVideoId(link: string | null | undefined): string | null {
  let text = (link ?? '').trim()
  if (text.length === 0 || text.length > MAX_LINK_LENGTH) {
    return null
  }
  if (!text.includes('://')) {
    // Pasted without the scheme, as "youtu.be/..." or "www.youtube.com/watch?v=...".
    text = `https://${text}`
  }
  let url: URL
  try {
    url = new URL(text)
  } catch {
    return null
  }
  if (url.protocol !== 'https:' && url.protocol !== 'http:') {
    return null
  }
  const host = url.hostname.toLowerCase()
  const segments = url.pathname.split('/')

  let candidate: string | null = null
  if (host === 'youtu.be' || host === 'www.youtu.be') {
    candidate = segments[1] ?? null
  } else if (WATCH_HOSTS.has(host)) {
    if (segments[1] === 'watch') {
      candidate = url.searchParams.get('v')
    } else if (segments.length > 2 && ID_PATHS.has(segments[1])) {
      candidate = segments[2]
    }
  }
  return isYouTubeVideoId(candidate) ? candidate : null
}

/** The player, from the address YouTube keeps no cookies on. */
export function youTubeEmbedUrl(videoId: string): string {
  return `https://www.youtube-nocookie.com/embed/${encodeURIComponent(videoId)}?rel=0`
}

/** The picture YouTube keeps for every video, 4:3 with the video in its middle. */
export function youTubeThumbnailUrl(videoId: string): string {
  return `https://i.ytimg.com/vi/${encodeURIComponent(videoId)}/hqdefault.jpg`
}

/** The short link to the video, as the edit form shows it and to open it on YouTube. */
export function youTubeWatchUrl(videoId: string): string {
  return `https://youtu.be/${encodeURIComponent(videoId)}`
}
