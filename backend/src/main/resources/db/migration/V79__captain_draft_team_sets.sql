-- A set of the regular draft becomes a team game: 3:3, 2:2 or 1:1, laid out by the host to fit the
-- players of a team. Each set keeps its team size, its races, an optional note on the tiers it asks for
-- and its players per side in captain_draft_entry_players. Sets written before were one against
-- one; they stay as sets of one, their players copied over as slot 1. The old home_player_id and
-- away_player_id columns are left in place, no longer written.
alter table public.captain_draft_entries
    add column if not exists team_size integer,
    add column if not exists required_tier varchar(30);

update public.captain_draft_entries set team_size = 1 where team_size is null;

alter table public.captain_draft_entries
    alter column team_size set not null,
    alter column team_size set default 3;

create table if not exists public.captain_draft_entry_players (
    id bigserial primary key,
    entry_id bigint not null references public.captain_draft_entries (id) on delete cascade,
    team varchar(10) not null,
    slot integer not null,
    player_id bigint not null references public.players (id),
    constraint ck_captain_draft_entry_players_team check (team in ('HOME', 'AWAY')),
    constraint ck_captain_draft_entry_players_slot check (slot between 1 and 3),
    constraint uq_captain_draft_entry_players_slot unique (entry_id, team, slot)
);

create index if not exists idx_captain_draft_entry_players_player
    on public.captain_draft_entry_players (player_id);

insert into public.captain_draft_entry_players (entry_id, team, slot, player_id)
select id, 'HOME', 1, home_player_id from public.captain_draft_entries where home_player_id is not null
union all
select id, 'AWAY', 1, away_player_id from public.captain_draft_entries where away_player_id is not null
on conflict (entry_id, team, slot) do nothing;

alter table if exists public.captain_draft_entry_players enable row level security;
drop policy if exists no_client_access on public.captain_draft_entry_players;
create policy no_client_access on public.captain_draft_entry_players as permissive for all to public using (false) with check (false);
