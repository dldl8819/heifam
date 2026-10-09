-- A notice can ask members to vote for or against it. vote_status says whether it does: NONE
-- (no vote, as every notice so far), OPEN, or CLOSED (the result stays, nobody votes any more).
-- A vote is one row per person and notice, changed in place when they change their mind, and
-- removed with the account. Adds one column and one table.
alter table public.notices
    add column if not exists vote_status varchar(10) not null default 'NONE';

create table if not exists public.notice_votes (
    id bigserial primary key,
    notice_id bigint not null references public.notices(id) on delete cascade,
    voter_email varchar(320) not null,
    choice varchar(10) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_notice_votes_voter unique (notice_id, voter_email),
    constraint ck_notice_votes_choice check (choice in ('AGREE', 'DISAGREE'))
);

create index if not exists idx_notice_votes_voter
    on public.notice_votes (voter_email);

alter table if exists public.notice_votes enable row level security;
drop policy if exists no_client_access on public.notice_votes;
create policy no_client_access on public.notice_votes as permissive for all to public using (false) with check (false);
