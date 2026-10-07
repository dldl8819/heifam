import { describe, expect, it } from 'vitest'

import {
  NOTICE_IMAGE_ACCEPTED_TYPES,
  NoticeImageError,
  fitNoticeImage,
  insertNoticeImage,
  noticeImageEncodings,
  noticeImageIds,
  noticeImageMarker,
  prepareNoticeImage,
  splitNoticeContent,
} from '@/lib/notice-images'

describe('notice image markers', () => {
  it('names an image the way the backend reads it', () => {
    expect(noticeImageMarker(12)).toBe('[[image:12]]')
    expect(noticeImageIds(`a ${noticeImageMarker(12)} b`)).toEqual([12])
  })

  it('lists the images a text names once each, in order', () => {
    expect(noticeImageIds('[[image:12]]\ntext [[image:7]]\n[[image:12]] [[image:300]]')).toEqual([12, 7, 300])
    expect(noticeImageIds('plain text')).toEqual([])
  })

  it('ignores what only looks like a marker', () => {
    const content = '[[image:]] [[image:abc]] [image:5] [[image:-3]] [[image: 5]] [[IMAGE:5]] [[image:1234567890123456]]'

    expect(noticeImageIds(content)).toEqual([])
    expect(splitNoticeContent(content)).toEqual([{ kind: 'text', text: content }])
  })
})

describe('notice content parts', () => {
  it('keeps a text without images whole', () => {
    expect(splitNoticeContent('line one\n\nline two')).toEqual([{ kind: 'text', text: 'line one\n\nline two' }])
    expect(splitNoticeContent('')).toEqual([])
  })

  it('draws text and images in turn without the line breaks that set a marker apart', () => {
    expect(splitNoticeContent('intro\n[[image:1]]\nmiddle\n[[image:2]]\nend')).toEqual([
      { kind: 'text', text: 'intro' },
      { kind: 'image', imageId: 1 },
      { kind: 'text', text: 'middle' },
      { kind: 'image', imageId: 2 },
      { kind: 'text', text: 'end' },
    ])
  })

  it('keeps the empty lines the writer typed around an image', () => {
    expect(splitNoticeContent('intro\n\n[[image:1]]\n\nend')).toEqual([
      { kind: 'text', text: 'intro\n' },
      { kind: 'image', imageId: 1 },
      { kind: 'text', text: '\nend' },
    ])
  })

  it('handles a text that is one image, images side by side, and Windows line ends', () => {
    expect(splitNoticeContent('[[image:5]]')).toEqual([{ kind: 'image', imageId: 5 }])
    expect(splitNoticeContent('[[image:5]]\n[[image:6]]')).toEqual([
      { kind: 'image', imageId: 5 },
      { kind: 'image', imageId: 6 },
    ])
    expect(splitNoticeContent('a\r\n[[image:5]]\r\nb')).toEqual([
      { kind: 'text', text: 'a' },
      { kind: 'image', imageId: 5 },
      { kind: 'text', text: 'b' },
    ])
  })

  it('leaves an image inside a line where it is', () => {
    expect(splitNoticeContent('see [[image:5]] here')).toEqual([
      { kind: 'text', text: 'see ' },
      { kind: 'image', imageId: 5 },
      { kind: 'text', text: ' here' },
    ])
  })
})

