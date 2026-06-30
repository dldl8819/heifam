package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.PlayerRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonthlyTierRefreshServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Test
    void refreshesTierFromMonthEndMmrAtKstMonthEndSettlementTime() {
        OffsetDateTime now = OffsetDateTime.parse("2026-04-30T14:59:59Z");
        MonthlyTierRefreshService service = service(now);
        Player player = player(1L, "S", 890);

        when(playerRepository.findAll()).thenReturn(List.of(player));

        service.applyMonthlyTierRefreshIfDue();

        assertThat(player.getTier()).isEqualTo("B-");
        assertThat(player.getLastTierRecalculatedAt()).isEqualTo(now);
        assertThat(player.getLastTierSnapshotAt())
            .isEqualTo(OffsetDateTime.parse("2026-04-30T23:59:59+09:00"));
        assertThat(player.getLastTierSnapshotMmr()).isEqualTo(890);
        verify(playerRepository).saveAll(List.of(player));
    }

    @Test
    void refreshesTierToDFromMonthEndMmr() {
        OffsetDateTime now = OffsetDateTime.parse("2026-04-30T14:59:59Z");
        MonthlyTierRefreshService service = service(now);
        Player player = player(1L, "C-", 150);

        when(playerRepository.findAll()).thenReturn(List.of(player));

        service.applyMonthlyTierRefreshIfDue();

        assertThat(player.getTier()).isEqualTo("D");
        assertThat(player.getLastTierSnapshotMmr()).isEqualTo(150);
        verify(playerRepository).saveAll(List.of(player));
    }

    @Test
    void doesNotRefreshTierBeforeKstMonthEndSettlementTime() {
        MonthlyTierRefreshService service = service(OffsetDateTime.parse("2026-04-30T14:59:58Z"));
        Player player = player(1L, "S", 890);

        service.applyMonthlyTierRefreshIfDue();

        assertThat(player.getTier()).isEqualTo("S");
        verify(playerRepository, never()).findAll();
        verify(playerRepository, never()).saveAll(anyList());
    }

    @Test
    void doesNotRefreshTierOnFirstDayOfKstMonth() {
        MonthlyTierRefreshService service = service(OffsetDateTime.parse("2026-04-30T15:00:00Z"));
        Player player = player(1L, "S", 890);

        service.applyMonthlyTierRefreshIfDue();

        assertThat(player.getTier()).isEqualTo("S");
        verify(playerRepository, never()).findAll();
        verify(playerRepository, never()).saveAll(anyList());
    }

    @Test
    void refreshesOnlyOncePerKstMonth() {
        OffsetDateTime now = OffsetDateTime.parse("2026-04-30T14:59:59Z");
        MonthlyTierRefreshService service = service(now);
        Player player = player(1L, "A", 930);
        player.setLastTierRecalculatedAt(now);

        when(playerRepository.findAll()).thenReturn(List.of(player));

        service.applyMonthlyTierRefreshIfDue();

        assertThat(player.getTier()).isEqualTo("A");
        verify(playerRepository, never()).saveAll(anyList());
    }

    private MonthlyTierRefreshService service(OffsetDateTime now) {
        return new MonthlyTierRefreshService(
            playerRepository,
            true,
            "Asia/Seoul",
            new GroupReadCacheService(0),
            Clock.fixed(now.toInstant(), ZoneOffset.UTC)
        );
    }

    private Player player(Long id, String tier, int mmr) {
        Group group = new Group();
        group.setId(1L);

        Player player = new Player();
        player.setId(id);
        player.setGroup(group);
        player.setNickname("로보");
        player.setRace("P");
        player.setTier(tier);
        player.setMmr(mmr);
        return player;
    }
}
