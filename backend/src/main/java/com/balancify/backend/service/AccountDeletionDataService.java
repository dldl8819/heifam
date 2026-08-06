package com.balancify.backend.service;

import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.PlayerLifecycleStatus;
import com.balancify.backend.repository.AccountPersonalDataRepository;
import com.balancify.backend.repository.PlayerRepository;
import com.balancify.backend.service.exception.AccountDeletionException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.NoSuchElementException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountDeletionDataService {

    public static final String DELETED_MEMBER_LABEL = PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL;
    private static final int INACTIVE_IDENTITY_RETENTION_YEARS = 1;

    private final AccountPersonalDataRepository accountPersonalDataRepository;
    private final PlayerRepository playerRepository;
    private final GroupReadCacheService groupReadCacheService;
    private final AccessControlService accessControlService;
    private final Clock clock;

    @Autowired
    public AccountDeletionDataService(
        AccountPersonalDataRepository accountPersonalDataRepository,
        PlayerRepository playerRepository,
        GroupReadCacheService groupReadCacheService,
        AccessControlService accessControlService
    ) {
        this(
            accountPersonalDataRepository,
            playerRepository,
            groupReadCacheService,
            accessControlService,
            Clock.systemUTC()
        );
    }

    AccountDeletionDataService(
        AccountPersonalDataRepository accountPersonalDataRepository,
        PlayerRepository playerRepository,
        GroupReadCacheService groupReadCacheService,
        AccessControlService accessControlService,
        Clock clock
    ) {
        this.accountPersonalDataRepository = accountPersonalDataRepository;
        this.playerRepository = playerRepository;
        this.groupReadCacheService = groupReadCacheService;
        this.accessControlService = accessControlService;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Transactional
    public void linkPlayers(UUID authUserId, String email) {
        if (authUserId == null) {
            return;
        }
        String normalizedEmail = normalizeEmail(email);
        String nickname = accountPersonalDataRepository
            .findLinkedNickname(normalizedEmail)
            .orElse("");
        if (nickname.isEmpty()) {
            return;
        }

        List<Player> candidates = playerRepository
            .findByNicknameIgnoreCaseAndAnonymizedAtIsNull(nickname);
        if (candidates.size() != 1
            || PlayerIdentityPolicy.isIdentityHidden(candidates.get(0))) {
            return;
        }
        Player candidate = candidates.get(0);
        if (candidate.getAuthUserId() != null
            && !authUserId.equals(candidate.getAuthUserId())) {
            return;
        }
        List<Player> changedPlayers = candidate.getAuthUserId() == null
            || candidate.getRetentionSubjectHash() != null
            ? List.of(candidate)
            : List.of();
        changedPlayers.forEach(changedPlayer -> {
            changedPlayer.setAuthUserId(authUserId);
            changedPlayer.setRetentionSubjectHash(null);
        });
        if (!changedPlayers.isEmpty()) {
            playerRepository.saveAll(changedPlayers);
        }
    }

    @Transactional
    public WithdrawalAnonymizationResult anonymizeWithdrawnAccount(UUID authUserId, String email) {
        if (authUserId == null) {
            throw new IllegalArgumentException("A verified account identifier is required");
        }
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isEmpty()) {
            throw new IllegalArgumentException("A verified account email is required");
        }
        if (accessControlService.hasConfiguredAccessGrant(normalizedEmail)) {
            throw new AccountDeletionException(
                "Account withdrawal requires removal from the configured access list"
            );
        }

        String linkedNickname = accountPersonalDataRepository
            .findLinkedNickname(normalizedEmail)
            .orElse("");

        Map<Long, Player> linkedPlayers = new LinkedHashMap<>();
        addPlayers(linkedPlayers, playerRepository.findByAuthUserIdAndAnonymizedAtIsNull(authUserId));
        addPlayers(
            linkedPlayers,
            playerRepository.findByRetentionSubjectHashAndAnonymizedAtIsNull(
                retentionSubjectHash(authUserId)
            )
        );
        if (!linkedNickname.isEmpty()) {
            if (linkedPlayers.isEmpty()) {
                List<Player> fallbackCandidates = playerRepository
                    .findByNicknameIgnoreCaseAndAnonymizedAtIsNull(linkedNickname);
                validateWithdrawalOwnership(authUserId, fallbackCandidates);
                if (fallbackCandidates.size() > 1) {
                    throw new AccountDeletionException("Account data ownership could not be resolved");
                }
                addPlayers(linkedPlayers, fallbackCandidates);
            }
        }

        OffsetDateTime withdrawnAt = OffsetDateTime.now(clock);
        accountPersonalDataRepository.enqueuePendingAuthDeletion(authUserId, withdrawnAt);
        Set<Long> groupIds = new LinkedHashSet<>();
        for (Player player : linkedPlayers.values()) {
            PlayerIdentityPolicy.anonymize(player, withdrawnAt);
            if (player.getGroup() != null && player.getGroup().getId() != null) {
                groupIds.add(player.getGroup().getId());
            }
        }
        if (!linkedPlayers.isEmpty()) {
            playerRepository.saveAllAndFlush(linkedPlayers.values());
        }

        List<Long> playerIds = List.copyOf(linkedPlayers.keySet());
        accountPersonalDataRepository.anonymizeHistoricalIdentity(
            normalizedEmail,
            playerIds,
            DELETED_MEMBER_LABEL
        );
        accountPersonalDataRepository.deleteAccountIdentity(authUserId, normalizedEmail);
        evictCaches(normalizedEmail, groupIds);

        return new WithdrawalAnonymizationResult(playerIds.size());
    }

    @Transactional
    public InactivePlayerCleanupOutcome retainInactivePlayer(
        Long playerId,
        OffsetDateTime inactiveAt,
        String inactiveReason
    ) {
        if (playerId == null) {
            throw new IllegalArgumentException("A persisted player is required");
        }
        Player player = playerRepository.findByIdForIdentityUpdate(playerId)
            .orElseThrow(() -> new NoSuchElementException("Player not found"));
        if (PlayerIdentityPolicy.isIdentityHidden(player)) {
            throw new NoSuchElementException("Player not found");
        }
        if (inactiveAt == null) {
            throw new IllegalArgumentException("Inactive time is required");
        }
        if (!PlayerIdentityPolicy.isAllowedInactiveReason(inactiveReason)) {
            throw new IllegalArgumentException("Inactive reason must use an allowed category");
        }
        player.setChatLeftAt(inactiveAt);
        player.setChatLeftReason(inactiveReason.trim());
        return retainInactivePlayer(player);
    }

    InactivePlayerCleanupOutcome retainInactivePlayer(Player player) {
        if (player == null || player.getId() == null) {
            throw new IllegalArgumentException("A persisted player is required");
        }

        ResolvedAccountIdentity resolvedAccountIdentity = resolveInactiveAccountIdentity(player);
        UUID authUserId = resolvedAccountIdentity.authUserId();
        String accountEmail = resolvedAccountIdentity.normalizedEmail();
        if (authUserId != null) {
            // Lock every row sharing the account link before deciding whether the
            // external Auth identity is still needed by another active player.
            playerRepository.findByAuthUserIdAndAnonymizedAtIsNull(authUserId);
        }
        boolean hasAnotherActiveLinkedPlayer = authUserId != null
            && playerRepository
                .existsByAuthUserIdAndActiveTrueAndAnonymizedAtIsNullAndLifecycleStatusAndIdNot(
                authUserId,
                PlayerLifecycleStatus.ACTIVE,
                player.getId()
            );
        Long groupId = player.getGroup() == null ? null : player.getGroup().getId();
        boolean requiresAuthDeletion = authUserId != null && !hasAnotherActiveLinkedPlayer;
        boolean requiresAccountCleanup = !accountEmail.isEmpty() && !hasAnotherActiveLinkedPlayer;

        if (requiresAccountCleanup && accessControlService.hasConfiguredAccessGrant(accountEmail)) {
            throw new AccountDeletionException(
                "Account deactivation requires removal from the configured access list"
            );
        }

        OffsetDateTime processedAt = OffsetDateTime.now(clock);
        OffsetDateTime inactiveAt = player.getChatLeftAt() == null
            ? processedAt
            : player.getChatLeftAt();
        if (inactiveAt.isAfter(processedAt)) {
            throw new IllegalArgumentException("Inactive time cannot be in the future");
        }
        OffsetDateTime retainedUntil = inactiveAt.plusYears(INACTIVE_IDENTITY_RETENTION_YEARS);
        OffsetDateTime maximumRetainedUntil = processedAt.plusYears(INACTIVE_IDENTITY_RETENTION_YEARS);
        if (retainedUntil.isAfter(maximumRetainedUntil)) {
            retainedUntil = maximumRetainedUntil;
        }
        if (requiresAuthDeletion) {
            accountPersonalDataRepository.enqueuePendingAuthDeletion(authUserId, processedAt);
        }

        if (retainedUntil.isAfter(processedAt)) {
            PlayerIdentityPolicy.retainAdministrativeIdentity(
                player,
                PlayerLifecycleStatus.INACTIVE,
                inactiveAt,
                player.getChatLeftReason(),
                retainedUntil
            );
            player.setRetentionSubjectHash(
                authUserId != null
                    ? retentionSubjectHash(authUserId)
                    : null
            );
        } else {
            PlayerIdentityPolicy.anonymize(player, processedAt);
        }
        playerRepository.saveAndFlush(player);

        if (requiresAccountCleanup) {
            accountPersonalDataRepository.anonymizeHistoricalIdentity(
                accountEmail,
                List.of(player.getId()),
                DELETED_MEMBER_LABEL
            );
            accountPersonalDataRepository.deleteAccountIdentity(authUserId, accountEmail);
        } else if (!accountEmail.isEmpty()) {
            accountPersonalDataRepository.anonymizeHistoricalIdentityInGroup(
                accountEmail,
                groupId,
                List.of(player.getId()),
                DELETED_MEMBER_LABEL
            );
        } else {
            accountPersonalDataRepository.anonymizeHistoricalPlayerIdentity(
                List.of(player.getId()),
                DELETED_MEMBER_LABEL
            );
        }

        evictCaches(
            requiresAccountCleanup ? accountEmail : "",
            groupId == null ? Set.of() : Set.of(groupId)
        );
        return new InactivePlayerCleanupOutcome(authUserId, requiresAuthDeletion);
    }

    @Transactional
    public void completePendingAuthDeletion(UUID authUserId) {
        playerRepository.clearRetentionSubjectHash(retentionSubjectHash(authUserId));
        accountPersonalDataRepository.deletePendingAuthDeletion(authUserId);
    }

    @Transactional
    public UUID resolveRetainedAuthUserId(String expectedRetentionSubjectHash) {
        String expectedHash = safeTrim(expectedRetentionSubjectHash).toLowerCase(Locale.ROOT);
        if (!expectedHash.matches("^[0-9a-f]{64}$")) {
            return null;
        }
        UUID resolved = null;
        for (UUID candidate : playerRepository.findDistinctActiveAuthUserIds()) {
            if (candidate == null || !expectedHash.equals(retentionSubjectHash(candidate))) {
                continue;
            }
            if (resolved != null && !resolved.equals(candidate)) {
                return null;
            }
            resolved = candidate;
        }
        if (resolved == null) {
            return null;
        }

        List<Player> lockedAccountLinks = playerRepository
            .findByAuthUserIdAndAnonymizedAtIsNull(resolved);
        boolean activeAccountLinkStillExists = lockedAccountLinks.stream()
            .anyMatch(player -> player.isActive()
                && player.getLifecycleStatus() == PlayerLifecycleStatus.ACTIVE);
        return activeAccountLinkStillExists ? resolved : null;
    }

    private ResolvedAccountIdentity resolveInactiveAccountIdentity(Player player) {
        UUID linkedAuthUserId = player.getAuthUserId();
        if (linkedAuthUserId != null) {
            String linkedEmail = accountPersonalDataRepository
                .findAccountEmail(linkedAuthUserId)
                .orElse("");
            if (linkedEmail.isEmpty()) {
                throw new AccountDeletionException(
                    "Account deactivation requires a verified account email"
                );
            }
            return new ResolvedAccountIdentity(linkedAuthUserId, normalizeEmail(linkedEmail));
        }

        List<Player> nicknameMatches = playerRepository
            .findByNicknameIgnoreCaseAndAnonymizedAtIsNull(player.getNickname());
        if (nicknameMatches.size() != 1
            || !player.getId().equals(nicknameMatches.get(0).getId())) {
            throw new AccountDeletionException(
                "Account deactivation identity ownership could not be resolved"
            );
        }

        List<AccountPersonalDataRepository.NicknameAccountCandidate> candidates =
            accountPersonalDataRepository.findAccountIdentitiesByNickname(player.getNickname());
        if (candidates.isEmpty()) {
            return new ResolvedAccountIdentity(null, "");
        }

        Set<String> emails = new LinkedHashSet<>();
        Set<UUID> authUserIds = new LinkedHashSet<>();
        for (AccountPersonalDataRepository.NicknameAccountCandidate candidate : candidates) {
            String candidateEmail = normalizeEmail(candidate.normalizedEmail());
            if (!candidateEmail.isEmpty()) {
                emails.add(candidateEmail);
            }
            if (candidate.authUserId() != null) {
                authUserIds.add(candidate.authUserId());
            }
        }
        if (emails.size() != 1 || authUserIds.size() > 1) {
            throw new AccountDeletionException(
                "Account deactivation identity ownership could not be resolved"
            );
        }
        return new ResolvedAccountIdentity(
            authUserIds.stream().findFirst().orElse(null),
            emails.iterator().next()
        );
    }

    private void evictCaches(String normalizedEmail, Set<Long> groupIds) {
        String email = normalizeEmail(normalizedEmail);
        Set<Long> safeGroupIds = groupIds == null ? Set.of() : Set.copyOf(groupIds);
        TransactionAfterCommit.runNowAndAfterCommit(() -> {
            if (!email.isEmpty()) {
                accessControlService.evictAccountCache(email);
            }
            safeGroupIds.forEach(groupReadCacheService::evictGroup);
        });
    }

    private void addPlayers(Map<Long, Player> target, List<Player> players) {
        if (players == null) {
            return;
        }
        for (Player player : players) {
            if (player != null && player.getId() != null) {
                target.putIfAbsent(player.getId(), player);
            }
        }
    }

    private void validateWithdrawalOwnership(UUID authUserId, List<Player> candidates) {
        boolean linkedToAnotherAccount = candidates != null && candidates.stream()
            .anyMatch(candidate -> candidate.getAuthUserId() != null
                && !authUserId.equals(candidate.getAuthUserId()));
        if (linkedToAnotherAccount) {
            throw new AccountDeletionException("Account data ownership could not be resolved");
        }
    }

    static String retentionSubjectHash(UUID authUserId) {
        if (authUserId == null) {
            return "";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(authUserId.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String normalizeEmail(String value) {
        return safeTrim(value).toLowerCase(Locale.ROOT);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    public record WithdrawalAnonymizationResult(int anonymizedPlayerCount) {
    }

    public record InactivePlayerCleanupOutcome(UUID authUserId, boolean requiresAuthDeletion) {
    }

    private record ResolvedAccountIdentity(
        UUID authUserId,
        String normalizedEmail
    ) {
    }
}
