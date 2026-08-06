package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.GroupPlayerUpdateRequest;
import com.balancify.backend.api.group.dto.GroupPlayerMmrUpdateRequest;
import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.PlayerLifecycleStatus;
import com.balancify.backend.domain.PlayerTierPolicy;
import com.balancify.backend.repository.PlayerRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerAdminService {

    private static final int CHAT_LEFT_REASON_MAX_LENGTH = 500;
    private static final Set<String> ACKNOWLEDGEABLE_TIERS = Set.of(
        "S", "A+", "A", "A-", "B+", "B", "B-", "C+", "C", "C-", "D", "UNASSIGNED"
    );
    private static final Set<String> EDITABLE_TIERS = ACKNOWLEDGEABLE_TIERS;

    private final PlayerRepository playerRepository;
    private final OperationAuditLogService operationAuditLogService;
    private final GroupReadCacheService groupReadCacheService;
    private final AccountDeletionService accountDeletionService;
    private final Clock clock;

    public PlayerAdminService(
        PlayerRepository playerRepository,
        OperationAuditLogService operationAuditLogService,
        GroupReadCacheService groupReadCacheService,
        AccountDeletionService accountDeletionService
    ) {
        this(
            playerRepository,
            operationAuditLogService,
            groupReadCacheService,
            accountDeletionService,
            Clock.systemUTC()
        );
    }

    PlayerAdminService(
        PlayerRepository playerRepository,
        OperationAuditLogService operationAuditLogService,
        GroupReadCacheService groupReadCacheService,
        AccountDeletionService accountDeletionService,
        Clock clock
    ) {
        this.playerRepository = playerRepository;
        this.operationAuditLogService = operationAuditLogService;
        this.groupReadCacheService = groupReadCacheService;
        this.accountDeletionService = accountDeletionService;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Transactional
    public void updatePlayer(
        Long groupId,
        Long playerId,
        GroupPlayerUpdateRequest request
    ) {
        updatePlayer(groupId, playerId, request, null, null);
    }

    @Transactional
    public void updatePlayer(
        Long groupId,
        Long playerId,
        GroupPlayerUpdateRequest request,
        String actorEmail,
        String actorNickname
    ) {
        Player player = playerRepository.findByIdAndGroup_Id(playerId, groupId)
            .orElseThrow(() -> new NoSuchElementException("Player not found"));

        String previousNickname = safeTrim(player.getNickname());
        String previousRace = PlayerRacePolicy.toDisplayRace(player.getRace());
        String previousTier = normalizeAuditTier(player.getTier());
        Integer previousMmr = player.getMmr();
        boolean previousActive = player.isActive();
        String nickname = safeTrim(request == null ? null : request.nickname());
        String race = safeTrim(request == null ? null : request.race());
        String tier = normalizeEditableTier(request == null ? null : request.tier());
        Boolean active = request == null ? null : request.active();
        requireProfileUpdateAllowed(player, active);
        OffsetDateTime chatLeftAt = request == null ? null : request.chatLeftAt();
        String chatLeftReason = safeTrim(request == null ? null : request.chatLeftReason());
        OffsetDateTime chatRejoinedAt = request == null ? null : request.chatRejoinedAt();
        String tierChangeAcknowledgedTier =
            normalizeTierChangeAcknowledgement(request == null ? null : request.tierChangeAcknowledgedTier());
        boolean hasDormancyMmrFloorTier = request != null && request.dormancyMmrFloorTier() != null;
        String dormancyMmrFloorTier = hasDormancyMmrFloorTier
            ? normalizeDormancyMmrFloorTier(request.dormancyMmrFloorTier())
            : null;
        String normalizedRace = race.isEmpty() ? "" : race.toUpperCase(Locale.ROOT);

        if (
            nickname.isEmpty()
                && normalizedRace.isEmpty()
                && tier.isEmpty()
                && active == null
                && chatLeftAt == null
                && chatLeftReason.isEmpty()
                && chatRejoinedAt == null
                && tierChangeAcknowledgedTier.isEmpty()
                && !hasDormancyMmrFloorTier
        ) {
            throw new IllegalArgumentException("At least one field is required");
        }

        boolean hasChatMetadata = chatLeftAt != null || !chatLeftReason.isEmpty() || chatRejoinedAt != null;
        if (active == null && hasChatMetadata) {
            throw new IllegalArgumentException("Chat activity metadata requires active status change");
        }

        boolean reactivating = !previousActive && Boolean.TRUE.equals(active);
        if (reactivating) {
            if (chatRejoinedAt == null) {
                throw new IllegalArgumentException("Chat rejoined time is required when reactivating player");
            }
            if (!nickname.isEmpty()
                || !normalizedRace.isEmpty()
                || !tier.isEmpty()
                || chatLeftAt != null
                || !chatLeftReason.isEmpty()
                || !tierChangeAcknowledgedTier.isEmpty()
                || hasDormancyMmrFloorTier) {
                throw new IllegalArgumentException("Reactivation request contains unsupported fields");
            }
            boolean duplicateActiveNickname = playerRepository
                .findByGroup_IdAndNicknameIgnoreCase(groupId, previousNickname)
                .stream()
                .anyMatch(candidate -> candidate.getId() != null
                    && !candidate.getId().equals(playerId)
                    && candidate.isActive()
                    && !candidate.isAnonymized());
            if (duplicateActiveNickname) {
                throw new IllegalArgumentException("Nickname already exists in group");
            }
            int updated = playerRepository.reactivateRetainedInactivePlayer(
                groupId,
                playerId,
                chatRejoinedAt,
                OffsetDateTime.now(clock)
            );
            if (updated != 1) {
                throw new NoSuchElementException("Player not found");
            }
            PlayerIdentityPolicy.reactivate(player, chatRejoinedAt);
            TransactionAfterCommit.runNowAndAfterCommit(
                () -> groupReadCacheService.evictGroup(groupId)
            );
            operationAuditLogService.recordPlayerActivityUpdate(
                actorEmail,
                actorNickname,
                groupId,
                player,
                false,
                true
            );
            return;
        }

        boolean nicknameChanged = !nickname.isEmpty() && !nickname.equals(previousNickname);
        if (!nickname.isEmpty()) {
            List<Player> sameNicknamePlayers =
                playerRepository.findByGroup_IdAndNicknameIgnoreCase(groupId, nickname);
            boolean duplicateExists = sameNicknamePlayers
                .stream()
                .anyMatch(found -> found.getId() != null && !found.getId().equals(playerId));
            if (duplicateExists) {
                throw new IllegalArgumentException("Nickname already exists in group");
            }

            player.setNickname(nickname);
        }

        String nextRace = "";
        boolean raceChanged = false;
        if (!normalizedRace.isEmpty()) {
            try {
                nextRace = PlayerRacePolicy.normalizeCapability(normalizedRace);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Race must be one of P,T,Z,PT,PZ,TZ,PTZ");
            }
            raceChanged = !nextRace.equals(previousRace);
            if (raceChanged) {
                player.setRace(nextRace);
            }
        }

        boolean tierChanged = !tier.isEmpty() && !tier.equals(previousTier);
        if (tierChanged) {
            int defaultMmr = PlayerTierPolicy.resolveDefaultMmrForTier(tier);
            player.setBaseMmr(defaultMmr);
            player.setMmr(defaultMmr);
            player.setTier(tier);
        }

        if (active != null) {
            if (active) {
                if (chatRejoinedAt == null) {
                    throw new IllegalArgumentException("Chat rejoined time is required when reactivating player");
                }
                PlayerIdentityPolicy.reactivate(player, chatRejoinedAt);
            } else {
                if (chatLeftAt == null) {
                    throw new IllegalArgumentException("Chat left time is required when deactivating player");
                }
                if (chatLeftReason.isEmpty()) {
                    throw new IllegalArgumentException("Chat left reason is required when deactivating player");
                }
                if (chatLeftReason.length() > CHAT_LEFT_REASON_MAX_LENGTH) {
                    throw new IllegalArgumentException("Chat left reason must be 500 characters or fewer");
                }
                player.setChatLeftAt(chatLeftAt);
                player.setChatLeftReason(chatLeftReason);
                accountDeletionService.deactivatePlayer(player);
            }
        }

        if (!tierChangeAcknowledgedTier.isEmpty()) {
            player.setTierChangeAcknowledgedTier(tierChangeAcknowledgedTier);
            player.setTierChangeAcknowledgedAt(OffsetDateTime.now());
        }

        if (hasDormancyMmrFloorTier && !Objects.equals(dormancyMmrFloorTier, player.getDormancyMmrFloorTier())) {
            player.setDormancyMmrFloorTier(dormancyMmrFloorTier);
        }

        playerRepository.save(player);
        groupReadCacheService.evictGroup(groupId);
        boolean deactivated = active != null && !active && previousActive;
        if (!deactivated && (nicknameChanged || raceChanged)) {
            operationAuditLogService.recordPlayerProfileUpdate(
                actorEmail,
                actorNickname,
                groupId,
                player,
                previousNickname,
                player.getNickname(),
                previousRace,
                player.getRace()
            );
        }
        if (!deactivated && tierChanged) {
            operationAuditLogService.recordPlayerTierUpdate(
                actorEmail,
                actorNickname,
                groupId,
                player,
                previousTier,
                tier,
                previousMmr,
                player.getMmr()
            );
        }
        if (active != null && previousActive != player.isActive()) {
            operationAuditLogService.recordPlayerActivityUpdate(
                actorEmail,
                actorNickname,
                groupId,
                player,
                previousActive,
                player.isActive()
            );
        }
    }

    @Transactional
    public void updatePlayerMmr(
        Long groupId,
        Long playerId,
        GroupPlayerMmrUpdateRequest request
    ) {
        Player player = playerRepository.findByIdAndGroup_Id(playerId, groupId)
            .orElseThrow(() -> new NoSuchElementException("Player not found"));
        requireActivePlayer(player);

        Integer nextMmr = request == null ? null : request.mmr();
        if (nextMmr == null) {
            throw new IllegalArgumentException("MMR is required");
        }
        if (nextMmr < 0 || nextMmr > 5000) {
            throw new IllegalArgumentException("MMR must be between 0 and 5000");
        }

        player.setMmr(nextMmr);
        playerRepository.save(player);
        groupReadCacheService.evictGroup(groupId);
    }

    @Transactional
    public void deletePlayer(Long groupId, Long playerId) {
        Player player = playerRepository.findByIdAndGroup_Id(playerId, groupId)
            .orElseThrow(() -> new NoSuchElementException("Player not found"));
        requireActivePlayer(player);
        try {
            playerRepository.delete(player);
            playerRepository.flush();
            groupReadCacheService.evictGroup(groupId);
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException(
                "매치 또는 드래프트 기록이 남아 있는 선수는 삭제할 수 없습니다.",
                exception
            );
        }
    }

    private void requireProfileUpdateAllowed(Player player, Boolean requestedActive) {
        if (player == null || player.isAnonymized()
            || player.getLifecycleStatus() == PlayerLifecycleStatus.WITHDRAWN) {
            throw new NoSuchElementException("Player not found");
        }
        if (!player.isActive() && !Boolean.TRUE.equals(requestedActive)) {
            throw new NoSuchElementException("Player not found");
        }
    }

    private void requireActivePlayer(Player player) {
        if (PlayerIdentityPolicy.isIdentityHidden(player)) {
            throw new NoSuchElementException("Player not found");
        }
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeTierChangeAcknowledgement(String value) {
        String normalized = safeTrim(value).toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "";
        }
        if ("NONE".equals(normalized) || "PENDING".equals(normalized) || "TBD".equals(normalized)) {
            return "UNASSIGNED";
        }
        if (!ACKNOWLEDGEABLE_TIERS.contains(normalized)) {
            throw new IllegalArgumentException("Tier change acknowledgement tier is invalid");
        }
        return normalized;
    }

    private String normalizeEditableTier(String value) {
        String normalized = safeTrim(value).toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "";
        }
        if ("NONE".equals(normalized) || "PENDING".equals(normalized) || "TBD".equals(normalized)) {
            return "UNASSIGNED";
        }
        if (!EDITABLE_TIERS.contains(normalized)) {
            throw new IllegalArgumentException("Tier is invalid");
        }
        return normalized;
    }

    private String normalizeDormancyMmrFloorTier(String value) {
        String normalized = safeTrim(value).toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()
            || "UNASSIGNED".equals(normalized)
            || "NONE".equals(normalized)
            || "PENDING".equals(normalized)
            || "TBD".equals(normalized)) {
            return null;
        }

        String rankedTier = PlayerTierPolicy.normalizeRankedTier(normalized);
        if (rankedTier.isEmpty()) {
            throw new IllegalArgumentException("Dormancy MMR floor tier is invalid");
        }
        return rankedTier;
    }

    private String normalizeAuditTier(String value) {
        String normalized = safeTrim(value).toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || "NONE".equals(normalized) || "PENDING".equals(normalized) || "TBD".equals(normalized)) {
            return "UNASSIGNED";
        }
        return normalized;
    }
}
