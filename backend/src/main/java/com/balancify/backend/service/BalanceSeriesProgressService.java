package com.balancify.backend.service;

import com.balancify.backend.domain.BalanceSeries;
import com.balancify.backend.domain.BalanceSeriesStatus;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.BalanceSeriesRepository;
import com.balancify.backend.repository.GroupRepository;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchRepository;
import com.balancify.backend.repository.PlayerRepository;
import com.balancify.backend.service.exception.MatchConflictException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps a multi-balance series in step with its games, as TournamentProgressService does for
 * tournaments: whenever a game is recorded, corrected or deleted, the series is counted again
 * from its matches and the next game is set up as a match waiting for its result. Results are
 * recorded through the usual match result API by whoever may record ordinary matches.
 */
@Service
public class BalanceSeriesProgressService {

    private static final String TEAM_HOME = "HOME";
    private static final String TEAM_AWAY = "AWAY";

    private final BalanceSeriesRepository balanceSeriesRepository;
    private final MatchRepository matchRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final PlayerRepository playerRepository;
    private final GroupRepository groupRepository;
    private final GroupMatchAdminService groupMatchAdminService;

    public BalanceSeriesProgressService(
        BalanceSeriesRepository balanceSeriesRepository,
        MatchRepository matchRepository,
        MatchParticipantRepository matchParticipantRepository,
        PlayerRepository playerRepository,
        GroupRepository groupRepository,
        GroupMatchAdminService groupMatchAdminService
    ) {
        this.balanceSeriesRepository = balanceSeriesRepository;
        this.matchRepository = matchRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.playerRepository = playerRepository;
        this.groupRepository = groupRepository;
        this.groupMatchAdminService = groupMatchAdminService;
    }

