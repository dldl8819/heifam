package com.balancify.backend.service;

import com.balancify.backend.repository.OperationAuditLogRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationAuditRetentionService {

    static final int RETENTION_YEARS = 1;

    private final OperationAuditLogRepository operationAuditLogRepository;
    private final Clock clock;

    @Autowired
    public OperationAuditRetentionService(
        OperationAuditLogRepository operationAuditLogRepository
    ) {
        this(operationAuditLogRepository, Clock.systemUTC());
    }

    OperationAuditRetentionService(
        OperationAuditLogRepository operationAuditLogRepository,
        Clock clock
    ) {
        this.operationAuditLogRepository = operationAuditLogRepository;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Scheduled(
        initialDelayString = "${balancify.privacy.audit-retention.initial-delay-ms:15000}",
        fixedDelayString = "${balancify.privacy.audit-retention.fixed-delay-ms:86400000}"
    )
    @Transactional
    public int deleteExpiredAuditLogs() {
        OffsetDateTime cutoff = OffsetDateTime.now(clock).minusYears(RETENTION_YEARS);
        return operationAuditLogRepository.deleteExpiredBefore(cutoff);
    }
}
