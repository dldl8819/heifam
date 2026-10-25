import { describe, expect, it } from 'vitest'
import { isColorTheme, isContrastMode, resolveColorTheme, resolveContrastMode } from '@/lib/theme'

describe('theme preference', () => {
  it('keeps an explicit user selection ahead of the system preference', () => {
    expect(resolveColorTheme('light', true)).toBe('light')
    expect(resolveColorTheme('dark', false)).toBe('dark')
  })

  it('uses the system preference when no valid user selection exists', () => {
    expect(resolveColorTheme(null, true)).toBe('dark')
    expect(resolveColorTheme(null, false)).toBe('light')
    expect(resolveColorTheme('unexpected', true)).toBe('dark')
  })

  it('accepts only supported stored theme values', () => {
    expect(isColorTheme('light')).toBe(true)
    expect(isColorTheme('dark')).toBe(true)
    expect(isColorTheme('system')).toBe(false)
  })
})

describe('contrast preference', () => {
  it('keeps a choice made on this browser ahead of the device setting', () => {
    expect(resolveContrastMode('normal', true)).toBe('normal')
    expect(resolveContrastMode('more', false)).toBe('more')
  })

  it("follows the device's increase-contrast setting when nothing was chosen", () => {
    expect(resolveContrastMode(null, true)).toBe('more')
    expect(resolveContrastMode(null, false)).toBe('normal')
    expect(resolveContrastMode('high', false)).toBe('normal')
  })

  it('accepts only the two stored values', () => {
    expect(isContrastMode('more')).toBe(true)
    expect(isContrastMode('normal')).toBe(true)
    expect(isContrastMode('less')).toBe(false)
  })
})
