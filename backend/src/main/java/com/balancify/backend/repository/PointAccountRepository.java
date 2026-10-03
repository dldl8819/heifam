package com.balancify.backend.repository;

import com.balancify.backend.domain.PointAccount;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointAccountRepository extends JpaRepository<PointAccount, Long> {

    Optional<PointAccount> findByNormalizedEmail(String normalizedEmail);

    // Two first-time grants for the same person may race; the unique email keeps one row.
    @Modifying
    @Query(
        value = """
            insert into point_accounts (normalized_email, created_at)
            values (:normalizedEmail, now())
            on conflict (normalized_email) do nothing
            """,
        nativeQuery = true
    )
    void insertIfMissing(@Param("normalizedEmail") String normalizedEmail);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from PointAccount account where account.normalizedEmail = :normalizedEmail")
    Optional<PointAccount> findByNormalizedEmailForUpdate(@Param("normalizedEmail") String normalizedEmail);
}
