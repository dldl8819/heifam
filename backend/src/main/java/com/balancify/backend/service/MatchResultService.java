package com.balancify.backend.service;

import com.balancify.backend.api.match.dto.MatchResultParticipantResponse;
import com.balancify.backend.api.match.dto.MatchResultRequest;
import com.balancify.backend.api.match.dto.MatchResultResponse;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.MatchStatus;
import com.balancify.backend.domain.MmrHistory;
import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.PlayerTierPolicy;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchRepository;
import com.balancify.backend.repository.MmrHistoryRepository;
import com.balancify.backend.repository.PlayerRepository;
import com.balancify.backend.service.exception.MatchConflictException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchResultService {

    private static final String TEAM_HOME = "HOME";
    private static final String TEAM_AWAY = "AWAY";

    private final MatchRepository matchRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final PlayerRepository playerRepository;
    private final MmrHistoryRepository mmrHistoryRepository;
    private final int baseKFactor;
    private final double largeGapThreshold;
    private final double largeGapRange;
    private final double largeGapMinMultiplier;
    private final double lowTierMultiplier;

    public MatchResultService(
        MatchRepository matchRepository,
        MatchParticipantRepository matchParticipantRepository,
        PlayerRepository playerRepository,
        MmrHistoryRepository mmrHistoryRepository,
        @Value("${balancify.elo.k-factor:24}") int baseKFactor,
        @Value("${balancify.elo.large-gap-threshold:300}") double largeGapThreshold,
        @Value("${balancify.elo.large-gap-range:900}") double largeGapRange,
        @Value("${balancify.elo.large-gap-min-multiplier:0.6}") double largeGapMinMultiplier,
        @Value("${balancify.elo.low-tier-multiplier:0.7}") double lowTierMultiplier
    ) {
        this.matchRepository = matchRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.playerRepository = playerRepository;
        this.mmrHistoryRepository = mmrHistoryRepository;
        this.baseKFactor = baseKFactor;
        this.largeGapThreshold = largeGapThreshold;
        this.largeGapRange = largeGapRange <= 0 ? 900 : largeGapRange;
        this.largeGapMinMultiplier = Math.max(0.1, Math.min(1.0, largeGapMinMultiplier));
        this.lowTierMultiplier = Math.max(0.1, Math.min(1.0, lowTierMultiplier));
    }

    @Transactional
    public MatchResultResponse processMatchResult(Long matchId, MatchResultRequest request) {
        return processMatchResult(matchId, request, null, null, false);
    }

    @Transactional
    public MatchResultResponse processMatchResult(
        Long matchId,
        MatchResultRequest request,
        String recordedByEmail
    ) {
        return processMatchResult(matchId, request, recordedByEmail, null, false);
    }

    @Transactional
    public MatchResultResponse processMatchResult(
        Long matchId,
        MatchResultRequest request,
        String recordedByEmail,
        String recordedByNickname
    ) {
        return processMatchResult(matchId, request, recordedByEmail, recordedByNickname, false);
    }

    @Transactional
    public MatchResultResponse processMatchResult(
        Long matchId,
        MatchResultRequest request,
        String recordedByEmail,
        String recordedByNickname,
        boolean allowReprocess
    ) {
        Match match = matchRepository.findByIdForUpdate(matchId)
            .orElseThrow(() -> new NoSuchElementException("Match not found: " + matchId));

        String winnerTeam = normalizeTeam(request == null ? null : request.winnerTeam());
        String normalizedRecordedByEmail = normalizeRecordedByEmail(recordedByEmail);
        String normalizedRecordedByNickname = normalizeRecordedByNickname(recordedByNickname);
        MatchStatus currentStatus = normalizeMatchStatus(match);
        boolean alreadyProcessed = hasProcessedResult(match.getWinningTeam())
            || currentStatus == MatchStatus.COMPLETED;

        if (!allowReprocess) {
            if (alreadyProcessed) {
                throw new MatchConflictException("이미 결과가 확정된 경기입니다.");
            }
            if (currentStatus != MatchStatus.CONFIRMED) {
                throw new MatchConflictException("결과 입력이 가능한 상태의 경기가 아닙니다.");
            }
        } else if (currentStatus == MatchStatus.CANCELLED) {
            throw new MatchConflictException("취소된 경기는 결과를 수정할 수 없습니다.");
        }

        List<MatchParticipant> participants =
            matchParticipantRepository.findByMatchIdWithPlayerAndMatch(matchId);
        int teamSize = resolveRequiredTeamSize(match, participants);
        int requiredParticipants = teamSize * 2;
        if (participants.size() != requiredParticipants) {
            throw new IllegalArgumentException(
                "Exactly %d participants are required".formatted(requiredParticipants)
            );
        }

        List<MatchParticipant> homeParticipants = participants.stream()
            .filter(participant -> TEAM_HOME.equals(normalizeTeam(participant.getTeam())))
            .toList();
        List<MatchParticipant> awayParticipants = participants.stream()
            .filter(participant -> TEAM_AWAY.equals(normalizeTeam(participant.getTeam())))
            .toList();

        if (homeParticipants.size() != teamSize || awayParticipants.size() != teamSize) {
            throw new IllegalArgumentException(
                "Match must have %d HOME and %d AWAY participants".formatted(teamSize, teamSize)
            );
        }

        double homeAverageMmr = calculateAverageMmr(homeParticipants);
        double awayAverageMmr = calculateAverageMmr(awayParticipants);
        double homeExpectedWinRate = calculateExpectedWinRate(homeAverageMmr, awayAverageMmr);
        double awayExpectedWinRate = 1.0 - homeExpectedWinRate;
        int effectiveKFactor = calculateEffectiveKFactor(participants, homeAverageMmr, awayAverageMmr);

        Map<Long, MmrHistory> existingHistoriesByPlayerId = loadHistoryMap(matchId);

        List<MmrHistory> mmrHistories = new ArrayList<>();
        Map<Long, Player> updatedPlayers = new LinkedHashMap<>();
        List<MatchResultParticipantResponse> responseParticipants = new ArrayList<>();
        Map<Long, Integer> completedRankedGamesByPlayerId = new HashMap<>();

        for (MatchParticipant participant : participants) {
            Player player = participant.getPlayer();
            int baseMmrBefore = participant.getMmrBefore() != null
                ? participant.getMmrBefore()
                : safeMmr(player.getMmr());
            boolean homeSide = TEAM_HOME.equals(normalizeTeam(participant.getTeam()));
            double expected = homeSide ? homeExpectedWinRate : awayExpectedWinRate;
            double actual = winnerTeam.equals(normalizeTeam(participant.getTeam())) ? 1.0 : 0.0;

            int mmrDelta = (int) Math.round(effectiveKFactor * (actual - expected));
            int mmrAfter = baseMmrBefore + mmrDelta;
            int previousDelta = safeMmr(participant.getMmrDelta());
            int currentPlayerMmr = safeMmr(player.getMmr());
            int updatedPlayerMmr = alreadyProcessed
                ? currentPlayerMmr - previousDelta + mmrDelta
                : currentPlayerMmr + mmrDelta;

            if (participant.getRace() == null || participant.getRace().isBlank()) {
                participant.setRace(player.getRace());
            }
            participant.setMmrBefore(baseMmrBefore);
            participant.setMmrAfter(mmrAfter);
            participant.setMmrDelta(mmrDelta);

            int completedRankedGames = resolveCompletedRankedGamesAfterResult(
                player.getId(),
                alreadyProcessed,
                completedRankedGamesByPlayerId
            );
            player.applyRankedMmr(updatedPlayerMmr, completedRankedGames);
            updatedPlayers.put(player.getId(), player);

            MmrHistory mmrHistory = existingHistoriesByPlayerId.get(player.getId());
            if (mmrHistory == null) {
                mmrHistory = new MmrHistory();
                mmrHistory.setPlayer(player);
                mmrHistory.setMatch(match);
            }
            mmrHistory.setBeforeMmr(baseMmrBefore);
            mmrHistory.setAfterMmr(mmrAfter);
            mmrHistory.setDelta(mmrDelta);
            mmrHistories.add(mmrHistory);

            responseParticipants.add(new MatchResultParticipantResponse(
                player.getId(),
                player.getNickname(),
                normalizeTeam(participant.getTeam()),
                resolveAssignedRace(participant),
                baseMmrBefore,
                mmrAfter,
                mmrDelta
            ));
        }

        match.setWinningTeam(winnerTeam);
        match.setStatus(MatchStatus.COMPLETED);
        if (match.getTeamSize() == null || match.getTeamSize() <= 0) {
            match.setTeamSize(teamSize);
        }
        match.setResultRecordedAt(OffsetDateTime.now());
        match.setResultRecordedByEmail(normalizedRecordedByEmail);
        match.setResultRecordedByNickname(normalizedRecordedByNickname);

        matchParticipantRepository.saveAll(participants);
        playerRepository.saveAll(new ArrayList<>(updatedPlayers.values()));
        mmrHistoryRepository.saveAll(mmrHistories);
        matchRepository.save(match);

        return new MatchResultResponse(
            match.getId(),
            winnerTeam,
            effectiveKFactor,
            round4(homeExpectedWinRate),
            round4(awayExpectedWinRate),
            responseParticipants
        );
    }

    private double calculateAverageMmr(List<MatchParticipant> participants) {
        return participants.stream()
            .mapToInt(this::participantReferenceMmr)
            .average()
            .orElse(0.0);
    }

    private int participantReferenceMmr(MatchParticipant participant) {
        if (participant.getMmrBefore() != null) {
            return participant.getMmrBefore();
        }
        if (participant.getPlayer() != null) {
            return safeMmr(participant.getPlayer().getMmr());
        }
        return 0;
    }

    private int safeMmr(Integer mmr) {
        return mmr == null ? 0 : mmr;
    }

    private int resolveRequiredTeamSize(Match match, List<MatchParticipant> participants) {
        Integer declaredTeamSize = match.getTeamSize();
        if (declaredTeamSize != null && declaredTeamSize > 0) {
            return declaredTeamSize;
        }

        if (participants == null || participants.isEmpty() || participants.size() % 2 != 0) {
            throw new IllegalArgumentException("Match participants are invalid");
        }

        return participants.size() / 2;
    }

    private int resolveCompletedRankedGamesAfterResult(
        Long playerId,
        boolean alreadyProcessed,
        Map<Long, Integer> cache
    ) {
        if (playerId == null) {
            return 0;
        }

        return cache.computeIfAbsent(playerId, id -> {
            long completedGames = matchParticipantRepository.countByPlayer_IdAndMatch_WinningTeamIsNotNull(id);
            long adjustedCompletedGames = alreadyProcessed ? completedGames : completedGames + 1;
            return (int) Math.max(0, Math.min(Integer.MAX_VALUE, adjustedCompletedGames));
        });
    }

    private boolean hasProcessedResult(String winningTeam) {
        return winningTeam != null && !winningTeam.isBlank();
    }

    private MatchStatus normalizeMatchStatus(Match match) {
        if (match.getStatus() != null) {
            return match.getStatus();
        }
        if (hasProcessedResult(match.getWinningTeam())) {
            return MatchStatus.COMPLETED;
        }
        return MatchStatus.CONFIRMED;
    }

    private Map<Long, MmrHistory> loadHistoryMap(Long matchId) {
        Map<Long, MmrHistory> histories = new HashMap<>();
        List<MmrHistory> matchedHistories = mmrHistoryRepository.findByMatch_Id(matchId);
        if (matchedHistories == null) {
            return histories;
        }
        for (MmrHistory history : matchedHistories) {
            if (history.getPlayer() == null || history.getPlayer().getId() == null) {
                continue;
            }
            histories.put(history.getPlayer().getId(), history);
        }
        return histories;
    }

    private double calculateExpectedWinRate(double homeAverageMmr, double awayAverageMmr) {
        double ratingGap = (awayAverageMmr - homeAverageMmr) / 400.0;
        return 1.0 / (1.0 + Math.pow(10.0, ratingGap));
    }

    private int calculateEffectiveKFactor(
        List<MatchParticipant> participants,
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
            .map(this::participantReferenceMmr)
            .anyMatch(PlayerTierPolicy::isLowTier);
        double tierMultiplier = containsLowTier ? lowTierMultiplier : 1.0;

        int effective = (int) Math.round(baseKFactor * gapMultiplier * tierMultiplier);
        return Math.max(4, effective);
    }

    private String resolveAssignedRace(MatchParticipant participant) {
        if (participant == null) {
            return null;
        }

        try {
            if (participant.getAssignedRace() != null && !participant.getAssignedRace().isBlank()) {
                return PlayerRacePolicy.normalizeAssignedRace(participant.getAssignedRace());
            }
            if (participant.getRace() != null && participant.getRace().length() == 1) {
                return PlayerRacePolicy.normalizeAssignedRace(participant.getRace());
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        return null;
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private String normalizeTeam(String team) {
        if (team == null || team.isBlank()) {
            throw new IllegalArgumentException("winnerTeam must be HOME or AWAY");
        }

        String normalized = team.trim().toUpperCase(Locale.ROOT);
        if (!TEAM_HOME.equals(normalized) && !TEAM_AWAY.equals(normalized)) {
            throw new IllegalArgumentException("winnerTeam must be HOME or AWAY");
        }

        return normalized;
    }

    private String normalizeRecordedByEmail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRecordedByNickname(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 100) {
            return normalized.substring(0, 100);
        }
        return normalized;
    }

    @Transactional
    public void deleteMatch(Long matchId) {
        Match match = matchRepository.findById(matchId)
            .orElseThrow(() -> new NoSuchElementException("Match not found: " + matchId));

        List<MatchParticipant> participants =
            matchParticipantRepository.findByMatchIdWithPlayerAndMatch(matchId);

        Map<Long, Player> playersToUpdate = new LinkedHashMap<>();
        boolean matchHadResult = hasProcessedResult(match.getWinningTeam());
        Map<Long, Integer> completedRankedGamesByPlayerId = new HashMap<>();
        for (MatchParticipant participant : participants) {
            Player player = participant.getPlayer();
            if (player == null || player.getId() == null) {
                continue;
            }

            int currentMmr = safeMmr(player.getMmr());
            int rollbackDelta = safeMmr(participant.getMmrDelta());
            int completedRankedGames = resolveCompletedRankedGamesAfterDelete(
                player.getId(),
                matchHadResult,
                completedRankedGamesByPlayerId
            );
            player.applyRankedMmr(currentMmr - rollbackDelta, completedRankedGames);
            playersToUpdate.put(player.getId(), player);
        }

        if (!playersToUpdate.isEmpty()) {
            playerRepository.saveAll(new ArrayList<>(playersToUpdate.values()));
        }

        mmrHistoryRepository.deleteByMatch_Id(matchId);
        matchParticipantRepository.deleteByMatch_Id(matchId);
        matchRepository.delete(match);
    }

    private int resolveCompletedRankedGamesAfterDelete(
        Long playerId,
        boolean matchHadResult,
        Map<Long, Integer> cache
    ) {
        if (playerId == null) {
            return 0;
        }

        return cache.computeIfAbsent(playerId, id -> {
            long completedGames = matchParticipantRepository.countByPlayer_IdAndMatch_WinningTeamIsNotNull(id);
            long adjustedCompletedGames = matchHadResult
                ? Math.max(0, completedGames - 1)
                : completedGames;
            return (int) Math.max(0, Math.min(Integer.MAX_VALUE, adjustedCompletedGames));
        });
    }
}
