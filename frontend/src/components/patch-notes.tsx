import Link from 'next/link'
import { t } from '@/lib/i18n'
import { formatPatchDate, patchNoteSlug, patchNoteTitle } from '@/lib/patch-notes'
import type { PatchChange, PatchEntry, PatchNote } from '@/lib/patch-notes'

/**
 * The patch notes pages (/updates): the list of notes, and one note in full.
 *
 * Written to be heard as well as seen. What only looks like something (the version tile, the
 * separators, the arrow of a change) is hidden from screen readers, and a change is said in one
 * sentence of words. Korean breaks between words only (break-keep), and the lines are loose.
 */

const CARD_CLASS = 'rounded-xl border border-slate-200 bg-white shadow-xs dark:border-slate-700 dark:bg-slate-900'
const LINK_CLASS = 'text-sm font-medium text-sky-700 underline-offset-2 hover:underline dark:text-sky-300'
const BODY_CLASS = 'text-sm leading-relaxed text-slate-700 dark:text-slate-200'
// A heading reached from the table of contents clears the sticky header.
const ANCHOR_CLASS = 'scroll-mt-44'

function PatchDates({ note }: { note: PatchNote }) {
  return (
    <p className="text-xs leading-relaxed text-slate-500 dark:text-slate-400">
      <time dateTime={note.date}>{formatPatchDate(note.date)}</time>
      <span aria-hidden="true"> · </span>
      <span className="sr-only">, </span>
      {t('updates.period', { period: note.period })}
    </p>
  )
}

function LatestBadge() {
  return (
    <span className="rounded-sm bg-emerald-100 px-1.5 py-0.5 text-xs font-semibold text-emerald-800 dark:bg-emerald-900/60 dark:text-emerald-200">
      {t('updates.latest')}
    </span>
  )
}

/**
 * The title is the link, so a screen reader hears the patch's name for it and the rest as text; the
 * link still covers the whole card for a click or a tap.
 */
function PatchNoteCard({ note, latest }: { note: PatchNote; latest: boolean }) {
  return (
    <div className={`${CARD_CLASS} relative flex overflow-hidden transition-colors hover:border-sky-400 dark:hover:border-sky-500`}>
      {/* The version as a tile, where the game's patch notes have a picture; the title says it too. */}
      <div
        aria-hidden="true"
        className="flex w-20 shrink-0 items-center justify-center bg-sky-700 text-lg font-bold text-white sm:w-28 sm:text-2xl"
      >
        {note.version}
      </div>
      <div className="min-w-0 space-y-1.5 p-4">
        <h3 className="flex flex-wrap items-center gap-2 text-base font-semibold">
          <Link
            href={`/updates/${patchNoteSlug(note.version)}`}
            className="text-slate-900 after:absolute after:inset-0 hover:text-sky-700 dark:text-slate-100 dark:hover:text-sky-300"
          >
            {patchNoteTitle(note.version)}
          </Link>
          {latest && <LatestBadge />}
        </h3>
        <p className={BODY_CLASS}>{note.summary}</p>
        <PatchDates note={note} />
      </div>
    </div>
  )
}

