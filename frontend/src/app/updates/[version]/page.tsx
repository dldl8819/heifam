import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import { PatchNoteArticle } from '@/components/patch-notes'
import { t } from '@/lib/i18n'
import { PATCH_NOTES, findPatchNote, patchNoteSlug, patchNoteTitle } from '@/lib/patch-notes'

type PatchNotePageProps = {
  params: Promise<{ version: string }>
}

// Every patch note is made at build time, so a note costs no function call to read; any other
// address is not found.
export const dynamicParams = false

export function generateStaticParams() {
  return PATCH_NOTES.map((note) => ({ version: patchNoteSlug(note.version) }))
}

// The note's own title, which the app shell keeps, so a shared link names the patch.
export async function generateMetadata({ params }: PatchNotePageProps): Promise<Metadata> {
  const note = findPatchNote((await params).version)
  if (!note) {
    return {}
  }
  return {
    title: t('a11y.pageTitle', { page: patchNoteTitle(note.version), site: t('meta.title') }),
    description: note.summary,
  }
}

export default async function PatchNotePage({ params }: PatchNotePageProps) {
  const note = findPatchNote((await params).version)
  if (!note) {
    notFound()
  }
  return <PatchNoteArticle note={note} others={PATCH_NOTES.filter((other) => other !== note)} />
}
