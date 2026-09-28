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

    // Only races that are known: a PPP team leaves no doubt, anywhere else the race counts only if
    // a recorder set it, since otherwise it is the balancer's guess. A team is PPP by the same rule
    // as the composition rows shown beside it (player_game_type_stats), so both tables agree even
    // for early matches stored without a composition. 3v3 only.
    @Query(value = """
        with player_rows as (
            select
                mp.match_id,
                upper(mp.team) as team,
                upper(coalesce(mp.assigned_race, '')) as assigned_race,
                m.races_recorded,
                upper(coalesce(m.race_composition, '')) as stored_composition,
                case
                    when upper(m.winning_team) = upper(mp.team) then 'W'
                    else 'L'
                end as result_symbol
            from match_participants mp
            join matches m on m.id = mp.match_id
            where mp.player_id = :playerId
                and m.group_id = :groupId
                and m.team_size = 3
                and m.winning_team is not null
                and mp.team is not null
                and btrim(mp.team) <> ''
                and m.played_at >= :fromInclusive
                and m.played_at < :toExclusive
        ),
        team_races as (
            select
                player_rows.match_id,
                player_rows.team,
                count(*) as members,
                count(teammate_race.race) as known_members,
                count(*) filter (where teammate_race.race = 'P') as protoss_members
            from player_rows
            join match_participants teammate
                on teammate.match_id = player_rows.match_id
                and upper(teammate.team) = player_rows.team
            cross join lateral (
                select case
                    when upper(coalesce(teammate.assigned_race, '')) in ('P', 'T', 'Z')
                        then upper(teammate.assigned_race)
                    when upper(coalesce(teammate.race, '')) in ('P', 'T', 'Z')
                        then upper(teammate.race)
                end as race
            ) teammate_race
            group by player_rows.match_id, player_rows.team
        )
        select
            race,
            cast(count(*) filter (where result_symbol = 'W') as integer) as wins,
            cast(count(*) filter (where result_symbol = 'L') as integer) as losses
        from (
            select
                case
                    when team_races.known_members = team_races.members
                        and team_races.protoss_members = team_races.members
                        then 'P'
                    when team_races.known_members < team_races.members
                        and player_rows.stored_composition = 'PPP'
                        then 'P'
                    when player_rows.races_recorded
                        and player_rows.assigned_race in ('P', 'T', 'Z')
                        then player_rows.assigned_race
                end as race,
                player_rows.result_symbol
            from player_rows
            join team_races
                on team_races.match_id = player_rows.match_id
                and team_races.team = player_rows.team
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
