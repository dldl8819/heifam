import { describe, expect, it } from 'vitest'
import { drawAnnouncement } from './announce'

const NAMES = ['가람', '나래', '다온', '라온']

describe('drawAnnouncement', () => {
  it('says nothing before the start, and that the draw has started from the count on', () => {
    expect(drawAnnouncement('ready', [], NAMES, 2, 'FIRST')).toBe('')
    expect(drawAnnouncement('countdown', [], NAMES, 2, 'FIRST')).toBe('추첨을 시작합니다.')
    expect(drawAnnouncement('running', [], NAMES, 2, 'FIRST')).toBe('추첨을 시작합니다.')
  })

  it('tells each first-in place as its ball arrives, then stays on the last place', () => {
    expect(drawAnnouncement('running', [2], NAMES, 2, 'FIRST')).toBe('1등 다온')
    expect(drawAnnouncement('running', [2, 0], NAMES, 2, 'FIRST')).toBe('2등 가람')
    // Balls after the winners change nothing, so the reader is not told again.
    expect(drawAnnouncement('running', [2, 0, 3], NAMES, 2, 'FIRST')).toBe('2등 가람')
  })

  it('tells a last-in draw nothing until it is over', () => {
    expect(drawAnnouncement('running', [2, 0, 3], NAMES, 2, 'LAST')).toBe('추첨을 시작합니다.')
  })

  it('tells every place once the draw is over', () => {
    expect(drawAnnouncement('done', [2, 0, 3, 1], NAMES, 2, 'FIRST')).toBe('추첨이 끝났습니다. 1등 다온, 2등 가람')
    // Last in, first place.
    expect(drawAnnouncement('done', [2, 0, 3, 1], NAMES, 2, 'LAST')).toBe('추첨이 끝났습니다. 1등 나래, 2등 라온')
  })
})
