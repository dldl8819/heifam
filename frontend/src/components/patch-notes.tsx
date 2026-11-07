import Link from 'next/link'
import { t } from '@/lib/i18n'
import { formatPatchDate, patchNoteSlug, patchNoteTitle } from '@/lib/patch-notes'
import type { PatchChange, PatchEntry, PatchNote } from '@/lib/patch-notes'

/** The patch notes pages (/updates): the list of notes, and one note in full. */

const CARD_CLASS = 'rounded-xl border border-slate-200 bg-white shadow-xs dark:border-slate-700 dark:bg-slate-900'
const LINK_CLASS = 'text-sm font-medium text-sky-700 underline-offset-2 hover:underline dark:text-sky-300'

function PatchMeta({ note }: { note: PatchNote }) {
  return (
    <p className="text-xs text-slate-500 dark:text-slate-400">
      <span className="font-semibold text-sky-700 dark:text-sky-300">{t('updates.category')}</span>
      <span aria-hidden="true"> · </span>
      <time dateTime={note.date}>{formatPatchDate(note.date)}</time>
    </p>
  )
}

function InProgress({ items }: { items: string[] }) {
  return (
    <section aria-labelledby="updates-in-progress" className={`${CARD_CLASS} space-y-2 p-4`}>
      <h3 id="updates-in-progress" className="text-sm font-semibold text-slate-900 dark:text-slate-100">
        {t('updates.inProgress')}
      </h3>
      <ul className="list-disc space-y-1 pl-5 text-sm text-slate-700 dark:text-slate-200">
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
      <p className="text-xs text-slate-500 dark:text-slate-400">{t('updates.inProgressNote')}</p>
    </section>
  )
}

function PatchNoteCard({ note, latest }: { note: PatchNote; latest: boolean }) {
  return (
    <Link
      href={`/updates/${patchNoteSlug(note.version)}`}
      className={`${CARD_CLASS} group flex overflow-hidden transition-colors hover:border-sky-400 dark:hover:border-sky-500`}
    >
      {/* The version as a tile, where the game's patch notes have a picture; the title says it too. */}
      <div
        aria-hidden="true"
        className="flex w-20 shrink-0 items-center justify-center bg-sky-700 text-lg font-bold text-white sm:w-28 sm:text-2xl"
      >
        {note.version}
      </div>
      <div className="min-w-0 space-y-1 p-4">
        <div className="flex flex-wrap items-center gap-2">
          <PatchMeta note={note} />
          {latest && (
            <span className="rounded-sm bg-emerald-100 px-1.5 py-0.5 text-[11px] font-semibold text-emerald-800 dark:bg-emerald-900/60 dark:text-emerald-200">
              {t('updates.latest')}
            </span>
          )}
        </div>
        <h3 className="text-base font-semibold text-slate-900 group-hover:text-sky-700 dark:text-slate-100 dark:group-hover:text-sky-300">
          {patchNoteTitle(note.version)}
        </h3>
        <p className="text-sm text-slate-600 dark:text-slate-300">{note.summary}</p>
        <p className="text-xs text-slate-500 dark:text-slate-400">{t('updates.period', { period: note.period })}</p>
      </div>
    </Link>
  )
}

/** Every patch note, newest first, under what is being made now. */
export function PatchNoteList({ notes }: { notes: readonly PatchNote[] }) {
  const inProgress = notes[0]?.inProgress ?? []

  return (
    <section className="space-y-6">
      <div className="space-y-1">
        <h2 className="text-lg font-semibold text-slate-900 dark:text-slate-100">{t('updates.title')}</h2>
        <p className="text-sm text-slate-500 dark:text-slate-400">{t('updates.description')}</p>
      </div>
      {inProgress.length > 0 && <InProgress items={inProgress} />}
      <ol className="space-y-3">
        {notes.map((note, index) => (
          <li key={note.version}>
            <PatchNoteCard note={note} latest={index === 0} />
          </li>
        ))}
      </ol>
    </section>
  )
}

function ChangeLine({ change }: { change: PatchChange }) {
  if (typeof change === 'string') {
    return <li>{change}</li>
  }

  return (
    <li>
      <span className="font-semibold text-slate-900 dark:text-slate-100">{change.label}</span>
      {': '}
      <span className="sr-only">{t('updates.before')} </span>
      <span className="text-slate-500 dark:text-slate-400">{change.before}</span>
      <span aria-hidden="true" className="mx-1.5 text-sky-700 dark:text-sky-300">
        ⇒
      </span>
      <span className="sr-only">, {t('updates.after')} </span>
      <span className="font-semibold text-emerald-700 dark:text-emerald-400">{change.after}</span>
    </li>
  )
}

function EntryTag({ tag }: { tag: NonNullable<PatchEntry['tag']> }) {
  const tone =
    tag === 'new'
      ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/60 dark:text-emerald-200'
      : 'bg-amber-100 text-amber-900 dark:bg-amber-900/50 dark:text-amber-200'
  return <span className={`rounded-sm px-1.5 py-0.5 text-[11px] font-semibold ${tone}`}>{t(`updates.tags.${tag}`)}</span>
}

