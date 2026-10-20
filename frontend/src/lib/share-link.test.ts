import { describe, expect, it, vi } from 'vitest'
import { shareLink, type ShareEnvironment } from '@/lib/share-link'

const LINK = { title: '공지', url: 'https://www.heifam.co.kr/notices/5' }

function environment(overrides: Partial<ShareEnvironment> = {}): ShareEnvironment {
  return { share: vi.fn(async () => {}), prefersShareSheet: true, copy: vi.fn(async () => {}), ...overrides }
}

describe('shareLink', () => {
  it('opens the share sheet on a phone, with the title and the link', async () => {
    const env = environment()

    expect(await shareLink(LINK, env)).toBe('shared')
    expect(env.share).toHaveBeenCalledWith({ title: '공지', url: 'https://www.heifam.co.kr/notices/5' })
    expect(env.copy).not.toHaveBeenCalled()
  })

  it('copies the link on a computer, or where there is no share sheet', async () => {
    const computer = environment({ prefersShareSheet: false })
    expect(await shareLink(LINK, computer)).toBe('copied')
    expect(computer.share).not.toHaveBeenCalled()
    expect(computer.copy).toHaveBeenCalledWith('https://www.heifam.co.kr/notices/5')

    const noSheet = environment({ share: undefined })
    expect(await shareLink(LINK, noSheet)).toBe('copied')
  })

  it('takes a closed share sheet as no failure, and copies when the sheet fails otherwise', async () => {
    const closed = environment({ share: vi.fn(async () => Promise.reject(Object.assign(new Error('closed'), { name: 'AbortError' }))) })
    expect(await shareLink(LINK, closed)).toBe('cancelled')
    expect(closed.copy).not.toHaveBeenCalled()

    const broken = environment({ share: vi.fn(async () => Promise.reject(new Error('NotAllowedError'))) })
    expect(await shareLink(LINK, broken)).toBe('copied')
    expect(broken.copy).toHaveBeenCalledWith('https://www.heifam.co.kr/notices/5')
  })

  it('fails when the link cannot be copied either', async () => {
    const env = environment({ prefersShareSheet: false, copy: vi.fn(async () => Promise.reject(new Error('denied'))) })

    await expect(shareLink(LINK, env)).rejects.toThrow('denied')
  })
})
