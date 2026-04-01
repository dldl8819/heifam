package com.balancify.backend.service;

import com.balancify.backend.api.match.dto.BalancePlayerDto;
import com.balancify.backend.api.match.dto.BalanceRequest;
import com.balancify.backend.api.match.dto.BalanceResponse;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.PlayerRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class TeamBalancingService {

    private final PlayerRepository playerRepository;

    public TeamBalancingService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public BalanceResponse balance(BalanceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }

        int teamSize = normalizeTeamSize(request.teamSize());
        int requiredPlayers = teamSize * 2;
        List<BalancePlayerDto> players = resolvePlayers(request, requiredPlayers, teamSize);

        for (BalancePlayerDto player : players) {
            if (player == null) {
                throw new IllegalArgumentException("Player entry must not be null");
            }
        }

        List<BalanceCandidate> candidates = generateCandidates(players, teamSize);
        BalanceCandidate best = candidates
            .stream()
            .min(Comparator.comparingInt(BalanceCandidate::mmrDiff))
            .orElseThrow(() -> new IllegalStateException("Unable to split players into two teams"));

        return toResponse(best);
    }

    public List<BalanceCandidate> generateCandidates(List<BalancePlayerDto> players) {
        if (players == null || players.size() != 6) {
            throw new IllegalArgumentException("Exactly 6 players are required");
        }
        return generateCandidates(players, 3);
    }

    public List<BalanceCandidate> generateCandidates(List<BalancePlayerDto> players, int teamSize) {
        int normalizedTeamSize = normalizeTeamSize(teamSize);
        int requiredPlayers = normalizedTeamSize * 2;
        if (players == null || players.size() != requiredPlayers) {
            throw new IllegalArgumentException(
                "teamSize=" + normalizedTeamSize + " requires exactly " + requiredPlayers + " players"
            );
        }

        for (BalancePlayerDto player : players) {
            if (player == null) {
                throw new IllegalArgumentException("Player entry must not be null");
            }
        }

        List<BalanceCandidate> candidates = new ArrayList<>();
        int totalMasks = 1 << players.size();
        for (int mask = 0; mask < totalMasks; mask++) {
            if (Integer.bitCount(mask) != normalizedTeamSize || (mask & 1) == 0) {
                continue;
            }

            List<BalancePlayerDto> homeTeam = new ArrayList<>();
            List<BalancePlayerDto> awayTeam = new ArrayList<>();
            int homeMmr = 0;
            int awayMmr = 0;

            for (int i = 0; i < players.size(); i++) {
                BalancePlayerDto player = players.get(i);
                if ((mask & (1 << i)) != 0) {
                    homeTeam.add(player);
                    homeMmr += player.mmr();
                } else {
                    awayTeam.add(player);
                    awayMmr += player.mmr();
                }
            }

            int diff = Math.abs(homeMmr - awayMmr);
            candidates.add(new BalanceCandidate(normalizedTeamSize, homeTeam, awayTeam, homeMmr, awayMmr, diff));
        }

        if (candidates.isEmpty()) {
            throw new IllegalStateException("Unable to split players into two teams");
        }

        return candidates;
    }

    public BalanceResponse toResponse(BalanceCandidate candidate) {
        double expectedWinRate = calculateExpectedWinRate(candidate.homeMmr(), candidate.awayMmr());
        return new BalanceResponse(
            candidate.teamSize(),
            candidate.homeTeam(),
            candidate.awayTeam(),
            candidate.homeMmr(),
            candidate.awayMmr(),
            candidate.mmrDiff(),
            expectedWinRate
        );
    }

    private int normalizeTeamSize(Integer teamSize) {
        int normalized = teamSize == null ? 3 : teamSize;
        if (normalized < 2) {
            throw new IllegalArgumentException("teamSize must be at least 2");
        }
        return normalized;
    }

    private List<BalancePlayerDto> resolvePlayers(BalanceRequest request, int requiredPlayers, int teamSize) {
        if (request.players() != null && !request.players().isEmpty()) {
            if (request.players().size() != requiredPlayers) {
                throw new IllegalArgumentException(
                    "teamSize=" + teamSize + " requires exactly " + requiredPlayers + " players"
                );
            }
            return request.players();
        }

        if (request.groupId() == null || request.groupId() <= 0) {
            throw new IllegalArgumentException("groupId must be a positive number");
        }
        if (request.playerIds() == null || request.playerIds().isEmpty()) {
            throw new IllegalArgumentException("playerIds is required");
        }
        if (request.playerIds().size() != requiredPlayers) {
            throw new IllegalArgumentException(
                "teamSize=" + teamSize + " requires exactly " + requiredPlayers + " players"
            );
        }

        Set<Long> uniquePlayerIds = new HashSet<>(request.playerIds());
        if (uniquePlayerIds.size() != request.playerIds().size()) {
            throw new IllegalArgumentException("playerIds must not contain duplicates");
        }

        List<Player> loadedPlayers = playerRepository.findByGroup_IdAndIdIn(request.groupId(), request.playerIds());
        if (loadedPlayers.size() != request.playerIds().size()) {
            Set<Long> foundIds = new HashSet<>();
            loadedPlayers.forEach(player -> foundIds.add(player.getId()));
            List<Long> missingIds = request.playerIds().stream().filter(id -> !foundIds.contains(id)).distinct().toList();
            throw new IllegalArgumentException("Players not found in group: " + missingIds);
        }

        Map<Long, Player> loadedById = new HashMap<>();
        for (Player player : loadedPlayers) {
            loadedById.put(player.getId(), player);
        }

        List<BalancePlayerDto> resolved = new ArrayList<>();
        for (Long playerId : request.playerIds()) {
            Player player = loadedById.get(playerId);
            if (player == null) {
                throw new IllegalArgumentException("Players not found in group: [" + playerId + "]");
            }
            resolved.add(new BalancePlayerDto(player.getId(), player.getNickname(), safeMmr(player.getMmr())));
        }
        return resolved;
    }

    private double calculateExpectedWinRate(int homeMmr, int awayMmr) {
        double ratingGap = (awayMmr - homeMmr) / 400.0;
        double winRate = 1.0 / (1.0 + Math.pow(10.0, ratingGap));
        return Math.round(winRate * 10000.0) / 10000.0;
    }

    private int safeMmr(Integer mmr) {
        return mmr == null ? 0 : mmr;
    }

    public record BalanceCandidate(
        int teamSize,
        List<BalancePlayerDto> homeTeam,
        List<BalancePlayerDto> awayTeam,
        int homeMmr,
        int awayMmr,
        int mmrDiff
    ) {
    }
}
