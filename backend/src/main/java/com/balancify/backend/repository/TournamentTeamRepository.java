package com.balancify.backend.repository;

import com.balancify.backend.domain.TournamentTeam;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TournamentTeamRepository extends JpaRepository<TournamentTeam, Long> {

    List<TournamentTeam> findByTournament_IdOrderByTeamNumberAsc(Long tournamentId);
}
