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
        String raceComposition = RaceCompositionPolicy.normalizeForTeamSize(request.raceComposition(), teamSize);
        List<BalancePlayerSelection> players = resolvePlayers(
            request,
            requiredPlayers,
            teamSize,
            raceComposition != null
        );

        if (raceComposition == null) {
            throw new IllegalArgumentException("종족 조합을 선택해 주세요.");
        }

        for (BalancePlayerSelection player : players) {
            if (player == null || player.dto() == null) {
                throw new IllegalArgumentException("Player entry must not be null");
            }
        }

        List<BalanceCandidate> candidates = generateCandidatesInternal(players, teamSize, raceComposition);
        BalanceCandidate best = candidates
            .stream()
            .min(Comparator.comparingInt(BalanceCandidate::mmrDiff))
            .orElseThrow(() ->
                new IllegalStateException(
                    raceComposition == null
                        ? "Unable to split players into two teams"
                        : "선택한 종족 조합으로 매치를 구성할 수 없습니다"
                )
            );

        return toResponse(best);
    }

    public List<BalanceCandidate> generateCandidates(List<BalancePlayerDto> players) {
        if (players == null || players.size() != 6) {
            throw new IllegalArgumentException("Exactly 6 players are required");
        }
        return generateCandidates(players, 3);
    }

    public List<BalanceCandidate> generateCandidates(List<BalancePlayerDto> players, int teamSize) {
        List<BalancePlayerSelection> selections = players.stream()
            .map(player -> new BalancePlayerSelection(player, null))
            .toList();
        return generateCandidatesInternal(selections, teamSize, null);
    }

    public List<BalanceCandidate> generateCandidates(
        List<BalancePlayerDto> players,
        int teamSize,
        List<String> playerRaces,
        String raceComposition
    ) {
        if (playerRaces == null || players == null || playerRaces.size() != players.size()) {
            throw new IllegalArgumentException("선수 종족 정보가 올바르지 않습니다.");
        }

        String normalizedRaceComposition = RaceCompositionPolicy.normalizeForTeamSize(raceComposition, teamSize);
        List<BalancePlayerSelection> selections = new ArrayList<>();
        for (int index = 0; index < players.size(); index++) {
            selections.add(
                new BalancePlayerSelection(
                    players.get(index),
                    normalizedRaceComposition == null
                        ? null
                        : PlayerRacePolicy.normalizeCapability(playerRaces.get(index))
                )
            );
        }
        return generateCandidatesInternal(selections, teamSize, normalizedRaceComposition);
    }

    private List<BalanceCandidate> generateCandidatesInternal(
        List<BalancePlayerSelection> players,
        int teamSize,
        String raceComposition
    ) {
        int normalizedTeamSize = normalizeTeamSize(teamSize);
        int requiredPlayers = normalizedTeamSize * 2;
        if (players == null || players.size() != requiredPlayers) {
            throw new IllegalArgumentException(
                "teamSize=" + normalizedTeamSize + " requires exactly " + requiredPlayers + " players"
            );
        }

        for (BalancePlayerSelection player : players) {
            if (player == null || player.dto() == null) {
                throw new IllegalArgumentException("Player entry must not be null");
            }
        }

        List<BalanceCandidate> candidates = new ArrayList<>();
        int totalMasks = 1 << players.size();
        for (int mask = 0; mask < totalMasks; mask++) {
            if (Integer.bitCount(mask) != normalizedTeamSize || (mask & 1) == 0) {
                continue;
            }

            List<BalancePlayerDto> rawHomeTeam = new ArrayList<>();
            List<BalancePlayerDto> rawAwayTeam = new ArrayList<>();
            List<String> homeCapabilities = new ArrayList<>();
            List<String> awayCapabilities = new ArrayList<>();
            int homeMmr = 0;
            int awayMmr = 0;

            for (int i = 0; i < players.size(); i++) {
                BalancePlayerSelection player = players.get(i);
                if ((mask & (1 << i)) != 0) {
                    rawHomeTeam.add(player.dto());
                    homeMmr += player.dto().mmr();
                    if (raceComposition != null) {
                        homeCapabilities.add(player.normalizedRace());
                    }
                } else {
                    rawAwayTeam.add(player.dto());
                    awayMmr += player.dto().mmr();
                    if (raceComposition != null) {
                        awayCapabilities.add(player.normalizedRace());
                    }
                }
            }

            List<BalancePlayerDto> homeTeam = rawHomeTeam;
            List<BalancePlayerDto> awayTeam = rawAwayTeam;
            if (raceComposition != null) {
                PlayerRacePolicy.TeamRaceAssignment homeAssignment =
                    PlayerRacePolicy.assignToComposition(homeCapabilities, raceComposition);
                if (homeAssignment == null) {
                    continue;
                }

                PlayerRacePolicy.TeamRaceAssignment awayAssignment =
                    PlayerRacePolicy.assignToComposition(awayCapabilities, raceComposition);
                if (awayAssignment == null) {
                    continue;
                }

                homeTeam = applyAssignedRaces(rawHomeTeam, homeAssignment);
                awayTeam = applyAssignedRaces(rawAwayTeam, awayAssignment);
            }

            int diff = Math.abs(homeMmr - awayMmr);
            candidates.add(new BalanceCandidate(normalizedTeamSize, homeTeam, awayTeam, homeMmr, awayMmr, diff));
        }

        if (candidates.isEmpty()) {
            throw new IllegalStateException(
                raceComposition == null
                    ? "Unable to split players into two teams"
                    : "선택한 종족 조합으로 매치를 구성할 수 없습니다"
            );
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

    private List<BalancePlayerSelection> resolvePlayers(
        BalanceRequest request,
        int requiredPlayers,
        int teamSize,
        boolean requireRaceData
    ) {
        if (request.players() != null && !request.players().isEmpty()) {
            if (request.players().size() != requiredPlayers) {
                throw new IllegalArgumentException(
                    "teamSize=" + teamSize + " requires exactly " + requiredPlayers + " players"
                );
            }
            if (requireRaceData) {
                throw new IllegalArgumentException("선수 종족 정보가 올바르지 않습니다.");
            }
            return request.players().stream()
                .map(player -> new BalancePlayerSelection(player, null))
                .toList();
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
        Map<Long, Player> loadedById = new HashMap<>();
        for (Player player : loadedPlayers) {
            if (player.isActive()) {
                loadedById.put(player.getId(), player);
            }
        }
        if (loadedById.size() != request.playerIds().size()) {
            Set<Long> foundIds = new HashSet<>();
            loadedById.values().forEach(player -> foundIds.add(player.getId()));
            List<Long> missingIds = request.playerIds().stream().filter(id -> !foundIds.contains(id)).distinct().toList();
            throw new IllegalArgumentException("Players not found in group: " + missingIds);
        }

        List<BalancePlayerSelection> resolved = new ArrayList<>();
        for (Long playerId : request.playerIds()) {
            Player player = loadedById.get(playerId);
            if (player == null) {
                throw new IllegalArgumentException("Players not found in group: [" + playerId + "]");
            }
            resolved.add(new BalancePlayerSelection(
                new BalancePlayerDto(player.getId(), player.getNickname(), safeMmr(player.getMmr())),
                requireRaceData ? PlayerRacePolicy.normalizeCapability(player.getRace()) : null
            ));
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

    private List<BalancePlayerDto> applyAssignedRaces(
        List<BalancePlayerDto> players,
        PlayerRacePolicy.TeamRaceAssignment assignment
    ) {
        List<String> assignedRaces = assignment.assignedRaces();
        if (players.size() != assignedRaces.size()) {
            throw new IllegalStateException("배정 종족 정보가 올바르지 않습니다.");
        }

        List<BalancePlayerDto> assignedPlayers = new ArrayList<>(players.size());
        for (int index = 0; index < players.size(); index++) {
            BalancePlayerDto player = players.get(index);
            assignedPlayers.add(new BalancePlayerDto(
                player.playerId(),
                player.name(),
                player.mmr(),
                assignedRaces.get(index)
            ));
        }
        return assignedPlayers;
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

    private record BalancePlayerSelection(
        BalancePlayerDto dto,
        String normalizedRace
    ) {
    }
}
