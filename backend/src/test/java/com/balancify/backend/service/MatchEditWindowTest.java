package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class MatchEditWindowTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-11T12:00:00+09:00");
    private final MatchEditWindow window =
        new MatchEditWindow(Clock.fixed(Instant.from(NOW), ZoneOffset.UTC));

    @Test
    void staysOpenFor48HoursAfterTheResultWentIn() {
        assertThat(window.isOpen(decided(NOW.minusHours(1), null))).isTrue();
        assertThat(window.isOpen(decided(NOW.minusHours(47).minusMinutes(59), null))).isTrue();
    }

    @Test
    void closesAt48HoursAfterTheResultWentIn() {
        assertThat(window.isOpen(decided(NOW.minusHours(48), null))).isFalse();
        assertThat(window.isOpen(decided(NOW.minusDays(5), null))).isFalse();
    }

    @Test
    void countsFromTheResultNotFromWhenTheMatchWasSetUp() {
        // Set up long ago, decided an hour ago: still open.
        assertThat(window.isOpen(decided(NOW.minusHours(1), NOW.minusDays(10)))).isTrue();
    }

    @Test
    void fallsBackToWhenItWasPlayedForAResultWithoutItsTime() {
        assertThat(window.isOpen(decided(null, NOW.minusHours(10)))).isTrue();
        assertThat(window.isOpen(decided(null, NOW.minusHours(49)))).isFalse();
    }

    @Test
    void keepsAResultWithNoTimeAtAllForSuperAdmins() {
        assertThat(window.isOpen(decided(null, null))).isFalse();
    }

    @Test
    void leavesAMatchWithoutAResultOpen() {
        Match match = new Match();
        match.setStatus(MatchStatus.CONFIRMED);
        match.setPlayedAt(NOW.minusDays(10));
        assertThat(window.isOpen(match)).isTrue();
        assertThat(window.isOpen(null)).isTrue();
    }

    @Test
    void treatsACompletedMatchAsDecidedEvenWithoutAWinnerOnRecord() {
        Match match = new Match();
        match.setStatus(MatchStatus.COMPLETED);
        match.setResultRecordedAt(NOW.minusHours(72));
        assertThat(window.isOpen(match)).isFalse();
    }

    private static Match decided(OffsetDateTime resultRecordedAt, OffsetDateTime playedAt) {
        Match match = new Match();
        match.setStatus(MatchStatus.COMPLETED);
        match.setWinningTeam("HOME");
        match.setResultRecordedAt(resultRecordedAt);
        match.setPlayedAt(playedAt);
        return match;
    }
}
