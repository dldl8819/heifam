import { describe, expect, it } from 'vitest'
import { isColorTheme, resolveColorTheme } from '@/lib/theme'

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
