import { describe, expect, it } from 'vitest'
import { normalizeLedgerTargetInput } from './ledger-target'
import type { BalancePlayerOption } from '@/types/api'

const roster: BalancePlayerOption[] = [
  { id: 1, nickname: 'YOUR_NICKNAME_ALPHA', race: 'P' },
  { id: 2, nickname: 'YOUR_NICKNAME_BETA', race: 'T' },
  { id: 3, nickname: 'YOUR_NICKNAME_BETA_TWO', race: 'Z' },
]

describe('normalizeLedgerTargetInput', () => {
  it('canonicalizes a single unique nickname prefix', () => {
    expect(normalizeLedgerTargetInput('YOUR_NICKNAME_ALP', roster)).toBe('YOUR_NICKNAME_ALPHA')
  })

  it('canonicalizes multiple comma-separated nicknames', () => {
    expect(normalizeLedgerTargetInput('YOUR_NICKNAME_ALP, YOUR_NICKNAME_ALPHA', roster)).toBe(
      'YOUR_NICKNAME_ALPHA, YOUR_NICKNAME_ALPHA'
    )
  })

  it('keeps a token unchanged when the prefix is ambiguous', () => {
    expect(normalizeLedgerTargetInput('YOUR_NICKNAME_BETA', roster)).toBe('YOUR_NICKNAME_BETA')
  })

  it('keeps a token unchanged when it matches no roster nickname', () => {
    expect(normalizeLedgerTargetInput('결제 수단 예시', roster)).toBe('결제 수단 예시')
  })

  it('trims whitespace and drops empty segments', () => {
    expect(normalizeLedgerTargetInput('  YOUR_NICKNAME_ALP , , YOUR_NICKNAME_BETA_T ', roster)).toBe(
      'YOUR_NICKNAME_ALPHA, YOUR_NICKNAME_BETA_TWO'
    )
  })

  it('returns an empty string for blank input', () => {
    expect(normalizeLedgerTargetInput('', roster)).toBe('')
    expect(normalizeLedgerTargetInput('   ', roster)).toBe('')
  })
})
