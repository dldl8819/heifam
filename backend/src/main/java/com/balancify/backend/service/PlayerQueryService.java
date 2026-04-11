package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.GroupPlayerResponse;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.PlayerTierPolicy;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.PlayerRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PlayerQueryService {

    private final PlayerRepository playerRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final boolean dormancyEnabled;
    private final int dormancyInactiveDays;
    private final int dormancyDemoteSteps;
    private final Clock clock;

    @Autowired
    public PlayerQueryService(
        PlayerRepository playerRepository,
        MatchParticipantRepository matchParticipantRepository,
        @Value("${balancify.rank.dormancy.enabled:true}") boolean dormancyEnabled,
        @Value("${balancify.rank.dormancy.inactive-days:30}") int dormancyInactiveDays,
        @Value("${balancify.rank.dormancy.demote-steps:1}") int dormancyDemoteSteps
    ) {
        this(
            playerRepository,
            matchParticipantRepository,
            dormancyEnabled,
            dormancyInactiveDays,
            dormancyDemoteSteps,
            Clock.systemUTC()
        );
    }

    PlayerQueryService(
        PlayerRepository playerRepository,
        MatchParticipantRepository matchParticipantRepository,
        boolean dormancyEnabled,
        int dormancyInactiveDays,
        int dormancyDemoteSteps,
        Clock clock
    ) {
        this.playerRepository = playerRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.dormancyEnabled = dormancyEnabled;
        this.dormancyInactiveDays = Math.max(0, dormancyInactiveDays);
        this.dormancyDemoteSteps = Math.max(0, dormancyDemoteSteps);
        this.clock = clock;
    }

    public List<GroupPlayerResponse> getGroupPlayers(Long groupId, boolean includeInactive) {
        List<Player> players = new ArrayList<>(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(groupId));
        if (!includeInactive) {
            players = new ArrayList<>(players.stream().filter(Player::isActive).toList());
        }
        players.sort(
            Comparator
                .comparingInt((Player player) -> safeInt(player.getMmr()))
                .reversed()
                .thenComparing(Player::getId, Comparator.nullsLast(Long::compareTo))
        );

        List<MatchParticipant> matchParticipants =
            matchParticipantRepository.findByGroupIdOrderByPlayedAtDesc(groupId);

        Map<Long, StatsAccumulator> statsByPlayerId = new HashMap<>();
        for (MatchParticipant participant : matchParticipants) {
            if (participant.getPlayer() == null || participant.getPlayer().getId() == null) {
                continue;
            }

            Long playerId = participant.getPlayer().getId();
            StatsAccumulator stats =
                statsByPlayerId.computeIfAbsent(playerId, ignored -> new StatsAccumulator());

            if (stats.lastPlayedAt == null
                && participant.getMatch() != null
                && participant.getMatch().getPlayedAt() != null) {
                stats.lastPlayedAt = participant.getMatch().getPlayedAt();
            }

            Result result = resolveResult(participant);
            if (result == Result.WIN) {
                stats.wins++;
            } else if (result == Result.LOSS) {
                stats.losses++;
            }
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        List<GroupPlayerResponse> responses = new ArrayList<>();
        for (Player player : players) {
            StatsAccumulator stats =
                statsByPlayerId.getOrDefault(player.getId(), new StatsAccumulator());
            int games = stats.wins + stats.losses;
            Integer baseMmr = player.getBaseMmr();
            String baseTier = baseMmr == null ? null : PlayerTierPolicy.resolveTier(baseMmr);
            int currentMmr = safeInt(player.getMmr());
            String currentTier = PlayerTierPolicy.resolveTierForSnapshot(player.getTier(), currentMmr);
            DormancyAdjustment dormancyAdjustment = applyDormancyDemotion(
                currentTier,
                currentMmr,
                stats.lastPlayedAt,
                player.getCreatedAt(),
                now
            );

            responses.add(new GroupPlayerResponse(
                player.getId(),
                player.getNickname(),
                normalizeRace(player.getRace()),
                dormancyAdjustment.tier(),
                baseMmr,
                baseTier,
                dormancyAdjustment.mmr(),
                stats.wins,
                stats.losses,
                games,
                player.isActive()
            ));
        }

        return responses;
    }

    private Result resolveResult(MatchParticipant participant) {
        if (participant.getMatch() == null
            || participant.getMatch().getWinningTeam() == null
            || participant.getTeam() == null
            || participant.getTeam().isBlank()) {
            return Result.UNKNOWN;
        }

        return participant.getMatch().getWinningTeam().equalsIgnoreCase(participant.getTeam())
            ? Result.WIN
            : Result.LOSS;
    }

    private String normalizeRace(String race) {
        return PlayerRacePolicy.toDisplayRace(race);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private DormancyAdjustment applyDormancyDemotion(
        String currentTier,
        int currentMmr,
        OffsetDateTime lastPlayedAt,
        OffsetDateTime createdAt,
        OffsetDateTime now
    ) {
        if (!dormancyEnabled || dormancyDemoteSteps <= 0) {
            return new DormancyAdjustment(currentTier, currentMmr);
        }

        OffsetDateTime activityReference = lastPlayedAt != null ? lastPlayedAt : createdAt;
        if (activityReference == null) {
            return new DormancyAdjustment(currentTier, currentMmr);
        }

        long inactiveDays = ChronoUnit.DAYS.between(activityReference.toInstant(), now.toInstant());
        if (inactiveDays < dormancyInactiveDays) {
            return new DormancyAdjustment(currentTier, currentMmr);
        }

        String demotedTier = PlayerTierPolicy.demoteTier(currentTier, dormancyDemoteSteps);
        int demotedMmr = PlayerTierPolicy.resolveDormancyAdjustedMmr(
            currentTier,
            currentMmr,
            dormancyDemoteSteps
        );
        String effectiveTier = PlayerTierPolicy.resolveTierForSnapshot(demotedTier, demotedMmr);
        return new DormancyAdjustment(effectiveTier, demotedMmr);
    }

    private enum Result {
        WIN,
        LOSS,
        UNKNOWN
    }

    private static class StatsAccumulator {
        private int wins;
        private int losses;
        private OffsetDateTime lastPlayedAt;
    }

    private record DormancyAdjustment(
        String tier,
        int mmr
    ) {
    }
}
