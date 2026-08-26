package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.GroupRecentMatchPlayerResponse;
import com.balancify.backend.api.group.dto.GroupRecentMatchResponse;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class MatchQueryService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;
    private static final int DEFAULT_OFFSET = 0;

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
        return getRecentMatches(groupId, limit, DEFAULT_OFFSET, null);
    }

    public List<GroupRecentMatchResponse> getRecentMatches(Long groupId, Integer limit, Integer offset) {
        return getRecentMatches(groupId, limit, offset, null);
    }

    public List<GroupRecentMatchResponse> getRecentMatches(
        Long groupId,
        Integer limit,
        Integer offset,
        String requesterEmail
    ) {
        boolean requesterIsAdmin = accessControlService.isAdminEmail(requesterEmail);
        int normalizedLimit = normalizeLimit(limit);
        int normalizedOffset = normalizeOffset(offset);
        int page = normalizedOffset / normalizedLimit;
        List<Match> matches = matchRepository.findRecentByGroupId(
            groupId,
            PageRequest.of(page, normalizedLimit)
        );

        Map<Long, List<MatchParticipant>> participantsByMatchId = loadParticipantsByMatchId(matches);
        Map<String, String> nicknameByRecordedByEmail = loadRecordedByNicknamesByEmail(matches);

        List<GroupRecentMatchResponse> responses = new ArrayList<>();
        for (Match match : matches) {
            if (match.getId() == null) {
                continue;
            }

            List<MatchParticipant> participants =
                participantsByMatchId.getOrDefault(match.getId(), List.of());

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

                boolean hiddenPlayer = PlayerIdentityPolicy.isIdentityHidden(participant.getPlayer());
                String team = normalizeTeam(participant.getTeam());
                GroupRecentMatchPlayerResponse playerResponse = new GroupRecentMatchPlayerResponse(
                    hiddenPlayer ? null : participant.getPlayer().getId(),
                    hiddenPlayer ? PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL : participant.getPlayer().getNickname(),
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
                resolveRecordedByNickname(match, nicknameByRecordedByEmail),
                resolveTeamRaceComposition(match, participants, "HOME"),
                resolveTeamRaceComposition(match, participants, "AWAY"),
                homeTeam,
                awayTeam,
                homeMmr,
                awayMmr,
                Math.abs(homeMmr - awayMmr),
                requesterIsAdmin || isSameRecordedByEmail(match.getResultRecordedByEmail(), requesterEmail)
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

    private int normalizeOffset(Integer offset) {
        if (offset == null || offset <= 0) {
            return DEFAULT_OFFSET;
        }
        return offset;
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

    /**
     * Fetches all participants for the given matches in a single IN-clause query instead of one
     * query per match, then groups them back by match id. findByMatchIdInWithPlayerAndMatch orders
     * by (playedAt, match id, participant id), so within any single match id the relative order is
     * still participant id ascending — identical to the old per-match query's own ordering.
     */
    private Map<Long, List<MatchParticipant>> loadParticipantsByMatchId(List<Match> matches) {
        List<Long> matchIds = matches.stream()
            .map(Match::getId)
            .filter(Objects::nonNull)
            .toList();
        if (matchIds.isEmpty()) {
            return Map.of();
        }

        return matchParticipantRepository.findByMatchIdInWithPlayerAndMatch(matchIds)
            .stream()
            .collect(Collectors.groupingBy(
                participant -> participant.getMatch().getId(),
                LinkedHashMap::new,
                Collectors.toList()
            ));
    }

    /**
     * Resolves the recorder nickname for each distinct result_recorded_by_email across the given
     * matches in one pass, instead of re-querying AccessControlService (which itself issues several
     * lookups per email) once per match.
     */
    private Map<String, String> loadRecordedByNicknamesByEmail(List<Match> matches) {
        Set<String> recordedByEmails = new LinkedHashSet<>();
        for (Match match : matches) {
            String email = safeTrim(match.getResultRecordedByEmail()).toLowerCase(Locale.ROOT);
            if (!email.isEmpty()) {
                recordedByEmails.add(email);
            }
        }

        Map<String, String> nicknameByEmail = new LinkedHashMap<>();
        for (String email : recordedByEmails) {
            String nickname = safeTrim(accessControlService.resolveAccessProfile(email).nickname());
            if (!nickname.isEmpty()) {
                nicknameByEmail.put(email, nickname);
            }
        }
        return nicknameByEmail;
    }

    private String resolveRecordedByNickname(Match match, Map<String, String> nicknameByRecordedByEmail) {
        if (match == null) {
            return null;
        }

        String recordedByEmail = safeTrim(match.getResultRecordedByEmail()).toLowerCase(Locale.ROOT);
        if (recordedByEmail.isEmpty()) {
            return null;
        }

        return nicknameByRecordedByEmail.get(recordedByEmail);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isSameRecordedByEmail(String storedEmail, String requesterEmail) {
        String normalizedStored = safeTrim(storedEmail).toLowerCase(Locale.ROOT);
        String normalizedRequester = safeTrim(requesterEmail).toLowerCase(Locale.ROOT);
        return !normalizedStored.isEmpty() && normalizedStored.equals(normalizedRequester);
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
        return null;
    }
}
