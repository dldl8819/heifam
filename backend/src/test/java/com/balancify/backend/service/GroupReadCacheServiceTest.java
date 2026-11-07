package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GroupReadCacheServiceTest {

    @Test
    void keepsAValueForTheTimeAskedAndDropsItWithItsGroup() {
        GroupReadCacheService cache = new GroupReadCacheService(30_000);
        AtomicInteger loads = new AtomicInteger();

        cache.get("visitor:group:1:", 300_000, loads::incrementAndGet);
        cache.get("visitor:group:1:", 300_000, loads::incrementAndGet);
        assertThat(loads).hasValue(1);

        cache.evictGroup(1L);
        cache.get("visitor:group:1:", 300_000, loads::incrementAndGet);
        assertThat(loads).hasValue(2);
    }

    @Test
    void keepsNothingWhileSwitchedOff() {
        GroupReadCacheService cache = new GroupReadCacheService(0);
        AtomicInteger loads = new AtomicInteger();

        cache.get("visitor:group:1:", 300_000, loads::incrementAndGet);
        cache.get("visitor:group:1:", 300_000, loads::incrementAndGet);

        assertThat(loads).hasValue(2);
    }

    @Test
    void dropsEntriesRunOutOnceThereAreMany() {
        GroupReadCacheService cache = new GroupReadCacheService(30_000);
        cache.get("kept:group:1:", 300_000, () -> "kept");
        // Group numbers nobody has, each kept for no time at all.
        for (int group = 2; group <= GroupReadCacheService.SWEEP_ABOVE + 1; group++) {
            cache.get("visitor:group:" + group + ":", 0, () -> "gone");
        }

        assertThat(cache.size()).isLessThanOrEqualTo(2);
        assertThat(cache.<String>get("kept:group:1:", 300_000, () -> "read again")).isEqualTo("kept");
    }
}
