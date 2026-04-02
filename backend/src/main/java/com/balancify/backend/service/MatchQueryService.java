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

    public MatchQueryService(
        MatchRepository matchRepository,
        MatchParticipantRepository matchParticipantRepository
    ) {
        this.matchRepository = matchRepository;
        this.matchParticipantRepository = matchParticipantRepository;
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
                match.getResultRecordedByNickname(),
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
}
