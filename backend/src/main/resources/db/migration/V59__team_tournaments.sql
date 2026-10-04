-- Team tournaments built on multi-balance: fixed three-player teams meet in series of up to
-- three games. Every game is an ordinary match row, so ratings, rankings and race stats count
-- it as usual; matches.series_id ties the game to its series.
create table if not exists public.team_tournaments (
    id bigserial primary key,
    group_id bigint not null references public.groups(id),
    status varchar(20) not null,
    team_count integer not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    finished_at timestamptz
);

create index if not exists idx_team_tournaments_group_status
    on public.team_tournaments (group_id, status, id desc);

-- Players left over when the count did not divide into teams; the next tournament plays them first.
create table if not exists public.team_tournament_waiting_players (
    tournament_id bigint not null references public.team_tournaments(id) on delete cascade,
    player_id bigint not null references public.players(id),
    primary key (tournament_id, player_id)
);

create table if not exists public.tournament_teams (
    id bigserial primary key,
    tournament_id bigint not null references public.team_tournaments(id) on delete cascade,
    team_number integer not null,
    final_rank integer,
    created_at timestamptz not null default now(),
    constraint uq_tournament_teams_number unique (tournament_id, team_number)
);

create table if not exists public.tournament_team_members (
    team_id bigint not null references public.tournament_teams(id) on delete cascade,
    slot integer not null,
    player_id bigint not null references public.players(id),
    primary key (team_id, slot)
);

create index if not exists idx_tournament_team_members_player
    on public.tournament_team_members (player_id);

create table if not exists public.match_series (
    id bigserial primary key,
    tournament_id bigint not null references public.team_tournaments(id) on delete cascade,
    round varchar(20) not null,
    bracket_slot integer not null,
    home_team_id bigint not null references public.tournament_teams(id),
    away_team_id bigint not null references public.tournament_teams(id),
    format varchar(20) not null,
    game_compositions varchar(40) not null,
    status varchar(20) not null,
    home_wins integer not null default 0,
    away_wins integer not null default 0,
    winner_team_id bigint references public.tournament_teams(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    finished_at timestamptz,
    constraint uq_match_series_round unique (tournament_id, round, bracket_slot)
);

alter table public.matches
    add column if not exists series_id bigint references public.match_series(id) on delete set null;

alter table public.matches
    add column if not exists series_game_number integer;

create unique index if not exists uq_matches_series_game
    on public.matches (series_id, series_game_number)
    where series_id is not null;

alter table if exists public.team_tournaments enable row level security;
drop policy if exists no_client_access on public.team_tournaments;
create policy no_client_access on public.team_tournaments as permissive for all to public using (false) with check (false);

alter table if exists public.team_tournament_waiting_players enable row level security;
drop policy if exists no_client_access on public.team_tournament_waiting_players;
create policy no_client_access on public.team_tournament_waiting_players as permissive for all to public using (false) with check (false);

alter table if exists public.tournament_teams enable row level security;
drop policy if exists no_client_access on public.tournament_teams;
create policy no_client_access on public.tournament_teams as permissive for all to public using (false) with check (false);

alter table if exists public.tournament_team_members enable row level security;
drop policy if exists no_client_access on public.tournament_team_members;
create policy no_client_access on public.tournament_team_members as permissive for all to public using (false) with check (false);

alter table if exists public.match_series enable row level security;
drop policy if exists no_client_access on public.match_series;
create policy no_client_access on public.match_series as permissive for all to public using (false) with check (false);
