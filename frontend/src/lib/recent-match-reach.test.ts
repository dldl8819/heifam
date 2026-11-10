import { describe, expect, it } from 'vitest'
import { recentMatchMaxLimit } from './recent-match-reach'

describe('recentMatchMaxLimit', () => {
  it('lets admins page back through the newest 100 matches', () => {
    expect(recentMatchMaxLimit(true)).toBe(100)
  })

  it('keeps members at the newest 30 matches', () => {
    expect(recentMatchMaxLimit(false)).toBe(30)
  })
})
