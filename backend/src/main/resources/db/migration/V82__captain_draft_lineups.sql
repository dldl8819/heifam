-- A regular draft can have each captain hand in their team's line-up without seeing the other
-- team's: entry_mode CAPTAINS. HOST is the way so far, the host filling both teams in for everyone to
-- see, and every draft before this one keeps it. The mode can be changed while the draft runs.
-- captain_draft_lineups keeps when each captain handed a stage in: MAIN for every regular round at
-- once, ACE for an ace decider. The players stay in captain_draft_entry_players as before; until both
-- teams of a stage are in, the API shows a team's players to that team only. Nothing personal is kept
-- here: the one who hands a team in is its captain.
-- ace_decider marks the extra round played when the regular rounds end level (에이스 결정전).
alter table public.captain_drafts
    add column if not exists entry_mode varchar(10) not null default 'HOST'
        constraint ck_captain_drafts_entry_mode check (entry_mode in ('HOST', 'CAPTAINS'));

alter table public.captain_draft_entries
    add column if not exists ace_decider boolean not null default false;

create table if not exists public.captain_draft_lineups (
    id bigserial primary key,
    draft_id bigint not null references public.captain_drafts (id) on delete cascade,
    stage varchar(10) not null,
    team varchar(10) not null,
    submitted_at timestamptz not null,
    constraint ck_captain_draft_lineups_stage check (stage in ('MAIN', 'ACE')),
    constraint ck_captain_draft_lineups_team check (team in ('HOME', 'AWAY')),
    constraint uq_captain_draft_lineups_stage_team unique (draft_id, stage, team)
);

alter table if exists public.captain_draft_lineups enable row level security;
drop policy if exists no_client_access on public.captain_draft_lineups;
create policy no_client_access on public.captain_draft_lineups as permissive for all to public using (false) with check (false);
