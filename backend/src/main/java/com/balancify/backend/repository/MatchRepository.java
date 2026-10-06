package com.balancify.backend.repository;

import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchStatus;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRepository extends JpaRepository<Match, Long>, JpaSpecificationExecutor<Match> {

    List<Match> findByWinningTeamIsNotNullOrderByPlayedAtAscIdAsc();

    Optional<Match> findTopByGroup_IdOrderByPlayedAtDescIdDesc(Long groupId);

    Optional<Match> findTopByGroup_IdAndStatusOrderByPlayedAtDescIdDesc(Long groupId, MatchStatus status);

    long countByGroup_IdAndWinningTeamIsNotNull(Long groupId);

    List<Match> findBySeriesIdOrderBySeriesGameNumberAsc(Long seriesId);

    List<Match> findBySeriesIdInOrderBySeriesGameNumberAsc(Collection<Long> seriesIds);

    List<Match> findByBalanceSeriesIdOrderBySeriesGameNumberAsc(Long balanceSeriesId);

    List<Match> findByBalanceSeriesIdInOrderBySeriesGameNumberAsc(Collection<Long> balanceSeriesIds);

    // Matches set up but not played yet: the ones a prediction can be about.
    @Query("""
        select m
        from Match m
        where m.group.id = :groupId
          and m.status = com.balancify.backend.domain.MatchStatus.CONFIRMED
          and m.winningTeam is null
          and m.createdAt >= :fromInclusive
        order by m.createdAt desc, m.id desc
        """)
    List<Match> findAwaitingResultSince(
        @Param("groupId") Long groupId,
        @Param("fromInclusive") OffsetDateTime fromInclusive
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Match m where m.id = :matchId")
    Optional<Match> findByIdForUpdate(@Param("matchId") Long matchId);

    /** Matches of the same players whose result came in since the given time, newest first. */
    @Query("""
        select m
        from Match m
        where m.group.id = :groupId
          and m.teamSize = :teamSize
          and m.participantSignature = :participantSignature
          and m.winningTeam is not null
          and m.status <> com.balancify.backend.domain.MatchStatus.CANCELLED
          and m.resultRecordedAt >= :fromInclusive
        order by m.resultRecordedAt desc, m.id desc
        """)
    List<Match> findRecentResultsOfSamePlayers(
        @Param("groupId") Long groupId,
        @Param("teamSize") Integer teamSize,
        @Param("participantSignature") String participantSignature,
        @Param("fromInclusive") OffsetDateTime fromInclusive
    );

    @Query("""
        select m
        from Match m
        where m.group.id = :groupId
          and m.source = com.balancify.backend.domain.MatchSource.BALANCED
          and m.winningTeam is not null
          and m.resultRecordedAt >= :fromInclusive
        order by m.resultRecordedAt desc, m.id desc
        """)
    List<Match> findBalancedResultsRecordedSince(
        @Param("groupId") Long groupId,
        @Param("fromInclusive") OffsetDateTime fromInclusive
    );

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
          and (
            (:raceComposition is null and m.raceComposition is null)
            or m.raceComposition = :raceComposition
          )
          and m.status <> com.balancify.backend.domain.MatchStatus.CANCELLED
          and m.createdAt >= :fromInclusive
        order by m.createdAt desc, m.id desc
        """)
    List<Match> findRecentDuplicateCandidates(
        @Param("groupId") Long groupId,
        @Param("teamSize") Integer teamSize,
        @Param("participantSignature") String participantSignature,
        @Param("raceComposition") String raceComposition,
        @Param("fromInclusive") OffsetDateTime fromInclusive
    );

    @Query("""
        select m
        from Match m
        where m.id <> :excludedMatchId
          and m.group.id = :groupId
          and m.teamSize = :teamSize
          and m.participantSignature = :participantSignature
          and (
            (:raceComposition is null and m.raceComposition is null)
            or m.raceComposition = :raceComposition
          )
          and m.status <> com.balancify.backend.domain.MatchStatus.CANCELLED
          and m.createdAt >= :fromInclusive
          and m.createdAt <= :toInclusive
        order by m.createdAt desc, m.id desc
        """)
    List<Match> findRecentDuplicateCandidatesExcludingMatch(
        @Param("excludedMatchId") Long excludedMatchId,
        @Param("groupId") Long groupId,
        @Param("teamSize") Integer teamSize,
        @Param("participantSignature") String participantSignature,
        @Param("raceComposition") String raceComposition,
        @Param("fromInclusive") OffsetDateTime fromInclusive,
        @Param("toInclusive") OffsetDateTime toInclusive
    );
}
