/** Limits and small rules of the nickname change requests; the backend (NicknameRequestService) enforces the same limits. */

import type { NicknameRequestStatus } from '@/types/api'

export const NICKNAME_REQUEST_NICKNAME_MAX_LENGTH = 50
export const NICKNAME_REQUEST_REASON_MAX_LENGTH = 300
export const NICKNAME_REQUEST_NOTE_MAX_LENGTH = 300

export type NicknameRequestProblem = {
  // The name of the message under "nicknameRequests." that says what is wrong.
  key: 'nicknameRequired' | 'nicknameTooLong' | 'nicknameUnchanged' | 'reasonTooLong' | 'noteTooLong'
  max: number
}

/** What stops a request from being filed, or null when it can be. Lengths are counted after trimming. */
export function validateNicknameRequest(
  desiredNickname: string,
  reason: string,
  currentNickname?: string | null
): NicknameRequestProblem | null {
  const desired = desiredNickname.trim()
  if (desired.length === 0) {
    return { key: 'nicknameRequired', max: NICKNAME_REQUEST_NICKNAME_MAX_LENGTH }
  }
  if (desired.length > NICKNAME_REQUEST_NICKNAME_MAX_LENGTH) {
    return { key: 'nicknameTooLong', max: NICKNAME_REQUEST_NICKNAME_MAX_LENGTH }
  }
  if (currentNickname && currentNickname.trim() === desired) {
    return { key: 'nicknameUnchanged', max: NICKNAME_REQUEST_NICKNAME_MAX_LENGTH }
  }
  if (reason.trim().length > NICKNAME_REQUEST_REASON_MAX_LENGTH) {
    return { key: 'reasonTooLong', max: NICKNAME_REQUEST_REASON_MAX_LENGTH }
  }
  return null
}

export function validateNicknameDecisionNote(note: string): NicknameRequestProblem | null {
  return note.trim().length > NICKNAME_REQUEST_NOTE_MAX_LENGTH
    ? { key: 'noteTooLong', max: NICKNAME_REQUEST_NOTE_MAX_LENGTH }
    : null
}

/** The colours of a status mark: waiting stands out, a refusal is red, the rest is quiet. */
export function nicknameRequestStatusClass(status: NicknameRequestStatus): string {
  switch (status) {
    case 'PENDING':
      return 'bg-amber-100 text-amber-800 dark:bg-amber-950/60 dark:text-amber-300'
    case 'APPROVED':
      return 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950/60 dark:text-emerald-300'
    case 'REJECTED':
      return 'bg-rose-100 text-rose-800 dark:bg-rose-950/60 dark:text-rose-300'
    default:
      return 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300'
  }
}