    /** Before a series game's result is recorded or changed: a winner changes only on the latest recorded game. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void checkResultChange(Match match, boolean firstResult) {
        BalanceSeries series = lockSeries(match);
        if (series == null || firstResult) {
            return;
        }
        requireNoLaterResult(series, match);
    }

    /** Before a series game is deleted; a game still waiting for its result is simply set up again. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void checkDeletion(Match match) {
        BalanceSeries series = lockSeries(match);
        if (series == null || !hasResult(match)) {
            return;
        }
        requireNoLaterResult(series, match);
    }

    /** Recounts the series of a recorded, corrected or deleted game and sets up its next game. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void sync(Long balanceSeriesId) {
        if (balanceSeriesId == null) {
            return;
        }
        BalanceSeries series = balanceSeriesRepository.findByIdForUpdate(balanceSeriesId).orElse(null);
        if (series == null || series.getStatus() == BalanceSeriesStatus.CANCELLED) {
            return;
        }
        syncSeries(series, matchRepository.findByBalanceSeriesIdOrderBySeriesGameNumberAsc(series.getId()));
    }

    /** Sets up the first game of a series just saved. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void open(BalanceSeries series) {
        syncSeries(series, List.of());
    }

    /** Removes the games of a cancelled series that were set up but never played. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void removeUnplayedGames(BalanceSeries series) {
        deleteUnplayed(matchRepository.findByBalanceSeriesIdOrderBySeriesGameNumberAsc(series.getId()).stream()
            .filter(game -> !hasResult(game))
            .toList());
    }

    private void syncSeries(BalanceSeries series, List<Match> games) {
        List<Match> recorded = games.stream().filter(this::hasResult).toList();
        int homeWins = (int) recorded.stream().filter(game -> TEAM_HOME.equals(game.getWinningTeam())).count();
        int awayWins = recorded.size() - homeWins;
        series.setHomeWins(homeWins);
        series.setAwayWins(awayWins);
        List<Match> unplayed = games.stream().filter(game -> !hasResult(game)).toList();

        if (TournamentSeriesPlanner.isDecided(series.getFormat(), homeWins, awayWins, recorded.size())) {
            series.setStatus(BalanceSeriesStatus.COMPLETED);
            series.setWinnerTeam(homeWins > awayWins ? TEAM_HOME : TEAM_AWAY);
            if (series.getFinishedAt() == null) {
                series.setFinishedAt(OffsetDateTime.now());
            }
            deleteUnplayed(unplayed);
            return;
        }

        series.setStatus(BalanceSeriesStatus.IN_PROGRESS);
        series.setWinnerTeam(null);
        series.setFinishedAt(null);
        int nextNumber = recorded.stream().mapToInt(this::gameNumber).max().orElse(0) + 1;
        deleteUnplayed(unplayed.stream().filter(game -> gameNumber(game) != nextNumber).toList());
        boolean nextReady = unplayed.stream().anyMatch(game -> gameNumber(game) == nextNumber);
        if (!nextReady && nextNumber <= series.plannedCompositions().size()) {
            createGame(series, nextNumber);
        }
    }

    private void createGame(BalanceSeries series, int gameNumber) {
        List<Long> homeIds = series.homePlayerIds();
        List<Long> awayIds = series.awayPlayerIds();
        Map<Long, Player> players = new HashMap<>();
        playerRepository.findAllById(series.getPlayerIds()).forEach(player -> players.put(player.getId(), player));
        List<String> homeCapabilities = capabilities(homeIds, players);
        List<String> awayCapabilities = capabilities(awayIds, players);
        String composition = series.plannedCompositions().get(gameNumber - 1);
        // A race edited after the series was planned can rule a composition out; play a shared one instead.
        if (!TournamentSeriesPlanner.canPlay(homeCapabilities, composition)
            || !TournamentSeriesPlanner.canPlay(awayCapabilities, composition)) {
            List<String> shared = TournamentSeriesPlanner.sharedCompositions(homeCapabilities, awayCapabilities);
            if (!shared.isEmpty()) {
                composition = shared.getFirst();
            }
        }
        groupMatchAdminService.createBalanceSeriesGameMatch(
            series.getGroupId(),
            homeIds,
            awayIds,
            series.getTeamSize(),
            composition,
            series.getId(),
            gameNumber
        );
    }

    private void requireNoLaterResult(BalanceSeries series, Match match) {
        int number = gameNumber(match);
        boolean laterGameRecorded = matchRepository.findByBalanceSeriesIdOrderBySeriesGameNumberAsc(series.getId()).stream()
            .anyMatch(game -> !Objects.equals(game.getId(), match.getId()) && gameNumber(game) > number && hasResult(game));
        if (laterGameRecorded) {
            throw new MatchConflictException("이 시리즈는 다음 판 결과가 있어 이 판을 바꿀 수 없습니다. 마지막 판부터 고쳐 주세요.");
        }
    }

    private BalanceSeries lockSeries(Match match) {
        if (match == null || match.getBalanceSeriesId() == null) {
            return null;
        }
        BalanceSeries series = balanceSeriesRepository.findByIdForUpdate(match.getBalanceSeriesId()).orElse(null);
        if (series == null || series.getStatus() == BalanceSeriesStatus.CANCELLED) {
            return null;
        }
        // Taken before any player row is touched, as match creation and race edits do, so a result
        // here and one of those never wait on each other in a circle.
        groupRepository.findByIdForUpdate(series.getGroupId());
        return series;
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

    private List<String> capabilities(List<Long> playerIds, Map<Long, Player> players) {
        return playerIds.stream()
            .map(playerId -> {
                Player player = players.get(playerId);
                return TournamentSeriesPlanner.capabilityOf(player == null ? null : player.getRace());
            })
            .toList();
    }

    private boolean hasResult(Match game) {
        return game.getWinningTeam() != null;
    }

    private int gameNumber(Match game) {
        return game.getSeriesGameNumber() == null ? 0 : game.getSeriesGameNumber();
    }
}
