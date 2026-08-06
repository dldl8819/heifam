package com.balancify.backend.service;

import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.PlayerLifecycleStatus;
import com.balancify.backend.repository.AccountPersonalDataRepository;
import com.balancify.backend.repository.PlayerRepository;
import com.balancify.backend.service.exception.AccountDeletionException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountDeletionDataService {

    public static final String DELETED_MEMBER_LABEL = PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL;
    public static final String SELF_WITHDRAWAL_REASON = "회원 본인 요청";
    private static final int MAX_IDENTITY_RETENTION_YEARS = 5;

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
            .findByNicknameIgnoreCaseAndAnonymizedAtIsNull(nickname)
            .stream()
            .filter(candidate -> !PlayerIdentityPolicy.isIdentityHidden(candidate))
            .toList();
        boolean linkedToAnotherAccount = candidates.stream()
            .anyMatch(candidate -> candidate.getAuthUserId() != null
                && !authUserId.equals(candidate.getAuthUserId()));
        if (linkedToAnotherAccount) {
            return;
        }

        List<Player> changedPlayers = candidates.stream()
            .filter(candidate -> candidate.getAuthUserId() == null)
            .toList();
        changedPlayers.forEach(candidate -> candidate.setAuthUserId(authUserId));
        if (!changedPlayers.isEmpty()) {
            playerRepository.saveAll(changedPlayers);
        }
    }

    @Transactional
    public WithdrawalRetentionResult retainWithdrawnAccount(UUID authUserId, String email) {
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
        if (linkedPlayers.isEmpty() && !linkedNickname.isEmpty()) {
            List<Player> fallbackCandidates = playerRepository
                .findByNicknameIgnoreCaseAndAnonymizedAtIsNull(linkedNickname);
            boolean linkedToAnotherAccount = fallbackCandidates.stream()
                .anyMatch(candidate -> candidate.getAuthUserId() != null
                    && !authUserId.equals(candidate.getAuthUserId()));
            if (linkedToAnotherAccount) {
                throw new AccountDeletionException("Account data ownership could not be resolved");
            }
            addPlayers(linkedPlayers, fallbackCandidates);
        }

        OffsetDateTime withdrawnAt = OffsetDateTime.now(clock);
        OffsetDateTime retainedUntil = withdrawnAt.plusYears(MAX_IDENTITY_RETENTION_YEARS);
        accountPersonalDataRepository.enqueuePendingAuthDeletion(authUserId, withdrawnAt);
        Set<Long> groupIds = new LinkedHashSet<>();
        for (Player player : linkedPlayers.values()) {
            PlayerIdentityPolicy.retainAdministrativeIdentity(
                player,
                PlayerLifecycleStatus.WITHDRAWN,
                withdrawnAt,
                SELF_WITHDRAWAL_REASON,
                retainedUntil
            );
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

        return new WithdrawalRetentionResult(playerIds.size());
    }

    @Transactional
    public InactivePlayerCleanupOutcome retainInactivePlayer(Player player) {
        if (player == null || player.getId() == null) {
            throw new IllegalArgumentException("A persisted player is required");
        }

        ResolvedAccountIdentity resolvedAccountIdentity = resolveInactiveAccountIdentity(player);
        UUID authUserId = resolvedAccountIdentity.authUserId();
        String accountEmail = resolvedAccountIdentity.normalizedEmail();
        boolean hasAnotherActiveLinkedPlayer = authUserId != null
            && playerRepository.existsByAuthUserIdAndActiveTrueAndAnonymizedAtIsNullAndIdNot(
                authUserId,
                player.getId()
            );
        if (!hasAnotherActiveLinkedPlayer && resolvedAccountIdentity.resolvedByNickname()) {
            hasAnotherActiveLinkedPlayer = playerRepository
                .existsByNicknameIgnoreCaseAndActiveTrueAndAnonymizedAtIsNullAndIdNot(
                    player.getNickname(),
                    player.getId()
                );
        }
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
        OffsetDateTime retainedUntil = inactiveAt.plusYears(MAX_IDENTITY_RETENTION_YEARS);
        OffsetDateTime maximumRetainedUntil = processedAt.plusYears(MAX_IDENTITY_RETENTION_YEARS);
        if (retainedUntil.isAfter(maximumRetainedUntil)) {
            retainedUntil = maximumRetainedUntil;
        }
        if (requiresAuthDeletion) {
            accountPersonalDataRepository.enqueuePendingAuthDeletion(authUserId, processedAt);
        }

        PlayerIdentityPolicy.retainAdministrativeIdentity(
            player,
            PlayerLifecycleStatus.INACTIVE,
            inactiveAt,
            player.getChatLeftReason(),
            retainedUntil
        );
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
        accountPersonalDataRepository.deletePendingAuthDeletion(authUserId);
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
            return new ResolvedAccountIdentity(linkedAuthUserId, normalizeEmail(linkedEmail), false);
        }

        List<AccountPersonalDataRepository.NicknameAccountCandidate> candidates =
            accountPersonalDataRepository.findAccountIdentitiesByNickname(player.getNickname());
        if (candidates.isEmpty()) {
            return new ResolvedAccountIdentity(null, "", false);
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
            emails.iterator().next(),
            true
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

    private String normalizeEmail(String value) {
        return safeTrim(value).toLowerCase(Locale.ROOT);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    public record WithdrawalRetentionResult(int retainedPlayerCount) {
    }

    public record InactivePlayerCleanupOutcome(UUID authUserId, boolean requiresAuthDeletion) {
    }

    private record ResolvedAccountIdentity(
        UUID authUserId,
        String normalizedEmail,
        boolean resolvedByNickname
    ) {
    }
}
