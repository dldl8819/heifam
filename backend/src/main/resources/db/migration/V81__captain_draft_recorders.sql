-- Who started a regular draft, and who put each set's result in, kept the way matches keep theirs:
-- the email to know them by (never sent to the page) and the nickname shown, as it was then. A
-- deleted account leaves no email and 탈퇴한 회원 for the nickname (AccountPersonalDataRepository).
-- New nullable columns only: drafts and sets saved before stay as they are, with no one named.
alter table public.captain_drafts
    add column if not exists created_by_email varchar(320),
    add column if not exists created_by_nickname varchar(100);

alter table public.captain_draft_entries
    add column if not exists result_recorded_by_email varchar(320),
    add column if not exists result_recorded_by_nickname varchar(100),
    add column if not exists result_recorded_at timestamptz;
