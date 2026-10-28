package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.PlayerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class GroupRosterCacheTest {

    private final PlayerRepository playerRepository = mock(PlayerRepository.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-28T12:00:00Z"));
    private final GroupRosterCache cache = new GroupRosterCache(playerRepository, clock);

    @Test
    void readsAGroupOnceUntilAPlayerChanges() {
        Player before = player(1L, "Alpha");
        Player after = player(1L, "Alpha2");
        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L)).thenReturn(List.of(before), List.of(after));
        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(2L)).thenReturn(List.of());

        assertThat(cache.roster(1L)).containsExactly(before);
        assertThat(cache.roster(1L)).containsExactly(before);
        assertThat(cache.roster(2L)).isEmpty();
        verify(playerRepository, times(1)).findByGroup_IdOrderByMmrDescIdAsc(1L);

        cache.changed();

        assertThat(cache.roster(1L)).containsExactly(after);
        verify(playerRepository, times(2)).findByGroup_IdOrderByMmrDescIdAsc(1L);
        // Every group's copy goes with a change, not just the changed player's.
        cache.roster(2L);
        verify(playerRepository, times(2)).findByGroup_IdOrderByMmrDescIdAsc(2L);
    }

    @Test
    void readsAfreshAfterMaxAgeWhateverChangedOutsideTheBackend() {
        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L)).thenReturn(List.of());

        cache.roster(1L);
        clock.advanceSeconds(GroupRosterCache.MAX_AGE.toSeconds() - 1);
        cache.roster(1L);
        verify(playerRepository, times(1)).findByGroup_IdOrderByMmrDescIdAsc(1L);

        clock.advanceSeconds(1);
        cache.roster(1L);
        verify(playerRepository, times(2)).findByGroup_IdOrderByMmrDescIdAsc(1L);
    }

    @Test
    void handsOutButDoesNotKeepACopyReadWhileAChangeLanded() {
        Player stale = player(1L, "Alpha");
        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L)).thenAnswer(invocation -> {
            // A change commits while the roster is being read.
            cache.changed();
            return List.of(stale);
        });

        assertThat(cache.roster(1L)).containsExactly(stale);
        cache.roster(1L);

        verify(playerRepository, times(2)).findByGroup_IdOrderByMmrDescIdAsc(1L);
    }

    private static Player player(Long id, String nickname) {
        Player player = new Player();
        player.setId(id);
        player.setNickname(nickname);
        return player;
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
