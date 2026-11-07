package com.balancify.backend.security;

import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * How many answers visitors get a minute, all of them together. A visitor (anyone without member
 * access: not signed in, or signed in with an account the site does not let in) reads two things:
 * the recent results and the notice titles. Both are kept in memory, so the database feels little,
 * but nothing else bounds how often they are asked for, through the site or straight at the
 * backend. Past the limit a visitor is told to wait (429) until the minute is over. Members are
 * never counted.
 */
@Component
public class VisitorReadLimiter {

    static final long WINDOW_MS = 60_000L;
    private static final Logger LOGGER = LoggerFactory.getLogger(VisitorReadLimiter.class);

    private final int perMinute;
    private final LongSupplier clock;
    private final Object lock = new Object();
    private long windowStart;
    private int used;
    private boolean limitLogged;

    @Autowired
    public VisitorReadLimiter(@Value("${balancify.visitor-reads.per-minute:120}") int perMinute) {
        this(perMinute, System::currentTimeMillis);
    }

    VisitorReadLimiter(int perMinute, LongSupplier clock) {
        this.perMinute = Math.max(1, perMinute);
        this.clock = clock;
        this.windowStart = clock.getAsLong();
    }

    /** Counts one visitor answer, or refuses it with 429 when this minute's are used up. */
    public void requireAllowance() {
        if (!tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many visitor requests; try again in a minute");
        }
    }

    boolean tryAcquire() {
        long now = clock.getAsLong();
        synchronized (lock) {
            if (now - windowStart >= WINDOW_MS || now < windowStart) {
                windowStart = now;
                used = 0;
                limitLogged = false;
            }
            if (used >= perMinute) {
                if (!limitLogged) {
                    limitLogged = true;
                    LOGGER.warn("Visitors used this minute's {} answers; the rest wait for the next minute", perMinute);
                }
                return false;
            }
            used++;
            return true;
        }
    }
}
