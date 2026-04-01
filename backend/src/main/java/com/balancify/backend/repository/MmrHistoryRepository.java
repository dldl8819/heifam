package com.balancify.backend.repository;

import com.balancify.backend.domain.MmrHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MmrHistoryRepository extends JpaRepository<MmrHistory, Long> {

    List<MmrHistory> findByMatch_Id(Long matchId);

    void deleteByMatch_Id(Long matchId);
}
