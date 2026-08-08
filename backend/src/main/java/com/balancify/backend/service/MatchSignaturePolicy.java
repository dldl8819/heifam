package com.balancify.backend.service;

import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchParticipant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

final class MatchSignaturePolicy {

    private static final String TEAM_HOME = "HOME";
    private static final String TEAM_AWAY = "AWAY";

    private MatchSignaturePolicy() {
    }

    static Signature fromPlayerIds(List<Long> homePlayerIds, List<Long> awayPlayerIds) {
        List<Long> participantIds = new ArrayList<>(homePlayerIds.size() + awayPlayerIds.size());
        participantIds.addAll(homePlayerIds);
        participantIds.addAll(awayPlayerIds);
        return new Signature(
            participantSignature(participantIds),
            teamSignature(homePlayerIds, awayPlayerIds)
        );
    }

    static Signature fromStored(Match match) {
        if (match == null || match.getParticipantSignature() == null || match.getTeamSignature() == null) {
            return null;
        }
        String participantSignature = match.getParticipantSignature().trim();
        String teamSignature = canonicalizeStoredTeamSignature(match.getTeamSignature());
        if (participantSignature.isEmpty() || teamSignature == null) {
            return null;
        }
        return new Signature(participantSignature, teamSignature);
    }

    static Signature fromParticipants(List<MatchParticipant> participants) {
        if (participants == null || participants.isEmpty() || participants.size() % 2 != 0) {
            return null;
        }

        List<Long> homePlayerIds = new ArrayList<>();
        List<Long> awayPlayerIds = new ArrayList<>();
        for (MatchParticipant participant : participants) {
            if (participant == null || participant.getPlayer() == null || participant.getPlayer().getId() == null) {
                return null;
            }
            if (TEAM_HOME.equals(participant.getTeam())) {
                homePlayerIds.add(participant.getPlayer().getId());
            } else if (TEAM_AWAY.equals(participant.getTeam())) {
                awayPlayerIds.add(participant.getPlayer().getId());
            } else {
                return null;
            }
        }

        int inferredTeamSize = participants.size() / 2;
        if (homePlayerIds.size() != inferredTeamSize || awayPlayerIds.size() != inferredTeamSize) {
            return null;
        }
        return fromPlayerIds(homePlayerIds, awayPlayerIds);
    }

    private static String participantSignature(List<Long> playerIds) {
        return playerIds.stream()
            .sorted()
            .map(String::valueOf)
            .collect(Collectors.joining("-"));
    }

    private static String teamSignature(List<Long> homePlayerIds, List<Long> awayPlayerIds) {
        return canonicalTeamPairSignature(
            participantSignature(homePlayerIds),
            participantSignature(awayPlayerIds)
        );
    }

    private static String canonicalizeStoredTeamSignature(String value) {
        String normalized = value == null ? "" : value.trim();
        String[] teams = normalized.split("\\|", -1);
        if (teams.length != 2) {
            return null;
        }
        String firstTeam = rosterPart(teams[0]);
        String secondTeam = rosterPart(teams[1]);
        if (firstTeam == null || secondTeam == null) {
            return null;
        }
        return canonicalTeamPairSignature(firstTeam, secondTeam);
    }

    private static String rosterPart(String teamSignaturePart) {
        int separatorIndex = teamSignaturePart == null ? -1 : teamSignaturePart.indexOf(':');
        if (separatorIndex < 0 || separatorIndex == teamSignaturePart.length() - 1) {
            return null;
        }
        return teamSignaturePart.substring(separatorIndex + 1).trim();
    }

    private static String canonicalTeamPairSignature(String firstTeam, String secondTeam) {
        if (firstTeam.compareTo(secondTeam) <= 0) {
            return "TEAM1:" + firstTeam + "|TEAM2:" + secondTeam;
        }
        return "TEAM1:" + secondTeam + "|TEAM2:" + firstTeam;
    }

    record Signature(
        String participantSignature,
        String teamSignature
    ) {
    }
}