/** Every patch note, newest first. */
export function PatchNoteList({ notes }: { notes: readonly PatchNote[] }) {
  return (
    <section className="space-y-6 break-keep">
      <div className="space-y-1">
        <h2 className="text-lg font-semibold text-slate-900 dark:text-slate-100">{t('updates.title')}</h2>
        <p className="text-sm leading-relaxed text-slate-500 dark:text-slate-400">{t('updates.description')}</p>
      </div>
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

  // Seen as "label: before ⇒ after"; heard as one sentence, so it is not read out in pieces.
  return (
    <li>
      <span aria-hidden="true">
        <span className="font-semibold text-slate-900 dark:text-slate-100">{change.label}</span>
        {': '}
        <span className="text-slate-500 dark:text-slate-400">{change.before}</span>
        <span className="mx-1.5 text-sky-700 dark:text-sky-300">⇒</span>
        <span className="font-semibold text-emerald-700 dark:text-emerald-400">{change.after}</span>
      </span>
      <span className="sr-only">{t('updates.valueChange', { label: change.label, before: change.before, after: change.after })}</span>
    </li>
  )
}

function EntryTag({ tag }: { tag: NonNullable<PatchEntry['tag']> }) {
  const tone =
    tag === 'new'
      ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/60 dark:text-emerald-200'
      : 'bg-amber-100 text-amber-900 dark:bg-amber-900/50 dark:text-amber-200'
  return <span className={`rounded-sm px-1.5 py-0.5 text-xs font-semibold ${tone}`}>{t(`updates.tags.${tag}`)}</span>
}

function Entry({ entry }: { entry: PatchEntry }) {
  return (
    <div className="space-y-2 py-5 first:pt-3 last:pb-0">
      <h4 className="flex flex-wrap items-center gap-2 text-base font-semibold text-slate-900 dark:text-slate-100">
        {entry.title}
        {entry.tag && <EntryTag tag={entry.tag} />}
      </h4>
      {entry.note && (
        <p className="border-l-2 border-slate-300 pl-3 text-sm leading-relaxed text-slate-600 dark:border-slate-600 dark:text-slate-300">
          <span className="sr-only">{t('updates.reason')} </span>
          {entry.note}
        </p>
      )}
      <ul className={`list-disc space-y-1.5 pl-5 ${BODY_CLASS}`}>
        {entry.changes.map((change) => (
          <ChangeLine key={typeof change === 'string' ? change : change.label} change={change} />
        ))}
      </ul>
    </div>
  )
}

function SectionHeading({ id, title }: { id: string; title: string }) {
  return (
    <h3 id={id} className={`${ANCHOR_CLASS} text-lg font-semibold text-slate-900 dark:text-slate-100`}>
      {title}
    </h3>
  )
}

const sectionId = (index: number) => `updates-section-${index}`
const FIXES_ID = 'updates-fixes'

/** Where the note's parts are, so a long note on a phone is a tap from each. */
function Contents({ note }: { note: PatchNote }) {
  const links = [
    ...note.sections.map((section, index) => ({ href: `#${sectionId(index)}`, label: section.title })),
    ...(note.fixes.length > 0 ? [{ href: `#${FIXES_ID}`, label: t('updates.fixes') }] : []),
  ]

  return (
    <nav aria-labelledby="updates-contents" className={`${CARD_CLASS} space-y-2 p-4`}>
      <h3 id="updates-contents" className="text-sm font-semibold text-slate-900 dark:text-slate-100">
        {t('updates.contents')}
      </h3>
      <ul className="flex flex-wrap gap-2">
        {links.map((link) => (
          <li key={link.href}>
            <a
              href={link.href}
              className="inline-flex rounded-full border border-slate-300 px-3 py-1 text-sm text-slate-700 transition-colors hover:border-sky-500 hover:text-sky-700 dark:border-slate-600 dark:text-slate-200 dark:hover:border-sky-400 dark:hover:text-sky-300"
            >
              {link.label}
            </a>
          </li>
        ))}
      </ul>
    </nav>
  )
}

/** One patch note in full, in the order of the game's: comment, highlight, changes, fixes. */
export function PatchNoteArticle({ note, others }: { note: PatchNote; others: readonly PatchNote[] }) {
  return (
    <article aria-labelledby="updates-title" className="mx-auto max-w-3xl space-y-8 break-keep">
      {/* A block of its own: the article's spacing is a margin below each child, which an inline link drops. */}
      <Link href="/updates" className={`${LINK_CLASS} inline-block`}>
        <span aria-hidden="true">← </span>
        {t('updates.backToList')}
      </Link>

      <header className="space-y-2">
        <h2 id="updates-title" className="text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
          {patchNoteTitle(note.version)}
        </h2>
        <p className={BODY_CLASS}>{note.summary}</p>
        <PatchDates note={note} />
      </header>

      <Contents note={note} />

      {note.comment.length > 0 && (
        <section aria-labelledby="updates-comment" className="space-y-2">
          <SectionHeading id="updates-comment" title={t('updates.comment')} />
          <blockquote className={`space-y-2 border-l-4 border-sky-600 bg-sky-50 p-4 dark:border-sky-400 dark:bg-sky-950/40 ${BODY_CLASS}`}>
            {note.comment.map((paragraph) => (
              <p key={paragraph}>{paragraph}</p>
            ))}
          </blockquote>
        </section>
      )}

      <section aria-labelledby="updates-highlight" className="space-y-2">
        <SectionHeading id="updates-highlight" title={t('updates.highlight')} />
        <div className="space-y-2 rounded-xl bg-sky-700 p-5 text-white">
          <h4 className="text-lg font-semibold">{note.highlight.title}</h4>
          <p className="text-sm leading-relaxed text-sky-50">{note.highlight.body}</p>
          {note.highlight.href && (
            <Link
              href={note.highlight.href}
              aria-label={t('updates.goToPage', { page: note.highlight.title })}
              className="inline-block text-sm font-medium text-white underline underline-offset-2"
            >
              {t('updates.goTo')}
            </Link>
          )}
        </div>
      </section>

      {note.sections.map((section, index) => (
        <section key={section.title} aria-labelledby={sectionId(index)} className="space-y-1">
          <div className="flex items-baseline justify-between gap-3 border-b border-slate-200 pb-2 dark:border-slate-700">
            <SectionHeading id={sectionId(index)} title={section.title} />
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
        <section aria-labelledby={FIXES_ID} className="space-y-3">
          <div className="border-b border-slate-200 pb-2 dark:border-slate-700">
            <SectionHeading id={FIXES_ID} title={t('updates.fixes')} />
          </div>
          <ul className={`list-disc space-y-1.5 pl-5 ${BODY_CLASS}`}>
            {note.fixes.map((fix) => (
              <li key={fix}>{fix}</li>
            ))}
          </ul>
        </section>
      )}

      {others.length > 0 && (
        <nav aria-labelledby="updates-others" className="space-y-2 border-t border-slate-200 pt-6 dark:border-slate-700">
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
