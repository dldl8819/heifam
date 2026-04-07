package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.GroupRecentMatchPlayerResponse;
import com.balancify.backend.api.group.dto.GroupRecentMatchResponse;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class MatchQueryService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final MatchRepository matchRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final AccessControlService accessControlService;

    public MatchQueryService(
        MatchRepository matchRepository,
        MatchParticipantRepository matchParticipantRepository,
        AccessControlService accessControlService
    ) {
        this.matchRepository = matchRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.accessControlService = accessControlService;
    }

    public List<GroupRecentMatchResponse> getRecentMatches(Long groupId, Integer limit) {
        int normalizedLimit = normalizeLimit(limit);
        List<Match> matches = matchRepository.findRecentByGroupId(
            groupId,
            PageRequest.of(0, normalizedLimit)
        );

        List<GroupRecentMatchResponse> responses = new ArrayList<>();
        for (Match match : matches) {
            if (match.getId() == null) {
                continue;
            }

            List<MatchParticipant> participants =
                matchParticipantRepository.findByMatchIdWithPlayerAndMatch(match.getId());

            List<GroupRecentMatchPlayerResponse> homeTeam = new ArrayList<>();
            List<GroupRecentMatchPlayerResponse> awayTeam = new ArrayList<>();
            int homeMmr = 0;
            int awayMmr = 0;

            for (MatchParticipant participant : participants) {
                if (participant.getPlayer() == null || participant.getPlayer().getId() == null) {
                    continue;
                }

                int mmr = participant.getMmrBefore() != null
                    ? participant.getMmrBefore()
                    : safeInt(participant.getPlayer().getMmr());

                String team = normalizeTeam(participant.getTeam());
                GroupRecentMatchPlayerResponse playerResponse = new GroupRecentMatchPlayerResponse(
                    participant.getPlayer().getId(),
                    participant.getPlayer().getNickname(),
                    team,
                    mmr
                );

                if ("HOME".equals(team)) {
                    homeTeam.add(playerResponse);
                    homeMmr += mmr;
                } else if ("AWAY".equals(team)) {
                    awayTeam.add(playerResponse);
                    awayMmr += mmr;
                }
            }

            responses.add(new GroupRecentMatchResponse(
                match.getId(),
                match.getPlayedAt(),
                normalizeStatus(match.getStatus()),
                normalizeWinningTeam(match.getWinningTeam()),
                match.getResultRecordedAt(),
                resolveRecordedByNickname(match),
                resolveTeamRaceComposition(match, participants, "HOME"),
                resolveTeamRaceComposition(match, participants, "AWAY"),
                homeTeam,
                awayTeam,
                homeMmr,
                awayMmr,
                Math.abs(homeMmr - awayMmr)
            ));
        }

        return responses;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String normalizeTeam(String team) {
        if (team == null || team.isBlank()) {
            return "UNKNOWN";
        }

        String normalized = team.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "HOME", "AWAY" -> normalized;
            default -> "UNKNOWN";
        };
    }

    private String normalizeWinningTeam(String winningTeam) {
        if (winningTeam == null || winningTeam.isBlank()) {
            return null;
        }

        String normalized = winningTeam.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "HOME", "AWAY" -> normalized;
            default -> null;
        };
    }

    private String normalizeStatus(Enum<?> status) {
        if (status == null) {
            return "UNKNOWN";
        }
        return status.name();
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String resolveRecordedByNickname(Match match) {
        if (match == null) {
            return null;
        }

        String recordedByEmail = safeTrim(match.getResultRecordedByEmail());
        if (recordedByEmail.isEmpty()) {
            return null;
        }

        String accessNickname = safeTrim(accessControlService.resolveAccessProfile(recordedByEmail).nickname());
        return accessNickname.isEmpty() ? null : accessNickname;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String resolveTeamRaceComposition(
        Match match,
        List<MatchParticipant> participants,
        String team
    ) {
        String storedRaceComposition = safeTrim(match == null ? null : match.getRaceComposition()).toUpperCase(Locale.ROOT);
        if (!storedRaceComposition.isEmpty()) {
            return storedRaceComposition;
        }

        List<String> assignedRaces = new ArrayList<>();
        for (MatchParticipant participant : participants) {
            if (!team.equals(normalizeTeam(participant.getTeam()))) {
                continue;
            }

            String concreteRace = resolveConcreteParticipantRace(participant);
            if (concreteRace == null) {
                return null;
            }
            assignedRaces.add(concreteRace);
        }

        if (assignedRaces.isEmpty()) {
            return null;
        }

        return RaceCompositionPolicy.canonicalize(assignedRaces);
    }

    private String resolveConcreteParticipantRace(MatchParticipant participant) {
        if (participant == null) {
            return null;
        }

        String assignedRace = safeTrim(participant.getAssignedRace());
        if (!assignedRace.isEmpty()) {
            return PlayerRacePolicy.normalizeAssignedRace(assignedRace);
        }

        String capability = safeTrim(participant.getRace());
        if (capability.isEmpty() && participant.getPlayer() != null) {
            capability = safeTrim(participant.getPlayer().getRace());
        }

        if (capability.isEmpty()) {
            return null;
        }

        String normalizedCapability = PlayerRacePolicy.normalizeCapabilityOrDefault(capability, "");
        return normalizedCapability.length() == 1 ? normalizedCapability : null;
    }
}
