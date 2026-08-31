import { describe, expect, it } from 'vitest'

import { escapeCsvCell } from './csv-download'

describe('escapeCsvCell', () => {
  it('wraps plain values in quotes', () => {
    expect(escapeCsvCell('YOUR_VALUE')).toBe('"YOUR_VALUE"')
  })

  it('doubles embedded double quotes', () => {
    expect(escapeCsvCell('say "hi"')).toBe('"say ""hi"""')
  })

  it('preserves commas and newlines inside the quoted cell', () => {
    expect(escapeCsvCell('a,b\nc')).toBe('"a,b\nc"')
  })
})
