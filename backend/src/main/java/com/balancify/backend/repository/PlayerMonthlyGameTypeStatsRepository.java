package com.balancify.backend.repository;

import com.balancify.backend.domain.PlayerMonthlyGameTypeStats;
import com.balancify.backend.domain.PlayerMonthlyGameTypeStatsId;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerMonthlyGameTypeStatsRepository
    extends JpaRepository<PlayerMonthlyGameTypeStats, PlayerMonthlyGameTypeStatsId> {

    List<PlayerMonthlyGameTypeStats> findByGroupIdAndPlayerIdAndStatMonth(
        Long groupId,
        Long playerId,
        LocalDate statMonth
    );

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query(value = "delete from player_monthly_game_type_stats where group_id = :groupId", nativeQuery = true)
    void deleteByGroupIdForRebuild(@Param("groupId") Long groupId);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query(value = """
        insert into player_monthly_game_type_stats (
            player_id,
            group_id,
            stat_month,
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
                cast(date_trunc('month', m.played_at at time zone 'Asia/Seoul') as date) as stat_month,
                upper(coalesce(mp.team, '')) as team,
                case
                    when upper(coalesce(mp.assigned_race, '')) in ('P', 'T', 'Z')
                        then upper(mp.assigned_race)
                    when upper(coalesce(mp.race, '')) in ('P', 'T', 'Z')
                        then upper(mp.race)
                    else null
                end as race_token,
                upper(coalesce(m.race_composition, '')) as stored_game_type,
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
        raw_team_compositions as (
            select
                match_id,
                stat_month,
                team,
                max(stored_game_type) as stored_game_type,
                count(*) as team_size,
                count(race_token) as concrete_race_count,
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
                ) as assigned_game_type
            from participant_base
            where team in ('HOME', 'AWAY')
            group by match_id, stat_month, team
        ),
        team_compositions as (
            select
                match_id,
                stat_month,
                team,
                case
                    when team_size in (2, 3)
                        and concrete_race_count = team_size
                        and assigned_game_type in ('PP', 'PT', 'PZ', 'PPP', 'PPT', 'PPZ', 'PTZ')
                        then assigned_game_type
                    when team_size in (2, 3)
                        and stored_game_type in ('PP', 'PT', 'PZ', 'PPP', 'PPT', 'PPZ', 'PTZ')
                        and length(stored_game_type) = team_size
                        then stored_game_type
                    else null
                end as game_type
            from raw_team_compositions
        ),
        participant_results as (
            select
                base.player_id,
                base.group_id,
                base.stat_month,
                composition.game_type,
                base.result_symbol
            from participant_base base
            left join team_compositions composition
                on composition.match_id = base.match_id
                and composition.stat_month = base.stat_month
                and composition.team = base.team
        )
        select
            player_id,
            group_id,
            stat_month,
            game_type,
            cast(count(*) filter (where result_symbol = 'W') as integer) as wins,
            cast(count(*) filter (where result_symbol = 'L') as integer) as losses,
            cast(count(*) filter (where result_symbol in ('W', 'L')) as integer) as games,
            now()
        from participant_results
        where result_symbol in ('W', 'L')
            and stat_month is not null
            and game_type is not null
        group by player_id, group_id, stat_month, game_type
        """, nativeQuery = true)
    void insertGroupMonthlyGameTypeStats(@Param("groupId") Long groupId);
}
