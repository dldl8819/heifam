package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.domain.MatchResultEditorEmail;
import com.balancify.backend.repository.MatchResultEditorEmailRepository;
import com.balancify.backend.repository.OperationAuditLogRepository;
import com.balancify.backend.service.exception.MatchEditForbiddenException;
import com.balancify.backend.service.exception.MatchEditQuotaExceededException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchResultEditQuotaServiceTest {

    private static final String EDITOR = "YOUR_USERNAME@example.com";
    // 2026-10-03 00:30 in KST, still 2026-10-02 in UTC.
    private static final Clock JUST_AFTER_KST_MIDNIGHT =
        Clock.fixed(Instant.parse("2026-10-02T15:30:00Z"), ZoneId.of("Asia/Seoul"));
    private static final OffsetDateTime START_OF_KST_DAY = OffsetDateTime.parse("2026-10-03T00:00:00+09:00");

    @Mock
    private MatchResultEditorEmailRepository matchResultEditorEmailRepository;

    @Mock
    private OperationAuditLogRepository operationAuditLogRepository;

    private MatchResultEditQuotaService quotaService;

    @BeforeEach
    void setUp() {
        quotaService = new MatchResultEditQuotaService(
            matchResultEditorEmailRepository,
            operationAuditLogRepository,
            JUST_AFTER_KST_MIDNIGHT
        );
    }

    @Test
    void allowsTheTenthDistinctMatchOfTheKstDay() {
        givenEditorEditedToday(LongStream.rangeClosed(1, 9).boxed().toList());

        assertThatCode(() -> quotaService.reserve(EDITOR, 10L)).doesNotThrowAnyException();
    }

    @Test
    void rejectsAnEleventhDistinctMatch() {
        givenEditorEditedToday(LongStream.rangeClosed(1, 10).boxed().toList());

        assertThatThrownBy(() -> quotaService.reserve(EDITOR, 11L))
            .isInstanceOf(MatchEditQuotaExceededException.class)
            .hasMessageContaining("10경기");
    }

    @Test
    void stillAllowsReEditingAMatchAlreadyEditedToday() {
        givenEditorEditedToday(LongStream.rangeClosed(1, 10).boxed().toList());

        assertThatCode(() -> quotaService.reserve(EDITOR, 4L)).doesNotThrowAnyException();
    }

    @Test
    void refusesAnEditorWhosePermissionWasJustRevoked() {
        when(matchResultEditorEmailRepository.findByNormalizedEmailForUpdate(EDITOR.toLowerCase()))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> quotaService.reserve(EDITOR, 1L))
            .isInstanceOf(MatchEditForbiddenException.class);
        verify(operationAuditLogRepository, never()).findTargetIdsByActionAndActorSince(anyString(), anyString(), any());
    }

    private void givenEditorEditedToday(List<Long> matchIds) {
        when(matchResultEditorEmailRepository.findByNormalizedEmailForUpdate(EDITOR.toLowerCase()))
            .thenReturn(Optional.of(new MatchResultEditorEmail()));
        when(operationAuditLogRepository.findTargetIdsByActionAndActorSince(
            OperationAuditLogService.ACTION_MATCH_RESULT_UPDATED,
            EDITOR.toLowerCase(),
            START_OF_KST_DAY
        )).thenReturn(matchIds);
    }
}
