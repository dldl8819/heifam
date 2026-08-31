package com.balancify.backend.repository;

import com.balancify.backend.domain.LedgerExpenseEntry;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerExpenseEntryRepository extends JpaRepository<LedgerExpenseEntry, Long> {

    List<LedgerExpenseEntry> findByGroupIdOrderByEntryDateDescIdDesc(Long groupId);

    List<LedgerExpenseEntry> findByGroupIdAndExpenseTypeOrderByEntryDateDescIdDesc(Long groupId, String expenseType);

    List<LedgerExpenseEntry> findByGroupIdOrderByEntryDateAscIdAsc(Long groupId);

    Optional<LedgerExpenseEntry> findByIdAndGroupId(Long id, Long groupId);

    @Query(
        "select distinct e.category from LedgerExpenseEntry e "
            + "where e.groupId = :groupId and e.expenseType = :expenseType order by e.category"
    )
    List<String> findDistinctCategoriesByGroupIdAndExpenseType(
        @Param("groupId") Long groupId,
        @Param("expenseType") String expenseType
    );
}
