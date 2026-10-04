package com.balancify.backend.service;

import com.balancify.backend.api.tournament.dto.TeamScoreBoardResponse;
import com.balancify.backend.api.tournament.dto.TeamScoreEntryResponse;
import com.balancify.backend.domain.MatchSeries;
import com.balancify.backend.domain.MatchSeriesStatus;
import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.PlayerStats;
import com.balancify.backend.domain.TeamTournament;
import com.balancify.backend.domain.TeamTournamentStatus;
import com.balancify.backend.domain.TournamentTeam;
import com.balancify.backend.repository.MatchSeriesRepository;
import com.balancify.backend.repository.PlayerRepository;
import com.balancify.backend.repository.PlayerStatsRepository;
import com.balancify.backend.repository.TeamTournamentRepository;
import com.balancify.backend.repository.TournamentTeamRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Team scores: how players do as part of a tournament team, next to their own game record. A
 * 3v3 game is won or lost by the whole team, so the personal win rate already is the team's;
 * what it misses is how a player's teams go in series and tournaments.
 */
@Service
public class TeamScoreService {

    private final TeamTournamentRepository teamTournamentRepository;
    private final TournamentTeamRepository tournamentTeamRepository;
    private final MatchSeriesRepository matchSeriesRepository;
    private final PlayerRepository playerRepository;
    private final PlayerStatsRepository playerStatsRepository;

    public TeamScoreService(
        TeamTournamentRepository teamTournamentRepository,
        TournamentTeamRepository tournamentTeamRepository,
        MatchSeriesRepository matchSeriesRepository,
        PlayerRepository playerRepository,
        PlayerStatsRepository playerStatsRepository
    ) {
        this.teamTournamentRepository = teamTournamentRepository;
        this.tournamentTeamRepository = tournamentTeamRepository;
        this.matchSeriesRepository = matchSeriesRepository;
        this.playerRepository = playerRepository;
        this.playerStatsRepository = playerStatsRepository;
    }

    /**
     * Placement points count finished tournaments only. Series count wherever they were played
     * to the end, even in a tournament cancelled later, since those games were real.
     */
    @Transactional(readOnly = true)
    public TeamScoreBoardResponse board(Long groupId) {
        List<TeamTournament> tournaments = teamTournamentRepository.findByGroup_Id(groupId);
        if (tournaments.isEmpty()) {
            return new TeamScoreBoardResponse(List.of());
        }
        Map<Long, TeamTournament> tournamentsById = new HashMap<>();
        tournaments.forEach(tournament -> tournamentsById.put(tournament.getId(), tournament));
        List<Long> tournamentIds = List.copyOf(tournamentsById.keySet());

        Map<Long, Tally> tallies = new LinkedHashMap<>();
        for (TournamentTeam team : tournamentTeamRepository.findByTournament_IdIn(tournamentIds)) {
            TeamTournament tournament = tournamentsById.get(team.getTournament().getId());
            boolean placed = tournament != null
                && tournament.getStatus() == TeamTournamentStatus.COMPLETED
                && team.getFinalRank() != null;
            if (!placed) {
                continue;
            }
            for (Long playerId : team.getMemberPlayerIds()) {
                tallies.computeIfAbsent(playerId, Tally::new).place(team.getFinalRank());
            }
        }
        for (MatchSeries series : matchSeriesRepository.findByTournament_IdInAndStatus(tournamentIds, MatchSeriesStatus.COMPLETED)) {
            TournamentTeam winner = series.getWinnerTeam();
            if (winner == null) {
                continue;
            }
            TournamentTeam loser = Objects.equals(winner.getId(), series.getHomeTeam().getId())
                ? series.getAwayTeam()
                : series.getHomeTeam();
            winner.getMemberPlayerIds().forEach(playerId -> tallies.computeIfAbsent(playerId, Tally::new).seriesWins++);
            loser.getMemberPlayerIds().forEach(playerId -> tallies.computeIfAbsent(playerId, Tally::new).seriesLosses++);
        }
        if (tallies.isEmpty()) {
            return new TeamScoreBoardResponse(List.of());
        }

        Map<Long, Player> players = new HashMap<>();
        playerRepository.findAllById(tallies.keySet()).forEach(player -> players.put(player.getId(), player));
        Map<Long, PlayerStats> stats = new HashMap<>();
        playerStatsRepository.findByGroupId(groupId).forEach(stat -> stats.put(stat.getPlayerId(), stat));

        // Players who left or were deactivated drop out, as they do from the ranking.
        List<Tally> shown = new ArrayList<>();
        for (Tally tally : tallies.values()) {
            Player player = players.get(tally.playerId);
            if (player != null && !PlayerIdentityPolicy.isIdentityHidden(player)) {
                tally.nickname = player.getNickname();
                shown.add(tally);
            }
        }
        shown.sort(Comparator
            .comparingInt((Tally tally) -> tally.points).reversed()
            .thenComparing(Comparator.comparingInt((Tally tally) -> tally.seriesWins).reversed())
            .thenComparingInt(tally -> tally.seriesLosses)
            .thenComparing(tally -> tally.nickname == null ? "" : tally.nickname));

        List<TeamScoreEntryResponse> entries = new ArrayList<>();
        int rank = 0;
        Tally previous = null;
        for (int index = 0; index < shown.size(); index++) {
            Tally tally = shown.get(index);
            if (previous == null || !tally.sharesPlaceWith(previous)) {
                rank = index + 1;
            }
            previous = tally;
            PlayerStats stat = stats.get(tally.playerId);
            int wins = stat == null || stat.getWins() == null ? 0 : stat.getWins();
            int losses = stat == null || stat.getLosses() == null ? 0 : stat.getLosses();
            entries.add(new TeamScoreEntryResponse(
                rank,
                tally.playerId,
                tally.nickname,
                tally.points,
                tally.championships,
                tally.runnerUps,
                tally.thirdPlaces,
                tally.tournaments,
                tally.seriesWins,
                tally.seriesLosses,
                percentage(tally.seriesWins, tally.seriesLosses),
                wins,
                losses,
                percentage(wins, losses)
            ));
        }
        return new TeamScoreBoardResponse(entries);
    }

    static int placementPoints(int finalRank) {
        return switch (finalRank) {
            case 1 -> 3;
            case 2 -> 2;
            case 3 -> 1;
            default -> 0;
        };
    }

    private static Double percentage(int wins, int losses) {
        int total = wins + losses;
        return total == 0 ? null : Math.round(wins * 1000.0 / total) / 10.0;
    }

    private static final class Tally {
        private final Long playerId;
        private String nickname;
        private int points;
        private int championships;
        private int runnerUps;
        private int thirdPlaces;
        private int tournaments;
        private int seriesWins;
        private int seriesLosses;

        private Tally(Long playerId) {
            this.playerId = playerId;
        }

        private void place(int finalRank) {
            tournaments++;
            points += placementPoints(finalRank);
            if (finalRank == 1) {
                championships++;
            } else if (finalRank == 2) {
                runnerUps++;
            } else if (finalRank == 3) {
                thirdPlaces++;
            }
        }

        private boolean sharesPlaceWith(Tally other) {
            return points == other.points && seriesWins == other.seriesWins && seriesLosses == other.seriesLosses;
        }
    }
}
