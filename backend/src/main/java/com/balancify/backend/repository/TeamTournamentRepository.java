package com.balancify.backend.repository;

import com.balancify.backend.domain.TeamTournament;
import com.balancify.backend.domain.TeamTournamentStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamTournamentRepository extends JpaRepository<TeamTournament, Long> {

    Optional<TeamTournament> findFirstByGroup_IdAndStatusOrderByIdDesc(Long groupId, TeamTournamentStatus status);

    Optional<TeamTournament> findFirstByGroup_IdOrderByIdDesc(Long groupId);

    Optional<TeamTournament> findFirstByGroup_IdAndStatusNotOrderByIdDesc(Long groupId, TeamTournamentStatus status);

    Optional<TeamTournament> findByIdAndGroup_Id(Long id, Long groupId);

    // Series results in one tournament move its bracket, so they take this lock one at a time.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select tournament from TeamTournament tournament where tournament.id = :id")
    Optional<TeamTournament> findByIdForUpdate(@Param("id") Long id);
}
