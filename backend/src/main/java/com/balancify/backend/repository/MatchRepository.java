package com.balancify.backend.repository;

import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchStatus;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRepository extends JpaRepository<Match, Long> {

    Optional<Match> findTopByGroup_IdOrderByPlayedAtDescIdDesc(Long groupId);

    Optional<Match> findTopByGroup_IdAndStatusOrderByPlayedAtDescIdDesc(Long groupId, MatchStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Match m where m.id = :matchId")
    Optional<Match> findByIdForUpdate(@Param("matchId") Long matchId);

    @Query("""
        select m
        from Match m
        where m.group.id = :groupId
          and m.status = com.balancify.backend.domain.MatchStatus.COMPLETED
        order by m.playedAt desc, m.id desc
        """)
    List<Match> findRecentByGroupId(@Param("groupId") Long groupId, Pageable pageable);

    @Query("""
        select m
        from Match m
        where m.group.id = :groupId
          and m.status in :statuses
          and m.playedAt >= :fromInclusive
        order by m.playedAt desc, m.id desc
        """)
    List<Match> findRecentByGroupIdAndStatusIn(
        @Param("groupId") Long groupId,
        @Param("statuses") List<MatchStatus> statuses,
        @Param("fromInclusive") OffsetDateTime fromInclusive
    );

    @Query("""
        select m
        from Match m
        where m.group.id = :groupId
          and m.teamSize = :teamSize
          and m.participantSignature = :participantSignature
          and m.status in :statuses
          and m.createdAt >= :fromInclusive
        order by m.createdAt desc, m.id desc
        """)
    List<Match> findRecentDuplicateCandidates(
        @Param("groupId") Long groupId,
        @Param("teamSize") Integer teamSize,
        @Param("participantSignature") String participantSignature,
        @Param("statuses") List<MatchStatus> statuses,
        @Param("fromInclusive") OffsetDateTime fromInclusive
    );
}
