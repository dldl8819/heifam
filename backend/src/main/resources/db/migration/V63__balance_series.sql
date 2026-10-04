-- Series after a multi-balance: the two teams of each match stay together for up to three games,
-- PPP, PPT and PPZ when both can field Terran or Zerg, otherwise PPP best of three. Every game is
-- an ordinary match tied to its series by matches.balance_series_id; team tournaments keep their
-- own series in match_series, so tournament places and team scores never count these.
create table if not exists public.balance_series (
    id bigserial primary key,
    group_id bigint not null references public.groups(id),
    team_size integer not null,
    format varchar(20) not null,
    game_compositions varchar(40) not null,
    status varchar(20) not null,
    home_wins integer not null default 0,
    away_wins integer not null default 0,
    winner_team varchar(10),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    finished_at timestamptz
);

create index if not exists idx_balance_series_group_status
    on public.balance_series (group_id, status, id desc);

-- The players in order: the first team_size slots are HOME, the rest AWAY.
create table if not exists public.balance_series_players (
    series_id bigint not null references public.balance_series(id) on delete cascade,
    slot integer not null,
    player_id bigint not null references public.players(id),
    primary key (series_id, slot)
);

create index if not exists idx_balance_series_players_player
    on public.balance_series_players (player_id);

alter table public.matches
    add column if not exists balance_series_id bigint references public.balance_series(id) on delete set null;

create unique index if not exists uq_matches_balance_series_game
    on public.matches (balance_series_id, series_game_number)
    where balance_series_id is not null;

alter table if exists public.balance_series enable row level security;
drop policy if exists no_client_access on public.balance_series;
create policy no_client_access on public.balance_series as permissive for all to public using (false) with check (false);

alter table if exists public.balance_series_players enable row level security;
drop policy if exists no_client_access on public.balance_series_players;
create policy no_client_access on public.balance_series_players as permissive for all to public using (false) with check (false);
