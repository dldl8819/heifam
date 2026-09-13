package com.balancify.backend.repository;

import com.balancify.backend.domain.MatchParticipant;
import org.springframework.data.jpa.repository.Modifying;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchParticipantRepository extends JpaRepository<MatchParticipant, Long> {

    /**
     * Loads only the participants of matches the given player took part in.
     *
     * <p>The dashboard summaries are all scoped to a single player: their own per-race and
     * per-game-type records plus the team compositions and teammates of the matches they played.
     * Fetching every participant row in the group pulls the whole history on each request, so this
     * narrows the scan to the target player's matches while still returning the full roster of
     * those matches (needed to resolve team compositions and teammates).
     */
    @Query("""
        select mp
        from MatchParticipant mp
        join fetch mp.match m
        join fetch mp.player p
        where p.group.id = :groupId
          and m.id in (
            select mp2.match.id
            from MatchParticipant mp2
            where mp2.player.id = :playerId
          )
        order by m.playedAt desc, m.id desc, mp.id desc
        """)
    List<MatchParticipant> findByGroupIdAndPlayerMatchesOrderByPlayedAtDesc(
        @Param("groupId") Long groupId,
        @Param("playerId") Long playerId
    );

    @Query(value = """
        select
            mp.player_id as "playerId",
            max(m.played_at) as "lastPlayedAt"
        from match_participants mp
        join matches m on m.id = mp.match_id
        join players p on p.id = mp.player_id
        where p.group_id = :groupId
          and m.played_at is not null
          and m.status = 'COMPLETED'
        group by mp.player_id
        """, nativeQuery = true)
    List<PlayerLastPlayedAtProjection> findLastPlayedAtByGroupId(@Param("groupId") Long groupId);

    @Query("""
        select max(m.playedAt)
        from MatchParticipant mp
        join mp.match m
        join mp.player p
        where p.id = :playerId
          and p.group.id = :groupId
          and m.group.id = :groupId
          and m.status = com.balancify.backend.domain.MatchStatus.COMPLETED
          and m.playedAt is not null
        """)
    Optional<OffsetDateTime> findLastCompletedPlayedAt(
        @Param("groupId") Long groupId,
        @Param("playerId") Long playerId
    );

    @Query("""
        select mp
        from MatchParticipant mp
        join fetch mp.match m
        join fetch mp.player p
        where m.id = :matchId
        order by mp.id asc
        """)
    List<MatchParticipant> findByMatchIdWithPlayerAndMatch(@Param("matchId") Long matchId);

    @Query("""
        select mp
        from MatchParticipant mp
        join fetch mp.match m
        join fetch mp.player p
        where m.id in :matchIds
        order by m.playedAt asc, m.id asc, mp.id asc
        """)
    List<MatchParticipant> findByMatchIdInWithPlayerAndMatch(@Param("matchIds") List<Long> matchIds);

    @Modifying
    @Query("""
        update MatchParticipant mp
        set mp.mmrBefore = null,
            mp.mmrAfter = null,
            mp.mmrDelta = 0
        """)
    void resetAllDerivedRatings();


    void deleteByMatch_Id(Long matchId);

    interface PlayerLastPlayedAtProjection {
        Long getPlayerId();
        Instant getLastPlayedAt();
    }

}
