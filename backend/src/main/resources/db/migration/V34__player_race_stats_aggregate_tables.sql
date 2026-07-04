create table if not exists public.player_race_stats (
    player_id bigint not null references public.players(id) on delete cascade,
    group_id bigint not null references public.groups(id) on delete cascade,
    race varchar(3) not null,
    wins integer not null default 0,
    losses integer not null default 0,
    games integer not null default 0,
    updated_at timestamptz not null default now(),
    primary key (player_id, race),
    constraint chk_player_race_stats_race check (race in ('P', 'T', 'Z', 'PT', 'PZ', 'TZ', 'PTZ')),
    constraint chk_player_race_stats_wins_non_negative check (wins >= 0),
    constraint chk_player_race_stats_losses_non_negative check (losses >= 0),
    constraint chk_player_race_stats_games_non_negative check (games >= 0),
    constraint chk_player_race_stats_games_match_record check (games = wins + losses)
);

create index if not exists idx_player_race_stats_group_id
    on public.player_race_stats (group_id);

create index if not exists idx_player_race_stats_group_player
    on public.player_race_stats (group_id, player_id);

alter table if exists public.player_race_stats enable row level security;

drop policy if exists no_client_access on public.player_race_stats;
create policy no_client_access
    on public.player_race_stats
    as permissive
    for all
    to public
    using (false)
    with check (false);

create table if not exists public.player_game_type_stats (
    player_id bigint not null references public.players(id) on delete cascade,
    group_id bigint not null references public.groups(id) on delete cascade,
    game_type varchar(10) not null,
    wins integer not null default 0,
    losses integer not null default 0,
    games integer not null default 0,
    updated_at timestamptz not null default now(),
    primary key (player_id, game_type),
    constraint chk_player_game_type_stats_game_type check (game_type ~ '^(P|T|Z|PTZ)+$'),
    constraint chk_player_game_type_stats_wins_non_negative check (wins >= 0),
    constraint chk_player_game_type_stats_losses_non_negative check (losses >= 0),
    constraint chk_player_game_type_stats_games_non_negative check (games >= 0),
    constraint chk_player_game_type_stats_games_match_record check (games = wins + losses)
);

create index if not exists idx_player_game_type_stats_group_id
    on public.player_game_type_stats (group_id);

create index if not exists idx_player_game_type_stats_group_player
    on public.player_game_type_stats (group_id, player_id);

alter table if exists public.player_game_type_stats enable row level security;

drop policy if exists no_client_access on public.player_game_type_stats;
create policy no_client_access
    on public.player_game_type_stats
    as permissive
    for all
    to public
    using (false)
    with check (false);

insert into public.player_race_stats (
    player_id,
    group_id,
    race,
    wins,
    losses,
    games,
    updated_at
)
with participant_results as (
    select
        p.id as player_id,
        p.group_id,
        case
            when upper(coalesce(mp.assigned_race, '')) in ('P', 'T', 'Z')
                then upper(mp.assigned_race)
            when upper(coalesce(mp.race, '')) in ('P', 'T', 'Z', 'PT', 'PZ', 'TZ', 'PTZ')
                then upper(mp.race)
            else 'P'
        end as race,
        case
            when m.winning_team is null
                or mp.team is null
                or btrim(mp.team) = ''
                then null
            when upper(m.winning_team) = upper(mp.team)
                then 'W'
            else 'L'
        end as result_symbol
    from public.players p
    join public.match_participants mp on mp.player_id = p.id
    join public.matches m on m.id = mp.match_id and m.group_id = p.group_id
)
select
    player_id,
    group_id,
    race,
    count(*) filter (where result_symbol = 'W')::integer as wins,
    count(*) filter (where result_symbol = 'L')::integer as losses,
    count(*) filter (where result_symbol in ('W', 'L'))::integer as games,
    now()
from participant_results
where result_symbol in ('W', 'L')
group by player_id, group_id, race
on conflict (player_id, race) do update set
    group_id = excluded.group_id,
    wins = excluded.wins,
    losses = excluded.losses,
    games = excluded.games,
    updated_at = excluded.updated_at;

insert into public.player_game_type_stats (
    player_id,
    group_id,
    game_type,
    wins,
    losses,
    games,
    updated_at
)
with participant_base as (
    select
        p.id as player_id,
        p.group_id,
        m.id as match_id,
        upper(coalesce(mp.team, '')) as team,
        case
            when upper(coalesce(mp.assigned_race, '')) in ('P', 'T', 'Z')
                then upper(mp.assigned_race)
            when upper(coalesce(mp.race, '')) in ('P', 'T', 'Z')
                then upper(mp.race)
            else 'PTZ'
        end as race_token,
        case
            when m.winning_team is null
                or mp.team is null
                or btrim(mp.team) = ''
                then null
            when upper(m.winning_team) = upper(mp.team)
                then 'W'
            else 'L'
        end as result_symbol
    from public.players p
    join public.match_participants mp on mp.player_id = p.id
    join public.matches m on m.id = mp.match_id and m.group_id = p.group_id
),
team_compositions as (
    select
        match_id,
        team,
        string_agg(
            race_token,
            ''
            order by
                case race_token
                    when 'P' then 1
                    when 'T' then 2
                    when 'Z' then 3
                    else 4
                end,
                race_token
        ) as game_type
    from participant_base
    where team in ('HOME', 'AWAY')
    group by match_id, team
),
participant_results as (
    select
        base.player_id,
        base.group_id,
        coalesce(nullif(composition.game_type, ''), base.race_token) as game_type,
        base.result_symbol
    from participant_base base
    left join team_compositions composition
        on composition.match_id = base.match_id
        and composition.team = base.team
)
select
    player_id,
    group_id,
    game_type,
    count(*) filter (where result_symbol = 'W')::integer as wins,
    count(*) filter (where result_symbol = 'L')::integer as losses,
    count(*) filter (where result_symbol in ('W', 'L'))::integer as games,
    now()
from participant_results
where result_symbol in ('W', 'L')
group by player_id, group_id, game_type
on conflict (player_id, game_type) do update set
    group_id = excluded.group_id,
    wins = excluded.wins,
    losses = excluded.losses,
    games = excluded.games,
    updated_at = excluded.updated_at;
