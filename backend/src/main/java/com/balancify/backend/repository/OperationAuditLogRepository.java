package com.balancify.backend.repository;

import com.balancify.backend.domain.OperationAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationAuditLogRepository extends JpaRepository<OperationAuditLog, Long> {

    Page<OperationAuditLog> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
