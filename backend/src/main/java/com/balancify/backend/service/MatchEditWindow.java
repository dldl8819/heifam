package com.balancify.backend.service;

import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * How long a match's result may still be changed or the match deleted: 48 hours from when its
 * result first went in (the time the results list shows; editing a result does not move it), or
 * from when it was played for a result kept without that time. The user's rule (2026-11-11):
 * admins, members granted result editing and a member fixing their own match's races all stop
 * there; only super admins may change or delete a result after it. A match without a result has
 * nothing to keep, so it is always open.
 */
@Component
public class MatchEditWindow {

    public static final Duration WINDOW = Duration.ofHours(48);
    public static final String CLOSED_MESSAGE = "결과 입력 후 48시간이 지난 경기는 최상위 관리자만 수정하거나 삭제할 수 있습니다.";

    private final Clock clock;

    @Autowired
    public MatchEditWindow() {
        this(Clock.systemUTC());
    }

    MatchEditWindow(Clock clock) {
        this.clock = clock;
    }

    /** Whether anyone but a super admin may still change this match's result or delete it. */
    public boolean isOpen(Match match) {
        if (match == null || !hasResult(match)) {
            return true;
        }
        OffsetDateTime since = match.getResultRecordedAt() != null ? match.getResultRecordedAt() : match.getPlayedAt();
        // A result with no time at all is old: keep it for super admins.
        return since != null && clock.instant().isBefore(since.plus(WINDOW).toInstant());
    }

    private boolean hasResult(Match match) {
        String winner = match.getWinningTeam();
        return (winner != null && !winner.isBlank()) || match.getStatus() == MatchStatus.COMPLETED;
    }
}
