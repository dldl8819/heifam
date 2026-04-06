package com.balancify.backend.service;

import com.balancify.backend.api.admin.dto.RatingRecalculationRequest;
import com.balancify.backend.api.admin.dto.RatingRecalculationResponse;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchRepository;
import com.balancify.backend.repository.PlayerRepository;
import com.balancify.backend.service.exception.MatchConflictException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RatingRecalculationService {

    private static final int MATCH_BATCH_SIZE = 200;

    private final PlayerRepository playerRepository;
    private final MatchRepository matchRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final RatingReplayCalculator ratingReplayCalculator;
    private final RatingRecalculationWriteService ratingRecalculationWriteService;
    private final AtomicBoolean recalculationRunning = new AtomicBoolean(false);

    public RatingRecalculationService(
        PlayerRepository playerRepository,
        MatchRepository matchRepository,
        MatchParticipantRepository matchParticipantRepository,
        RatingReplayCalculator ratingReplayCalculator,
        RatingRecalculationWriteService ratingRecalculationWriteService
    ) {
        this.playerRepository = playerRepository;
        this.matchRepository = matchRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.ratingReplayCalculator = ratingReplayCalculator;
        this.ratingRecalculationWriteService = ratingRecalculationWriteService;
    }

    public RatingRecalculationResponse recalculate(RatingRecalculationRequest request) {
        boolean dryRun = request != null && request.dryRunEnabled();
        if (!dryRun && (request == null || !request.confirmed())) {
            throw new IllegalArgumentException("confirm=true is required");
        }
        if (!recalculationRunning.compareAndSet(false, true)) {
            throw new MatchConflictException("이미 MMR 재계산이 실행 중입니다.");
        }

        long startedAt = System.currentTimeMillis();
        try {
            List<Player> players = loadPlayers();
            List<Match> matches = loadMatches();
            Map<Long, List<MatchParticipant>> participantsByMatchId = loadParticipantsByMatchId(matches);
            RatingReplayPlan plan = ratingReplayCalculator.calculate(players, matches, participantsByMatchId);

            if (!dryRun) {
                ratingRecalculationWriteService.apply(plan);
            }

            return new RatingRecalculationResponse(
                plan.processedMatches(),
                plan.updatedPlayers(),
                System.currentTimeMillis() - startedAt,
                dryRun ? "DRY_RUN" : "SUCCESS",
                dryRun,
                plan.averageAbsoluteDeltaDifference(),
                plan.samplePlayerChanges()
            );
        } finally {
            recalculationRunning.set(false);
        }
    }

    @Transactional(readOnly = true)
    protected List<Player> loadPlayers() {
        return playerRepository.findAll();
    }

    @Transactional(readOnly = true)
    protected List<Match> loadMatches() {
        return matchRepository.findByWinningTeamIsNotNullOrderByPlayedAtAscIdAsc();
    }

    @Transactional(readOnly = true)
    protected Map<Long, List<MatchParticipant>> loadParticipantsByMatchId(List<Match> matches) {
        Map<Long, List<MatchParticipant>> grouped = new LinkedHashMap<>();
        for (Match match : matches) {
            grouped.put(match.getId(), new ArrayList<>());
        }

        List<Long> allMatchIds = matches.stream().map(Match::getId).toList();
        for (int index = 0; index < allMatchIds.size(); index += MATCH_BATCH_SIZE) {
            int endIndex = Math.min(index + MATCH_BATCH_SIZE, allMatchIds.size());
            List<Long> batchIds = allMatchIds.subList(index, endIndex);
            List<MatchParticipant> batchParticipants =
                matchParticipantRepository.findByMatchIdInWithPlayerAndMatch(batchIds);

            for (MatchParticipant participant : batchParticipants) {
                Long matchId = participant.getMatch() == null ? null : participant.getMatch().getId();
                if (matchId == null || !grouped.containsKey(matchId)) {
                    continue;
                }
                grouped.get(matchId).add(participant);
            }
        }

        return grouped;
    }
}
