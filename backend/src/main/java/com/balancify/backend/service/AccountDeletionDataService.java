package com.balancify.backend.service;

import com.balancify.backend.domain.Player;
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
    public AnonymizationResult anonymizeAccount(UUID authUserId, String email) {
        if (authUserId == null) {
            throw new IllegalArgumentException("A verified account identifier is required");
        }
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isEmpty()) {
            throw new IllegalArgumentException("A verified account email is required");
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

        OffsetDateTime anonymizedAt = OffsetDateTime.now(clock);
        Set<Long> groupIds = new LinkedHashSet<>();
        for (Player player : linkedPlayers.values()) {
            PlayerIdentityPolicy.anonymize(player, anonymizedAt);
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
        accessControlService.evictAccountCache(normalizedEmail);
        groupIds.forEach(groupReadCacheService::evictGroup);

        return new AnonymizationResult(playerIds.size());
    }

    @Transactional
    public InactivePlayerCleanupOutcome anonymizeInactivePlayer(Player player) {
        if (player == null || player.getId() == null) {
            throw new IllegalArgumentException("A persisted player is required");
        }

        UUID authUserId = player.getAuthUserId();
        boolean hasAnotherActiveLinkedPlayer = authUserId != null
            && playerRepository.existsByAuthUserIdAndActiveTrueAndAnonymizedAtIsNullAndIdNot(
                authUserId,
                player.getId()
            );
        String accountEmail = authUserId == null
            ? ""
            : accountPersonalDataRepository.findAccountEmail(authUserId).orElse("");
        Long groupId = player.getGroup() == null ? null : player.getGroup().getId();
        boolean requiresAuthDeletion = authUserId != null && !hasAnotherActiveLinkedPlayer;

        if (requiresAuthDeletion
            && !accountEmail.isEmpty()
            && accessControlService.hasConfiguredAccessGrant(accountEmail)) {
            throw new AccountDeletionException(
                "Account deactivation requires removal from the configured access list"
            );
        }

        OffsetDateTime anonymizedAt = OffsetDateTime.now(clock);
        if (requiresAuthDeletion) {
            accountPersonalDataRepository.enqueuePendingAuthDeletion(authUserId, anonymizedAt);
        }

        PlayerIdentityPolicy.anonymize(player, anonymizedAt);
        playerRepository.saveAndFlush(player);

        if (requiresAuthDeletion) {
            if (!accountEmail.isEmpty()) {
                accountPersonalDataRepository.anonymizeHistoricalIdentity(
                    accountEmail,
                    List.of(player.getId()),
                    DELETED_MEMBER_LABEL
                );
            } else {
                accountPersonalDataRepository.anonymizeHistoricalPlayerIdentity(
                    List.of(player.getId()),
                    DELETED_MEMBER_LABEL
                );
            }
            accountPersonalDataRepository.deleteAccountIdentity(authUserId, accountEmail);
            if (!accountEmail.isEmpty()) {
                accessControlService.evictAccountCache(accountEmail);
            }
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

        if (groupId != null) {
            groupReadCacheService.evictGroup(groupId);
        }
        return new InactivePlayerCleanupOutcome(authUserId, requiresAuthDeletion);
    }

    @Transactional
    public void completePendingAuthDeletion(UUID authUserId) {
        accountPersonalDataRepository.deletePendingAuthDeletion(authUserId);
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

    public record AnonymizationResult(int anonymizedPlayerCount) {
    }

    public record InactivePlayerCleanupOutcome(UUID authUserId, boolean requiresAuthDeletion) {
    }
}
