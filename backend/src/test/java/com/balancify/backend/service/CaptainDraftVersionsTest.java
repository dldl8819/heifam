package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CaptainDraftVersionsTest {
    private static final UUID READER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_READER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-27T12:00:00Z"));
    private final CaptainDraftVersions versions = new CaptainDraftVersions(clock);

    @Test
    void keepsACopyCurrentForItsReaderUntilTheDraftChanges() {
        String tag = versions.tag(100L, READER);

        assertThat(versions.isCurrent(100L, READER, tag)).isTrue();
        // As browsers may send it back.
        assertThat(versions.isCurrent(100L, READER, "W/" + tag)).isTrue();
        // Another reader may be shown other things (a team's line-up), and another draft is another copy.
        assertThat(versions.isCurrent(100L, OTHER_READER, tag)).isFalse();
        assertThat(versions.isCurrent(101L, READER, tag)).isFalse();

        versions.changed(100L);

        assertThat(versions.isCurrent(100L, READER, tag)).isFalse();
        assertThat(versions.isCurrent(100L, READER, versions.tag(100L, READER))).isTrue();
    }

    @Test
    void readsAfreshAfterMaxAgeWhateverChangedElsewhere() {
        String tag = versions.tag(100L, null);

        clock.advanceSeconds(CaptainDraftVersions.MAX_AGE.toSeconds() - 1);
        assertThat(versions.isCurrent(100L, null, tag)).isTrue();

        // Changes made elsewhere (a player's tier) show within MAX_AGE.
        clock.advanceSeconds(1);
        assertThat(versions.isCurrent(100L, null, tag)).isFalse();
    }

    @Test
    void neverTrustsATagFromAnotherProcessOrNotItsOwn() {
        String tag = versions.tag(100L, null);
        CaptainDraftVersions afterRestart = new CaptainDraftVersions(clock);

        assertThat(afterRestart.isCurrent(100L, null, tag)).isFalse();
        assertThat(versions.isCurrent(100L, null, null)).isFalse();
        assertThat(versions.isCurrent(100L, null, "*")).isFalse();
        assertThat(versions.isCurrent(100L, null, "\"a-b-c\"")).isFalse();
        assertThat(versions.isCurrent(100L, null, tag.replace("-0-", "-x-"))).isFalse();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
