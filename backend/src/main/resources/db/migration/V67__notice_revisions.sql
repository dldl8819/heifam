-- Announcing an edited notice again: each announcement starts a new revision. A member has read
-- a notice when their read is not older than its latest revision, so a re-announced notice turns
-- unread for everyone, and reading it earns the reading point once more. Existing notices stay at
-- revision 0 with no revised_at, so every read so far still counts. Adds the two columns only.
alter table public.notices
    add column if not exists revision integer not null default 0,
    add column if not exists revised_at timestamptz;
