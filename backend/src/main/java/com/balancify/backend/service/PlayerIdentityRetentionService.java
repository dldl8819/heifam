package com.balancify.backend.service;

import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.AccountPersonalDataRepository;
import com.balancify.backend.repository.PlayerRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerIdentityRetentionService {

    private final PlayerRepository playerRepository;
    private final AccountPersonalDataRepository accountPersonalDataRepository;
    private final GroupReadCacheService groupReadCacheService;
    private final Clock clock;

    @Autowired
    public PlayerIdentityRetentionService(
        PlayerRepository playerRepository,
        AccountPersonalDataRepository accountPersonalDataRepository,
        GroupReadCacheService groupReadCacheService
    ) {
        this(
            playerRepository,
            accountPersonalDataRepository,
            groupReadCacheService,
            Clock.systemUTC()
        );
    }

    PlayerIdentityRetentionService(
        PlayerRepository playerRepository,
        AccountPersonalDataRepository accountPersonalDataRepository,
        GroupReadCacheService groupReadCacheService,
        Clock clock
    ) {
        this.playerRepository = playerRepository;
        this.accountPersonalDataRepository = accountPersonalDataRepository;
        this.groupReadCacheService = groupReadCacheService;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Scheduled(cron = "${balancify.privacy.identity-retention.sweep-cron:0 30 4 * * *}")
    @Scheduled(
        initialDelayString = "${balancify.privacy.identity-retention.initial-delay-ms:5000}",
        fixedDelayString = "${balancify.privacy.identity-retention.fixed-delay-ms:21600000}"
    )
    @Transactional
    public int anonymizeExpiredIdentities() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Player> expiredPlayers = playerRepository
            .findIdentityRetentionExpiryCandidates(
                now,
                PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL
            );
        if (expiredPlayers.isEmpty()) {
            return 0;
        }

        List<ExpiryCandidate> candidates = expiredPlayers.stream()
            .filter(player -> player.getId() != null)
            .map(player -> new ExpiryCandidate(
                player.getId(),
                player.getGroup() == null ? null : player.getGroup().getId()
            ))
            .toList();
        List<Long> playerIds = new ArrayList<>();
        Set<Long> groupIds = new LinkedHashSet<>();
        for (ExpiryCandidate candidate : candidates) {
            int anonymized = playerRepository.anonymizeExpiredIdentity(
                candidate.playerId(),
                PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL,
                now
            );
            if (anonymized != 1) {
                continue;
            }
            playerIds.add(candidate.playerId());
            if (candidate.groupId() != null) {
                groupIds.add(candidate.groupId());
            }
        }

        if (playerIds.isEmpty()) {
            return 0;
        }
        accountPersonalDataRepository.anonymizeHistoricalPlayerIdentity(
            playerIds,
            PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL
        );
        Set<Long> committedGroupIds = Set.copyOf(groupIds);
        TransactionAfterCommit.runNowAndAfterCommit(
            () -> committedGroupIds.forEach(groupReadCacheService::evictGroup)
        );
        return playerIds.size();
    }

    private record ExpiryCandidate(Long playerId, Long groupId) {
    }
}
