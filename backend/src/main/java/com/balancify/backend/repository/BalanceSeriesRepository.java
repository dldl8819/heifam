package com.balancify.backend.repository;

import com.balancify.backend.domain.BalanceSeries;
import com.balancify.backend.domain.BalanceSeriesStatus;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BalanceSeriesRepository extends JpaRepository<BalanceSeries, Long> {

    Optional<BalanceSeries> findByIdAndGroupId(Long id, Long groupId);

    // Results of one series' games take this lock one at a time.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select series from BalanceSeries series where series.id = :id")
    Optional<BalanceSeries> findByIdForUpdate(@Param("id") Long id);

    /** Series still running, and those started since the given time that were not cancelled. */
    @Query("""
        select series from BalanceSeries series
        where series.groupId = :groupId
          and (series.status = com.balancify.backend.domain.BalanceSeriesStatus.IN_PROGRESS
               or (series.status = com.balancify.backend.domain.BalanceSeriesStatus.COMPLETED
                   and series.createdAt >= :since))
        order by series.id desc
        """)
    List<BalanceSeries> findRecentForBoard(@Param("groupId") Long groupId, @Param("since") OffsetDateTime since);

    @Query("""
        select playerId from BalanceSeries series join series.playerIds playerId
        where series.groupId = :groupId and series.status = :status
        """)
    List<Long> findPlayerIdsByGroupIdAndStatus(
        @Param("groupId") Long groupId,
        @Param("status") BalanceSeriesStatus status
    );
}
