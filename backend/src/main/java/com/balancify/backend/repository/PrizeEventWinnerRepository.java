package com.balancify.backend.repository;

import com.balancify.backend.domain.PrizeEventWinner;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrizeEventWinnerRepository extends JpaRepository<PrizeEventWinner, Long> {

    List<PrizeEventWinner> findByEventIdInOrderByEventIdAscPlaceAsc(Collection<Long> eventIds);
}
