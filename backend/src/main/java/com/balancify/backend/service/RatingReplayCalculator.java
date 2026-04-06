package com.balancify.backend.service;

import com.balancify.backend.api.admin.dto.RatingRecalculationPlayerChangeResponse;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.PlayerTierPolicy;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class RatingReplayCalculator {

    private static final String TEAM_HOME = "HOME";
    private static final String TEAM_AWAY = "AWAY";

    private final int baseKFactor;
    private final double largeGapThreshold;
    private final double largeGapRange;
    private final double largeGapMinMultiplier;
    private final double lowTierMultiplier;

    RatingReplayCalculator(
        @Value("${balancify.elo.k-factor:24}") int baseKFactor,
        @Value("${balancify.elo.large-gap-threshold:300}") double largeGapThreshold,
        @Value("${balancify.elo.large-gap-range:900}") double largeGapRange,
        @Value("${balancify.elo.large-gap-min-multiplier:0.6}") double largeGapMinMultiplier,
        @Value("${balancify.elo.low-tier-multiplier:0.7}") double lowTierMultiplier
    ) {
        this.baseKFactor = baseKFactor;
        this.largeGapThreshold = largeGapThreshold;
        this.largeGapRange = largeGapRange <= 0 ? 900 : largeGapRange;
        this.largeGapMinMultiplier = Math.max(0.1, Math.min(1.0, largeGapMinMultiplier));
        this.lowTierMultiplier = Math.max(0.1, Math.min(1.0, lowTierMultiplier));
    }

    RatingReplayPlan calculate(
        List<Player> players,
        List<Match> matches,
        Map<Long, List<MatchParticipant>> participantsByMatchId
    ) {
        Map<Long, Integer> earliestRecordedBefore = resolveEarliestRecordedBefore(matches, participantsByMatchId);
        Map<Long, PlayerState> states = initializeStates(players, earliestRecordedBefore);

        List<RatingReplayPlan.ParticipantResult> participantResults = new ArrayList<>();
        long totalAbsoluteDeltaDifference = 0L;
        long totalDeltaSamples = 0L;

        List<Match> orderedMatches = matches.stream()
            .sorted(
                Comparator.comparing(
                    Match::getPlayedAt,
                    Comparator.nullsFirst(Comparator.naturalOrder())
                ).thenComparing(Match::getId)
            )
            .toList();

        for (Match match : orderedMatches) {
            List<MatchParticipant> participants = participantsByMatchId.getOrDefault(match.getId(), List.of());
            if (participants.isEmpty()) {
                throw new IllegalStateException("Match participants are missing for match " + match.getId());
            }
            Set<Long> distinctPlayerIds = participants.stream()
                .map(participant -> participant.getPlayer() == null ? null : participant.getPlayer().getId())
                .collect(java.util.stream.Collectors.toSet());
            if (distinctPlayerIds.size() != participants.size()) {
                throw new IllegalStateException("Duplicate players found in match " + match.getId());
            }

            int teamSize = resolveRequiredTeamSize(match, participants);
            List<MatchParticipant> homeParticipants = participants.stream()
                .filter(participant -> TEAM_HOME.equals(normalizeTeam(participant.getTeam())))
                .toList();
            List<MatchParticipant> awayParticipants = participants.stream()
                .filter(participant -> TEAM_AWAY.equals(normalizeTeam(participant.getTeam())))
                .toList();

            if (homeParticipants.size() != teamSize || awayParticipants.size() != teamSize) {
                throw new IllegalStateException("Match teams are invalid for match " + match.getId());
            }

            double homeAverageMmr = averageCurrentMmr(homeParticipants, states);
            double awayAverageMmr = averageCurrentMmr(awayParticipants, states);
            double homeExpectedWinRate = calculateExpectedWinRate(homeAverageMmr, awayAverageMmr);
            double awayExpectedWinRate = 1.0 - homeExpectedWinRate;
            int effectiveKFactor = calculateEffectiveKFactor(participants, states, homeAverageMmr, awayAverageMmr);
            String winnerTeam = normalizeTeam(match.getWinningTeam());
            OffsetDateTime historyCreatedAt = match.getResultRecordedAt() != null
                ? match.getResultRecordedAt()
                : (match.getPlayedAt() != null ? match.getPlayedAt() : OffsetDateTime.now());

            for (MatchParticipant participant : participants) {
                Long playerId = participant.getPlayer() == null ? null : participant.getPlayer().getId();
                if (playerId == null) {
                    throw new IllegalStateException("Participant player is missing for match " + match.getId());
                }

                PlayerState state = states.get(playerId);
                if (state == null) {
                    throw new IllegalStateException("Player state is missing for player " + playerId);
                }

                boolean homeSide = TEAM_HOME.equals(normalizeTeam(participant.getTeam()));
                double expected = homeSide ? homeExpectedWinRate : awayExpectedWinRate;
                double actual = winnerTeam.equals(normalizeTeam(participant.getTeam())) ? 1.0 : 0.0;

                int beforeMmr = state.currentMmr();
                int delta = (int) Math.round(effectiveKFactor * (actual - expected));
                int afterMmr = beforeMmr + delta;
                int completedGames = state.completedGames() + 1;
                String nextTier = PlayerTierPolicy.resolveTierForRankedMatch(
                    state.currentTier(),
                    afterMmr,
                    completedGames
                );

                state.currentMmr(afterMmr);
                state.currentTier(nextTier);
                state.completedGames(completedGames);

                totalAbsoluteDeltaDifference += Math.abs(delta - safeInt(participant.getMmrDelta()));
                totalDeltaSamples++;

                participantResults.add(
                    new RatingReplayPlan.ParticipantResult(
                        participant.getId(),
                        match.getId(),
                        playerId,
                        beforeMmr,
                        afterMmr,
                        delta,
                        historyCreatedAt
                    )
                );
            }
        }

        List<RatingReplayPlan.PlayerResult> playerResults = states.values().stream()
            .sorted(Comparator.comparing(PlayerState::playerId))
            .map(state -> new RatingReplayPlan.PlayerResult(
                state.playerId(),
                state.currentMmr(),
                state.currentTier(),
                state.originalMmr()
            ))
            .toList();

        List<RatingRecalculationPlayerChangeResponse> samplePlayerChanges = states.values().stream()
            .sorted(Comparator
                .comparingInt((PlayerState state) -> Math.abs(state.currentMmr() - state.originalMmr()))
                .reversed()
                .thenComparing(PlayerState::playerId))
            .limit(5)
            .map(state -> new RatingRecalculationPlayerChangeResponse(
                state.playerId(),
                state.nickname(),
                state.originalMmr(),
                state.currentMmr()
            ))
            .toList();

        double averageAbsoluteDeltaDifference = totalDeltaSamples == 0
            ? 0.0
            : (double) totalAbsoluteDeltaDifference / (double) totalDeltaSamples;

        return new RatingReplayPlan(
            playerResults,
            participantResults,
            orderedMatches.size(),
            states.size(),
            averageAbsoluteDeltaDifference,
            samplePlayerChanges
        );
    }

    private Map<Long, Integer> resolveEarliestRecordedBefore(
        List<Match> matches,
        Map<Long, List<MatchParticipant>> participantsByMatchId
    ) {
        Map<Long, Integer> earliest = new HashMap<>();
        List<Match> orderedMatches = matches.stream()
            .sorted(
                Comparator.comparing(
                    Match::getPlayedAt,
                    Comparator.nullsFirst(Comparator.naturalOrder())
                ).thenComparing(Match::getId)
            )
            .toList();

        for (Match match : orderedMatches) {
            List<MatchParticipant> participants = participantsByMatchId.getOrDefault(match.getId(), List.of());
            for (MatchParticipant participant : participants) {
                Long playerId = participant.getPlayer() == null ? null : participant.getPlayer().getId();
                if (playerId == null || participant.getMmrBefore() == null) {
                    continue;
                }
                earliest.putIfAbsent(playerId, participant.getMmrBefore());
            }
        }

        return earliest;
    }

    private Map<Long, PlayerState> initializeStates(
        List<Player> players,
        Map<Long, Integer> earliestRecordedBefore
    ) {
        Map<Long, PlayerState> states = new LinkedHashMap<>();
        players.stream()
            .sorted(Comparator.comparing(Player::getId))
            .forEach(player -> {
                int seedMmr = resolveSeedMmr(player, earliestRecordedBefore.get(player.getId()));
                states.put(
                    player.getId(),
                    new PlayerState(
                        player.getId(),
                        player.getNickname(),
                        safeInt(player.getMmr()),
                        seedMmr,
                        PlayerTierPolicy.resolveTier(seedMmr),
                        0
                    )
                );
            });
        return states;
    }

    private int resolveSeedMmr(Player player, Integer earliestRecordedBefore) {
        if (player.getBaseMmr() != null) {
            return safeInt(player.getBaseMmr());
        }
        if (earliestRecordedBefore != null) {
            return safeInt(earliestRecordedBefore);
        }
        return safeInt(player.getMmr());
    }

    private double averageCurrentMmr(List<MatchParticipant> participants, Map<Long, PlayerState> states) {
        return participants.stream()
            .mapToInt(participant -> {
                Long playerId = participant.getPlayer() == null ? null : participant.getPlayer().getId();
                if (playerId == null || !states.containsKey(playerId)) {
                    throw new IllegalStateException("Player state is missing");
                }
                return states.get(playerId).currentMmr();
            })
            .average()
            .orElse(0.0);
    }

    private int calculateEffectiveKFactor(
        List<MatchParticipant> participants,
        Map<Long, PlayerState> states,
        double homeAverageMmr,
        double awayAverageMmr
    ) {
        double teamGap = Math.abs(homeAverageMmr - awayAverageMmr);
        double gapMultiplier = 1.0;
        if (teamGap > largeGapThreshold) {
            double progressed = (teamGap - largeGapThreshold) / largeGapRange;
            gapMultiplier = Math.max(largeGapMinMultiplier, 1.0 - progressed);
        }

        boolean containsLowTier = participants.stream()
            .map(participant -> participant.getPlayer() == null ? null : participant.getPlayer().getId())
            .filter(states::containsKey)
            .map(states::get)
            .map(PlayerState::currentMmr)
            .anyMatch(PlayerTierPolicy::isLowTier);

        double tierMultiplier = containsLowTier ? lowTierMultiplier : 1.0;
        int effective = (int) Math.round(baseKFactor * gapMultiplier * tierMultiplier);
        return Math.max(4, effective);
    }

    private double calculateExpectedWinRate(double homeAverageMmr, double awayAverageMmr) {
        double ratingGap = (awayAverageMmr - homeAverageMmr) / 400.0;
        return 1.0 / (1.0 + Math.pow(10.0, ratingGap));
    }

    private int resolveRequiredTeamSize(Match match, List<MatchParticipant> participants) {
        Integer declaredTeamSize = match.getTeamSize();
        if (declaredTeamSize != null && declaredTeamSize > 0) {
            return declaredTeamSize;
        }
        if (participants.isEmpty() || participants.size() % 2 != 0) {
            throw new IllegalStateException("Match participants are invalid for match " + match.getId());
        }
        return participants.size() / 2;
    }

    private String normalizeTeam(String team) {
        if (team == null || team.isBlank()) {
            throw new IllegalStateException("winnerTeam must be HOME or AWAY");
        }

        String normalized = team.trim().toUpperCase(Locale.ROOT);
        if (!TEAM_HOME.equals(normalized) && !TEAM_AWAY.equals(normalized)) {
            throw new IllegalStateException("winnerTeam must be HOME or AWAY");
        }
        return normalized;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static final class PlayerState {

        private final Long playerId;
        private final String nickname;
        private final int originalMmr;
        private int currentMmr;
        private String currentTier;
        private int completedGames;

        private PlayerState(
            Long playerId,
            String nickname,
            int originalMmr,
            int currentMmr,
            String currentTier,
            int completedGames
        ) {
            this.playerId = playerId;
            this.nickname = nickname;
            this.originalMmr = originalMmr;
            this.currentMmr = currentMmr;
            this.currentTier = currentTier;
            this.completedGames = completedGames;
        }

        private Long playerId() {
            return playerId;
        }

        private String nickname() {
            return nickname;
        }

        private int originalMmr() {
            return originalMmr;
        }

        private int currentMmr() {
            return currentMmr;
        }

        private void currentMmr(int currentMmr) {
            this.currentMmr = currentMmr;
        }

        private String currentTier() {
            return currentTier;
        }

        private void currentTier(String currentTier) {
            this.currentTier = currentTier;
        }

        private int completedGames() {
            return completedGames;
        }

        private void completedGames(int completedGames) {
            this.completedGames = completedGames;
        }
    }
}
