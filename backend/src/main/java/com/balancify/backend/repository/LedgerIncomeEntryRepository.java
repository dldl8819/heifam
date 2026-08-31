package com.balancify.backend.repository;

import com.balancify.backend.domain.LedgerIncomeEntry;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerIncomeEntryRepository extends JpaRepository<LedgerIncomeEntry, Long> {

    List<LedgerIncomeEntry> findByGroupIdOrderByEntryDateDescIdDesc(Long groupId);

    List<LedgerIncomeEntry> findByGroupIdOrderByEntryDateAscIdAsc(Long groupId);

    Optional<LedgerIncomeEntry> findByIdAndGroupId(Long id, Long groupId);

    @Query("select distinct e.category from LedgerIncomeEntry e where e.groupId = :groupId order by e.category")
    List<String> findDistinctCategoriesByGroupId(@Param("groupId") Long groupId);
}
