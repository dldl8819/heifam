package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.GroupPlayerResponse;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.PlayerTierPolicy;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.PlayerRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PlayerQueryService {

    private final PlayerRepository playerRepository;
    private final MatchParticipantRepository matchParticipantRepository;

    public PlayerQueryService(
        PlayerRepository playerRepository,
        MatchParticipantRepository matchParticipantRepository
    ) {
        this.playerRepository = playerRepository;
        this.matchParticipantRepository = matchParticipantRepository;
    }

    public List<GroupPlayerResponse> getGroupPlayers(Long groupId) {
        List<Player> players = new ArrayList<>(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(groupId));
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

            responses.add(new GroupPlayerResponse(
                player.getId(),
                player.getNickname(),
                normalizeRace(player.getRace()),
                PlayerTierPolicy.resolveTier(player.getMmr()),
                safeInt(player.getMmr()),
                stats.wins,
                stats.losses,
                games
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
        if (race == null || race.isBlank()) {
            return "P";
        }

        String normalized = race.trim().toUpperCase();
        return switch (normalized) {
            case "P", "T", "Z", "PT", "PZ", "TZ", "R" -> normalized;
            default -> "P";
        };
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
    }
}
