package com.balancify.backend.service;

import com.balancify.backend.repository.MatchRepository;
import java.time.Clock;

public class UnplayedMatchCleanupService {

    UnplayedMatchCleanupService(
        MatchRepository matchRepository,
        MatchResultService matchResultService,
        OperationAuditLogService operationAuditLogService,
        long afterHours,
        Clock clock
    ) {
    }

    public int cancelMatchesLeftWithoutResult() {
        return 0;
    }
}
