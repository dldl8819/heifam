package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.GroupPlayerResponse;
import com.balancify.backend.api.group.dto.GroupPlayerTierBoardResponse;
import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.PlayerStats;
import com.balancify.backend.domain.PlayerTierPolicy;
import com.balancify.backend.repository.PlayerRepository;
import com.balancify.backend.repository.PlayerStatsRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PlayerQueryService {

    private final PlayerRepository playerRepository;
    private final PlayerStatsRepository playerStatsRepository;
    private final GroupReadCacheService groupReadCacheService;
    private final Clock clock;

    @Autowired
    public PlayerQueryService(
        PlayerRepository playerRepository,
        PlayerStatsRepository playerStatsRepository,
        GroupReadCacheService groupReadCacheService
    ) {
        this(playerRepository, playerStatsRepository, groupReadCacheService, Clock.systemUTC());
    }

    PlayerQueryService(
        PlayerRepository playerRepository,
        PlayerStatsRepository playerStatsRepository,
        GroupReadCacheService groupReadCacheService,
        Clock clock
    ) {
        this.playerRepository = playerRepository;
        this.playerStatsRepository = playerStatsRepository;
        this.groupReadCacheService = groupReadCacheService;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public List<GroupPlayerResponse> getGroupPlayers(Long groupId, boolean includeInactive) {
        // Player identity changes must be visible immediately across application instances.
        // The local read cache cannot be invalidated reliably after withdrawal or anonymization.
        return List.copyOf(loadGroupPlayers(groupId, includeInactive));
    }

    private List<GroupPlayerResponse> loadGroupPlayers(Long groupId, boolean includeInactive) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Player> players = new ArrayList<>(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(groupId)
            .stream()
            .filter(player -> !PlayerIdentityPolicy.isIdentityHidden(player)
                || (includeInactive
                    && PlayerIdentityPolicy.isAdministrativeIdentityRetained(player, now)))
            .toList());
        if (players.isEmpty()) {
            return List.of();
        }

        players.sort((first, second) -> compareRosterPlayers(first, second, now));

        Map<Long, StatsAccumulator> statsByPlayerId = new HashMap<>();
        for (PlayerStats stats : playerStatsRepository.findByGroupId(groupId)) {
            if (stats.getPlayerId() == null) {
                continue;
            }
            statsByPlayerId.put(stats.getPlayerId(), new StatsAccumulator(
                safeInt(stats.getWins()),
                safeInt(stats.getLosses())
            ));
        }

        List<GroupPlayerResponse> responses = new ArrayList<>();
        for (Player player : players) {
            if (PlayerIdentityPolicy.isIdentityHidden(player)) {
                StatsAccumulator stats = statsByPlayerId.getOrDefault(
                    player.getId(),
                    new StatsAccumulator(0, 0)
                );
                responses.add(retainedInactivePlayer(player, stats));
                continue;
            }

            StatsAccumulator stats =
                statsByPlayerId.getOrDefault(player.getId(), new StatsAccumulator(0, 0));
            int games = stats.wins() + stats.losses();
            Integer baseMmr = player.getBaseMmr();
            String baseTier = baseMmr == null ? null : PlayerTierPolicy.resolveTier(baseMmr);
            int currentMmr = safeInt(player.getMmr());
            String currentTier = PlayerTierPolicy.resolveTierForSnapshot(player.getTier(), currentMmr);
            Integer lastTierSnapshotMmr = player.getLastTierSnapshotMmr();
            String lastTierSnapshotTier = lastTierSnapshotMmr == null
                ? null
                : currentTier;
            String liveTier = PlayerTierPolicy.resolveTier(currentMmr);

            responses.add(new GroupPlayerResponse(
                player.getId(),
                player.getNickname(),
                normalizeRace(player.getRace()),
                currentTier,
                baseMmr,
                baseTier,
                currentMmr,
                player.getLastTierSnapshotAt(),
                lastTierSnapshotMmr,
                lastTierSnapshotTier,
                liveTier,
                stats.wins(),
                stats.losses(),
                games,
                player.isActive(),
                player.getChatLeftAt(),
                player.getChatLeftReason(),
                player.getChatRejoinedAt(),
                player.getTierChangeAcknowledgedTier(),
                player.getTierChangeAcknowledgedAt(),
                PlayerTierPolicy.normalizeRankedTier(player.getDormancyMmrFloorTier()).isEmpty()
                    ? null
                    : PlayerTierPolicy.normalizeRankedTier(player.getDormancyMmrFloorTier()),
                player.getLifecycleStatus() == null ? null : player.getLifecycleStatus().name(),
                player.getIdentityRetainedUntil()
            ));
        }

        return responses;
    }

    private int compareRosterPlayers(Player first, Player second, OffsetDateTime now) {
        boolean firstIdentityHidden = PlayerIdentityPolicy.isIdentityHidden(first);
        boolean secondIdentityHidden = PlayerIdentityPolicy.isIdentityHidden(second);
        if (firstIdentityHidden != secondIdentityHidden) {
            return firstIdentityHidden ? 1 : -1;
        }
        if (firstIdentityHidden) {
            boolean firstRetained = PlayerIdentityPolicy.isAdministrativeIdentityRetained(first, now);
            boolean secondRetained = PlayerIdentityPolicy.isAdministrativeIdentityRetained(second, now);
            if (firstRetained != secondRetained) {
                return firstRetained ? -1 : 1;
            }
            if (!firstRetained) {
                return 0;
            }
            int nicknameComparison = Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
                .compare(first.getNickname(), second.getNickname());
            if (nicknameComparison != 0) {
                return nicknameComparison;
            }
            return Comparator.nullsLast(Long::compareTo).compare(first.getId(), second.getId());
        }

        int mmrComparison = Integer.compare(safeInt(second.getMmr()), safeInt(first.getMmr()));
        if (mmrComparison != 0) {
            return mmrComparison;
        }
        return Comparator.nullsLast(Long::compareTo).compare(first.getId(), second.getId());
    }

    private GroupPlayerResponse retainedInactivePlayer(Player player, StatsAccumulator stats) {
        return new GroupPlayerResponse(
            player.getId(),
            player.getNickname(),
            normalizeRace(player.getRace()),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            stats.wins(),
            stats.losses(),
            stats.wins() + stats.losses(),
            false,
            player.getChatLeftAt(),
            player.getChatLeftReason(),
            null,
            null,
            null,
            null,
            player.getLifecycleStatus() == null ? null : player.getLifecycleStatus().name(),
            player.getIdentityRetainedUntil()
        );
    }

    public List<GroupPlayerTierBoardResponse> getGroupPlayerTierBoard(Long groupId) {
        return List.copyOf(loadGroupPlayerTierBoard(groupId));
    }

    private List<GroupPlayerTierBoardResponse> loadGroupPlayerTierBoard(Long groupId) {
        List<Player> players = new ArrayList<>(
            playerRepository.findByGroup_IdOrderByMmrDescIdAsc(groupId)
                .stream()
                .filter(player -> !PlayerIdentityPolicy.isIdentityHidden(player))
                .toList()
        );
        players.sort(
            Comparator
                .comparingInt((Player player) -> safeInt(player.getMmr()))
                .reversed()
                .thenComparing(Player::getId, Comparator.nullsLast(Long::compareTo))
        );

        return players
            .stream()
            .map(player -> {
                int currentMmr = safeInt(player.getMmr());
                String monthlyTier = PlayerTierPolicy.resolveTierForSnapshot(player.getTier(), currentMmr);
                String liveTier = PlayerTierPolicy.resolveTier(currentMmr);
                return new GroupPlayerTierBoardResponse(
                    player.getId(),
                    player.getNickname(),
                    normalizeRace(player.getRace()),
                    monthlyTier,
                    liveTier,
                    player.isActive()
                );
            })
            .toList();
    }

    private String normalizeRace(String race) {
        return PlayerRacePolicy.toDisplayRace(race);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private record StatsAccumulator(int wins, int losses) {
    }

}
