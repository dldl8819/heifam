package com.balancify.backend.service;

import com.balancify.backend.api.series.dto.BalanceSeriesLineupRequest;
import com.balancify.backend.api.series.dto.BalanceSeriesListResponse;
import com.balancify.backend.api.series.dto.BalanceSeriesResponse;
import com.balancify.backend.domain.BalanceSeries;
import com.balancify.backend.domain.BalanceSeriesStatus;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.MatchSeriesFormat;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.BalanceSeriesRepository;
import com.balancify.backend.repository.GroupRepository;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchRepository;
import com.balancify.backend.repository.PlayerRepository;
import com.balancify.backend.service.exception.MatchConflictException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Starts, shows and cancels the series played after a multi-balance: the two teams of a match
 * stay together for PPP, PPT and PPZ when both can field Terran or Zerg, otherwise for PPP best
 * of three (TournamentSeriesPlanner). BalanceSeriesProgressService moves each series along.
 */
@Service
public class BalanceSeriesService {

    static final int MAX_SERIES_PER_START = 6;
    // Finished series stay on the board this long after they started, so the last scores can be read.
    static final Duration BOARD_WINDOW = Duration.ofHours(12);

    private final GroupRepository groupRepository;
    private final PlayerRepository playerRepository;
    private final BalanceSeriesRepository balanceSeriesRepository;
    private final MatchRepository matchRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final BalanceSeriesProgressService balanceSeriesProgressService;
    private final OperationAuditLogService operationAuditLogService;
    private final Clock clock;

    @Autowired
    public BalanceSeriesService(
        GroupRepository groupRepository,
        PlayerRepository playerRepository,
        BalanceSeriesRepository balanceSeriesRepository,
        MatchRepository matchRepository,
        MatchParticipantRepository matchParticipantRepository,
        BalanceSeriesProgressService balanceSeriesProgressService,
        OperationAuditLogService operationAuditLogService
    ) {
        this(
            groupRepository,
            playerRepository,
            balanceSeriesRepository,
            matchRepository,
            matchParticipantRepository,
            balanceSeriesProgressService,
            operationAuditLogService,
            Clock.systemUTC()
        );
    }

    BalanceSeriesService(
        GroupRepository groupRepository,
        PlayerRepository playerRepository,
        BalanceSeriesRepository balanceSeriesRepository,
        MatchRepository matchRepository,
        MatchParticipantRepository matchParticipantRepository,
        BalanceSeriesProgressService balanceSeriesProgressService,
        OperationAuditLogService operationAuditLogService,
        Clock clock
    ) {
        this.groupRepository = groupRepository;
        this.playerRepository = playerRepository;
        this.balanceSeriesRepository = balanceSeriesRepository;
        this.matchRepository = matchRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.balanceSeriesProgressService = balanceSeriesProgressService;
        this.operationAuditLogService = operationAuditLogService;
        this.clock = clock;
    }

    /** One series per lineup; a player already in a running series cannot start another. */
    @Transactional
    public BalanceSeriesListResponse start(
        Long groupId,
        List<BalanceSeriesLineupRequest> lineups,
        String actorEmail,
        String actorNickname,
        boolean showMmr
    ) {
        List<Lineup> normalized = normalizeLineups(lineups);
        Set<Long> allIds = new LinkedHashSet<>();
        normalized.forEach(lineup -> {
            allIds.addAll(lineup.home());
            allIds.addAll(lineup.away());
        });

        groupRepository.findByIdForUpdate(groupId)
            .orElseThrow(() -> new NoSuchElementException("Group not found: " + groupId));
        Set<Long> busy = new HashSet<>(
            balanceSeriesRepository.findPlayerIdsByGroupIdAndStatus(groupId, BalanceSeriesStatus.IN_PROGRESS)
        );
        if (allIds.stream().anyMatch(busy::contains)) {
            throw new MatchConflictException("진행 중인 시리즈에 들어 있는 선수가 있습니다. 그 시리즈를 끝내거나 취소한 뒤 시작해 주세요.");
        }
        Map<Long, Player> players = new HashMap<>();
        playerRepository.findByGroup_IdAndIdIn(groupId, new ArrayList<>(allIds)).stream()
            .filter(player -> !PlayerIdentityPolicy.isIdentityHidden(player))
            .forEach(player -> players.put(player.getId(), player));
        if (players.size() != allIds.size()) {
            throw new IllegalArgumentException("그룹에 없는 선수가 있습니다.");
        }

        for (Lineup lineup : normalized) {
            TournamentSeriesPlanner.SeriesPlan plan = TournamentSeriesPlanner.plan(
                capabilities(lineup.home(), players),
                capabilities(lineup.away(), players),
                lineup.format()
            );
            BalanceSeries series = new BalanceSeries();
            series.setGroupId(groupId);
            series.setTeamSize(lineup.home().size());
            List<Long> playerIds = new ArrayList<>(lineup.home());
            playerIds.addAll(lineup.away());
            series.setPlayerIds(playerIds);
            series.setFormat(plan.format());
            series.setGameCompositions(String.join(",", plan.compositions()));
            balanceSeriesProgressService.open(balanceSeriesRepository.save(series));
        }

        operationAuditLogService.recordBalanceSeriesStarted(actorEmail, actorNickname, groupId, normalized.size());
        return list(groupId, showMmr);
    }

    /** Running series, and those that finished after starting within the board window. */
    @Transactional(readOnly = true)
    public BalanceSeriesListResponse list(Long groupId, boolean showMmr) {
        OffsetDateTime since = OffsetDateTime.now(clock).minus(BOARD_WINDOW);
        return new BalanceSeriesListResponse(toResponses(balanceSeriesRepository.findRecentForBoard(groupId, since), showMmr));
    }

