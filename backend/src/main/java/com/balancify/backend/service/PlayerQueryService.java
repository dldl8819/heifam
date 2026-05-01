package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.GroupPlayerResponse;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.PlayerTierPolicy;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.PlayerRepository;
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
    private final MatchParticipantRepository matchParticipantRepository;
    private final DormancyMmrDecayService dormancyMmrDecayService;
    private final MonthlyTierRefreshService monthlyTierRefreshService;

    @Autowired
    public PlayerQueryService(
        PlayerRepository playerRepository,
        MatchParticipantRepository matchParticipantRepository,
        DormancyMmrDecayService dormancyMmrDecayService,
        MonthlyTierRefreshService monthlyTierRefreshService
    ) {
        this.playerRepository = playerRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.dormancyMmrDecayService = dormancyMmrDecayService;
        this.monthlyTierRefreshService = monthlyTierRefreshService;
    }

    public List<GroupPlayerResponse> getGroupPlayers(Long groupId, boolean includeInactive) {
        monthlyTierRefreshService.applyMonthlyTierRefreshIfDue();
        dormancyMmrDecayService.applyGroupDormancyDecay(groupId);
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

        List<GroupPlayerResponse> responses = new ArrayList<>();
        for (Player player : players) {
            StatsAccumulator stats =
                statsByPlayerId.getOrDefault(player.getId(), new StatsAccumulator());
            int games = stats.wins + stats.losses;
            Integer baseMmr = player.getBaseMmr();
            String baseTier = baseMmr == null ? null : PlayerTierPolicy.resolveTier(baseMmr);
            int currentMmr = safeInt(player.getMmr());
            String currentTier = PlayerTierPolicy.resolveTierForSnapshot(player.getTier(), currentMmr);

            responses.add(new GroupPlayerResponse(
                player.getId(),
                player.getNickname(),
                normalizeRace(player.getRace()),
                currentTier,
                baseMmr,
                baseTier,
                currentMmr,
                stats.wins,
                stats.losses,
                games,
                player.isActive(),
                player.getChatLeftAt(),
                player.getChatLeftReason(),
                player.getChatRejoinedAt()
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

}
