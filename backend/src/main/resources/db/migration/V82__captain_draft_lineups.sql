-- A regular draft can have each captain hand in their team's line-up for a round without seeing the
-- other team's: entry_mode CAPTAINS. HOST is the way so far, the host filling both teams in for
-- everyone to see, and every draft before this one keeps it.
-- captain_draft_lineups keeps when each captain handed a round in. The players stay in
-- captain_draft_entry_players as before; until both teams of a round are in, the API shows a team's
-- players to that team only. Nothing personal is kept here: the one who hands a team in is its captain.
alter table public.captain_drafts
    add column if not exists entry_mode varchar(10) not null default 'HOST'
        constraint ck_captain_drafts_entry_mode check (entry_mode in ('HOST', 'CAPTAINS'));

create table if not exists public.captain_draft_lineups (
    id bigserial primary key,
    draft_id bigint not null references public.captain_drafts (id) on delete cascade,
    round_number integer not null,
    team varchar(10) not null,
    submitted_at timestamptz not null,
    constraint ck_captain_draft_lineups_team check (team in ('HOME', 'AWAY')),
    constraint uq_captain_draft_lineups_round_team unique (draft_id, round_number, team)
);

alter table if exists public.captain_draft_lineups enable row level security;
drop policy if exists no_client_access on public.captain_draft_lineups;
create policy no_client_access on public.captain_draft_lineups as permissive for all to public using (false) with check (false);
