import { beforeEach, describe, expect, it } from 'vitest'
import { claimPageMemory, forgetAllPageState, recallPageState, rememberPageState } from '@/lib/page-memory'

describe('page memory', () => {
  beforeEach(() => {
    forgetAllPageState()
  })

  it('gives back what a page left when the member returns to it', () => {
    expect(recallPageState('balance')).toBeNull()

    rememberPageState('balance', { slots: [1, 2, null] })
    rememberPageState('multi', { mode: 'MMR_FIRST' })

    expect(recallPageState('balance')).toEqual({ slots: [1, 2, null] })
    expect(recallPageState('multi')).toEqual({ mode: 'MMR_FIRST' })
  })

  it('forgets everything on sign-out', () => {
    rememberPageState('balance', { slots: [1] })

    forgetAllPageState()

    expect(recallPageState('balance')).toBeNull()
  })

  it('keeps the state for the same account, also while the session is refreshed', () => {
    claimPageMemory('YOUR_USERNAME_1')
    rememberPageState('balance', { slots: [1] })

    claimPageMemory(null)
    claimPageMemory('YOUR_USERNAME_1')

    expect(recallPageState('balance')).toEqual({ slots: [1] })
  })

  it('never shows one account what another left behind', () => {
    claimPageMemory('YOUR_USERNAME_1')
    rememberPageState('balance', { slots: [1] })

    claimPageMemory('YOUR_USERNAME_2')

    expect(recallPageState('balance')).toBeNull()
  })
})
