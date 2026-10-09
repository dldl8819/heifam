import { describe, expect, it } from 'vitest'
import {
  NICKNAME_REQUEST_NICKNAME_MAX_LENGTH,
  NICKNAME_REQUEST_NOTE_MAX_LENGTH,
  NICKNAME_REQUEST_REASON_MAX_LENGTH,
  nicknameRequestStatusClass,
  validateNicknameDecisionNote,
  validateNicknameRequest,
} from './nickname-requests'

describe('validateNicknameRequest', () => {
  it('takes a new nickname with or without a reason', () => {
    expect(validateNicknameRequest('YOUR_USERNAME', '', 'OLD_NAME')).toBeNull()
    expect(validateNicknameRequest('  YOUR_USERNAME  ', ' because ', null)).toBeNull()
    expect(validateNicknameRequest('x'.repeat(NICKNAME_REQUEST_NICKNAME_MAX_LENGTH), 'y'.repeat(NICKNAME_REQUEST_REASON_MAX_LENGTH))).toBeNull()
  })

  it('asks for a nickname', () => {
    expect(validateNicknameRequest('   ', 'why')?.key).toBe('nicknameRequired')
  })

  it('refuses a nickname longer than the roster keeps', () => {
    expect(validateNicknameRequest('x'.repeat(NICKNAME_REQUEST_NICKNAME_MAX_LENGTH + 1), '')).toEqual({
      key: 'nicknameTooLong',
      max: NICKNAME_REQUEST_NICKNAME_MAX_LENGTH,
    })
  })

  it('refuses the nickname the account already shows', () => {
    expect(validateNicknameRequest(' OLD_NAME ', '', 'OLD_NAME')?.key).toBe('nicknameUnchanged')
    // Without a known nickname there is nothing to compare with.
    expect(validateNicknameRequest('OLD_NAME', '', undefined)).toBeNull()
  })

  it('refuses a reason that is too long', () => {
    expect(validateNicknameRequest('YOUR_USERNAME', 'y'.repeat(NICKNAME_REQUEST_REASON_MAX_LENGTH + 1))).toEqual({
      key: 'reasonTooLong',
      max: NICKNAME_REQUEST_REASON_MAX_LENGTH,
    })
  })
})

describe('validateNicknameDecisionNote', () => {
  it('lets an admin decide without a note and limits a long one', () => {
    expect(validateNicknameDecisionNote('')).toBeNull()
    expect(validateNicknameDecisionNote('z'.repeat(NICKNAME_REQUEST_NOTE_MAX_LENGTH))).toBeNull()
    expect(validateNicknameDecisionNote('z'.repeat(NICKNAME_REQUEST_NOTE_MAX_LENGTH + 1))?.key).toBe('noteTooLong')
  })
})

describe('nicknameRequestStatusClass', () => {
  it('marks each status differently', () => {
    const classes = (['PENDING', 'APPROVED', 'REJECTED', 'CANCELED'] as const).map(nicknameRequestStatusClass)
    expect(new Set(classes).size).toBe(4)
  })
})