    /** Games already played stay as ordinary matches; a game set up but not played is removed. */
    @Transactional
    public BalanceSeriesListResponse cancel(
        Long groupId,
        Long seriesId,
        String actorEmail,
        String actorNickname,
        boolean showMmr
    ) {
        balanceSeriesRepository.findByIdAndGroupId(seriesId, groupId)
            .orElseThrow(() -> new NoSuchElementException("Series not found: " + seriesId));
        BalanceSeries series = balanceSeriesRepository.findByIdForUpdate(seriesId)
            .orElseThrow(() -> new NoSuchElementException("Series not found: " + seriesId));
        if (series.getStatus() != BalanceSeriesStatus.IN_PROGRESS) {
            throw new MatchConflictException("진행 중인 시리즈만 취소할 수 있습니다.");
        }
        series.setStatus(BalanceSeriesStatus.CANCELLED);
        series.setFinishedAt(OffsetDateTime.now(clock));
        balanceSeriesProgressService.removeUnplayedGames(series);
        operationAuditLogService.recordBalanceSeriesCancelled(actorEmail, actorNickname, series.getId(), groupId);
        return list(groupId, showMmr);
    }

    private List<Lineup> normalizeLineups(List<BalanceSeriesLineupRequest> lineups) {
        if (lineups == null || lineups.isEmpty()) {
            throw new IllegalArgumentException("시작할 경기를 골라 주세요.");
        }
        if (lineups.size() > MAX_SERIES_PER_START) {
            throw new IllegalArgumentException("한 번에 " + MAX_SERIES_PER_START + "개까지 시작할 수 있습니다.");
        }
        Set<Long> seen = new HashSet<>();
        List<Lineup> normalized = new ArrayList<>();
        for (BalanceSeriesLineupRequest lineup : lineups) {
            if (lineup == null) {
                throw new IllegalArgumentException("경기 구성이 비어 있습니다.");
            }
            List<Long> home = playerIds(lineup.homePlayerIds(), seen);
            List<Long> away = playerIds(lineup.awayPlayerIds(), seen);
            if (home.size() != away.size() || home.size() < 2 || home.size() > 3) {
                throw new IllegalArgumentException("시리즈는 2:2나 3:3으로만 할 수 있습니다.");
            }
            normalized.add(new Lineup(home, away, format(lineup.format())));
        }
        return normalized;
    }

    private MatchSeriesFormat format(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return MatchSeriesFormat.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("지원하지 않는 시리즈 방식입니다.");
        }
    }

    private List<Long> playerIds(List<Long> ids, Set<Long> seen) {
        if (ids == null) {
            return List.of();
        }
        List<Long> normalized = new ArrayList<>();
        for (Long id : ids) {
            if (id == null || id <= 0) {
                throw new IllegalArgumentException("Player ID must be a positive number");
            }
            if (!seen.add(id)) {
                throw new IllegalArgumentException("한 선수가 두 자리에 들어 있습니다.");
            }
            normalized.add(id);
        }
        return normalized;
    }

    private List<BalanceSeriesResponse> toResponses(List<BalanceSeries> seriesList, boolean showMmr) {
        if (seriesList.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Match>> gamesBySeries = new HashMap<>();
        List<Long> seriesIds = seriesList.stream().map(BalanceSeries::getId).toList();
        for (Match game : matchRepository.findByBalanceSeriesIdInOrderBySeriesGameNumberAsc(seriesIds)) {
            gamesBySeries.computeIfAbsent(game.getBalanceSeriesId(), ignored -> new ArrayList<>()).add(game);
        }
        List<Long> gameIds = gamesBySeries.values().stream().flatMap(List::stream).map(Match::getId).toList();
        Map<Long, Map<Long, MatchParticipant>> participantsByGame = new HashMap<>();
        if (!gameIds.isEmpty()) {
            for (MatchParticipant participant : matchParticipantRepository.findByMatchIdInWithPlayerAndMatch(gameIds)) {
                if (participant.getPlayer() == null) {
                    continue;
                }
                participantsByGame
                    .computeIfAbsent(participant.getMatch().getId(), ignored -> new HashMap<>())
                    .put(participant.getPlayer().getId(), participant);
            }
        }
        Set<Long> playerIds = new LinkedHashSet<>();
        seriesList.forEach(series -> playerIds.addAll(series.getPlayerIds()));
        Map<Long, Player> players = new HashMap<>();
        playerRepository.findAllById(playerIds).forEach(player -> players.put(player.getId(), player));

        return seriesList.stream()
            .map(series -> new BalanceSeriesResponse(
                series.getId(),
                series.getStatus().name(),
                series.getFormat().name(),
                series.getTeamSize(),
                series.getHomeWins(),
                series.getAwayWins(),
                series.getWinnerTeam(),
                series.getCreatedAt(),
                series.getFinishedAt(),
                series.homePlayerIds().stream().map(id -> SeriesGameViews.playerResponse(players.get(id), showMmr)).toList(),
                series.awayPlayerIds().stream().map(id -> SeriesGameViews.playerResponse(players.get(id), showMmr)).toList(),
                SeriesGameViews.games(
                    series.plannedCompositions(),
                    gamesBySeries.getOrDefault(series.getId(), List.of()),
                    series.homePlayerIds(),
                    series.awayPlayerIds(),
                    series.getStatus() != BalanceSeriesStatus.IN_PROGRESS,
                    participantsByGame,
                    players
                )
            ))
            .toList();
    }

    private List<String> capabilities(List<Long> playerIds, Map<Long, Player> players) {
        return playerIds.stream()
            .map(id -> TournamentSeriesPlanner.capabilityOf(players.get(id).getRace()))
            .toList();
    }

    private record Lineup(List<Long> home, List<Long> away, MatchSeriesFormat format) {
    }
}
