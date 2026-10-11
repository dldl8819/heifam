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

    /**
     * The email whose account holds the points of the person this email is, when it is grouped with
     * others as one person (access_people) and one of them has an account; empty otherwise. Grouping
     * merges their accounts into one, so there is one such account at most.
     */
    @Query(
        value = """
            select account.normalized_email
            from point_accounts account
            where account.normalized_email in (
                select other.normalized_email
                from access_person_emails mine
                join access_person_emails other on other.person_id = mine.person_id
                where mine.normalized_email = :email
            )
            order by account.id
            limit 1
            """,
        nativeQuery = true
    )
    Optional<String> findPersonAccountEmail(@Param("email") String email);

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
