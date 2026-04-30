package com.balancify.backend.service;

import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.GroupRepository;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.PlayerRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DormancyMmrDecayService {

    private final PlayerRepository playerRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final GroupRepository groupRepository;
    private final boolean enabled;
    private final int inactiveDays;
    private final int mmrDropPerPeriod;
    private final int returnBoostGames;
    private final double returnBoostMultiplier;
    private final Clock clock;

    public DormancyMmrDecayService(
        PlayerRepository playerRepository,
        MatchParticipantRepository matchParticipantRepository,
        GroupRepository groupRepository,
        @Value("${balancify.rank.dormancy.enabled:true}") boolean enabled,
        @Value("${balancify.rank.dormancy.inactive-days:30}") int inactiveDays,
        @Value("${balancify.rank.dormancy.mmr-drop-per-period:10}") int mmrDropPerPeriod,
        @Value("${balancify.rank.return-boost.games:5}") int returnBoostGames,
        @Value("${balancify.rank.return-boost.multiplier:2.0}") double returnBoostMultiplier
    ) {
        this(
            playerRepository,
            matchParticipantRepository,
            groupRepository,
            enabled,
            inactiveDays,
            mmrDropPerPeriod,
            returnBoostGames,
            returnBoostMultiplier,
            Clock.systemUTC()
        );
    }

    DormancyMmrDecayService(
        PlayerRepository playerRepository,
        MatchParticipantRepository matchParticipantRepository,
        GroupRepository groupRepository,
        boolean enabled,
        int inactiveDays,
        int mmrDropPerPeriod,
        int returnBoostGames,
        double returnBoostMultiplier,
        Clock clock
    ) {
        this.playerRepository = playerRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.groupRepository = groupRepository;
        this.enabled = enabled;
        this.inactiveDays = Math.max(0, inactiveDays);
        this.mmrDropPerPeriod = Math.max(0, mmrDropPerPeriod);
        this.returnBoostGames = Math.max(0, returnBoostGames);
        this.returnBoostMultiplier = Math.max(1.0, returnBoostMultiplier);
        this.clock = clock;
    }

    @Scheduled(cron = "${balancify.rank.dormancy.sweep-cron:0 0 4 * * *}")
    public void applyAllGroupsDormancyDecay() {
        if (!enabled || inactiveDays <= 0 || mmrDropPerPeriod <= 0) {
            return;
        }

        groupRepository.findAll()
            .stream()
            .filter(group -> group.getId() != null)
            .forEach(group -> applyGroupDormancyDecay(group.getId()));
    }

    @Transactional
    public void applyGroupDormancyDecay(Long groupId) {
        if (groupId == null || !enabled || inactiveDays <= 0 || mmrDropPerPeriod <= 0) {
            return;
        }

        List<Player> players = playerRepository.findByGroup_IdOrderByMmrDescIdAsc(groupId)
            .stream()
            .filter(Player::isActive)
            .toList();
        if (players.isEmpty()) {
            return;
        }

        Map<Long, OffsetDateTime> lastPlayedAtByPlayerId = resolveLastPlayedAtByPlayerId(groupId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Player> changedPlayers = new ArrayList<>();

        for (Player player : players) {
            if (applyPlayerDormancyDecay(player, lastPlayedAtByPlayerId.get(player.getId()), now)) {
                changedPlayers.add(player);
            }
        }

        if (!changedPlayers.isEmpty()) {
            playerRepository.saveAll(changedPlayers);
        }
    }

    private Map<Long, OffsetDateTime> resolveLastPlayedAtByPlayerId(Long groupId) {
        List<MatchParticipant> participants = matchParticipantRepository.findByGroupIdOrderByPlayedAtDesc(groupId);
        Map<Long, OffsetDateTime> lastPlayedAtByPlayerId = new HashMap<>();
        for (MatchParticipant participant : participants) {
            if (participant.getPlayer() == null
                || participant.getPlayer().getId() == null
                || participant.getMatch() == null
                || participant.getMatch().getPlayedAt() == null) {
                continue;
            }

            lastPlayedAtByPlayerId.putIfAbsent(
                participant.getPlayer().getId(),
                participant.getMatch().getPlayedAt()
            );
        }
        return lastPlayedAtByPlayerId;
    }

    private boolean applyPlayerDormancyDecay(
        Player player,
        OffsetDateTime lastPlayedAt,
        OffsetDateTime now
    ) {
        OffsetDateTime activityReference = lastPlayedAt != null ? lastPlayedAt : player.getCreatedAt();
        if (activityReference == null) {
            return false;
        }

        OffsetDateTime lastDecayAt = player.getLastDormancyMmrDecayAt();
        OffsetDateTime decayAnchor =
            lastDecayAt != null && lastDecayAt.isAfter(activityReference) ? lastDecayAt : activityReference;
        long inactiveDayCount = ChronoUnit.DAYS.between(decayAnchor.toInstant(), now.toInstant());
        long elapsedPeriods = inactiveDayCount / inactiveDays;
        if (elapsedPeriods <= 0) {
            return false;
        }

        int currentMmr = Math.max(0, player.getMmr() == null ? 0 : player.getMmr());
        int totalDrop = safeTotalDrop(elapsedPeriods);
        int nextMmr = Math.max(0, currentMmr - totalDrop);
        OffsetDateTime appliedThrough = decayAnchor.plusDays(elapsedPeriods * (long) inactiveDays);
        OffsetDateTime dormantSince = activityReference.plusDays(inactiveDays);

        player.setMmr(nextMmr);
        player.setLastDormancyMmrDecayAt(appliedThrough);
        if (player.getDormantSince() == null || activityReference.isAfter(player.getDormantSince())) {
            player.setDormantSince(dormantSince);
            player.setReturnedAt(null);
            player.setReturnBoostGamesRemaining(0);
            player.setReturnBoostMultiplier(returnBoostGames > 0 ? returnBoostMultiplier : 1.0);
        }
        return nextMmr != currentMmr || !appliedThrough.equals(lastDecayAt);
    }

    private int safeTotalDrop(long elapsedPeriods) {
        if (elapsedPeriods > Integer.MAX_VALUE / (long) mmrDropPerPeriod) {
            return Integer.MAX_VALUE;
        }
        return (int) elapsedPeriods * mmrDropPerPeriod;
    }
}
