package com.balancify.backend.service;

import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.PlayerTierPolicy;
import com.balancify.backend.repository.PlayerRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonthlyTierRefreshService {

    private final PlayerRepository playerRepository;
    private final boolean enabled;
    private final Clock clock;

    @Autowired
    public MonthlyTierRefreshService(
        PlayerRepository playerRepository,
        @Value("${balancify.rank.monthly-tier-refresh.enabled:true}") boolean enabled
    ) {
        this(playerRepository, enabled, Clock.systemUTC());
    }

    MonthlyTierRefreshService(
        PlayerRepository playerRepository,
        boolean enabled,
        Clock clock
    ) {
        this.playerRepository = playerRepository;
        this.enabled = enabled;
        this.clock = clock;
    }

    @Scheduled(cron = "${balancify.rank.monthly-tier-refresh.cron:0 10 4 1 * *}")
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
        if (now.getDayOfMonth() != 1) {
            return;
        }

        YearMonth currentMonth = YearMonth.from(now);
        List<Player> playersToSave = new ArrayList<>();
        for (Player player : playerRepository.findAll()) {
            if (player.getLastTierRecalculatedAt() != null
                && YearMonth.from(player.getLastTierRecalculatedAt()).equals(currentMonth)) {
                continue;
            }

            String nextTier = PlayerTierPolicy.resolveTier(player.getMmr());
            if (!nextTier.equals(player.getTier())) {
                player.setTier(nextTier);
            }
            player.setLastTierRecalculatedAt(now);
            playersToSave.add(player);
        }

        if (!playersToSave.isEmpty()) {
            playerRepository.saveAll(playersToSave);
        }
    }
}
