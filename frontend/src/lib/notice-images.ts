/**
 * Images inside a notice. Where the text shows one it holds a marker, [[image:12]], naming the
 * uploaded image by id; the backend reads the same markers when the notice is saved.
 */

// Both limits are the backend's (NoticeImageService); it refuses what goes past them.
export const NOTICE_IMAGE_MAX_BYTES = 3 * 1024 * 1024
export const NOTICE_IMAGE_MAX_COUNT = 10
export const NOTICE_IMAGE_ACCEPTED_TYPES = ['image/png', 'image/jpeg', 'image/webp']

// Wide enough to stay sharp on a large screen; a long rules sheet keeps its length.
const MAX_WIDTH = 2000
const MAX_HEIGHT = 16000
// Safari draws nothing on a canvas with more pixels than this.
const MAX_PIXELS = 16_000_000

const MARKER_PATTERN = '\\[\\[image:(\\d{1,15})]]'

export type NoticeContentPart =
  | { kind: 'text'; text: string }
  | { kind: 'image'; imageId: number }

export function noticeImageMarker(imageId: number): string {
  return `[[image:${imageId}]]`
}

/** The images a text names, each once, in the order they first appear. */
export function noticeImageIds(content: string): number[] {
  const ids: number[] = []
  for (const match of content.matchAll(new RegExp(MARKER_PATTERN, 'g'))) {
    const imageId = Number(match[1])
    if (!ids.includes(imageId)) {
      ids.push(imageId)
    }
  }
  return ids
}

/**
 * Cuts a text into what is drawn: text and images in turn. The line break that only sets a marker
 * on its own line is dropped, so an image is not framed by empty lines the writer never typed.
 */
export function splitNoticeContent(content: string): NoticeContentPart[] {
  const parts: NoticeContentPart[] = []
  const pushText = (text: string, afterImage: boolean, beforeImage: boolean): void => {
    let trimmed = text
    if (afterImage) {
      trimmed = trimmed.replace(/^\r?\n/, '')
    }
    if (beforeImage) {
      trimmed = trimmed.replace(/\r?\n$/, '')
    }
    if (trimmed.length > 0) {
      parts.push({ kind: 'text', text: trimmed })
    }
  }

  let cursor = 0
  let afterImage = false
  for (const match of content.matchAll(new RegExp(MARKER_PATTERN, 'g'))) {
    const start = match.index ?? 0
    pushText(content.slice(cursor, start), afterImage, true)
    parts.push({ kind: 'image', imageId: Number(match[1]) })
    cursor = start + match[0].length
    afterImage = true
  }
  pushText(content.slice(cursor), afterImage, false)
  return parts
}

/**
 * Puts an image's marker on a line of its own where the selection is, and says where the caret
 * goes next: the start of the line after it.
 */
export function insertNoticeImage(
  content: string,
  selectionStart: number,
  selectionEnd: number,
  imageId: number
): { content: string; caret: number } {
  const start = Math.max(0, Math.min(selectionStart, content.length))
  const end = Math.max(start, Math.min(selectionEnd, content.length))
  const before = content.slice(0, start)
  const after = content.slice(end)
  const lineBreakBefore = before.length === 0 || before.endsWith('\n') ? '' : '\n'
  const followedByLineBreak = after.startsWith('\n')
  const inserted = `${lineBreakBefore}${noticeImageMarker(imageId)}${followedByLineBreak ? '' : '\n'}`
  return {
    content: `${before}${inserted}${after}`,
    caret: before.length + inserted.length + (followedByLineBreak ? 1 : 0),
  }
}

/** The size an image is drawn at before upload: never larger than it is, and within the limits. */
export function fitNoticeImage(width: number, height: number): { width: number; height: number } {
  const scale = Math.min(
    1,
    MAX_WIDTH / width,
    MAX_HEIGHT / height,
    Math.sqrt(MAX_PIXELS / (width * height))
  )
  return {
    width: Math.max(1, Math.round(width * scale)),
    height: Math.max(1, Math.round(height * scale)),
  }
}

export type NoticeImageEncoding = {
  type: 'image/png' | 'image/jpeg'
  quality?: number
  // Applied on top of fitNoticeImage.
  scale: number
}

/**
 * What is tried, in turn, until the file is small enough. A screen capture or a drawn sheet stays
 * exact as PNG when that fits; a photo, or anything too large that way, becomes a JPEG, first as
 * it is and then smaller.
 */
export function noticeImageEncodings(sourceType: string): NoticeImageEncoding[] {
  const lossy: NoticeImageEncoding[] = [
    { type: 'image/jpeg', quality: 0.9, scale: 1 },
    { type: 'image/jpeg', quality: 0.8, scale: 1 },
    { type: 'image/jpeg', quality: 0.8, scale: 0.8 },
    { type: 'image/jpeg', quality: 0.8, scale: 0.65 },
    { type: 'image/jpeg', quality: 0.75, scale: 0.5 },
  ]
  return sourceType === 'image/jpeg' ? lossy : [{ type: 'image/png', scale: 1 }, ...lossy]
}

export class NoticeImageError extends Error {
  reason: 'unsupported' | 'unreadable' | 'tooLarge'

  constructor(reason: 'unsupported' | 'unreadable' | 'tooLarge') {
    super(`Notice image ${reason}`)
    this.reason = reason
  }
}

function encodeCanvas(canvas: HTMLCanvasElement, encoding: NoticeImageEncoding): Promise<Blob | null> {
  return new Promise((resolve) => {
    canvas.toBlob(resolve, encoding.type, encoding.quality)
  })
}

/**
 * Redraws a chosen file for upload (browser only). What goes up is the picture alone: where and
 * when a photo was taken, which a phone writes into the file, does not survive the redraw.
 */
export async function prepareNoticeImage(file: Blob): Promise<Blob> {
  if (!NOTICE_IMAGE_ACCEPTED_TYPES.includes(file.type)) {
    throw new NoticeImageError('unsupported')
  }

  let bitmap: ImageBitmap
  try {
    bitmap = await createImageBitmap(file)
  } catch {
    throw new NoticeImageError('unreadable')
  }

  try {
    const fitted = fitNoticeImage(bitmap.width, bitmap.height)
    for (const encoding of noticeImageEncodings(file.type)) {
      const canvas = document.createElement('canvas')
      canvas.width = Math.max(1, Math.round(fitted.width * encoding.scale))
      canvas.height = Math.max(1, Math.round(fitted.height * encoding.scale))
      const context = canvas.getContext('2d')
      if (!context) {
        throw new NoticeImageError('unreadable')
      }
      if (encoding.type === 'image/jpeg') {
        // JPEG has no transparency; see-through parts would turn black without this.
        context.fillStyle = '#ffffff'
        context.fillRect(0, 0, canvas.width, canvas.height)
      }
      context.drawImage(bitmap, 0, 0, canvas.width, canvas.height)

      const encoded = await encodeCanvas(canvas, encoding)
      // A browser that cannot write the asked type answers with another; that one is not sent.
      if (encoded && encoded.type === encoding.type && encoded.size <= NOTICE_IMAGE_MAX_BYTES) {
        return encoded
      }
    }
    throw new NoticeImageError('tooLarge')
  } finally {
    bitmap.close()
  }
}
