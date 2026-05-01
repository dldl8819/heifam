package com.balancify.backend.service;

import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.PlayerTierPolicy;
import com.balancify.backend.repository.PlayerRepository;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonthlyTierRefreshService {

    private static final String DEFAULT_SETTLEMENT_ZONE_ID = "Asia/Seoul";

    private final PlayerRepository playerRepository;
    private final boolean enabled;
    private final ZoneId settlementZone;
    private final Clock clock;

    @Autowired
    public MonthlyTierRefreshService(
        PlayerRepository playerRepository,
        @Value("${balancify.rank.monthly-tier-refresh.enabled:true}") boolean enabled,
        @Value("${balancify.rank.monthly-tier-refresh.zone:" + DEFAULT_SETTLEMENT_ZONE_ID + "}") String settlementZoneId
    ) {
        this(playerRepository, enabled, settlementZoneId, Clock.systemUTC());
    }

    MonthlyTierRefreshService(
        PlayerRepository playerRepository,
        boolean enabled,
        String settlementZoneId,
        Clock clock
    ) {
        this.playerRepository = playerRepository;
        this.enabled = enabled;
        this.settlementZone = resolveSettlementZone(settlementZoneId);
        this.clock = clock;
    }

    @Scheduled(
        cron = "${balancify.rank.monthly-tier-refresh.cron:0 1 0 1 * *}",
        zone = "${balancify.rank.monthly-tier-refresh.zone:" + DEFAULT_SETTLEMENT_ZONE_ID + "}"
    )
    @Transactional
    public void refreshTiersOnMonthlySchedule() {
        applyMonthlyTierRefreshIfDue();
    }

    @Transactional
    public void applyMonthlyTierRefreshIfDue() {
        if (!enabled) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        ZonedDateTime nowInSettlementZone = now.atZoneSameInstant(settlementZone);
        if (nowInSettlementZone.getDayOfMonth() != 1) {
            return;
        }

        YearMonth effectiveMonth = YearMonth.from(nowInSettlementZone);
        OffsetDateTime snapshotAt = effectiveMonth.atDay(1)
            .atStartOfDay(settlementZone)
            .minusSeconds(1)
            .toOffsetDateTime();
        List<Player> playersToSave = new ArrayList<>();
        for (Player player : playerRepository.findAll()) {
            if (wasAlreadyRefreshedForEffectiveMonth(player, effectiveMonth)) {
                continue;
            }

            int snapshotMmr = safeInt(player.getMmr());
            String nextTier = PlayerTierPolicy.resolveTier(snapshotMmr);
            if (!nextTier.equals(player.getTier())) {
                player.setTier(nextTier);
            }
            player.setLastTierRecalculatedAt(now);
            player.setLastTierSnapshotAt(snapshotAt);
            player.setLastTierSnapshotMmr(snapshotMmr);
            playersToSave.add(player);
        }

        if (!playersToSave.isEmpty()) {
            playerRepository.saveAll(playersToSave);
        }
    }

    private boolean wasAlreadyRefreshedForEffectiveMonth(Player player, YearMonth effectiveMonth) {
        OffsetDateTime lastRecalculatedAt = player.getLastTierRecalculatedAt();
        return lastRecalculatedAt != null
            && YearMonth.from(lastRecalculatedAt.atZoneSameInstant(settlementZone)).equals(effectiveMonth);
    }

    private ZoneId resolveSettlementZone(String settlementZoneId) {
        if (settlementZoneId == null || settlementZoneId.isBlank()) {
            return ZoneId.of(DEFAULT_SETTLEMENT_ZONE_ID);
        }
        try {
            return ZoneId.of(settlementZoneId.trim());
        } catch (DateTimeException ignored) {
            return ZoneId.of(DEFAULT_SETTLEMENT_ZONE_ID);
        }
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
