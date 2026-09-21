package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.GroupPlayerTeammateStatResponse;
import com.balancify.backend.api.group.dto.GroupPlayerTeammateStatsResponse;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.PlayerRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Win rate per teammate: how one player did in the matches they shared a team with each other
 * player.
 *
 * <p>It reads the participants of that player's matches once and aggregates them in memory, so a
 * lookup costs a single query rather than one per teammate. Results are cached per group and player
 * for the same short window as the other group reads, and a match result change drops the cache.
 * Withdrawn members are left out, since their nickname is masked everywhere else anyway.
 */
@Service
public class PlayerTeammateStatsQueryService {

    private final PlayerRepository playerRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final GroupReadCacheService groupReadCacheService;

    public PlayerTeammateStatsQueryService(
        PlayerRepository playerRepository,
        MatchParticipantRepository matchParticipantRepository,
        GroupReadCacheService groupReadCacheService
    ) {
        this.playerRepository = playerRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.groupReadCacheService = groupReadCacheService;
    }

    @Transactional(readOnly = true)
    public GroupPlayerTeammateStatsResponse getTeammateStats(Long groupId, Long playerId) {
        return groupReadCacheService.get(
            "player-teammate-stats:group:" + groupId + ":player:" + playerId,
            () -> loadTeammateStats(groupId, playerId)
        );
    }

    private GroupPlayerTeammateStatsResponse loadTeammateStats(Long groupId, Long playerId) {
        Player player = playerRepository.findByIdAndGroup_Id(playerId, groupId)
            .filter(candidate -> !PlayerIdentityPolicy.isIdentityHidden(candidate))
            .orElseThrow(() -> new NoSuchElementException("Player not found"));

        // Newest match first, which is what the current win streak counts back from.
        Map<Long, List<MatchParticipant>> participantsByMatchId = new LinkedHashMap<>();
        for (MatchParticipant participant : matchParticipantRepository
            .findByGroupIdAndPlayerMatchesOrderByPlayedAtDesc(groupId, playerId)) {
            if (participant == null || participant.getMatch() == null || participant.getMatch().getId() == null) {
                continue;
            }
            participantsByMatchId
                .computeIfAbsent(participant.getMatch().getId(), ignored -> new ArrayList<>())
                .add(participant);
        }

        int wins = 0;
        int losses = 0;
        Map<Long, TeammateTotals> totalsByTeammateId = new LinkedHashMap<>();
        for (List<MatchParticipant> matchParticipants : participantsByMatchId.values()) {
            MatchParticipant own = findOwnParticipant(matchParticipants, playerId);
            if (own == null) {
                continue;
            }
            String ownTeam = normalizeTeam(own.getTeam());
            String winningTeam = normalizeTeam(own.getMatch().getWinningTeam());
            if (ownTeam.isEmpty() || winningTeam.isEmpty()) {
                continue;
            }

            boolean won = ownTeam.equals(winningTeam);
            if (won) {
                wins++;
            } else {
                losses++;
            }

            for (MatchParticipant other : matchParticipants) {
                if (other == null || other.getPlayer() == null || other.getPlayer().getId() == null) {
                    continue;
                }
                if (playerId.equals(other.getPlayer().getId()) || !ownTeam.equals(normalizeTeam(other.getTeam()))) {
                    continue;
                }
                // Withdrawn members are masked everywhere else, so a row for them would only ever
                // read as the hidden member label.
                if (PlayerIdentityPolicy.isIdentityHidden(other.getPlayer())) {
                    continue;
                }

                TeammateTotals totals = totalsByTeammateId
                    .computeIfAbsent(other.getPlayer().getId(), ignored -> new TeammateTotals());
                totals.nickname = PlayerIdentityPolicy.responseNickname(other.getPlayer());
                if (won) {
                    totals.wins++;
                    if (totals.streakOpen) {
                        totals.currentWinStreak++;
                    }
                } else {
                    totals.losses++;
                    totals.streakOpen = false;
                }
            }
        }

        Comparator<GroupPlayerTeammateStatResponse> byWinRate =
            Comparator.comparingDouble(GroupPlayerTeammateStatResponse::winRate).reversed();
        Comparator<GroupPlayerTeammateStatResponse> byGames =
            Comparator.comparingInt(GroupPlayerTeammateStatResponse::games).reversed();
        List<GroupPlayerTeammateStatResponse> teammates = totalsByTeammateId.entrySet()
            .stream()
            .map(entry -> toResponse(entry.getKey(), entry.getValue()))
            .sorted(byWinRate
                .thenComparing(byGames)
                .thenComparing(stat -> safeTrim(stat.nickname()), String.CASE_INSENSITIVE_ORDER))
            .toList();

        return new GroupPlayerTeammateStatsResponse(
            player.getId(),
            PlayerIdentityPolicy.responseNickname(player),
            wins,
            losses,
            wins + losses,
            winRate(wins, wins + losses),
            teammates
        );
    }

    private MatchParticipant findOwnParticipant(List<MatchParticipant> matchParticipants, Long playerId) {
        for (MatchParticipant participant : matchParticipants) {
            if (participant.getPlayer() != null && playerId.equals(participant.getPlayer().getId())) {
                return participant;
            }
        }
        return null;
    }

    private GroupPlayerTeammateStatResponse toResponse(Long teammateId, TeammateTotals totals) {
        int games = totals.wins + totals.losses;
        return new GroupPlayerTeammateStatResponse(
            teammateId,
            totals.nickname,
            totals.wins,
            totals.losses,
            games,
            winRate(totals.wins, games),
            totals.currentWinStreak
        );
    }

    private double winRate(int wins, int games) {
        if (games <= 0) {
            return 0.0;
        }
        return Math.round((wins * 10000.0) / games) / 100.0;
    }

    private String normalizeTeam(String team) {
        String normalized = safeTrim(team).toUpperCase();
        if ("HOME".equals(normalized) || "AWAY".equals(normalized)) {
            return normalized;
        }
        return "";
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class TeammateTotals {
        private String nickname;
        private int wins;
        private int losses;
        private int currentWinStreak;
        private boolean streakOpen = true;
    }
}
