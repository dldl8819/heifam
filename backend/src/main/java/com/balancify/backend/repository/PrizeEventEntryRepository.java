package com.balancify.backend.repository;

import com.balancify.backend.domain.PrizeEventEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrizeEventEntryRepository extends JpaRepository<PrizeEventEntry, Long> {

    List<PrizeEventEntry> findByEventIdOrderByScoreDescPlayerIdAsc(Long eventId);
}
