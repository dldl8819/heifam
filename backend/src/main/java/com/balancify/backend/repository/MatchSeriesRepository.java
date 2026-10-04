package com.balancify.backend.repository;

import com.balancify.backend.domain.MatchSeries;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchSeriesRepository extends JpaRepository<MatchSeries, Long> {

    List<MatchSeries> findByTournament_IdOrderByIdAsc(Long tournamentId);
}
