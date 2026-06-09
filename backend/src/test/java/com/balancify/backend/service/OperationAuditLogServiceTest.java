package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.domain.OperationAuditLog;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.OperationAuditLogRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class OperationAuditLogServiceTest {

    @Mock
    private OperationAuditLogRepository operationAuditLogRepository;

    private OperationAuditLogService operationAuditLogService;

    @BeforeEach
    void setUp() {
        operationAuditLogService = new OperationAuditLogService(operationAuditLogRepository);
        lenient().when(operationAuditLogRepository.save(any(OperationAuditLog.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void recordsPlayerRegistrationAuditLog() {
        Player player = new Player();
        player.setId(10L);
        player.setNickname("PlayerOne");
        player.setTier("B+");
        player.setRace("P");

        operationAuditLogService.recordPlayerRegistration(
            "OPS@EXAMPLE.COM",
            "운영진",
            1L,
            player,
            true,
            false
        );

        ArgumentCaptor<OperationAuditLog> logCaptor = ArgumentCaptor.forClass(OperationAuditLog.class);
        verify(operationAuditLogRepository).save(logCaptor.capture());
        OperationAuditLog log = logCaptor.getValue();

        assertThat(log.getAction()).isEqualTo(OperationAuditLogService.ACTION_PLAYER_REGISTERED);
        assertThat(log.getActorEmail()).isEqualTo("ops@example.com");
        assertThat(log.getActorNickname()).isEqualTo("운영진");
        assertThat(log.getTargetType()).isEqualTo("PLAYER");
        assertThat(log.getTargetId()).isEqualTo(10L);
        assertThat(log.getTargetLabel()).isEqualTo("PlayerOne");
        assertThat(log.getGroupId()).isEqualTo(1L);
        assertThat(log.getSummary()).isEqualTo("선수 등록");
        assertThat(log.getDetails()).isEqualTo("tier=B+, race=P");
    }

    @Test
    void recordsMatchDeletionAuditLog() {
        OffsetDateTime playedAt = OffsetDateTime.parse("2026-05-23T12:00:00Z");

        operationAuditLogService.recordMatchDeletion(
            "ops@example.com",
            "운영진",
            new MatchResultService.DeletedMatchAuditSnapshot(99L, 1L, playedAt, true)
        );

        ArgumentCaptor<OperationAuditLog> logCaptor = ArgumentCaptor.forClass(OperationAuditLog.class);
        verify(operationAuditLogRepository).save(logCaptor.capture());
        OperationAuditLog log = logCaptor.getValue();

        assertThat(log.getAction()).isEqualTo(OperationAuditLogService.ACTION_MATCH_DELETED);
        assertThat(log.getTargetType()).isEqualTo("MATCH");
        assertThat(log.getTargetId()).isEqualTo(99L);
        assertThat(log.getTargetLabel()).isEqualTo("#99");
        assertThat(log.getGroupId()).isEqualTo(1L);
        assertThat(log.getSummary()).isEqualTo("경기 삭제");
        assertThat(log.getDetails()).isEqualTo("matchId=99, deletedAt=" + log.getCreatedAt());
    }

    @Test
    void recordsPlayerProfileUpdateAuditLogWithChangedFields() {
        Player player = new Player();
        player.setId(10L);
        player.setNickname("PlayerAlpha");
        player.setRace("PTZ");

        operationAuditLogService.recordPlayerProfileUpdate(
            "ops@example.com",
            "OpsUser",
            1L,
            player,
            "PlayerAlpha",
            "PlayerAlpha",
            "P",
            "PTZ"
        );

        ArgumentCaptor<OperationAuditLog> logCaptor = ArgumentCaptor.forClass(OperationAuditLog.class);
        verify(operationAuditLogRepository).save(logCaptor.capture());
        OperationAuditLog log = logCaptor.getValue();

        assertThat(log.getAction()).isEqualTo(OperationAuditLogService.ACTION_PLAYER_REGISTRATION_UPDATED);
        assertThat(log.getTargetType()).isEqualTo("PLAYER");
        assertThat(log.getTargetId()).isEqualTo(10L);
        assertThat(log.getTargetLabel()).isEqualTo("PlayerAlpha");
        assertThat(log.getSummary()).isEqualTo("선수 정보 갱신");
        assertThat(log.getDetails()).isEqualTo("race=P -> PTZ");
    }

    @Test
    void recordsPlayerTierUpdateAuditLogWithPreviousAndNextTier() {
        Player player = new Player();
        player.setId(10L);
        player.setNickname("PlayerAlpha");

        operationAuditLogService.recordPlayerTierUpdate(
            "ops@example.com",
            "OpsUser",
            1L,
            player,
            "C",
            "B+",
            1000,
            1200
        );

        ArgumentCaptor<OperationAuditLog> logCaptor = ArgumentCaptor.forClass(OperationAuditLog.class);
        verify(operationAuditLogRepository).save(logCaptor.capture());
        OperationAuditLog log = logCaptor.getValue();

        assertThat(log.getAction()).isEqualTo(OperationAuditLogService.ACTION_PLAYER_TIER_UPDATED);
        assertThat(log.getTargetType()).isEqualTo("PLAYER");
        assertThat(log.getTargetId()).isEqualTo(10L);
        assertThat(log.getTargetLabel()).isEqualTo("PlayerAlpha");
        assertThat(log.getSummary()).isEqualTo("티어 수정");
        assertThat(log.getDetails()).isEqualTo("tier=C -> B+, mmr=1000 -> 1200");
    }

    @Test
    void returnsPagedAuditLogs() {
        OperationAuditLog log = new OperationAuditLog();
        log.setAction(OperationAuditLogService.ACTION_PLAYER_TIER_UPDATED);
        log.setTargetType("PLAYER");
        log.setSummary("티어 수정");
        log.setCreatedAt(OffsetDateTime.parse("2026-05-23T12:00:00Z"));

        when(operationAuditLogRepository.findAllByOrderByCreatedAtDescIdDesc(any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(log), PageRequest.of(1, 20), 21));

        var response = operationAuditLogService.getLogs(1, 20);

        assertThat(response.items()).hasSize(1);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(21);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.first()).isFalse();
        assertThat(response.last()).isTrue();
    }
}
