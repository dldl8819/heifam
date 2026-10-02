package com.balancify.backend.repository;

import com.balancify.backend.domain.MatchResultEditorEmail;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchResultEditorEmailRepository extends JpaRepository<MatchResultEditorEmail, Long> {

    Optional<MatchResultEditorEmail> findByNormalizedEmail(String normalizedEmail);

    List<MatchResultEditorEmail> findAllByOrderByNormalizedEmailAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select editor from MatchResultEditorEmail editor where editor.normalizedEmail = :normalizedEmail")
    Optional<MatchResultEditorEmail> findByNormalizedEmailForUpdate(@Param("normalizedEmail") String normalizedEmail);
}
