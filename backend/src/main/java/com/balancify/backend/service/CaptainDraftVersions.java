package com.balancify.backend.service;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Whether a reader's copy of a regular draft is still current. The draft page asks for its draft
 * every few seconds while it is open, and a draft is read whole: every participant with their
 * player, every set with its players. Most asks find nothing changed, so a copy is tagged (an
 * ETag), and an ask that brings back the current tag is answered "not modified" without reading the
 * draft again. What the database sends out is what Supabase meters (egress).
 *
 * Every change made through CaptainDraftService moves its draft's version on, before it commits and
 * again after, so no ask in between keeps a copy from before the change. A draft also shows its
 * players (a nickname, a linked account, a withdrawal), so a tag holds GroupRosterCache's count of
 * changes to players too. Only a change made outside the backend (by hand in the database) is not
 * followed, so a copy is trusted for MAX_AGE at most and then read afresh. Versions live in this
 * process only: a tag from before a restart never matches.
 */
@Component
public class CaptainDraftVersions {
    static final Duration MAX_AGE = Duration.ofMinutes(5);

    private final String bootId = Long.toString(ThreadLocalRandom.current().nextLong() & Long.MAX_VALUE, 36);
    private final ConcurrentMap<Long, AtomicLong> versions = new ConcurrentHashMap<>();
    private final LongSupplier playerChanges;
    private final Clock clock;

    @Autowired
    public CaptainDraftVersions(GroupRosterCache groupRosterCache) {
        this(groupRosterCache::generation, Clock.systemUTC());
    }

    CaptainDraftVersions(LongSupplier playerChanges, Clock clock) {
        this.playerChanges = playerChanges;
        this.clock = clock;
    }

    /** The tag for this reader's copy of the draft as it is now. */
    public String tag(Long draftId, UUID viewer) {
        return "\"" + bootId + "-" + draftId + "-" + version(draftId) + "-" + playerChanges.getAsLong() + "-"
            + viewerKey(viewer) + "-" + clock.instant().getEpochSecond() + "\"";
    }

    /**
     * Whether If-None-Match brings back a tag of this draft as it is now, given to this reader no
     * longer than MAX_AGE ago.
     */
    public boolean isCurrent(Long draftId, UUID viewer, String ifNoneMatch) {
        if (draftId == null || ifNoneMatch == null) {
            return false;
        }
        String value = ifNoneMatch.trim();
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        if (value.length() < 2 || !value.startsWith("\"") || !value.endsWith("\"")) {
            return false;
        }
        String[] parts = value.substring(1, value.length() - 1).split("-");
        if (parts.length != 6) {
            return false;
        }
        try {
            long age = clock.instant().getEpochSecond() - Long.parseLong(parts[5]);
            return parts[0].equals(bootId)
                && parts[1].equals(draftId.toString())
                && Long.parseLong(parts[2]) == version(draftId)
                && Long.parseLong(parts[3]) == playerChanges.getAsLong()
                && parts[4].equals(viewerKey(viewer))
                && age >= 0
                && age < MAX_AGE.toSeconds();
        } catch (NumberFormatException notOurTag) {
            return false;
        }
    }

    /** Moves the draft's version on: no tag given out before matches any more. */
    public void changed(Long draftId) {
        if (draftId != null) {
            versions.computeIfAbsent(draftId, id -> new AtomicLong()).incrementAndGet();
        }
    }

    private long version(Long draftId) {
        AtomicLong version = versions.get(draftId);
        return version == null ? 0L : version.get();
    }

    // A copy is one reader's: what a draft shows depends on who reads it (a team's line-up).
    private static String viewerKey(UUID viewer) {
        return viewer == null ? "none" : viewer.toString().replace("-", "");
    }
}
