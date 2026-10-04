package com.balancify.backend.service;

import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchSeries;
import com.balancify.backend.domain.MatchSeriesRound;
import com.balancify.backend.domain.MatchSeriesStatus;
import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.TeamTournament;
import com.balancify.backend.domain.TeamTournamentStatus;
import com.balancify.backend.domain.TournamentTeam;
import com.balancify.backend.repository.GroupRepository;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchRepository;
import com.balancify.backend.repository.MatchSeriesRepository;
import com.balancify.backend.repository.PlayerRepository;
import com.balancify.backend.repository.TeamTournamentRepository;
import com.balancify.backend.repository.TournamentTeamRepository;
import com.balancify.backend.service.exception.MatchConflictException;
import com.balancify.backend.service.exception.MatchEditForbiddenException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps a team tournament in step with its games. Whenever a tournament game is recorded,
 * corrected or deleted, its series is counted again from the matches, the next game is set up as
 * a match waiting for its result, and finished semifinals open the final and the third-place
 * series. The tournament row lock makes results from the two series of a round take turns.
 */
@Service
public class TournamentProgressService {

    private static final String TEAM_HOME = "HOME";

    private final TeamTournamentRepository teamTournamentRepository;
    private final TournamentTeamRepository tournamentTeamRepository;
    private final MatchSeriesRepository matchSeriesRepository;
    private final MatchRepository matchRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final PlayerRepository playerRepository;
    private final GroupRepository groupRepository;
    private final GroupMatchAdminService groupMatchAdminService;
    private final AccessControlService accessControlService;
    private final boolean membersEnabled;

    public TournamentProgressService(
        TeamTournamentRepository teamTournamentRepository,
        TournamentTeamRepository tournamentTeamRepository,
        MatchSeriesRepository matchSeriesRepository,
        MatchRepository matchRepository,
        MatchParticipantRepository matchParticipantRepository,
        PlayerRepository playerRepository,
        GroupRepository groupRepository,
        GroupMatchAdminService groupMatchAdminService,
        AccessControlService accessControlService,
        @Value("${balancify.tournament.members-enabled:false}") boolean membersEnabled
    ) {
        this.teamTournamentRepository = teamTournamentRepository;
        this.tournamentTeamRepository = tournamentTeamRepository;
        this.matchSeriesRepository = matchSeriesRepository;
        this.matchRepository = matchRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.playerRepository = playerRepository;
        this.groupRepository = groupRepository;
        this.groupMatchAdminService = groupMatchAdminService;
        this.accessControlService = accessControlService;
        this.membersEnabled = membersEnabled;
    }

    /** Admins only while tournaments are tried out; every member once balancify.tournament.members-enabled is on. */
    public boolean canRunTournaments(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return membersEnabled
            ? accessControlService.isServiceAccessAllowed(email)
            : accessControlService.isAdminEmail(email);
    }

