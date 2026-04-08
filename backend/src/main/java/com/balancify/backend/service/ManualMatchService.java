package com.balancify.backend.service;

import com.balancify.backend.api.match.dto.ManualMatchCreateRequest;
import com.balancify.backend.api.match.dto.MatchResultRequest;
import com.balancify.backend.api.match.dto.MatchResultResponse;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManualMatchService {

    private final GroupMatchAdminService groupMatchAdminService;
    private final MatchResultService matchResultService;

    public ManualMatchService(
        GroupMatchAdminService groupMatchAdminService,
        MatchResultService matchResultService
    ) {
        this.groupMatchAdminService = groupMatchAdminService;
        this.matchResultService = matchResultService;
    }

    @Transactional
    public MatchResultResponse createManualMatch(
        ManualMatchCreateRequest request,
        String recordedByEmail,
        String recordedByNickname
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }
        if (request.groupId() == null || request.groupId() <= 0) {
            throw new IllegalArgumentException("groupId is required");
        }
        if (request.teamSize() == null) {
            throw new IllegalArgumentException("teamSize is required");
        }
        if (request.winnerTeam() == null || request.winnerTeam().isBlank()) {
            throw new IllegalArgumentException("winnerTeam must be HOME or AWAY");
        }
        if (request.raceComposition() == null || request.raceComposition().isBlank()) {
            throw new IllegalArgumentException("종족 조합을 선택해 주세요.");
        }

        Match match = groupMatchAdminService.createConfirmedMatch(
            request.groupId(),
            request.homePlayerIds(),
            request.awayPlayerIds(),
            request.teamSize(),
            MatchSource.MANUAL,
            request.note(),
            request.raceComposition(),
            false
        );

        return matchResultService.processMatchResult(
            match.getId(),
            new MatchResultRequest(request.winnerTeam()),
            recordedByEmail,
            recordedByNickname,
            false
        );
    }
}
