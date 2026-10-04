package com.balancify.backend.repository;

import com.balancify.backend.domain.PushSubscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByEmailOrderByIdDesc(String email);

    // Also called from the push thread, outside any transaction, when a push service says it is gone.
    @Transactional
    @Modifying
    @Query("delete from PushSubscription subscription where subscription.endpoint = :endpoint")
    int deleteByEndpoint(@Param("endpoint") String endpoint);
}
