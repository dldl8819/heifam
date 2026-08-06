package com.balancify.backend.repository;

import com.balancify.backend.domain.OperationAuditLog;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OperationAuditLogRepository extends JpaRepository<OperationAuditLog, Long>, JpaSpecificationExecutor<OperationAuditLog> {

    Page<OperationAuditLog> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    Page<OperationAuditLog> findAllByCreatedAtGreaterThanEqualOrderByCreatedAtDescIdDesc(
        OffsetDateTime cutoff,
        Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from OperationAuditLog log where log.createdAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") OffsetDateTime cutoff);
}
