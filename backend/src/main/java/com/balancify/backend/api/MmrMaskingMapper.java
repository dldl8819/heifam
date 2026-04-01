package com.balancify.backend.api;

import com.balancify.backend.api.group.dto.DashboardKpiSummaryResponse;
import com.balancify.backend.api.group.dto.DashboardRecentBalancePreviewResponse;
import com.balancify.backend.api.group.dto.DashboardRecentBalanceTeamPlayerResponse;
import com.balancify.backend.api.group.dto.DashboardTopRankingPreviewItemResponse;
import com.balancify.backend.api.group.dto.GroupDashboardResponse;
import com.balancify.backend.api.group.dto.GroupPlayerResponse;
import com.balancify.backend.api.group.dto.GroupRecentMatchPlayerResponse;
import com.balancify.backend.api.group.dto.GroupRecentMatchResponse;
import com.balancify.backend.api.group.dto.RankingItemResponse;
import com.balancify.backend.api.match.dto.BalancePlayerDto;
import com.balancify.backend.api.match.dto.BalanceResponse;
import com.balancify.backend.api.match.dto.MultiBalanceMatchResponse;
import com.balancify.backend.api.match.dto.MultiBalanceResponse;
import java.util.List;

public final class MmrMaskingMapper {

    private MmrMaskingMapper() {
    }

    public static List<GroupPlayerResponse> maskGroupPlayers(List<GroupPlayerResponse> responses) {
        return responses
            .stream()
            .map(response ->
                new GroupPlayerResponse(
                    response.id(),
                    response.nickname(),
                    response.race(),
                    response.tier(),
                    0,
                    response.tier(),
                    0,
                    response.wins(),
                    response.losses(),
                    response.games()
                )
            )
            .toList();
    }

    public static List<RankingItemResponse> maskRanking(List<RankingItemResponse> responses) {
        return responses
            .stream()
            .map(response ->
                new RankingItemResponse(
                    response.rank(),
                    response.nickname(),
                    response.race(),
                    0,
                    response.wins(),
                    response.losses(),
                    response.games(),
                    response.winRate(),
                    response.streak(),
                    response.last10(),
                    0
                )
            )
            .toList();
    }

    public static GroupDashboardResponse maskDashboard(GroupDashboardResponse response) {
        DashboardKpiSummaryResponse sourceKpi = response.kpiSummary();
        DashboardKpiSummaryResponse maskedKpi = new DashboardKpiSummaryResponse(
            sourceKpi.totalPlayers(),
            0,
            0.0,
            sourceKpi.totalGames()
        );

        List<DashboardTopRankingPreviewItemResponse> maskedTopPreview = response
            .topRankingPreview()
            .stream()
            .map(item ->
                new DashboardTopRankingPreviewItemResponse(
                    item.rank(),
                    item.nickname(),
                    item.race(),
                    0,
                    item.winRate()
                )
            )
            .toList();

        DashboardRecentBalancePreviewResponse maskedRecentPreview = null;
        if (response.recentBalancePreview() != null) {
            DashboardRecentBalancePreviewResponse sourcePreview = response.recentBalancePreview();
            List<DashboardRecentBalanceTeamPlayerResponse> maskedHomeTeam = sourcePreview
                .homeTeam()
                .stream()
                .map(player -> new DashboardRecentBalanceTeamPlayerResponse(player.nickname(), 0))
                .toList();
            List<DashboardRecentBalanceTeamPlayerResponse> maskedAwayTeam = sourcePreview
                .awayTeam()
                .stream()
                .map(player -> new DashboardRecentBalanceTeamPlayerResponse(player.nickname(), 0))
                .toList();

            maskedRecentPreview = new DashboardRecentBalancePreviewResponse(
                sourcePreview.matchId(),
                maskedHomeTeam,
                maskedAwayTeam,
                0,
                0,
                0,
                sourcePreview.createdAt()
            );
        }

        return new GroupDashboardResponse(maskedKpi, maskedTopPreview, maskedRecentPreview);
    }

    public static List<GroupRecentMatchResponse> maskRecentMatches(List<GroupRecentMatchResponse> responses) {
        return responses
            .stream()
            .map(response ->
                new GroupRecentMatchResponse(
                    response.matchId(),
                    response.playedAt(),
                    response.winningTeam(),
                    response.resultRecordedAt(),
                    response.resultRecordedByNickname(),
                    maskRecentMatchPlayers(response.homeTeam()),
                    maskRecentMatchPlayers(response.awayTeam()),
                    0,
                    0,
                    0
                )
            )
            .toList();
    }

    public static BalanceResponse maskBalance(BalanceResponse response) {
        return new BalanceResponse(
            response.teamSize(),
            maskBalancePlayers(response.homeTeam()),
            maskBalancePlayers(response.awayTeam()),
            0,
            0,
            0,
            0.0
        );
    }

    public static MultiBalanceResponse maskMultiBalance(MultiBalanceResponse response) {
        List<MultiBalanceMatchResponse> maskedMatches = response
            .matches()
            .stream()
            .map(match ->
                new MultiBalanceMatchResponse(
                    match.matchNumber(),
                    match.matchType(),
                    match.teamSize(),
                    maskBalancePlayers(match.homeTeam()),
                    maskBalancePlayers(match.awayTeam()),
                    0,
                    0,
                    0,
                    0.0,
                    match.raceSummary(),
                    match.penaltySummary()
                )
            )
            .toList();

        return new MultiBalanceResponse(
            response.balanceMode(),
            response.totalPlayers(),
            response.assignedPlayers(),
            response.waitingPlayers(),
            response.matchCount(),
            maskedMatches
        );
    }

    private static List<GroupRecentMatchPlayerResponse> maskRecentMatchPlayers(
        List<GroupRecentMatchPlayerResponse> players
    ) {
        return players
            .stream()
            .map(player ->
                new GroupRecentMatchPlayerResponse(
                    player.playerId(),
                    player.nickname(),
                    player.team(),
                    0
                )
            )
            .toList();
    }

    private static List<BalancePlayerDto> maskBalancePlayers(List<BalancePlayerDto> players) {
        return players
            .stream()
            .map(player -> new BalancePlayerDto(player.playerId(), player.name(), 0))
            .toList();
    }
}
