package com.balancify.backend.repository;

import com.balancify.backend.domain.PlayerRaceStats;
import com.balancify.backend.domain.PlayerRaceStatsId;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerRaceStatsRepository extends JpaRepository<PlayerRaceStats, PlayerRaceStatsId> {

    List<PlayerRaceStats> findByGroupId(Long groupId);

    List<PlayerRaceStats> findByGroupIdAndPlayerId(Long groupId, Long playerId);

    // Only matches whose races a recorder set, since elsewhere the race is the balancer's guess;
    // 3v3 only, to line up with the composition rows shown beside it.
    @Query(value = """
        select
            race,
            cast(count(*) filter (where result_symbol = 'W') as integer) as wins,
            cast(count(*) filter (where result_symbol = 'L') as integer) as losses
        from (
            select
                case
                    when upper(coalesce(mp.assigned_race, '')) in ('P', 'T', 'Z')
                        then upper(mp.assigned_race)
                end as race,
                case
                    when upper(m.winning_team) = upper(mp.team) then 'W'
                    else 'L'
                end as result_symbol
            from match_participants mp
            join matches m on m.id = mp.match_id
            where mp.player_id = :playerId
                and m.group_id = :groupId
                and m.team_size = 3
                and m.races_recorded = true
                and m.winning_team is not null
                and mp.team is not null
                and btrim(mp.team) <> ''
                and m.played_at >= :fromInclusive
                and m.played_at < :toExclusive
        ) participant_results
        where race is not null
        group by race
        """, nativeQuery = true)
    List<RaceStatRow> findRaceStatsPlayedBetween(
        @Param("groupId") Long groupId,
        @Param("playerId") Long playerId,
        @Param("fromInclusive") OffsetDateTime fromInclusive,
        @Param("toExclusive") OffsetDateTime toExclusive
    );

    interface RaceStatRow {
        String getRace();

        Integer getWins();

        Integer getLosses();
    }

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query(value = "delete from player_race_stats where group_id = :groupId", nativeQuery = true)
    void deleteByGroupIdForRebuild(@Param("groupId") Long groupId);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query(value = """
        insert into player_race_stats (
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
            from players p
            join match_participants mp on mp.player_id = p.id
            join matches m on m.id = mp.match_id and m.group_id = p.group_id
            where p.group_id = :groupId
        )
        select
            player_id,
            group_id,
            race,
            cast(count(*) filter (where result_symbol = 'W') as integer) as wins,
            cast(count(*) filter (where result_symbol = 'L') as integer) as losses,
            cast(count(*) filter (where result_symbol in ('W', 'L')) as integer) as games,
            now()
        from participant_results
        where result_symbol in ('W', 'L')
        group by player_id, group_id, race
        """, nativeQuery = true)
    void insertGroupRaceStats(@Param("groupId") Long groupId);
}
