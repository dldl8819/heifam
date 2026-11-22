package com.balancify.backend.service;

import com.balancify.backend.repository.MatchRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Calls off matches still waiting for a result a day after they were set up.
 *
 * <p>A match set up and then not played, or played and entered again as a new match, otherwise
 * stays waiting for good: after six hours no screen lists it (the predictions board looks back six
 * hours, the results only show results), so nobody can call it off by hand. Each one goes the way a
 * match called off on the predictions board goes (MatchResultService.deleteMatch: its predictions
 * and players taken back with it), one transaction each, and the audit log names the clean-up as
 * whoever did it. Series games are left to their series.
 */
@Service
public class UnplayedMatchCleanupService {

    private static final Logger log = LoggerFactory.getLogger(UnplayedMatchCleanupService.class);

    // Enough for the matches of a busy day many times over; anything left goes on the next run.
    static final int BATCH_SIZE = 200;

    private final MatchRepository matchRepository;
    private final MatchResultService matchResultService;
    private final OperationAuditLogService operationAuditLogService;
    private final long afterHours;
    private final Clock clock;

    @Autowired
    public UnplayedMatchCleanupService(
        MatchRepository matchRepository,
        MatchResultService matchResultService,
        OperationAuditLogService operationAuditLogService,
        @Value("${balancify.match.unplayed-cleanup.after-hours:24}") long afterHours
    ) {
        this(matchRepository, matchResultService, operationAuditLogService, afterHours, Clock.systemUTC());
    }

    UnplayedMatchCleanupService(
        MatchRepository matchRepository,
        MatchResultService matchResultService,
        OperationAuditLogService operationAuditLogService,
        long afterHours,
        Clock clock
    ) {
        this.matchRepository = matchRepository;
        this.matchResultService = matchResultService;
        this.operationAuditLogService = operationAuditLogService;
        this.afterHours = Math.max(1L, afterHours);
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    // Hourly, so a match goes between 24 and 25 hours after it was set up; the first run a minute
    // after start-up also clears whatever piled up while the server was down or before this existed.
    @Scheduled(
        initialDelayString = "${balancify.match.unplayed-cleanup.initial-delay-ms:60000}",
        fixedDelayString = "${balancify.match.unplayed-cleanup.fixed-delay-ms:3600000}"
    )
    public int cancelMatchesLeftWithoutResult() {
        OffsetDateTime setUpBefore = OffsetDateTime.now(clock).minusHours(afterHours);
        List<Long> matchIds = matchRepository.findUnplayedMatchIdsSetUpBefore(
            setUpBefore,
            PageRequest.of(0, BATCH_SIZE)
        );
        int cancelled = 0;
        for (Long matchId : matchIds) {
            try {
                Optional<MatchResultService.DeletedMatchAuditSnapshot> snapshot =
                    matchResultService.cancelUnplayedMatchSetUpBefore(matchId, setUpBefore);
                if (snapshot.isPresent()) {
                    operationAuditLogService.recordAutomaticMatchCancellation(snapshot.get(), afterHours);
                    cancelled++;
                }
            } catch (RuntimeException exception) {
                // One match that cannot go now (a lock, a lost connection) waits for the next run.
                log.warn("Could not call off match {} left without a result: {}", matchId, exception.toString());
            }
        }
        if (cancelled > 0) {
            log.info("Called off {} match(es) left without a result for {} hours", cancelled, afterHours);
        }
        return cancelled;
    }
}
