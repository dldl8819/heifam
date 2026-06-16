create table if not exists public.player_stats (
    player_id bigint primary key references public.players(id) on delete cascade,
    group_id bigint not null references public.groups(id) on delete cascade,
    wins integer not null default 0,
    losses integer not null default 0,
    games integer not null default 0,
    last10 varchar(10) not null default '',
    streak_symbol varchar(1) not null default 'N',
    streak_count integer not null default 0,
    mmr_delta integer not null default 0,
    last_played_at timestamptz,
    updated_at timestamptz not null default now(),
    constraint chk_player_stats_wins_non_negative check (wins >= 0),
    constraint chk_player_stats_losses_non_negative check (losses >= 0),
    constraint chk_player_stats_games_non_negative check (games >= 0),
    constraint chk_player_stats_games_match_record check (games = wins + losses),
    constraint chk_player_stats_last10_symbols check (last10 ~ '^[WL]*$'),
    constraint chk_player_stats_streak_symbol check (streak_symbol in ('W', 'L', 'N')),
    constraint chk_player_stats_streak_count_non_negative check (streak_count >= 0)
);

create index if not exists idx_player_stats_group_id
    on public.player_stats (group_id);

create index if not exists idx_player_stats_group_player
    on public.player_stats (group_id, player_id);

alter table if exists public.player_stats enable row level security;

drop policy if exists no_client_access on public.player_stats;
create policy no_client_access
    on public.player_stats
    as permissive
    for all
    to public
    using (false)
    with check (false);

with participant_results as (
    select
        p.id as player_id,
        p.group_id,
        mp.id as participant_id,
        m.id as match_id,
        m.played_at,
        coalesce(mp.mmr_delta, 0) as mmr_delta,
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
    left join public.match_participants mp on mp.player_id = p.id
    left join public.matches m on m.id = mp.match_id and m.group_id = p.group_id
),
aggregate_stats as (
    select
        player_id,
        group_id,
        count(*) filter (where result_symbol = 'W')::integer as wins,
        count(*) filter (where result_symbol = 'L')::integer as losses,
        coalesce(sum(mmr_delta) filter (where participant_id is not null), 0)::integer as mmr_delta,
        max(played_at) filter (where participant_id is not null) as last_played_at
    from participant_results
    group by player_id, group_id
),
ordered_results as (
    select
        player_id,
        result_symbol,
        row_number() over (
            partition by player_id
            order by played_at desc nulls last, match_id desc, participant_id desc
        ) as rn
    from participant_results
    where result_symbol is not null
),
results_with_first as (
    select
        player_id,
        result_symbol,
        rn,
        first_value(result_symbol) over (partition by player_id order by rn) as first_result
    from ordered_results
),
marked_results as (
    select
        player_id,
        result_symbol,
        rn,
        first_result,
        sum(
            case when result_symbol = first_result then 0 else 1 end
        ) over (
            partition by player_id
            order by rn
            rows between unbounded preceding and current row
        ) as mismatch_count
    from results_with_first
),
summarized_results as (
    select
        player_id,
        coalesce(
            string_agg(result_symbol, '' order by rn)
                filter (where rn <= 10),
            ''
        ) as last10,
        coalesce(max(first_result), 'N') as streak_symbol,
        (count(result_symbol) filter (where mismatch_count = 0))::integer as streak_count
    from marked_results
    group by player_id
)
insert into public.player_stats (
    player_id,
    group_id,
    wins,
    losses,
    games,
    last10,
    streak_symbol,
    streak_count,
    mmr_delta,
    last_played_at,
    updated_at
)
select
    aggregate_stats.player_id,
    aggregate_stats.group_id,
    aggregate_stats.wins,
    aggregate_stats.losses,
    aggregate_stats.wins + aggregate_stats.losses as games,
    coalesce(summarized_results.last10, '') as last10,
    coalesce(summarized_results.streak_symbol, 'N') as streak_symbol,
    case
        when coalesce(summarized_results.streak_symbol, 'N') = 'N' then 0
        else coalesce(summarized_results.streak_count, 0)
    end as streak_count,
    aggregate_stats.mmr_delta,
    aggregate_stats.last_played_at,
    now()
from aggregate_stats
left join summarized_results on summarized_results.player_id = aggregate_stats.player_id
on conflict (player_id) do update set
    group_id = excluded.group_id,
    wins = excluded.wins,
    losses = excluded.losses,
    games = excluded.games,
    last10 = excluded.last10,
    streak_symbol = excluded.streak_symbol,
    streak_count = excluded.streak_count,
    mmr_delta = excluded.mmr_delta,
    last_played_at = excluded.last_played_at,
    updated_at = excluded.updated_at;
