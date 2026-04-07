package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.CreateGroupMatchRequest;
import com.balancify.backend.api.group.dto.CreateGroupMatchResponse;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.MatchSource;
import com.balancify.backend.domain.MatchStatus;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.GroupRepository;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchRepository;
import com.balancify.backend.repository.PlayerRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupMatchAdminService {

    private static final String TEAM_HOME = "HOME";
    private static final String TEAM_AWAY = "AWAY";
    private static final int TEAM_SIZE_3V3 = 3;
    private static final List<MatchStatus> DUPLICATE_BLOCKING_STATUSES =
        List.of(MatchStatus.DRAFT, MatchStatus.CONFIRMED);

    private final GroupRepository groupRepository;
    private final PlayerRepository playerRepository;
    private final MatchRepository matchRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final long duplicateWindowMinutes;

    public GroupMatchAdminService(
        GroupRepository groupRepository,
        PlayerRepository playerRepository,
        MatchRepository matchRepository,
        MatchParticipantRepository matchParticipantRepository,
        @Value("${balancify.match.confirm.duplicate-window-minutes:5}") long duplicateWindowMinutes
    ) {
        this.groupRepository = groupRepository;
        this.playerRepository = playerRepository;
        this.matchRepository = matchRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.duplicateWindowMinutes = Math.max(1, duplicateWindowMinutes);
    }

    @Transactional
    public CreateGroupMatchResponse createMatch(Long groupId, CreateGroupMatchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }

        MatchCreationOutcome outcome = createMatchInternal(
            groupId,
            request.homePlayerIds(),
            request.awayPlayerIds(),
            TEAM_SIZE_3V3,
            MatchSource.BALANCED,
            null,
            request.raceComposition(),
            true
        );

        if (outcome.reusedExisting()) {
            return new CreateGroupMatchResponse(
                outcome.match().getId(),
                "REUSED_EXISTING",
                "동일 참가자 조합의 활성 매치를 재사용했습니다."
            );
        }

        return new CreateGroupMatchResponse(
            outcome.match().getId(),
            "CREATED",
            "매치를 확정했습니다."
        );
    }

    @Transactional
    public Match createConfirmedMatch(
        Long groupId,
        List<Long> homePlayerIds,
        List<Long> awayPlayerIds,
        int teamSize,
        MatchSource source,
        String note,
        String raceComposition,
        boolean protectFromDuplicates
    ) {
        return createMatchInternal(
            groupId,
            homePlayerIds,
            awayPlayerIds,
            teamSize,
            source,
            note,
            raceComposition,
            protectFromDuplicates
        ).match();
    }

    private MatchParticipant createParticipant(
        Match match,
        Player player,
        String team,
        String assignedRace
    ) {
        if (player == null) {
            throw new IllegalArgumentException("Player not found in group");
        }

        MatchParticipant participant = new MatchParticipant();
        participant.setMatch(match);
        participant.setPlayer(player);
        participant.setTeam(team);
        participant.setRace(player.getRace());
        participant.setAssignedRace(assignedRace);
        participant.setMmrBefore(player.getMmr());
        participant.setMmrAfter(player.getMmr());
        participant.setMmrDelta(0);
        return participant;
    }

    private List<Long> normalizePlayerIds(List<Long> playerIds) {
        if (playerIds == null) {
            return List.of();
        }

        List<Long> normalized = new ArrayList<>();
        for (Long playerId : playerIds) {
            if (playerId == null || playerId <= 0) {
                throw new IllegalArgumentException("Player ID must be a positive number");
            }
            normalized.add(playerId);
        }
        return normalized;
    }

    private MatchCreationOutcome createMatchInternal(
        Long groupId,
        List<Long> rawHomePlayerIds,
        List<Long> rawAwayPlayerIds,
        int requestedTeamSize,
        MatchSource source,
        String note,
        String rawRaceComposition,
        boolean protectFromDuplicates
    ) {
        int normalizedTeamSize = normalizeRequestedTeamSize(requestedTeamSize);
        String normalizedRaceComposition = RaceCompositionPolicy.normalizeForTeamSize(
            rawRaceComposition,
            normalizedTeamSize
        );
        List<Long> homePlayerIds = normalizePlayerIds(rawHomePlayerIds);
        List<Long> awayPlayerIds = normalizePlayerIds(rawAwayPlayerIds);

        if (homePlayerIds.size() != normalizedTeamSize || awayPlayerIds.size() != normalizedTeamSize) {
            throw new IllegalArgumentException(
                "Exactly %d HOME and %d AWAY players are required".formatted(
                    normalizedTeamSize,
                    normalizedTeamSize
                )
            );
        }

        Set<Long> allIds = new LinkedHashSet<>();
        allIds.addAll(homePlayerIds);
        allIds.addAll(awayPlayerIds);
        if (allIds.size() != normalizedTeamSize * 2) {
            throw new IllegalArgumentException("Players must be unique across both teams");
        }

        Group group = groupRepository.findByIdForUpdate(groupId)
            .orElseThrow(() -> new NoSuchElementException("Group not found: " + groupId));

        List<Player> players = playerRepository.findByGroup_IdAndIdIn(groupId, new ArrayList<>(allIds));
        if (players.size() != normalizedTeamSize * 2) {
            throw new IllegalArgumentException("All players must belong to the group");
        }

        Map<Long, Player> playersById = players.stream()
            .collect(Collectors.toMap(Player::getId, Function.identity()));
        MatchRaceAssignments raceAssignments = assignRaceComposition(
            homePlayerIds,
            awayPlayerIds,
            playersById,
            normalizedRaceComposition
        );

        Signature requestedSignature = buildRequestedSignature(homePlayerIds, awayPlayerIds);
        if (protectFromDuplicates) {
            OffsetDateTime duplicateCheckStartAt = OffsetDateTime.now().minusMinutes(duplicateWindowMinutes);
            List<Match> activeMatches = matchRepository.findRecentDuplicateCandidates(
                groupId,
                normalizedTeamSize,
                MatchSource.BALANCED,
                requestedSignature.participantSignature(),
                DUPLICATE_BLOCKING_STATUSES,
                duplicateCheckStartAt
            );

            for (Match activeMatch : activeMatches) {
                if (activeMatch.getId() == null) {
                    continue;
                }
                MatchStatus activeStatus = activeMatch.getStatus();
                if (activeStatus == null || !DUPLICATE_BLOCKING_STATUSES.contains(activeStatus)) {
                    continue;
                }
                if (resolveTeamSize(activeMatch) != normalizedTeamSize) {
                    continue;
                }

                Signature existingSignature = readSignature(activeMatch);
                if (existingSignature == null) {
                    existingSignature = buildSignatureFromParticipants(activeMatch.getId());
                }
                if (existingSignature == null) {
                    continue;
                }

                if (!requestedSignature.participantSignature().equals(existingSignature.participantSignature())) {
                    continue;
                }

                return new MatchCreationOutcome(activeMatch, true);
            }
        }

        Match match = new Match();
        match.setGroup(group);
        match.setPlayedAt(OffsetDateTime.now());
        match.setStatus(MatchStatus.CONFIRMED);
        match.setSource(source == null ? MatchSource.BALANCED : source);
        match.setTeamSize(normalizedTeamSize);
        match.setParticipantSignature(requestedSignature.participantSignature());
        match.setTeamSignature(requestedSignature.teamSignature());
        match.setNote(normalizeNote(note));
        match.setRaceComposition(normalizedRaceComposition);
        Match savedMatch = matchRepository.save(match);

        List<MatchParticipant> participants = new ArrayList<>();
        for (int index = 0; index < homePlayerIds.size(); index++) {
            Long playerId = homePlayerIds.get(index);
            participants.add(createParticipant(
                savedMatch,
                playersById.get(playerId),
                TEAM_HOME,
                raceAssignments.homeAssignedRaces().get(index)
            ));
        }
        for (int index = 0; index < awayPlayerIds.size(); index++) {
            Long playerId = awayPlayerIds.get(index);
            participants.add(createParticipant(
                savedMatch,
                playersById.get(playerId),
                TEAM_AWAY,
                raceAssignments.awayAssignedRaces().get(index)
            ));
        }

        matchParticipantRepository.saveAll(participants);
        return new MatchCreationOutcome(savedMatch, false);
    }

    private MatchRaceAssignments assignRaceComposition(
        List<Long> homePlayerIds,
        List<Long> awayPlayerIds,
        Map<Long, Player> playersById,
        String raceComposition
    ) {
        if (raceComposition == null) {
            return MatchRaceAssignments.none(homePlayerIds.size(), awayPlayerIds.size());
        }

        List<String> homeCapabilities = homePlayerIds.stream()
            .map(playerId -> resolveCapability(playersById, playerId))
            .toList();
        List<String> awayCapabilities = awayPlayerIds.stream()
            .map(playerId -> resolveCapability(playersById, playerId))
            .toList();

        PlayerRacePolicy.TeamRaceAssignment homeAssignment =
            PlayerRacePolicy.assignToComposition(homeCapabilities, raceComposition);
        PlayerRacePolicy.TeamRaceAssignment awayAssignment =
            PlayerRacePolicy.assignToComposition(awayCapabilities, raceComposition);

        if (homeAssignment == null || awayAssignment == null) {
            throw new IllegalArgumentException("선택한 종족 조합으로 매치를 구성할 수 없습니다");
        }

        return new MatchRaceAssignments(homeAssignment.assignedRaces(), awayAssignment.assignedRaces());
    }

    private String resolveCapability(Map<Long, Player> playersById, Long playerId) {
        Player player = playersById.get(playerId);
        if (player == null) {
            throw new IllegalArgumentException("Player not found in group");
        }
        return PlayerRacePolicy.normalizeCapability(player.getRace());
    }

    private Signature buildRequestedSignature(List<Long> homePlayerIds, List<Long> awayPlayerIds) {
        List<Long> participantIds = new ArrayList<>(homePlayerIds.size() + awayPlayerIds.size());
        participantIds.addAll(homePlayerIds);
        participantIds.addAll(awayPlayerIds);
        return new Signature(
            buildParticipantSignature(participantIds),
            buildTeamSignature(homePlayerIds, awayPlayerIds)
        );
    }

    private Signature readSignature(Match match) {
        if (match.getParticipantSignature() == null || match.getTeamSignature() == null) {
            return null;
        }
        String participantSignature = match.getParticipantSignature().trim();
        String teamSignature = match.getTeamSignature().trim();
        if (participantSignature.isEmpty() || teamSignature.isEmpty()) {
            return null;
        }
        return new Signature(participantSignature, teamSignature);
    }

    private Signature buildSignatureFromParticipants(Long matchId) {
        List<MatchParticipant> participants =
            matchParticipantRepository.findByMatchIdWithPlayerAndMatch(matchId);
        if (participants.isEmpty()) {
            return null;
        }

        List<Long> homePlayerIds = new ArrayList<>();
        List<Long> awayPlayerIds = new ArrayList<>();
        List<Long> participantIds = new ArrayList<>();

        for (MatchParticipant participant : participants) {
            if (participant.getPlayer() == null || participant.getPlayer().getId() == null) {
                continue;
            }
            Long playerId = participant.getPlayer().getId();
            participantIds.add(playerId);
            if (TEAM_HOME.equals(participant.getTeam())) {
                homePlayerIds.add(playerId);
            } else if (TEAM_AWAY.equals(participant.getTeam())) {
                awayPlayerIds.add(playerId);
            }
        }

        if (participants.size() % 2 != 0) {
            return null;
        }

        int inferredTeamSize = participants.size() / 2;
        if (homePlayerIds.size() != inferredTeamSize || awayPlayerIds.size() != inferredTeamSize) {
            return null;
        }

        return new Signature(
            buildParticipantSignature(participantIds),
            buildTeamSignature(homePlayerIds, awayPlayerIds)
        );
    }

    private String buildParticipantSignature(List<Long> playerIds) {
        return playerIds.stream()
            .sorted()
            .map(String::valueOf)
            .collect(Collectors.joining("-"));
    }

    private String buildTeamSignature(List<Long> homePlayerIds, List<Long> awayPlayerIds) {
        String homeSignature = homePlayerIds.stream()
            .sorted()
            .map(String::valueOf)
            .collect(Collectors.joining("-"));
        String awaySignature = awayPlayerIds.stream()
            .sorted()
            .map(String::valueOf)
            .collect(Collectors.joining("-"));
        return "HOME:" + homeSignature + "|AWAY:" + awaySignature;
    }

    private int resolveTeamSize(Match match) {
        if (match.getTeamSize() == null || match.getTeamSize() <= 0) {
            return TEAM_SIZE_3V3;
        }
        return match.getTeamSize();
    }

    private int normalizeRequestedTeamSize(Integer teamSize) {
        if (teamSize == null) {
            return TEAM_SIZE_3V3;
        }
        if (teamSize != 2 && teamSize != 3) {
            throw new IllegalArgumentException("teamSize must be 2 or 3");
        }
        return teamSize;
    }

    private String normalizeNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String normalized = note.trim();
        if (normalized.length() > 255) {
            return normalized.substring(0, 255);
        }
        return normalized;
    }

    private record Signature(
        String participantSignature,
        String teamSignature
    ) {
    }

    private record MatchCreationOutcome(
        Match match,
        boolean reusedExisting
    ) {
    }

    private record MatchRaceAssignments(
        List<String> homeAssignedRaces,
        List<String> awayAssignedRaces
    ) {
        private static MatchRaceAssignments none(int homeSize, int awaySize) {
            return new MatchRaceAssignments(
                new ArrayList<>(java.util.Collections.nCopies(homeSize, null)),
                new ArrayList<>(java.util.Collections.nCopies(awaySize, null))
            );
        }
    }
}