function Entry({ entry }: { entry: PatchEntry }) {
  return (
    <div className="space-y-2 py-4 first:pt-2 last:pb-0">
      <h4 className="flex flex-wrap items-center gap-2 text-base font-semibold text-slate-900 dark:text-slate-100">
        {entry.title}
        {entry.tag && <EntryTag tag={entry.tag} />}
      </h4>
      {entry.note && (
        <p className="border-l-2 border-slate-300 pl-3 text-sm text-slate-500 dark:border-slate-600 dark:text-slate-400">{entry.note}</p>
      )}
      <ul className="list-disc space-y-1 pl-5 text-sm text-slate-700 dark:text-slate-200">
        {entry.changes.map((change) => (
          <ChangeLine key={typeof change === 'string' ? change : change.label} change={change} />
        ))}
      </ul>
    </div>
  )
}

function SectionHeading({ id, title }: { id: string; title: string }) {
  return (
    <h3 id={id} className="text-lg font-semibold text-slate-900 dark:text-slate-100">
      {title}
    </h3>
  )
}

/** One patch note in full, in the order of the game's: comment, highlight, changes, fixes, what is next. */
export function PatchNoteArticle({ note, others }: { note: PatchNote; others: readonly PatchNote[] }) {
  return (
    <article className="mx-auto max-w-3xl space-y-8">
      {/* A block of its own: the article's spacing is a margin below each child, which an inline link drops. */}
      <Link href="/updates" className={`${LINK_CLASS} inline-block`}>
        <span aria-hidden="true">← </span>
        {t('updates.backToList')}
      </Link>

      <header className="space-y-2">
        <PatchMeta note={note} />
        <h2 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100">{patchNoteTitle(note.version)}</h2>
        <p className="text-sm text-slate-600 dark:text-slate-300">{note.summary}</p>
        <p className="text-xs text-slate-500 dark:text-slate-400">{t('updates.period', { period: note.period })}</p>
      </header>

      {note.comment.length > 0 && (
        <section aria-labelledby="updates-comment" className="space-y-2">
          <SectionHeading id="updates-comment" title={t('updates.comment')} />
          <blockquote className="space-y-2 border-l-4 border-sky-600 bg-sky-50 p-4 text-sm text-slate-700 dark:border-sky-400 dark:bg-sky-950/40 dark:text-slate-200">
            {note.comment.map((paragraph) => (
              <p key={paragraph}>{paragraph}</p>
            ))}
          </blockquote>
        </section>
      )}

      <section aria-labelledby="updates-highlight" className="space-y-2">
        <SectionHeading id="updates-highlight" title={t('updates.highlight')} />
        <div className="space-y-2 rounded-xl bg-sky-700 p-5 text-white">
          <p className="text-lg font-semibold">{note.highlight.title}</p>
          <p className="text-sm text-sky-50">{note.highlight.body}</p>
          {note.highlight.href && (
            <Link href={note.highlight.href} className="inline-block text-sm font-medium text-white underline underline-offset-2">
              {t('updates.goTo')}
            </Link>
          )}
        </div>
      </section>

      {note.sections.map((section, index) => (
        <section key={section.title} aria-labelledby={`updates-section-${index}`} className="space-y-1">
          <div className="flex items-baseline justify-between gap-3 border-b border-slate-200 pb-2 dark:border-slate-700">
            <SectionHeading id={`updates-section-${index}`} title={section.title} />
            {section.href && (
              <Link
                href={section.href}
                aria-label={t('updates.goToPage', { page: section.title })}
                className={`${LINK_CLASS} shrink-0`}
              >
                {t('updates.goTo')}
              </Link>
            )}
          </div>
          <div className="divide-y divide-slate-100 dark:divide-slate-800">
            {section.entries.map((entry) => (
              <Entry key={entry.title} entry={entry} />
            ))}
          </div>
        </section>
      ))}

      {note.fixes.length > 0 && (
        <section aria-labelledby="updates-fixes" className="space-y-2">
          <div className="border-b border-slate-200 pb-2 dark:border-slate-700">
            <SectionHeading id="updates-fixes" title={t('updates.fixes')} />
          </div>
          <ul className="list-disc space-y-1 pl-5 text-sm text-slate-700 dark:text-slate-200">
            {note.fixes.map((fix) => (
              <li key={fix}>{fix}</li>
            ))}
          </ul>
        </section>
      )}

      {note.inProgress.length > 0 && <InProgress items={note.inProgress} />}

      {others.length > 0 && (
        <nav aria-labelledby="updates-others" className="space-y-2">
          <h3 id="updates-others" className="text-sm font-semibold text-slate-900 dark:text-slate-100">
            {t('updates.others')}
          </h3>
          <ul className="space-y-1">
            {others.map((other) => (
              <li key={other.version}>
                <Link href={`/updates/${patchNoteSlug(other.version)}`} className={LINK_CLASS}>
                  {patchNoteTitle(other.version)}
                </Link>
                <span className="ml-2 text-xs text-slate-500 dark:text-slate-400">{formatPatchDate(other.date)}</span>
              </li>
            ))}
          </ul>
        </nav>
      )}
    </article>
  )
}
