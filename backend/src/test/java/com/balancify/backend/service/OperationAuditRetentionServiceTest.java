package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.repository.OperationAuditLogRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

@ExtendWith(MockitoExtension.class)
class OperationAuditRetentionServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-07T03:00:00Z");

    @Mock
    private OperationAuditLogRepository operationAuditLogRepository;

    @Test
    void deletesAuditLogsOlderThanOneYear() {
        OffsetDateTime cutoff = NOW.minusYears(1);
        when(operationAuditLogRepository.deleteExpiredBefore(cutoff)).thenReturn(3);
        OperationAuditRetentionService service = new OperationAuditRetentionService(
            operationAuditLogRepository,
            Clock.fixed(Instant.parse("2026-08-07T03:00:00Z"), ZoneOffset.UTC)
        );

        assertThat(service.deleteExpiredAuditLogs()).isEqualTo(3);
        verify(operationAuditLogRepository).deleteExpiredBefore(cutoff);
    }

    @Test
    void schedulesDailyRetentionSweep() throws NoSuchMethodException {
        Scheduled schedule = OperationAuditRetentionService.class
            .getDeclaredMethod("deleteExpiredAuditLogs")
            .getAnnotation(Scheduled.class);

        assertThat(schedule.initialDelayString()).contains("audit-retention.initial-delay-ms");
        assertThat(schedule.fixedDelayString()).contains("audit-retention.fixed-delay-ms");
    }
}
