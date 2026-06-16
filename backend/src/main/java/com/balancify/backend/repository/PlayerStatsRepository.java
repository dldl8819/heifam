package com.balancify.backend.repository;

import com.balancify.backend.domain.PlayerStats;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerStatsRepository extends JpaRepository<PlayerStats, Long> {

    List<PlayerStats> findByGroupId(Long groupId);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query(value = """
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
            from players p
            left join match_participants mp on mp.player_id = p.id
            left join matches m on m.id = mp.match_id and m.group_id = p.group_id
            where p.group_id = :groupId
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
        insert into player_stats (
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
            updated_at = excluded.updated_at
        """, nativeQuery = true)
    void rebuildGroupStats(@Param("groupId") Long groupId);
}
