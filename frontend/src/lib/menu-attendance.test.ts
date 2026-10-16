import { describe, expect, it } from 'vitest'
import {
  ATTENDANCE_MENUS,
  attendanceMenuFor,
  attendanceMenuLabel,
  isRecorded,
  kstDay,
  menusLeft,
  withRecorded,
} from '@/lib/menu-attendance'

describe('menu attendance', () => {
  it('pays the ten menus the operator named', () => {
    expect(ATTENDANCE_MENUS.map(attendanceMenuLabel)).toEqual([
      '공지사항',
      '자유게시판',
      '익명게시판',
      '추천영상',
      '선수 관리',
      '랭킹',
      '승부 예측',
      '파트너스',
      '경기 결과',
      '포인트 정책',
    ])
  })

  it('counts a page under a menu as that menu, and nothing else', () => {
    expect(attendanceMenuFor('/notices')?.key).toBe('notices')
    expect(attendanceMenuFor('/notices/12')?.key).toBe('notices')
    expect(attendanceMenuFor('/boards/free/3')?.key).toBe('free-board')
    expect(attendanceMenuFor('/ads')?.key).toBe('partners')
    expect(attendanceMenuFor('/points/policy')?.key).toBe('point-policy')
    for (const path of ['/', '/points', '/points/ranking', '/boards/nickname', '/boards', '/rankings', '/balance']) {
      expect(attendanceMenuFor(path)).toBeNull()
    }
  })

  it('turns the day over at midnight in Korea', () => {
    expect(kstDay(new Date('2026-10-16T14:59:00Z'))).toBe('2026-10-16')
    expect(kstDay(new Date('2026-10-16T15:00:00Z'))).toBe('2026-10-17')
  })

  it('remembers a menu for one account and one day', () => {
    const record = withRecorded(null, 'member@example.com', '2026-10-17', 'ranking')

    expect(isRecorded(record, 'member@example.com', '2026-10-17', 'ranking')).toBe(true)
    expect(isRecorded(record, 'member@example.com', '2026-10-17', 'results')).toBe(false)
    expect(isRecorded(record, 'member@example.com', '2026-10-18', 'ranking')).toBe(false)
    expect(isRecorded(record, 'other@example.com', '2026-10-17', 'ranking')).toBe(false)

    const more = withRecorded(record, 'member@example.com', '2026-10-17', 'results')
    expect(more.menus).toEqual(['ranking', 'results'])
    expect(withRecorded(more, 'member@example.com', '2026-10-17', 'ranking').menus).toEqual(['ranking', 'results'])
    expect(withRecorded(more, 'member@example.com', '2026-10-18', 'notices').menus).toEqual(['notices'])
    expect(withRecorded(more, 'other@example.com', '2026-10-17', 'notices').menus).toEqual(['notices'])
  })

  it('lists the menus still to open, in menu order', () => {
    expect(menusLeft(['ranking', 'notices', 'unknown']).map((menu) => menu.key)).toEqual([
      'free-board',
      'anonymous-board',
      'video-board',
      'players',
      'predictions',
      'partners',
      'results',
      'point-policy',
    ])
    expect(menusLeft(ATTENDANCE_MENUS.map((menu) => menu.key))).toEqual([])
  })
})
