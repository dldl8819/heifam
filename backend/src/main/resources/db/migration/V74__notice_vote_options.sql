-- A vote on a notice was for or against and nothing else. It now has its own list of options
-- ("30분", "25분", ...), which the writer sets and, when the vote allows it, voters add to. The
-- writer also chooses whether it is anonymous: an anonymous vote shows counts only, as every vote
-- so far did; a named one shows who chose what.
--
-- Votes already cast are kept: each notice that asks or asked for a vote gets the two options it
-- had in all but name, 찬성 and 반대, and its votes are pointed at them. Every existing vote stays
-- anonymous (the default), so nobody who voted under that promise is shown.
--
-- notice_votes.choice is no longer written or read. It stays, nullable, for the version of the
-- backend that is still answering while this one starts; a later migration can drop it.
-- Adds two columns to notices, one table, and one column to notice_votes.
alter table public.notices
    add column if not exists vote_anonymous boolean not null default true;

alter table public.notices
    add column if not exists vote_allow_additions boolean not null default false;

create table if not exists public.notice_vote_options (
    id bigserial primary key,
    notice_id bigint not null references public.notices(id) on delete cascade,
    label varchar(50) not null,
    position integer not null,
    -- Who added it, kept for the account removal only; never sent out. Null for the options of
    -- votes from before this migration.
    created_by_email varchar(320),
    created_at timestamptz not null default now()
);

-- The same option twice, in whatever case, would split its votes.
create unique index if not exists uq_notice_vote_options_label
    on public.notice_vote_options (notice_id, lower(label));

create index if not exists idx_notice_vote_options_creator
    on public.notice_vote_options (created_by_email);

insert into public.notice_vote_options (notice_id, label, position)
select n.id, o.label, o.position
from public.notices n
cross join (values ('찬성', 0), ('반대', 1)) as o(label, position)
where (n.vote_status <> 'NONE' or exists (select 1 from public.notice_votes v where v.notice_id = n.id))
  and not exists (select 1 from public.notice_vote_options x where x.notice_id = n.id);

alter table public.notice_votes
    add column if not exists option_id bigint references public.notice_vote_options(id) on delete cascade;

update public.notice_votes v
set option_id = o.id
from public.notice_vote_options o
where v.option_id is null
  and o.notice_id = v.notice_id
  and o.label = case v.choice when 'AGREE' then '찬성' when 'DISAGREE' then '반대' end;

alter table public.notice_votes
    alter column option_id set not null;

alter table public.notice_votes
    alter column choice drop not null;

create index if not exists idx_notice_votes_option
    on public.notice_votes (option_id);

alter table if exists public.notice_vote_options enable row level security;
drop policy if exists no_client_access on public.notice_vote_options;
create policy no_client_access on public.notice_vote_options as permissive for all to public using (false) with check (false);
