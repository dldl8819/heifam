package com.balancify.backend.repository;

import com.balancify.backend.domain.LedgerServerCost;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerServerCostRepository extends JpaRepository<LedgerServerCost, Long> {

    List<LedgerServerCost> findByGroupIdOrderByBillingMonthDescIdDesc(Long groupId);

    Optional<LedgerServerCost> findByIdAndGroupId(Long id, Long groupId);
}
