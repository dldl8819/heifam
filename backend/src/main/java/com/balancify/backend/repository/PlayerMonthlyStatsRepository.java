package com.balancify.backend.repository;

import com.balancify.backend.domain.PlayerMonthlyStats;
import com.balancify.backend.domain.PlayerMonthlyStatsId;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerMonthlyStatsRepository extends JpaRepository<PlayerMonthlyStats, PlayerMonthlyStatsId> {

    List<PlayerMonthlyStats> findByGroupIdAndStatMonth(Long groupId, LocalDate statMonth);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query(value = "delete from player_monthly_stats where group_id = :groupId", nativeQuery = true)
    void deleteByGroupIdForRebuild(@Param("groupId") Long groupId);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query(value = """
        insert into player_monthly_stats (
            player_id,
            group_id,
            stat_month,
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
        with participant_results as (
            select
                p.id as player_id,
                p.group_id,
                mp.id as participant_id,
                m.id as match_id,
                m.played_at,
                cast(date_trunc('month', m.played_at at time zone 'Asia/Seoul') as date) as stat_month,
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
            join match_participants mp on mp.player_id = p.id
            join matches m on m.id = mp.match_id and m.group_id = p.group_id
            where p.group_id = :groupId
        ),
        aggregate_stats as (
            select
                player_id,
                group_id,
                stat_month,
                cast(count(*) filter (where result_symbol = 'W') as integer) as wins,
                cast(count(*) filter (where result_symbol = 'L') as integer) as losses,
                cast(coalesce(sum(mmr_delta) filter (where participant_id is not null), 0) as integer) as mmr_delta,
                max(played_at) filter (where participant_id is not null) as last_played_at
            from participant_results
            where stat_month is not null
            group by player_id, group_id, stat_month
        ),
        ordered_results as (
            select
                player_id,
                stat_month,
                result_symbol,
                row_number() over (
                    partition by player_id, stat_month
                    order by played_at desc nulls last, match_id desc, participant_id desc
                ) as rn
            from participant_results
            where result_symbol is not null
                and stat_month is not null
        ),
        results_with_first as (
            select
                player_id,
                stat_month,
                result_symbol,
                rn,
                first_value(result_symbol) over (partition by player_id, stat_month order by rn) as first_result
            from ordered_results
        ),
        marked_results as (
            select
                player_id,
                stat_month,
                result_symbol,
                rn,
                first_result,
                sum(
                    case when result_symbol = first_result then 0 else 1 end
                ) over (
                    partition by player_id, stat_month
                    order by rn
                    rows between unbounded preceding and current row
                ) as mismatch_count
            from results_with_first
        ),
        summarized_results as (
            select
                player_id,
                stat_month,
                coalesce(
                    string_agg(result_symbol, '' order by rn)
                        filter (where rn <= 10),
                    ''
                ) as last10,
                coalesce(max(first_result), 'N') as streak_symbol,
                cast(count(result_symbol) filter (where mismatch_count = 0) as integer) as streak_count
            from marked_results
            group by player_id, stat_month
        )
        select
            aggregate_stats.player_id,
            aggregate_stats.group_id,
            aggregate_stats.stat_month,
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
        left join summarized_results
            on summarized_results.player_id = aggregate_stats.player_id
            and summarized_results.stat_month = aggregate_stats.stat_month
        """, nativeQuery = true)
    void insertGroupMonthlyStats(@Param("groupId") Long groupId);
}
