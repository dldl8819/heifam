import { describe, expect, it } from 'vitest'
import { isYouTubeVideoId, parseYouTubeVideoId, youTubeEmbedUrl, youTubeThumbnailUrl, youTubeWatchUrl } from './youtube'

const ID = 'dQw4w9WgXcQ'

describe('parseYouTubeVideoId', () => {
  it('reads the id from every usual form of link', () => {
    for (const link of [
      `https://www.youtube.com/watch?v=${ID}`,
      `https://www.youtube.com/watch?v=${ID}&t=42s&list=PL123`,
      `https://www.youtube.com/watch?feature=share&v=${ID}`,
      `https://youtube.com/watch?v=${ID}`,
      `https://m.youtube.com/watch?v=${ID}`,
      `https://music.youtube.com/watch?v=${ID}`,
      `https://youtu.be/${ID}`,
      `https://youtu.be/${ID}?si=abcDEF123&t=10`,
      `https://www.youtube.com/shorts/${ID}`,
      `https://www.youtube.com/live/${ID}?feature=shared`,
      `https://www.youtube.com/embed/${ID}`,
      `https://www.youtube-nocookie.com/embed/${ID}`,
      `http://www.youtube.com/watch?v=${ID}`,
      `  youtu.be/${ID}  `,
      `www.youtube.com/watch?v=${ID}`,
      `HTTPS://WWW.YOUTUBE.COM/watch?v=${ID}`,
    ]) {
      expect(parseYouTubeVideoId(link), link).toBe(ID)
    }
  })

  it('takes nothing that is not one YouTube video', () => {
    for (const link of [
      null,
      undefined,
      '',
      '   ',
      'https://www.youtube.com/',
      'https://www.youtube.com/watch',
      'https://www.youtube.com/watch?v=',
      'https://www.youtube.com/watch?v=short',
      `https://www.youtube.com/watch?v=${ID}x`,
      'https://www.youtube.com/@channel',
      'https://www.youtube.com/playlist?list=PL123',
      `https://youtube.com.example.com/watch?v=${ID}`,
      `https://example.com/watch?v=${ID}`,
      `https://example.com/youtu.be/${ID}`,
      `https://notyoutu.be/${ID}`,
      `javascript:alert(1)//youtu.be/${ID}`,
      `ftp://youtu.be/${ID}`,
      `https://youtu.be/${ID.slice(0, 10)}"`,
      `https://youtu.be/${'x'.repeat(600)}`,
    ]) {
      expect(parseYouTubeVideoId(link), String(link)).toBeNull()
    }
  })
})

describe('the addresses built from an id', () => {
  it('knows an id when it sees one', () => {
    expect(isYouTubeVideoId(ID)).toBe(true)
    expect(isYouTubeVideoId('a-b_c-d_e-f')).toBe(true)
    for (const value of [null, undefined, '', 'short', `${ID}x`, 'dQw4w9WgXc!']) {
      expect(isYouTubeVideoId(value)).toBe(false)
    }
  })

  it('builds the player, the picture and the short link from the id alone', () => {
    expect(youTubeEmbedUrl(ID)).toBe(`https://www.youtube-nocookie.com/embed/${ID}?rel=0`)
    expect(youTubeThumbnailUrl(ID)).toBe(`https://i.ytimg.com/vi/${ID}/hqdefault.jpg`)
    expect(youTubeWatchUrl(ID)).toBe(`https://youtu.be/${ID}`)
    // Whatever reaches them stays inside its own path segment.
    expect(youTubeEmbedUrl('a/b?c#d')).toBe('https://www.youtube-nocookie.com/embed/a%2Fb%3Fc%23d?rel=0')
  })
})
