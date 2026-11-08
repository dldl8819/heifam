import { describe, expect, it } from 'vitest'
import { PATCH_NOTES, findPatchNote, formatPatchDate, patchNoteSlug, patchNoteTitle } from '@/lib/patch-notes'
import type { PatchNote } from '@/lib/patch-notes'

/** Every sentence of a patch note, the values of a change included. */
function allText(note: PatchNote): string[] {
  return [
    note.period,
    note.summary,
    ...note.comment,
    note.highlight.title,
    note.highlight.body,
    ...note.sections.flatMap((section) => [
      section.title,
      ...section.entries.flatMap((entry) => [
        entry.title,
        entry.note ?? '',
        ...entry.changes.flatMap((change) => (typeof change === 'string' ? [change] : [change.label, change.before, change.after])),
      ]),
    ]),
    ...note.fixes,
  ]
}

function versionNumbers(version: string): number[] {
  return version.split('.').map(Number)
}

describe('patch notes', () => {
  it('are newest first, each version and day once', () => {
    const versions = PATCH_NOTES.map((note) => note.version)
    expect(new Set(versions).size).toBe(versions.length)
    for (let index = 1; index < PATCH_NOTES.length; index += 1) {
      const [newerYear, newerNumber] = versionNumbers(PATCH_NOTES[index - 1].version)
      const [olderYear, olderNumber] = versionNumbers(PATCH_NOTES[index].version)
      expect(newerYear > olderYear || (newerYear === olderYear && newerNumber > olderNumber)).toBe(true)
      expect(PATCH_NOTES[index - 1].date > PATCH_NOTES[index].date).toBe(true)
    }
  })

  it('carry a real day, a summary, a highlight and something in every section', () => {
    for (const note of PATCH_NOTES) {
      expect(note.version).toMatch(/^\d{2}\.\d+$/)
      expect(note.date).toMatch(/^\d{4}-\d{2}-\d{2}$/)
      expect(Number.isNaN(Date.parse(`${note.date}T00:00:00+09:00`))).toBe(false)
      expect(note.summary).not.toBe('')
      expect(note.highlight.title).not.toBe('')
      expect(note.sections.length).toBeGreaterThan(0)
      for (const section of note.sections) {
        expect(section.entries.length).toBeGreaterThan(0)
        for (const entry of section.entries) {
          expect(entry.changes.length).toBeGreaterThan(0)
        }
      }
    }
  })

  it('link only to the site', () => {
    for (const note of PATCH_NOTES) {
      for (const href of [note.highlight.href, ...note.sections.map((section) => section.href)]) {
        if (href !== undefined) {
          expect(href).toMatch(/^\/[a-z]/)
        }
      }
    }
  })

  // Any member reads them and the pages are static, so they hold only what every member may know.
  it('say nothing about admins, operators, who may do what, or MMR', () => {
    const forbidden = /관리자|운영진|운영자|어드민|슈퍼|권한|admin|mmr|@/i
    for (const note of PATCH_NOTES) {
      for (const text of allText(note)) {
        expect(text, `${note.version}: ${text}`).not.toMatch(forbidden)
      }
    }
  })

  // Said in words: a symbol may be read out as its name, or not at all.
  it('use words where a screen reader could stumble on a symbol', () => {
    const symbols = /[~·/⇒]|\d:\d|\d+p\b/
    for (const note of PATCH_NOTES) {
      for (const text of allText(note)) {
        expect(text, `${note.version}: ${text}`).not.toMatch(symbols)
      }
    }
  })

  it('find a note by its address and nothing else', () => {
    expect(patchNoteSlug('26.3')).toBe('26-3')
    for (const note of PATCH_NOTES) {
      expect(findPatchNote(patchNoteSlug(note.version))).toBe(note)
    }
    expect(findPatchNote('26.3')).toBeUndefined()
    expect(findPatchNote('99-9')).toBeUndefined()
  })

  it('read the title and the day in Korean', () => {
    expect(patchNoteTitle('26.3')).toBe('헤이팸 26.3 패치 노트')
    expect(formatPatchDate('2026-11-08')).toBe('2026년 11월 8일')
  })
})
