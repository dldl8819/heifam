import { describe, expect, it } from 'vitest'
import {
  DEFAULT_PAGE_SIZE,
  PAGE_SIZE_OPTIONS,
  pageCountWithinReach,
  toPageSize,
  visiblePageNumbers,
} from './list-pagination'

describe('page sizes', () => {
  it('offers 10, 20, 50 and 100 a page and starts at 10', () => {
    expect(PAGE_SIZE_OPTIONS).toEqual([10, 20, 50, 100])
    expect(DEFAULT_PAGE_SIZE).toBe(10)
  })

  it('takes only an offered size and falls back to 10', () => {
    expect(toPageSize('50')).toBe(50)
    expect(toPageSize(100)).toBe(100)
    expect(toPageSize('30')).toBe(10)
    expect(toPageSize('abc')).toBe(10)
  })
})

describe('pageCountWithinReach', () => {
  it('splits an admin reach of 100 by the page size', () => {
    expect(pageCountWithinReach(100, 10, null)).toBe(10)
    expect(pageCountWithinReach(100, 20, null)).toBe(5)
    expect(pageCountWithinReach(100, 50, null)).toBe(2)
    expect(pageCountWithinReach(100, 100, null)).toBe(1)
  })

  it('splits a member reach of 30 by the page size', () => {
    expect(pageCountWithinReach(30, 10, null)).toBe(3)
    expect(pageCountWithinReach(30, 20, null)).toBe(2)
    expect(pageCountWithinReach(30, 50, null)).toBe(1)
    expect(pageCountWithinReach(30, 100, null)).toBe(1)
  })

  it('ends at a page that came back short', () => {
    expect(pageCountWithinReach(100, 10, 3)).toBe(4)
    expect(pageCountWithinReach(100, 10, 0)).toBe(1)
    expect(pageCountWithinReach(30, 20, 5)).toBe(2)
  })
})

describe('visiblePageNumbers', () => {
  it('shows every page when there are seven or fewer', () => {
    expect(visiblePageNumbers(0, 3)).toEqual([0, 1, 2])
    expect(visiblePageNumbers(4, 7)).toEqual([0, 1, 2, 3, 4, 5, 6])
    expect(visiblePageNumbers(0, 0)).toEqual([])
  })

  it('keeps seven slots with gaps for longer runs', () => {
    expect(visiblePageNumbers(0, 10)).toEqual([0, 1, 2, 3, 4, null, 9])
    expect(visiblePageNumbers(3, 10)).toEqual([0, 1, 2, 3, 4, null, 9])
    expect(visiblePageNumbers(5, 10)).toEqual([0, null, 4, 5, 6, null, 9])
    expect(visiblePageNumbers(6, 10)).toEqual([0, null, 5, 6, 7, 8, 9])
    expect(visiblePageNumbers(9, 10)).toEqual([0, null, 5, 6, 7, 8, 9])
    expect(visiblePageNumbers(200, 426)).toEqual([0, null, 199, 200, 201, null, 425])
  })

  it('clamps a page out of range', () => {
    expect(visiblePageNumbers(99, 10)).toEqual([0, null, 5, 6, 7, 8, 9])
    expect(visiblePageNumbers(-1, 10)).toEqual([0, 1, 2, 3, 4, null, 9])
  })
})
