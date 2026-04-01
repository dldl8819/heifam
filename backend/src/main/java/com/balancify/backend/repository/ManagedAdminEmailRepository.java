package com.balancify.backend.repository;

import com.balancify.backend.domain.ManagedAdminEmail;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagedAdminEmailRepository extends JpaRepository<ManagedAdminEmail, Long> {

    boolean existsByNormalizedEmail(String normalizedEmail);

    Optional<ManagedAdminEmail> findByNormalizedEmail(String normalizedEmail);

    List<ManagedAdminEmail> findAllByOrderByNormalizedEmailAsc();
}
