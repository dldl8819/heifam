package com.balancify.backend.repository;

import com.balancify.backend.domain.PrizeEvent;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrizeEventRepository extends JpaRepository<PrizeEvent, Long> {

    List<PrizeEvent> findTop20ByGroupIdOrderByIdDesc(Long groupId);

    // Confirming takes this lock so two clicks never pay the same event twice.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from PrizeEvent event where event.id = :id and event.groupId = :groupId")
    Optional<PrizeEvent> findForUpdate(@Param("id") Long id, @Param("groupId") Long groupId);
}