    /**
     * Before a tournament game's result is recorded or its winner changed. A winner can change only
     * on the latest recorded game of a series, and a semifinal only while the final and the
     * third-place series have no results.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void checkResultChange(Match match, String actorEmail, boolean firstResult) {
        SeriesContext context = lockContext(match);
        if (context == null) {
            return;
        }
        if (!canRunTournaments(actorEmail)) {
            throw new MatchEditForbiddenException("팀 토너먼트 경기는 지금 운영진만 기록할 수 있습니다.");
        }
        if (!firstResult) {
            requireNothingBuiltOn(context, match);
        }
    }

    /** Before a tournament game is deleted; a game still waiting for its result is simply set up again. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void checkDeletion(Match match) {
        SeriesContext context = lockContext(match);
        if (context == null || !hasResult(match)) {
            return;
        }
        requireNothingBuiltOn(context, match);
    }

    /** Recounts the series of a recorded, corrected or deleted game and moves its tournament on. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void sync(Long seriesId) {
        if (seriesId == null) {
            return;
        }
        MatchSeries series = matchSeriesRepository.findById(seriesId).orElse(null);
        if (series == null) {
            return;
        }
        TeamTournament tournament = teamTournamentRepository.findByIdForUpdate(series.getTournament().getId())
            .orElse(null);
        if (tournament == null || tournament.getStatus() == TeamTournamentStatus.CANCELLED) {
            return;
        }
        syncTournament(tournament);
    }

    /**
     * The first round of a new tournament: the final for two teams, one semifinal for three (the
     * third team waits in the final), two semifinals for four.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void openFirstRound(TeamTournament tournament, List<TournamentTeam> teams) {
        Map<Long, Player> players = loadPlayers(teams);
        List<MatchSeries> opened = new ArrayList<>();
        if (teams.size() == 2) {
            opened.add(openSeries(tournament, MatchSeriesRound.FINAL, 1, teams.get(0), teams.get(1), players));
        } else {
            opened.add(openSeries(tournament, MatchSeriesRound.SEMIFINAL, 1, teams.get(0), teams.get(1), players));
            if (teams.size() == 4) {
                opened.add(openSeries(tournament, MatchSeriesRound.SEMIFINAL, 2, teams.get(2), teams.get(3), players));
            }
        }
        for (MatchSeries series : opened) {
            syncSeries(tournament, series, List.of(), players);
        }
    }

    /** Removes the games of a cancelled tournament that were set up but never played. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void removeUnplayedGames(TeamTournament tournament) {
        List<MatchSeries> seriesList = matchSeriesRepository.findByTournament_IdOrderByIdAsc(tournament.getId());
        List<Match> unplayed = loadGames(seriesList).values().stream()
            .flatMap(List::stream)
            .filter(game -> !hasResult(game))
            .toList();
        deleteUnplayed(unplayed);
    }

    private void syncTournament(TeamTournament tournament) {
        List<TournamentTeam> teams = tournamentTeamRepository.findByTournament_IdOrderByTeamNumberAsc(tournament.getId());
        Map<Long, Player> players = loadPlayers(teams);
        List<MatchSeries> seriesList = matchSeriesRepository.findByTournament_IdOrderByIdAsc(tournament.getId());
        Map<Long, List<Match>> games = loadGames(seriesList);

        MatchSeries finalSeries = findRound(seriesList, MatchSeriesRound.FINAL);
        MatchSeries thirdPlace = findRound(seriesList, MatchSeriesRound.THIRD_PLACE);
        List<MatchSeries> semifinals = seriesList.stream()
            .filter(series -> series.getRound() == MatchSeriesRound.SEMIFINAL)
            .sorted(Comparator.comparingInt(MatchSeries::getBracketSlot))
            .toList();
        int teamCount = tournament.getTeamCount();
        if (teamCount >= 3) {
            for (MatchSeries semifinal : semifinals) {
                syncSeries(tournament, semifinal, games.getOrDefault(semifinal.getId(), List.of()), players);
            }

            boolean semifinalsDone = semifinals.size() == teamCount - 2
                && semifinals.stream().allMatch(series -> series.getStatus() == MatchSeriesStatus.COMPLETED);
            if (semifinalsDone && teamCount == 3) {
                // The team that sat out the semifinal meets its winner in the final.
                MatchSeries semifinal = semifinals.getFirst();
                finalSeries = ensureSeries(
                    tournament, finalSeries, MatchSeriesRound.FINAL,
                    semifinal.getWinnerTeam(), teamOutside(teams, semifinal), games, players
                );
            } else if (semifinalsDone) {
                MatchSeries first = semifinals.get(0);
                MatchSeries second = semifinals.get(1);
                finalSeries = ensureSeries(
                    tournament, finalSeries, MatchSeriesRound.FINAL,
                    first.getWinnerTeam(), second.getWinnerTeam(), games, players
                );
                thirdPlace = ensureSeries(
                    tournament, thirdPlace, MatchSeriesRound.THIRD_PLACE,
                    loserOf(first), loserOf(second), games, players
                );
            } else {
                removeSeries(finalSeries, games);
                removeSeries(thirdPlace, games);
                finalSeries = null;
                thirdPlace = null;
            }
        }
        if (finalSeries != null) {
            syncSeries(tournament, finalSeries, games.getOrDefault(finalSeries.getId(), List.of()), players);
        }
        if (thirdPlace != null) {
            syncSeries(tournament, thirdPlace, games.getOrDefault(thirdPlace.getId(), List.of()), players);
        }

        boolean finished = isCompleted(finalSeries) && (teamCount != 4 || isCompleted(thirdPlace));
        teams.forEach(team -> team.setFinalRank(null));
        if (finished) {
            rank(finalSeries, 1);
            if (thirdPlace != null) {
                rank(thirdPlace, 3);
            } else if (teamCount == 3) {
                loserOf(semifinals.getFirst()).setFinalRank(3);
            }
            tournament.setStatus(TeamTournamentStatus.COMPLETED);
            if (tournament.getFinishedAt() == null) {
                tournament.setFinishedAt(OffsetDateTime.now());
            }
        } else {
            tournament.setStatus(TeamTournamentStatus.IN_PROGRESS);
            tournament.setFinishedAt(null);
        }
    }

    private void syncSeries(
        TeamTournament tournament,
        MatchSeries series,
        List<Match> seriesGames,
        Map<Long, Player> players
    ) {
        List<Match> recorded = seriesGames.stream().filter(this::hasResult).toList();
        int homeWins = (int) recorded.stream().filter(game -> TEAM_HOME.equals(game.getWinningTeam())).count();
        int awayWins = recorded.size() - homeWins;
        series.setHomeWins(homeWins);
        series.setAwayWins(awayWins);
        List<Match> unplayed = seriesGames.stream().filter(game -> !hasResult(game)).toList();

        if (TournamentSeriesPlanner.isDecided(series.getFormat(), homeWins, awayWins, recorded.size())) {
            series.setStatus(MatchSeriesStatus.COMPLETED);
            series.setWinnerTeam(homeWins > awayWins ? series.getHomeTeam() : series.getAwayTeam());
            if (series.getFinishedAt() == null) {
                series.setFinishedAt(OffsetDateTime.now());
            }
            deleteUnplayed(unplayed);
            return;
        }

        series.setStatus(MatchSeriesStatus.IN_PROGRESS);
        series.setWinnerTeam(null);
        series.setFinishedAt(null);
        int nextNumber = recorded.stream().mapToInt(this::gameNumber).max().orElse(0) + 1;
        deleteUnplayed(unplayed.stream().filter(game -> gameNumber(game) != nextNumber).toList());
        boolean nextReady = unplayed.stream().anyMatch(game -> gameNumber(game) == nextNumber);
        if (!nextReady && nextNumber <= series.plannedCompositions().size()) {
            createGame(tournament, series, nextNumber, players);
        }
    }

    private void createGame(TeamTournament tournament, MatchSeries series, int gameNumber, Map<Long, Player> players) {
        List<Long> homeIds = series.getHomeTeam().getMemberPlayerIds();
        List<Long> awayIds = series.getAwayTeam().getMemberPlayerIds();
        String composition = series.plannedCompositions().get(gameNumber - 1);
        List<String> homeCapabilities = capabilities(homeIds, players);
        List<String> awayCapabilities = capabilities(awayIds, players);
        // A race edited after the series was planned can rule a composition out; play a shared one instead.
        if (!TournamentSeriesPlanner.canPlay(homeCapabilities, composition)
            || !TournamentSeriesPlanner.canPlay(awayCapabilities, composition)) {
            List<String> shared = TournamentSeriesPlanner.sharedCompositions(homeCapabilities, awayCapabilities);
            if (!shared.isEmpty()) {
                composition = shared.getFirst();
            }
        }
        groupMatchAdminService.createSeriesGameMatch(
            tournament.getGroup().getId(),
            List.copyOf(homeIds),
            List.copyOf(awayIds),
            composition,
            series.getId(),
            gameNumber
        );
    }

    private MatchSeries ensureSeries(
        TeamTournament tournament,
        MatchSeries existing,
        MatchSeriesRound round,
        TournamentTeam home,
        TournamentTeam away,
        Map<Long, List<Match>> games,
        Map<Long, Player> players
    ) {
        if (existing != null) {
            if (sameTeam(existing.getHomeTeam(), home) && sameTeam(existing.getAwayTeam(), away)) {
                return existing;
            }
            removeSeries(existing, games);
        }
        return openSeries(tournament, round, 1, home, away, players);
    }

    private MatchSeries openSeries(
        TeamTournament tournament,
        MatchSeriesRound round,
        int bracketSlot,
        TournamentTeam home,
        TournamentTeam away,
        Map<Long, Player> players
    ) {
        TournamentSeriesPlanner.SeriesPlan plan = TournamentSeriesPlanner.plan(
            capabilities(home.getMemberPlayerIds(), players),
            capabilities(away.getMemberPlayerIds(), players)
        );
        MatchSeries series = new MatchSeries();
        series.setTournament(tournament);
        series.setRound(round);
        series.setBracketSlot(bracketSlot);
        series.setHomeTeam(home);
        series.setAwayTeam(away);
        series.setFormat(plan.format());
        series.setGameCompositions(String.join(",", plan.compositions()));
        return matchSeriesRepository.save(series);
    }

    private void removeSeries(MatchSeries series, Map<Long, List<Match>> games) {
        if (series == null) {
            return;
        }
        List<Match> seriesGames = games.getOrDefault(series.getId(), List.of());
        if (seriesGames.stream().anyMatch(this::hasResult)) {
            throw new MatchConflictException("결승이나 3·4위전 결과가 있어 대진을 바꿀 수 없습니다.");
        }
        deleteUnplayed(seriesGames);
        matchSeriesRepository.delete(series);
        // A replacement series reuses the round's unique slot in the same transaction.
        matchSeriesRepository.flush();
    }

    private void deleteUnplayed(List<Match> unplayed) {
        if (unplayed.isEmpty()) {
            return;
        }
        for (Match game : unplayed) {
            matchParticipantRepository.deleteByMatch_Id(game.getId());
            matchRepository.delete(game);
        }
        // A new game may take the same series game number right after this.
        matchRepository.flush();
    }

    private void requireNothingBuiltOn(SeriesContext context, Match match) {
        MatchSeries series = context.series();
        int number = gameNumber(match);
        boolean laterGameRecorded = matchRepository.findBySeriesIdOrderBySeriesGameNumberAsc(series.getId()).stream()
            .anyMatch(game -> !Objects.equals(game.getId(), match.getId()) && gameNumber(game) > number && hasResult(game));
        if (laterGameRecorded) {
            throw new MatchConflictException("이 시리즈는 다음 판 결과가 있어 이 판을 바꿀 수 없습니다. 마지막 판부터 고쳐 주세요.");
        }
        if (series.getRound() != MatchSeriesRound.SEMIFINAL) {
            return;
        }
        List<MatchSeries> laterRounds = matchSeriesRepository.findByTournament_IdOrderByIdAsc(context.tournament().getId())
            .stream()
            .filter(other -> other.getRound() != MatchSeriesRound.SEMIFINAL)
            .toList();
        boolean laterRoundRecorded = loadGames(laterRounds).values().stream()
            .flatMap(List::stream)
            .anyMatch(this::hasResult);
        if (laterRoundRecorded) {
            throw new MatchConflictException("결승이나 3·4위전 결과가 있어 준결승 결과를 바꿀 수 없습니다.");
        }
    }

    private SeriesContext lockContext(Match match) {
        if (match == null || match.getSeriesId() == null) {
            return null;
        }
        MatchSeries series = matchSeriesRepository.findById(match.getSeriesId()).orElse(null);
        if (series == null) {
            return null;
        }
        TeamTournament tournament = teamTournamentRepository.findByIdForUpdate(series.getTournament().getId())
            .orElse(null);
        if (tournament == null || tournament.getStatus() == TeamTournamentStatus.CANCELLED) {
            return null;
        }
        // Taken before any player row is touched, as match creation and race edits do, so a result
        // here and one of those never wait on each other in a circle.
        groupRepository.findByIdForUpdate(tournament.getGroup().getId());
        return new SeriesContext(tournament, series);
    }

    private Map<Long, List<Match>> loadGames(List<MatchSeries> seriesList) {
        Map<Long, List<Match>> games = new HashMap<>();
        if (seriesList.isEmpty()) {
            return games;
        }
        List<Long> seriesIds = seriesList.stream().map(MatchSeries::getId).toList();
        for (Match game : matchRepository.findBySeriesIdInOrderBySeriesGameNumberAsc(seriesIds)) {
            games.computeIfAbsent(game.getSeriesId(), ignored -> new ArrayList<>()).add(game);
        }
        return games;
    }

    private Map<Long, Player> loadPlayers(List<TournamentTeam> teams) {
        Set<Long> playerIds = new LinkedHashSet<>();
        teams.forEach(team -> playerIds.addAll(team.getMemberPlayerIds()));
        Map<Long, Player> players = new HashMap<>();
        playerRepository.findAllById(playerIds).forEach(player -> players.put(player.getId(), player));
        return players;
    }

    private List<String> capabilities(List<Long> playerIds, Map<Long, Player> players) {
        return playerIds.stream()
            .map(playerId -> {
                Player player = players.get(playerId);
                return TournamentSeriesPlanner.capabilityOf(player == null ? null : player.getRace());
            })
            .toList();
    }

    private MatchSeries findRound(List<MatchSeries> seriesList, MatchSeriesRound round) {
        return seriesList.stream().filter(series -> series.getRound() == round).findFirst().orElse(null);
    }

    private TournamentTeam teamOutside(List<TournamentTeam> teams, MatchSeries series) {
        return teams.stream()
            .filter(team -> !sameTeam(team, series.getHomeTeam()) && !sameTeam(team, series.getAwayTeam()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("A three-team tournament needs a team outside its semifinal"));
    }

    private TournamentTeam loserOf(MatchSeries series) {
        return sameTeam(series.getWinnerTeam(), series.getHomeTeam()) ? series.getAwayTeam() : series.getHomeTeam();
    }

    private void rank(MatchSeries series, int winnerRank) {
        TournamentTeam winner = series.getWinnerTeam();
        winner.setFinalRank(winnerRank);
        loserOf(series).setFinalRank(winnerRank + 1);
    }

    private boolean isCompleted(MatchSeries series) {
        return series != null && series.getStatus() == MatchSeriesStatus.COMPLETED;
    }

    private boolean sameTeam(TournamentTeam left, TournamentTeam right) {
        return left != null && right != null && Objects.equals(left.getId(), right.getId());
    }

    private boolean hasResult(Match game) {
        return game.getWinningTeam() != null;
    }

    private int gameNumber(Match game) {
        return game.getSeriesGameNumber() == null ? 0 : game.getSeriesGameNumber();
    }

    private record SeriesContext(TeamTournament tournament, MatchSeries series) {
    }
}
