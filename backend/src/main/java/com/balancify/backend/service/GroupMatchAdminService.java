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
import com.balancify.backend.service.exception.MatchConflictException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
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
    // A match set up before play waits this long at most for its result. A result entered for the
    // same match in that time belongs to the one already waiting, not to a new one.
    private static final long AWAITING_MATCH_LOOKBACK_MINUTES = 180;

    private final GroupRepository groupRepository;
    private final PlayerRepository playerRepository;
    private final MatchRepository matchRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final NotificationService notificationService;
    private final long duplicateWindowMinutes;

    public GroupMatchAdminService(
        GroupRepository groupRepository,
        PlayerRepository playerRepository,
        MatchRepository matchRepository,
        MatchParticipantRepository matchParticipantRepository,
        NotificationService notificationService,
        @Value("${balancify.match.confirm.duplicate-window-minutes:5}") long duplicateWindowMinutes
    ) {
        this.groupRepository = groupRepository;
        this.playerRepository = playerRepository;
        this.matchRepository = matchRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.notificationService = notificationService;
        this.duplicateWindowMinutes = Math.max(1, duplicateWindowMinutes);
    }

    @Transactional
    public CreateGroupMatchResponse createMatch(Long groupId, CreateGroupMatchRequest request) {
        return createMatch(groupId, request, null);
    }

    /** Sets a balanced match up; createdByEmail is kept as whom the match is waiting on for its result. */
    @Transactional
    public CreateGroupMatchResponse createMatch(Long groupId, CreateGroupMatchRequest request, String createdByEmail) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }

        MatchCreationOutcome outcome = createMatchInternal(
            groupId,
            request.homePlayerIds(),
            request.awayPlayerIds(),
            normalizeRequestedTeamSize(request.teamSize()),
            MatchSource.BALANCED,
            null,
            request.raceComposition(),
            DuplicateHandling.REUSE_ACTIVE_REJECT_COMPLETED,
            null,
            Boolean.TRUE.equals(request.resultFollows()),
            createdByEmail,
            true
        );

        if (outcome.duplicateRejected()) {
            return new CreateGroupMatchResponse(
                null,
                "DUPLICATE_REJECTED",
                outcome.rejectionMessage() == null ? duplicateConflictMessage() : outcome.rejectionMessage()
            );
        }

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
        String raceComposition
    ) {
        return createMatchInternal(
            groupId,
            homePlayerIds,
            awayPlayerIds,
            teamSize,
            source,
            note,
            raceComposition,
            DuplicateHandling.REJECT,
            null,
            // Entered with its result; the result entry that follows refuses a result entered twice.
            false,
            null,
            false
        ).match();
    }

    /**
     * A team tournament game. It skips the duplicate check, since the same teams play up to three
     * games in a row and each game number of a series is created only once.
     */
    @Transactional
    public Match createSeriesGameMatch(
        Long groupId,
        List<Long> homePlayerIds,
        List<Long> awayPlayerIds,
        String raceComposition,
        Long seriesId,
        int seriesGameNumber
    ) {
        return createMatchInternal(
            groupId,
            homePlayerIds,
            awayPlayerIds,
            TEAM_SIZE_3V3,
            MatchSource.BALANCED,
            null,
            raceComposition,
            DuplicateHandling.NONE,
            new SeriesLink(seriesId, null, seriesGameNumber),
            false,
            null,
            false
        ).match();
    }

    /**
     * A game of a series started after a multi-balance. Like a tournament game it skips the
     * duplicate check: the same teams play up to three games in a row, each game number once.
     */
    @Transactional
    public Match createBalanceSeriesGameMatch(
        Long groupId,
        List<Long> homePlayerIds,
        List<Long> awayPlayerIds,
        int teamSize,
        String raceComposition,
        Long balanceSeriesId,
        int seriesGameNumber,
        String createdByEmail
    ) {
        return createMatchInternal(
            groupId,
            homePlayerIds,
            awayPlayerIds,
            teamSize,
            MatchSource.BALANCED,
            null,
            raceComposition,
            DuplicateHandling.NONE,
            new SeriesLink(null, balanceSeriesId, seriesGameNumber),
            false,
            createdByEmail,
            false
        ).match();
    }

    private static String normalizeCreatorEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
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
        DuplicateHandling duplicateHandling,
        SeriesLink seriesLink,
        // The balance page saves the match only to enter its result at once.
        boolean resultFollows,
        String createdByEmail,
        // Players on the roster only: a dormant one (휴면) is refused like one not in the group.
        boolean rosterOnly
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

        List<Player> players = playerRepository.findByGroup_IdAndIdIn(groupId, new ArrayList<>(allIds))
            .stream()
            .filter(player -> rosterOnly
                ? PlayerRosterPolicy.isOnRoster(player)
                : !PlayerIdentityPolicy.isIdentityHidden(player))
            .toList();
        if (players.size() != normalizedTeamSize * 2) {
            throw new IllegalArgumentException("All players must belong to the group");
        }

        Map<Long, Player> playersById = players.stream()
            .collect(Collectors.toMap(Player::getId, Function.identity()));
        MatchSource normalizedSource = source == null ? MatchSource.BALANCED : source;
        MatchRaceAssignments raceAssignments = assignRaceComposition(
            homePlayerIds,
            awayPlayerIds,
            playersById,
            normalizedRaceComposition,
            normalizedSource == MatchSource.MANUAL
        );

        MatchSignaturePolicy.Signature requestedSignature = MatchSignaturePolicy.fromPlayerIds(
            homePlayerIds,
            awayPlayerIds
        );
        if (duplicateHandling != DuplicateHandling.NONE) {
            OffsetDateTime duplicateCheckStartAt = OffsetDateTime.now().minusMinutes(duplicateWindowMinutes);
            // A member entering a result also finds the same match set up longer ago and still
            // waiting: the result is that match's. Setting a match up again later starts a new one.
            boolean entersResultOfAwaitingMatch = resultFollows
                && duplicateHandling == DuplicateHandling.REUSE_ACTIVE_REJECT_COMPLETED;
            OffsetDateTime candidatesSince = entersResultOfAwaitingMatch
                ? OffsetDateTime.now().minusMinutes(Math.max(duplicateWindowMinutes, AWAITING_MATCH_LOOKBACK_MINUTES))
                : duplicateCheckStartAt;
            List<Match> duplicateCandidates = matchRepository.findRecentDuplicateCandidates(
                groupId,
                normalizedTeamSize,
                requestedSignature.participantSignature(),
                normalizedRaceComposition,
                candidatesSince
            );

            for (Match duplicateCandidate : duplicateCandidates) {
                if (duplicateCandidate.getId() == null) {
                    continue;
                }
                if (resolveTeamSize(duplicateCandidate) != normalizedTeamSize) {
                    continue;
                }

                MatchSignaturePolicy.Signature existingSignature = MatchSignaturePolicy.fromStored(duplicateCandidate);
                if (existingSignature == null) {
                    existingSignature = MatchSignaturePolicy.fromParticipants(
                        matchParticipantRepository.findByMatchIdWithPlayerAndMatch(duplicateCandidate.getId())
                    );
                }
                if (existingSignature == null || !requestedSignature.equals(existingSignature)) {
                    continue;
                }
                if (!Objects.equals(normalizedRaceComposition, duplicateCandidate.getRaceComposition())) {
                    continue;
                }

                MatchStatus status = duplicateCandidate.getStatus();
                if (status == MatchStatus.CANCELLED) {
                    continue;
                }
                boolean awaitingResult = DUPLICATE_BLOCKING_STATUSES.contains(status)
                    && duplicateCandidate.getWinningTeam() == null;
                boolean createdInWindow = duplicateCandidate.getCreatedAt() != null
                    && !duplicateCandidate.getCreatedAt().isBefore(duplicateCheckStartAt);
                // Older than the window, only a match still waiting for its result counts.
                if (!awaitingResult && !createdInWindow) {
                    continue;
                }
                if (duplicateHandling == DuplicateHandling.REJECT) {
                    throw new MatchConflictException(duplicateConflictMessage());
                }
                if (DUPLICATE_BLOCKING_STATUSES.contains(status)) {
                    return new MatchCreationOutcome(duplicateCandidate, true, false, null);
                }
                return new MatchCreationOutcome(duplicateCandidate, false, true, duplicateConflictMessage());
            }

            // A result for the same two teams moments ago: a match saved to take its result now is
            // that game entered twice. Refused here, before the match is saved, so none is left behind.
            if (resultFollows && duplicateHandling == DuplicateHandling.REUSE_ACTIVE_REJECT_COMPLETED) {
                Match recent = RecentResultDuplicates.find(
                    matchRepository,
                    matchParticipantRepository,
                    groupId,
                    normalizedTeamSize,
                    requestedSignature,
                    duplicateWindowMinutes
                ).orElse(null);
                if (recent != null) {
                    return new MatchCreationOutcome(
                        recent, false, true, RecentResultDuplicates.conflictMessage(duplicateWindowMinutes)
                    );
                }
            }
        }

        Match match = new Match();
        match.setGroup(group);
        match.setPlayedAt(OffsetDateTime.now());
        match.setStatus(MatchStatus.CONFIRMED);
        match.setSource(normalizedSource);
        match.setTeamSize(normalizedTeamSize);
        match.setParticipantSignature(requestedSignature.participantSignature());
        match.setTeamSignature(requestedSignature.teamSignature());
        match.setNote(normalizeNote(note));
        match.setRaceComposition(normalizedRaceComposition);
        match.setCreatedByEmail(normalizeCreatorEmail(createdByEmail));
        if (seriesLink != null) {
            match.setSeriesId(seriesLink.seriesId());
            match.setBalanceSeriesId(seriesLink.balanceSeriesId());
            match.setSeriesGameNumber(seriesLink.gameNumber());
        }
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
        // A balanced match opens for predictions as it is created. A manual entry comes with its
        // result, and so does a balanced one saved at the moment its result is entered.
        if (normalizedSource == MatchSource.BALANCED && !resultFollows) {
            notificationService.publishPredictionsOpen(group.getId(), savedMatch.getId(), normalizedTeamSize);
        }
        return new MatchCreationOutcome(savedMatch, false, false, null);
    }

    private MatchRaceAssignments assignRaceComposition(
        List<Long> homePlayerIds,
        List<Long> awayPlayerIds,
        Map<Long, Player> playersById,
        String raceComposition,
        boolean allowManualOverride
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
            PlayerRacePolicy.assignToComposition(homeCapabilities, raceComposition, allowManualOverride);
        PlayerRacePolicy.TeamRaceAssignment awayAssignment =
            PlayerRacePolicy.assignToComposition(awayCapabilities, raceComposition, allowManualOverride);

        if (homeAssignment == null || awayAssignment == null) {
            throw new IllegalArgumentException("선택한 종족 조합으로 매치를 구성할 수 없습니다");
        }

        return new MatchRaceAssignments(
            homeAssignment.assignedRaces(),
            awayAssignment.assignedRaces()
        );
    }

    private String resolveCapability(Map<Long, Player> playersById, Long playerId) {
        Player player = playersById.get(playerId);
        if (player == null) {
            throw new IllegalArgumentException("Player not found in group");
        }
        return PlayerRacePolicy.normalizeCapability(player.getRace());
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

    static String duplicateConflictMessage() {
        return "같은 팀·같은 종족 조합의 경기가 최근 5분 안에 이미 입력되었습니다.";
    }

    private enum DuplicateHandling {
        NONE,
        REUSE_ACTIVE_REJECT_COMPLETED,
        REJECT
    }

    private record MatchCreationOutcome(
        Match match,
        boolean reusedExisting,
        boolean duplicateRejected,
        String rejectionMessage
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

    // The series a game belongs to: a tournament series or a multi-balance series, and its game number.
    private record SeriesLink(Long seriesId, Long balanceSeriesId, Integer gameNumber) {
    }
}
