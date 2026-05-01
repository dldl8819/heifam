package com.balancify.backend.repository;

import com.balancify.backend.domain.AdminMmrAccessEmail;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminMmrAccessEmailRepository extends JpaRepository<AdminMmrAccessEmail, Long> {

    Optional<AdminMmrAccessEmail> findByNormalizedEmail(String normalizedEmail);
}