describe('placing a marker in the text', () => {
  it('starts an empty text with the marker and moves to the next line', () => {
    expect(insertNoticeImage('', 0, 0, 3)).toEqual({ content: '[[image:3]]\n', caret: 12 })
  })

  it('gives the marker its own line in the middle of a line', () => {
    const result = insertNoticeImage('before after', 7, 7, 3)

    expect(result.content).toBe('before \n[[image:3]]\nafter')
    expect(result.content.slice(result.caret)).toBe('after')
  })

  it('adds no line break where one is already there', () => {
    const result = insertNoticeImage('first\n\nlast', 6, 6, 3)

    expect(result.content).toBe('first\n[[image:3]]\nlast')
    expect(result.content.slice(result.caret)).toBe('last')
  })

  it('replaces the selected text', () => {
    expect(insertNoticeImage('keep DROP keep', 5, 9, 3).content).toBe('keep \n[[image:3]]\n keep')
  })

  it('places a second marker after the first when the caret is carried over', () => {
    const first = insertNoticeImage('text', 4, 4, 1)
    const second = insertNoticeImage(first.content, first.caret, first.caret, 2)

    expect(second.content).toBe('text\n[[image:1]]\n[[image:2]]\n')
    expect(splitNoticeContent(second.content)).toEqual([
      { kind: 'text', text: 'text' },
      { kind: 'image', imageId: 1 },
      { kind: 'image', imageId: 2 },
    ])
  })

  it('treats a caret outside the text as its end', () => {
    expect(insertNoticeImage('text', 99, 120, 3).content).toBe('text\n[[image:3]]\n')
    expect(insertNoticeImage('text', -4, -1, 3).content).toBe('[[image:3]]\ntext')
  })
})

describe('sizing an image for upload', () => {
  it('never enlarges an image', () => {
    expect(fitNoticeImage(640, 480)).toEqual({ width: 640, height: 480 })
    expect(fitNoticeImage(1, 1)).toEqual({ width: 1, height: 1 })
  })

  it('keeps a long rules sheet at its full length', () => {
    expect(fitNoticeImage(1000, 9000)).toEqual({ width: 1000, height: 9000 })
  })

  it('narrows an image wider than the limit and keeps its shape', () => {
    expect(fitNoticeImage(4000, 3000)).toEqual({ width: 2000, height: 1500 })
  })

  it('stays within what a canvas can draw', () => {
    const tall = fitNoticeImage(1500, 30000)
    const huge = fitNoticeImage(2000, 12000)

    expect(tall.height).toBeLessThanOrEqual(16000)
    expect(tall.width * tall.height).toBeLessThanOrEqual(16_000_000)
    expect(huge.width * huge.height).toBeLessThanOrEqual(16_010_000)
    expect(huge.width / huge.height).toBeCloseTo(2000 / 12000, 2)
  })
})

describe('encodings tried for an upload', () => {
  it('keeps a capture exact first and falls back to ever smaller JPEGs', () => {
    const steps = noticeImageEncodings('image/png')

    expect(steps[0]).toEqual({ type: 'image/png', scale: 1 })
    expect(steps.slice(1).every((step) => step.type === 'image/jpeg')).toBe(true)
    expect(noticeImageEncodings('image/webp')[0].type).toBe('image/png')
  })

  it('never turns a photo into a PNG', () => {
    expect(noticeImageEncodings('image/jpeg').every((step) => step.type === 'image/jpeg')).toBe(true)
  })

  it('only gets smaller from one step to the next', () => {
    const lossy = noticeImageEncodings('image/jpeg')

    for (let index = 1; index < lossy.length; index += 1) {
      expect(lossy[index].scale).toBeLessThanOrEqual(lossy[index - 1].scale)
      expect(lossy[index].quality ?? 1).toBeLessThanOrEqual(lossy[index - 1].quality ?? 1)
    }
  })
})

describe('preparing a file', () => {
  it('refuses a kind of file that is not accepted before reading it', async () => {
    expect(NOTICE_IMAGE_ACCEPTED_TYPES).toEqual(['image/png', 'image/jpeg', 'image/webp'])
    for (const type of ['image/gif', 'image/svg+xml', 'text/html', '']) {
      const refusal = await prepareNoticeImage(new Blob(['x'], { type })).catch((error: unknown) => error)

      expect(refusal).toBeInstanceOf(NoticeImageError)
      expect((refusal as NoticeImageError).reason).toBe('unsupported')
    }
  })
})
