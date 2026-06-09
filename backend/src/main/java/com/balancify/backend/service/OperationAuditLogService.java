package com.balancify.backend.service;

import com.balancify.backend.api.admin.dto.OperationAuditLogPageResponse;
import com.balancify.backend.api.admin.dto.OperationAuditLogResponse;
import com.balancify.backend.domain.OperationAuditLog;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.OperationAuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationAuditLogService {

    public static final String ACTION_PLAYER_REGISTERED = "PLAYER_REGISTERED";
    public static final String ACTION_PLAYER_REGISTRATION_UPDATED = "PLAYER_REGISTRATION_UPDATED";
    public static final String ACTION_PLAYER_REACTIVATED_BY_REGISTRATION = "PLAYER_REACTIVATED_BY_REGISTRATION";
    public static final String ACTION_PLAYER_TIER_UPDATED = "PLAYER_TIER_UPDATED";
    public static final String ACTION_MATCH_DELETED = "MATCH_DELETED";

    private static final int MAX_PAGE_SIZE = 200;

    private final OperationAuditLogRepository operationAuditLogRepository;

    public OperationAuditLogService(OperationAuditLogRepository operationAuditLogRepository) {
        this.operationAuditLogRepository = operationAuditLogRepository;
    }

    @Transactional(readOnly = true)
    public List<OperationAuditLogResponse> getRecentLogs(int limit) {
        return getLogs(0, limit).items();
    }

    @Transactional(readOnly = true)
    public OperationAuditLogPageResponse getLogs(int page, int size) {
        return getLogs(page, size, OperationAuditLogFilter.empty());
    }

    @Transactional(readOnly = true)
    public OperationAuditLogPageResponse getLogs(int page, int size, OperationAuditLogFilter filter) {
        int normalizedPage = Math.max(0, page);
        int normalizedLimit = Math.max(1, Math.min(MAX_PAGE_SIZE, size));
        PageRequest pageRequest = PageRequest.of(
            normalizedPage,
            normalizedLimit,
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        OperationAuditLogFilter normalizedFilter = filter == null ? OperationAuditLogFilter.empty() : filter;
        Page<OperationAuditLog> logs = normalizedFilter.isEmpty()
            ? operationAuditLogRepository.findAllByOrderByCreatedAtDescIdDesc(pageRequest)
            : operationAuditLogRepository.findAll(buildSpecification(normalizedFilter), pageRequest);
        List<OperationAuditLogResponse> items = logs
            .stream()
            .map(this::toResponse)
            .toList();
        return new OperationAuditLogPageResponse(
            items,
            logs.getNumber(),
            logs.getSize(),
            logs.getTotalElements(),
            logs.getTotalPages(),
            logs.isFirst(),
            logs.isLast()
        );
    }

    private Specification<OperationAuditLog> buildSpecification(OperationAuditLogFilter filter) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.fromDate() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                    root.<OffsetDateTime>get("createdAt"),
                    filter.fromDate().atStartOfDay().atOffset(filter.zoneOffset())
                ));
            }
            if (filter.toDate() != null) {
                predicates.add(criteriaBuilder.lessThan(
                    root.<OffsetDateTime>get("createdAt"),
                    filter.toDate().plusDays(1).atStartOfDay().atOffset(filter.zoneOffset())
                ));
            }

            String actor = toLikePattern(filter.actor());
            if (actor != null) {
                predicates.add(criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("actorEmail")), actor, '\\'),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("actorNickname")), actor, '\\')
                ));
            }

            String action = normalizeActionFilter(filter.action());
            if (action != null) {
                predicates.add(criteriaBuilder.equal(root.get("action"), action));
            }

            String content = toLikePattern(filter.content());
            if (content != null) {
                predicates.add(criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("action")), content, '\\'),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("summary")), content, '\\'),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("details")), content, '\\')
                ));
            }

            String target = toLikePattern(filter.target());
            if (target != null) {
                List<Predicate> targetPredicates = new ArrayList<>();
                targetPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("targetType")), target, '\\'));
                targetPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("targetLabel")), target, '\\'));
                Long targetId = parseLong(filter.target());
                if (targetId != null) {
                    targetPredicates.add(criteriaBuilder.equal(root.get("targetId"), targetId));
                }
                predicates.add(criteriaBuilder.or(targetPredicates.toArray(Predicate[]::new)));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    @Transactional
    public void recordPlayerRegistration(
        String actorEmail,
        String actorNickname,
        Long groupId,
        Player player,
        boolean created,
        boolean reactivated
    ) {
        if (player == null) {
            return;
        }

        String action = created
            ? ACTION_PLAYER_REGISTERED
            : reactivated
                ? ACTION_PLAYER_REACTIVATED_BY_REGISTRATION
                : ACTION_PLAYER_REGISTRATION_UPDATED;
        String summary = switch (action) {
            case ACTION_PLAYER_REGISTERED -> "선수 등록";
            case ACTION_PLAYER_REACTIVATED_BY_REGISTRATION -> "비활성 선수 재등록";
            default -> "선수 등록 정보 갱신";
        };

        OperationAuditLog log = baseLog(actorEmail, actorNickname, action, "PLAYER", player.getId(), player.getNickname(), groupId);
        log.setSummary(summary);
        log.setDetails(buildPlayerDetails(player));
        operationAuditLogRepository.save(log);
    }

    @Transactional
    public void recordPlayerProfileUpdate(
        String actorEmail,
        String actorNickname,
        Long groupId,
        Player player,
        String previousNickname,
        String nextNickname,
        String previousRace,
        String nextRace
    ) {
        if (player == null) {
            return;
        }

        String details = buildPlayerProfileUpdateDetails(previousNickname, nextNickname, previousRace, nextRace);
        if (details == null) {
            return;
        }

        OperationAuditLog log = baseLog(
            actorEmail,
            actorNickname,
            ACTION_PLAYER_REGISTRATION_UPDATED,
            "PLAYER",
            player.getId(),
            player.getNickname(),
            groupId
        );
        log.setSummary("선수 정보 갱신");
        log.setDetails(details);
        operationAuditLogRepository.save(log);
    }

    @Transactional
    public void recordPlayerTierUpdate(
        String actorEmail,
        String actorNickname,
        Long groupId,
        Player player,
        String previousTier,
        String nextTier,
        Integer previousMmr,
        Integer nextMmr
    ) {
        if (player == null) {
            return;
        }

        OperationAuditLog log = baseLog(
            actorEmail,
            actorNickname,
            ACTION_PLAYER_TIER_UPDATED,
            "PLAYER",
            player.getId(),
            player.getNickname(),
            groupId
        );
        log.setSummary("티어 수정");
        log.setDetails(
            "tier=" + formatTierForAudit(previousTier) + " -> " + formatTierForAudit(nextTier)
                + ", mmr=" + formatMmrForAudit(previousMmr) + " -> " + formatMmrForAudit(nextMmr)
        );
        operationAuditLogRepository.save(log);
    }

    @Transactional
    public void recordMatchDeletion(
        String actorEmail,
        String actorNickname,
        MatchResultService.DeletedMatchAuditSnapshot snapshot
    ) {
        if (snapshot == null) {
            return;
        }

        OperationAuditLog log = baseLog(
            actorEmail,
            actorNickname,
            ACTION_MATCH_DELETED,
            "MATCH",
            snapshot.matchId(),
            snapshot.matchId() == null ? null : "#" + snapshot.matchId(),
            snapshot.groupId()
        );
        log.setSummary("경기 삭제");
        log.setDetails("matchId=" + snapshot.matchId() + ", deletedAt=" + log.getCreatedAt());
        operationAuditLogRepository.save(log);
    }

    private OperationAuditLog baseLog(
        String actorEmail,
        String actorNickname,
        String action,
        String targetType,
        Long targetId,
        String targetLabel,
        Long groupId
    ) {
        OperationAuditLog log = new OperationAuditLog();
        log.setAction(limit(safeTrim(action).toUpperCase(Locale.ROOT), 60));
        log.setActorEmail(limit(normalizeEmail(actorEmail), 320));
        log.setActorNickname(limit(trimToNull(actorNickname), 100));
        log.setTargetType(limit(safeTrim(targetType).toUpperCase(Locale.ROOT), 60));
        log.setTargetId(targetId);
        log.setTargetLabel(limit(trimToNull(targetLabel), 255));
        log.setGroupId(groupId);
        return log;
    }

    private String buildPlayerDetails(Player player) {
        String tier = trimToNull(player.getTier());
        String race = trimToNull(player.getRace());
        if (tier == null && race == null) {
            return null;
        }
        if (tier == null) {
            return "race=" + race;
        }
        if (race == null) {
            return "tier=" + tier;
        }
        return "tier=" + tier + ", race=" + race;
    }

    private String buildPlayerProfileUpdateDetails(
        String previousNickname,
        String nextNickname,
        String previousRace,
        String nextRace
    ) {
        List<String> changes = new ArrayList<>();
        addAuditChange(changes, "nickname", previousNickname, nextNickname);
        addAuditChange(changes, "race", previousRace, nextRace);
        return changes.isEmpty() ? null : String.join(", ", changes);
    }

    private void addAuditChange(List<String> changes, String field, String previousValue, String nextValue) {
        String previous = trimToNull(previousValue);
        String next = trimToNull(nextValue);
        if (previous == null ? next == null : previous.equals(next)) {
            return;
        }
        changes.add(field + "=" + formatTextForAudit(previous) + " -> " + formatTextForAudit(next));
    }

    private String formatTextForAudit(String value) {
        return value == null ? "-" : value;
    }

    private String normalizeActionFilter(String value) {
        String normalized = safeTrim(value).toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || "ALL".equals(normalized)) {
            return null;
        }
        return normalized;
    }

    private String toLikePattern(String value) {
        String normalized = safeTrim(value).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        return "%" + escapeLikePattern(normalized) + "%";
    }

    private String escapeLikePattern(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    }

    private Long parseLong(String value) {
        String normalized = safeTrim(value);
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String formatTierForAudit(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? "UNASSIGNED" : normalized.toUpperCase(Locale.ROOT);
    }

    private String formatMmrForAudit(Integer value) {
        return String.valueOf(Math.max(0, value == null ? 0 : value));
    }

    private OperationAuditLogResponse toResponse(OperationAuditLog log) {
        return new OperationAuditLogResponse(
            log.getId(),
            log.getAction(),
            log.getActorEmail(),
            log.getActorNickname(),
            log.getTargetType(),
            log.getTargetId(),
            log.getTargetLabel(),
            log.getGroupId(),
            log.getSummary(),
            log.getDetails(),
            log.getCreatedAt()
        );
    }

    private String normalizeEmail(String value) {
        String normalized = safeTrim(value).toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private String trimToNull(String value) {
        String trimmed = safeTrim(value);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public record OperationAuditLogFilter(
        LocalDate fromDate,
        LocalDate toDate,
        String actor,
        String action,
        String content,
        String target
    ) {
        private static final ZoneOffset KST_OFFSET = ZoneOffset.ofHours(9);

        public static OperationAuditLogFilter empty() {
            return new OperationAuditLogFilter(null, null, null, null, null, null);
        }

        private boolean isEmpty() {
            return fromDate == null
                && toDate == null
                && isBlank(actor)
                && isEmptyAction(action)
                && isBlank(content)
                && isBlank(target);
        }

        private ZoneOffset zoneOffset() {
            return KST_OFFSET;
        }

        private static boolean isBlank(String value) {
            return value == null || value.trim().isEmpty();
        }

        private static boolean isEmptyAction(String value) {
            return isBlank(value) || "ALL".equalsIgnoreCase(value.trim());
        }
    }
}
