import { PatchNoteList } from '@/components/patch-notes'
import { PATCH_NOTES } from '@/lib/patch-notes'

/** The patch notes, newest first. Built once, like the notes themselves. */
export default function PatchNotesPage() {
  return <PatchNoteList notes={PATCH_NOTES} />
}
