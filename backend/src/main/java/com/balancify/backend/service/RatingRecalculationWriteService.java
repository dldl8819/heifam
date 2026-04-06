package com.balancify.backend.service;

import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.MmrHistory;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchRepository;
import com.balancify.backend.repository.MmrHistoryRepository;
import com.balancify.backend.repository.PlayerRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RatingRecalculationWriteService {

    private static final int SAVE_BATCH_SIZE = 200;

    private final PlayerRepository playerRepository;
    private final MatchRepository matchRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final MmrHistoryRepository mmrHistoryRepository;

    RatingRecalculationWriteService(
        PlayerRepository playerRepository,
        MatchRepository matchRepository,
        MatchParticipantRepository matchParticipantRepository,
        MmrHistoryRepository mmrHistoryRepository
    ) {
        this.playerRepository = playerRepository;
        this.matchRepository = matchRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.mmrHistoryRepository = mmrHistoryRepository;
    }

    @Transactional
    public void apply(RatingReplayPlan plan) {
        mmrHistoryRepository.deleteAllInBatch();
        matchParticipantRepository.resetAllDerivedRatings();

        Map<Long, Player> playersById = new HashMap<>();
        for (Player player : playerRepository.findAllById(plan.playerIds())) {
            playersById.put(player.getId(), player);
        }

        Map<Long, MatchParticipant> participantsById = new HashMap<>();
        for (MatchParticipant participant : matchParticipantRepository.findAllById(plan.participantIds())) {
            participantsById.put(participant.getId(), participant);
        }

        Map<Long, Match> matchesById = new HashMap<>();
        for (Match match : matchRepository.findAllById(plan.matchIds())) {
            matchesById.put(match.getId(), match);
        }

        List<Player> playersToSave = new ArrayList<>(plan.players().size());
        for (RatingReplayPlan.PlayerResult result : plan.players()) {
            Player player = playersById.get(result.playerId());
            if (player == null) {
                throw new NoSuchElementException("Player not found: " + result.playerId());
            }
            player.setMmr(result.finalMmr());
            player.setTier(result.finalTier());
            playersToSave.add(player);
        }
        savePlayers(playersToSave);

        List<MatchParticipant> participantsToSave = new ArrayList<>(plan.participants().size());
        List<MmrHistory> historiesToSave = new ArrayList<>(plan.participants().size());
        for (RatingReplayPlan.ParticipantResult result : plan.participants()) {
            MatchParticipant participant = participantsById.get(result.participantId());
            if (participant == null) {
                throw new NoSuchElementException("Match participant not found: " + result.participantId());
            }
            participant.setMmrBefore(result.beforeMmr());
            participant.setMmrAfter(result.afterMmr());
            participant.setMmrDelta(result.delta());
            participantsToSave.add(participant);

            Player player = playersById.get(result.playerId());
            Match match = matchesById.get(result.matchId());
            if (player == null) {
                throw new NoSuchElementException("Player not found for history: " + result.playerId());
            }
            if (match == null) {
                throw new NoSuchElementException("Match not found for history: " + result.matchId());
            }

            MmrHistory history = new MmrHistory();
            history.setPlayer(player);
            history.setMatch(match);
            history.setBeforeMmr(result.beforeMmr());
            history.setAfterMmr(result.afterMmr());
            history.setDelta(result.delta());
            history.setCreatedAt(result.historyCreatedAt());
            historiesToSave.add(history);
        }

        saveParticipants(participantsToSave);
        saveHistories(historiesToSave);
    }

    private void savePlayers(List<Player> players) {
        for (int index = 0; index < players.size(); index += SAVE_BATCH_SIZE) {
            int endIndex = Math.min(index + SAVE_BATCH_SIZE, players.size());
            playerRepository.saveAll(players.subList(index, endIndex));
        }
    }

    private void saveParticipants(List<MatchParticipant> participants) {
        for (int index = 0; index < participants.size(); index += SAVE_BATCH_SIZE) {
            int endIndex = Math.min(index + SAVE_BATCH_SIZE, participants.size());
            matchParticipantRepository.saveAll(participants.subList(index, endIndex));
        }
    }

    private void saveHistories(List<MmrHistory> histories) {
        for (int index = 0; index < histories.size(); index += SAVE_BATCH_SIZE) {
            int endIndex = Math.min(index + SAVE_BATCH_SIZE, histories.size());
            mmrHistoryRepository.saveAll(histories.subList(index, endIndex));
        }
    }
}
