package com.balancify.backend.repository;

import com.balancify.backend.domain.MatchParticipant;
import org.springframework.data.jpa.repository.Modifying;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchParticipantRepository extends JpaRepository<MatchParticipant, Long> {

    @Query("""
        select mp
        from MatchParticipant mp
        join fetch mp.match m
        join fetch mp.player p
        where p.group.id = :groupId
        order by m.playedAt desc, m.id desc, mp.id desc
        """)
    List<MatchParticipant> findByGroupIdOrderByPlayedAtDesc(@Param("groupId") Long groupId);

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

    long countByPlayer_IdAndMatch_WinningTeamIsNotNull(Long playerId);

    void deleteByMatch_Id(Long matchId);
}
