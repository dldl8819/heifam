package com.balancify.backend.repository;

import com.balancify.backend.domain.AllowedUserEmail;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AllowedUserEmailRepository extends JpaRepository<AllowedUserEmail, Long> {

    boolean existsByNormalizedEmail(String normalizedEmail);

    Optional<AllowedUserEmail> findByNormalizedEmail(String normalizedEmail);

    List<AllowedUserEmail> findAllByOrderByNormalizedEmailAsc();
}
