package com.balancify.backend.repository;

import com.balancify.backend.domain.PointTransaction;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

    boolean existsByAccount_IdAndReasonAndReferenceKey(Long accountId, String reason, String referenceKey);

    long countByAccount_IdAndReasonAndKstDate(Long accountId, String reason, LocalDate kstDate);

    List<PointTransaction> findByReasonAndReferenceKey(String reason, String referenceKey);

    List<PointTransaction> findTop20ByAccount_IdOrderByIdDesc(Long accountId);

    List<PointTransaction> findByAccount_IdAndKstDateBetweenOrderByIdDesc(Long accountId, LocalDate fromDate, LocalDate toDate);

    long countByAccount_IdAndReferenceKeyStartingWith(Long accountId, String referencePrefix);

    @Query("""
        select coalesce(sum(pointTransaction.amount), 0)
        from PointTransaction pointTransaction
        where pointTransaction.account.id = :accountId
          and pointTransaction.referenceKey like :referencePattern
        """)
    long sumAmountByReferencePattern(
        @Param("accountId") Long accountId,
        @Param("referencePattern") String referencePattern
    );

    @Query("""
        select coalesce(sum(pointTransaction.amount), 0)
        from PointTransaction pointTransaction
        where pointTransaction.account.id = :accountId
          and pointTransaction.reason in :reasons
          and pointTransaction.kstDate = :kstDate
        """)
    long sumAmountByReasonsOnDate(
        @Param("accountId") Long accountId,
        @Param("reasons") Collection<String> reasons,
        @Param("kstDate") LocalDate kstDate
    );

    @Query("select coalesce(sum(pointTransaction.amount), 0) from PointTransaction pointTransaction where pointTransaction.account.id = :accountId")
    long sumAmountByAccountId(@Param("accountId") Long accountId);

    @Query("""
        select pointTransaction.account.id as accountId,
               pointTransaction.account.normalizedEmail as normalizedEmail,
               sum(pointTransaction.amount) as points
        from PointTransaction pointTransaction
        where pointTransaction.kstDate between :fromDate and :toDate
        group by pointTransaction.account.id, pointTransaction.account.normalizedEmail
        having sum(pointTransaction.amount) > 0
        order by sum(pointTransaction.amount) desc, pointTransaction.account.id asc
        """)
    List<AccountPointTotal> sumPositiveAccountTotalsBetween(
        @Param("fromDate") LocalDate fromDate,
        @Param("toDate") LocalDate toDate
    );

    interface AccountPointTotal {
        Long getAccountId();

        String getNormalizedEmail();

        Long getPoints();
    }

}
