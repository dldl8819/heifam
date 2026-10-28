package com.balancify.backend.service;

import com.balancify.backend.api.group.dto.GroupPlayerTeammateStatResponse;
import com.balancify.backend.api.group.dto.GroupPlayerTeammateStatsResponse;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.PlayerRepository;
import com.balancify.backend.repository.TeammateMatchRow;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Win rate per teammate: how one player did in the matches they shared a team with each other
 * player.
 *
 * <p>It reads the sides of that player's matches once (TeammateMatchRow) and aggregates them in
 * memory, then the teammates' names in one more query, rather than a query per teammate. Results are
 * cached per group and player
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

    /**
     * The cut a member sees of their own record. Their own totals stay whole; the list keeps the
     * teammates they win at least half their shared matches with. Admins see every teammate.
     */
    public static final double MEMBER_MIN_WIN_RATE = 50.0;

    /** Whether the signed-in account owns this roster row, which is what lets a member read it. */
    @Transactional(readOnly = true)
    public boolean isOwnPlayer(Long groupId, Long playerId, UUID requesterAuthUserId) {
        if (requesterAuthUserId == null) {
            return false;
        }
        return playerRepository.findByIdAndGroup_Id(playerId, groupId)
            .filter(player -> !PlayerIdentityPolicy.isIdentityHidden(player))
            .map(player -> requesterAuthUserId.equals(player.getAuthUserId()))
            .orElse(false);
    }

    /** A member's own view of their record: the losing teammates are left out. */
    @Transactional(readOnly = true)
    public GroupPlayerTeammateStatsResponse getOwnTeammateStats(Long groupId, Long playerId) {
        GroupPlayerTeammateStatsResponse stats = getTeammateStats(groupId, playerId);
        return new GroupPlayerTeammateStatsResponse(
            stats.playerId(),
            stats.nickname(),
            stats.wins(),
            stats.losses(),
            stats.games(),
            stats.winRate(),
            stats.teammates()
                .stream()
                .filter(teammate -> teammate.winRate() >= MEMBER_MIN_WIN_RATE)
                .toList()
        );
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
        Map<Long, List<TeammateMatchRow>> rowsByMatchId = new LinkedHashMap<>();
        for (TeammateMatchRow row : matchParticipantRepository.findTeammateMatchRows(groupId, playerId)) {
            if (row == null || row.matchId() == null) {
                continue;
            }
            rowsByMatchId.computeIfAbsent(row.matchId(), ignored -> new ArrayList<>()).add(row);
        }

        int wins = 0;
        int losses = 0;
        Map<Long, TeammateTotals> totalsByTeammateId = new LinkedHashMap<>();
        for (List<TeammateMatchRow> matchRows : rowsByMatchId.values()) {
            TeammateMatchRow own = findOwnRow(matchRows, playerId);
            if (own == null) {
                continue;
            }
            String ownTeam = normalizeTeam(own.team());
            String winningTeam = normalizeTeam(own.winningTeam());
            if (ownTeam.isEmpty() || winningTeam.isEmpty()) {
                continue;
            }

            boolean won = ownTeam.equals(winningTeam);
            if (won) {
                wins++;
            } else {
                losses++;
            }

            for (TeammateMatchRow other : matchRows) {
                if (other.playerId() == null
                    || playerId.equals(other.playerId())
                    || !ownTeam.equals(normalizeTeam(other.team()))) {
                    continue;
                }

                TeammateTotals totals = totalsByTeammateId
                    .computeIfAbsent(other.playerId(), ignored -> new TeammateTotals());
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

        // The rows carry no names, so the teammates' own rows are read once each for them.
        Map<Long, Player> teammatesById = new HashMap<>();
        for (Player teammate : playerRepository.findAllById(totalsByTeammateId.keySet())) {
            teammatesById.put(teammate.getId(), teammate);
        }

        Comparator<GroupPlayerTeammateStatResponse> byWinRate =
            Comparator.comparingDouble(GroupPlayerTeammateStatResponse::winRate).reversed();
        Comparator<GroupPlayerTeammateStatResponse> byGames =
            Comparator.comparingInt(GroupPlayerTeammateStatResponse::games).reversed();
        List<GroupPlayerTeammateStatResponse> teammates = totalsByTeammateId.entrySet()
            .stream()
            // Withdrawn members are masked everywhere else, so a row for them would only ever
            // read as the hidden member label.
            .filter(entry -> !PlayerIdentityPolicy.isIdentityHidden(teammatesById.get(entry.getKey())))
            .map(entry -> toResponse(
                entry.getKey(),
                PlayerIdentityPolicy.responseNickname(teammatesById.get(entry.getKey())),
                entry.getValue()
            ))
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

    private TeammateMatchRow findOwnRow(List<TeammateMatchRow> matchRows, Long playerId) {
        for (TeammateMatchRow row : matchRows) {
            if (playerId.equals(row.playerId())) {
                return row;
            }
        }
        return null;
    }

    private GroupPlayerTeammateStatResponse toResponse(Long teammateId, String nickname, TeammateTotals totals) {
        int games = totals.wins + totals.losses;
        return new GroupPlayerTeammateStatResponse(
            teammateId,
            nickname,
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
        private int wins;
        private int losses;
        private int currentWinStreak;
        private boolean streakOpen = true;
    }
}
