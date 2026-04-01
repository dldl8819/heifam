package com.balancify.backend.repository;

import com.balancify.backend.domain.UserRacePreference;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRacePreferenceRepository extends JpaRepository<UserRacePreference, Long> {

    Optional<UserRacePreference> findByNormalizedEmail(String normalizedEmail);
}
