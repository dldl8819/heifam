package com.balancify.backend.service;

import com.balancify.backend.repository.MatchResultEditorEmailRepository;
import com.balancify.backend.repository.OperationAuditLogRepository;
import com.balancify.backend.service.exception.MatchEditForbiddenException;
import com.balancify.backend.service.exception.MatchEditQuotaExceededException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caps how many matches a member granted result editing can change per KST day.
 *
 * <p>Edits are counted from their audit logs, which the caller writes in the same transaction.
 * Holding the editor's permission row locked until that transaction ends keeps two edits from
 * both taking the last slot.
 */
@Service
public class MatchResultEditQuotaService {

    public static final int DAILY_EDIT_LIMIT = 10;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MatchResultEditorEmailRepository matchResultEditorEmailRepository;
    private final OperationAuditLogRepository operationAuditLogRepository;
    private final Clock clock;

    @Autowired
    public MatchResultEditQuotaService(
        MatchResultEditorEmailRepository matchResultEditorEmailRepository,
        OperationAuditLogRepository operationAuditLogRepository
    ) {
        this(matchResultEditorEmailRepository, operationAuditLogRepository, Clock.system(KST));
    }

    MatchResultEditQuotaService(
        MatchResultEditorEmailRepository matchResultEditorEmailRepository,
        OperationAuditLogRepository operationAuditLogRepository,
        Clock clock
    ) {
        this.matchResultEditorEmailRepository = matchResultEditorEmailRepository;
        this.operationAuditLogRepository = operationAuditLogRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(String editorEmail, Long matchId) {
        String normalizedEmail = editorEmail == null ? "" : editorEmail.trim().toLowerCase(Locale.ROOT);
        matchResultEditorEmailRepository.findByNormalizedEmailForUpdate(normalizedEmail)
            .orElseThrow(() -> new MatchEditForbiddenException("경기 결과 수정 권한이 없습니다."));

        OffsetDateTime startOfToday = LocalDate.now(clock.withZone(KST)).atStartOfDay(KST).toOffsetDateTime();
        List<Long> matchIdsEditedToday = operationAuditLogRepository.findTargetIdsByActionAndActorSince(
            OperationAuditLogService.ACTION_MATCH_RESULT_UPDATED,
            normalizedEmail,
            startOfToday
        );
        if (!matchIdsEditedToday.contains(matchId) && matchIdsEditedToday.size() >= DAILY_EDIT_LIMIT) {
            throw new MatchEditQuotaExceededException(
                "오늘 수정할 수 있는 경기 수(" + DAILY_EDIT_LIMIT + "경기)를 모두 사용했습니다."
            );
        }
    }
}
