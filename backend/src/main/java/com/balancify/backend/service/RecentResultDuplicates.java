package com.balancify.backend.service;

import com.balancify.backend.domain.Match;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchRepository;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Finds a result entered moments ago for the same two teams. A match is often set up well before
 * it is played, so "was the same match created in the last minutes" misses the usual double entry:
 * one person enters the result on the match that was set up, and a minute later another enters
 * the same game as a new match. What repeats within minutes is the result, so that is what is
 * looked for, whatever race composition either entry names.
 */
final class RecentResultDuplicates {

    private RecentResultDuplicates() {
    }

    static Optional<Match> find(
        MatchRepository matchRepository,
        MatchParticipantRepository matchParticipantRepository,
        Long groupId,
        int teamSize,
        MatchSignaturePolicy.Signature signature,
        long windowMinutes
    ) {
        if (groupId == null || signature == null) {
            return Optional.empty();
        }
        for (Match candidate : matchRepository.findRecentResultsOfSamePlayers(
            groupId,
            teamSize,
            signature.participantSignature(),
            OffsetDateTime.now().minusMinutes(windowMinutes)
        )) {
            MatchSignaturePolicy.Signature candidateSignature = MatchSignaturePolicy.fromStored(candidate);
            if (candidateSignature == null) {
                candidateSignature = MatchSignaturePolicy.fromParticipants(
                    matchParticipantRepository.findByMatchIdWithPlayerAndMatch(candidate.getId())
                );
            }
            // The same players split into other teams are another game.
            if (signature.equals(candidateSignature)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    static String conflictMessage(long windowMinutes) {
        return "같은 팀의 경기 결과가 최근 " + windowMinutes + "분 안에 이미 입력되었습니다. "
            + "중복 입력이 아닌지 경기 결과에서 확인해 주세요. 다른 경기라면 잠시 뒤 다시 입력해 주세요.";
    }
}
