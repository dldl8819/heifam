package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.series.dto.BalanceSeriesLineupRequest;
import com.balancify.backend.api.series.dto.BalanceSeriesListResponse;
import com.balancify.backend.api.tournament.dto.TournamentGameResponse;
import com.balancify.backend.domain.BalanceSeries;
import com.balancify.backend.domain.BalanceSeriesStatus;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchSeriesFormat;
import com.balancify.backend.domain.MatchStatus;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.BalanceSeriesRepository;
import com.balancify.backend.repository.GroupRepository;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchRepository;
import com.balancify.backend.repository.PlayerRepository;
import com.balancify.backend.service.exception.MatchConflictException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

// The repositories keep their rows in memory, so a series can be played through with the real progress service.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BalanceSeriesServiceTest {

    private static final String ADMIN = "admin@example.com";

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private BalanceSeriesRepository balanceSeriesRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private MatchParticipantRepository matchParticipantRepository;

    @Mock
    private GroupMatchAdminService groupMatchAdminService;

    @Mock
    private OperationAuditLogService operationAuditLogService;

    @Mock
    private AccessControlService accessControlService;

    private BalanceSeriesProgressService progress;
    private BalanceSeriesService service;
    private final Map<Long, Player> players = new HashMap<>();
    private final List<BalanceSeries> storedSeries = new ArrayList<>();
    private final List<Match> storedGames = new ArrayList<>();
    private long nextSeriesId = 100;
    private long nextMatchId = 1000;

    @BeforeEach
    void setUp() {
        progress = new BalanceSeriesProgressService(
            balanceSeriesRepository,
            matchRepository,
            matchParticipantRepository,
            playerRepository,
            groupRepository,
            groupMatchAdminService
        );
        service = new BalanceSeriesService(
            groupRepository,
            playerRepository,
            balanceSeriesRepository,
            matchRepository,
            matchParticipantRepository,
            progress,
            operationAuditLogService,
            accessControlService,
            Clock.fixed(Instant.parse("2026-10-04T12:00:00Z"), ZoneOffset.UTC)
        );
        Group group = new Group();
        group.setId(7L);
        when(groupRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(group));
        when(playerRepository.findByGroup_IdAndIdIn(eq(7L), anyList())).thenAnswer(invocation -> {
            List<Player> found = new ArrayList<>();
            for (Long id : invocation.<List<Long>>getArgument(1)) {
                if (players.containsKey(id)) {
                    found.add(players.get(id));
                }
            }
            return found;
        });
        when(playerRepository.findAllById(any())).thenAnswer(invocation -> {
            List<Player> found = new ArrayList<>();
            for (Long id : invocation.<Iterable<Long>>getArgument(0)) {
                if (players.containsKey(id)) {
                    found.add(players.get(id));
                }
            }
            return found;
        });
        when(balanceSeriesRepository.save(any(BalanceSeries.class))).thenAnswer(invocation -> {
            BalanceSeries series = invocation.getArgument(0);
            if (series.getId() == null) {
                ReflectionTestUtils.setField(series, "id", nextSeriesId++);
                storedSeries.add(series);
            }
            return series;
        });
        when(balanceSeriesRepository.findByIdForUpdate(any())).thenAnswer(invocation -> findSeries(invocation.getArgument(0)));
        when(balanceSeriesRepository.findByIdAndGroupId(any(), eq(7L)))
            .thenAnswer(invocation -> findSeries(invocation.getArgument(0)));
        when(balanceSeriesRepository.findRecentForBoard(eq(7L), any())).thenAnswer(invocation -> storedSeries.stream()
            .filter(series -> series.getStatus() != BalanceSeriesStatus.CANCELLED)
            .sorted(Comparator.comparing(BalanceSeries::getId).reversed())
            .toList());
        when(balanceSeriesRepository.findPlayerIdsByGroupIdAndStatus(7L, BalanceSeriesStatus.IN_PROGRESS))
            .thenAnswer(invocation -> storedSeries.stream()
                .filter(series -> series.getStatus() == BalanceSeriesStatus.IN_PROGRESS)
                .flatMap(series -> series.getPlayerIds().stream())
                .toList());
        when(matchRepository.findByBalanceSeriesIdOrderBySeriesGameNumberAsc(any())).thenAnswer(invocation ->
            gamesWhere(game -> Objects.equals(game.getBalanceSeriesId(), invocation.getArgument(0)))
        );
        when(matchRepository.findByBalanceSeriesIdInOrderBySeriesGameNumberAsc(any())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return gamesWhere(game -> ids.contains(game.getBalanceSeriesId()));
        });
        doAnswer(invocation -> storedGames.remove(invocation.<Match>getArgument(0)))
            .when(matchRepository).delete(any(Match.class));
        when(matchParticipantRepository.findByMatchIdInWithPlayerAndMatch(anyList())).thenReturn(List.of());
        when(groupMatchAdminService.createBalanceSeriesGameMatch(eq(7L), anyList(), anyList(), anyInt(), any(), any(), anyInt(), any()))
            .thenAnswer(invocation -> {
                Match game = new Match();
                game.setId(nextMatchId++);
                game.setStatus(MatchStatus.CONFIRMED);
                game.setTeamSize(invocation.getArgument(3));
                game.setRaceComposition(invocation.getArgument(4));
                game.setBalanceSeriesId(invocation.getArgument(5));
                game.setSeriesGameNumber(invocation.getArgument(6));
                game.setCreatedByEmail(invocation.getArgument(7));
                storedGames.add(game);
                return game;
            });
    }

    @Test
    void startsOneSeriesPerMatchWithItsFirstGame() {
        BalanceSeriesListResponse response = service.start(
            7L,
            List.of(lineup(1, "P", "P", "P", "P", "P", "P"), lineup(2, "PT", "PZ", "P", "PTZ", "P", "P")),
            ADMIN,
            "Ops",
            false
        );

        assertThat(storedSeries).extracting(BalanceSeries::getFormat)
            .containsExactly(MatchSeriesFormat.BEST_OF_THREE, MatchSeriesFormat.MIXED_THREE);
        assertThat(storedSeries).extracting(BalanceSeries::getGameCompositions)
            .containsExactly("PPP,PPP,PPP", "PPP,PPT,PPZ");
        verify(groupMatchAdminService).createBalanceSeriesGameMatch(7L, List.of(11L, 12L, 13L), List.of(14L, 15L, 16L), 3, "PPP", 100L, 1, ADMIN);
        verify(groupMatchAdminService).createBalanceSeriesGameMatch(7L, List.of(21L, 22L, 23L), List.of(24L, 25L, 26L), 3, "PPP", 101L, 1, ADMIN);
        verify(operationAuditLogService).recordBalanceSeriesStarted(ADMIN, "Ops", 7L, 2);
        assertThat(response.series()).hasSize(2);
        assertThat(storedSeries).extracting(BalanceSeries::getMatchNumber).containsExactly(1, 2);
        assertThat(response.series().getFirst().matchNumber()).isEqualTo(2);
        assertThat(response.series().getFirst().homeTeamNumber()).isEqualTo(3);
        assertThat(response.series().getFirst().awayTeamNumber()).isEqualTo(4);
        assertThat(response.series().getLast().games()).extracting(TournamentGameResponse::status)
            .containsExactly("NEXT", "UPCOMING", "UPCOMING");
        // Newest first: the mixed series shows who takes Terran in its planned second game.
        assertThat(response.series().getFirst().games().get(1).homePlayers())
            .extracting(player -> player.assignedRace())
            .containsExactly("T", "P", "P");
    }

    @Test
    void endsBestOfThreeAtTwoWinsWithoutTheThirdGame() {
        service.start(7L, List.of(lineup(1, "P", "P", "P", "P", "P", "P")), ADMIN, null, false);
        BalanceSeries series = storedSeries.getFirst();

        playAndSync(series, 1, "AWAY");
        assertThat(series.getStatus()).isEqualTo(BalanceSeriesStatus.IN_PROGRESS);
        assertThat(game(series, 2).getRaceComposition()).isEqualTo("PPP");
        playAndSync(series, 2, "AWAY");

        assertThat(series.getStatus()).isEqualTo(BalanceSeriesStatus.COMPLETED);
        assertThat(series.getWinnerTeam()).isEqualTo("AWAY");
        assertThat(series.getAwayWins()).isEqualTo(2);
        assertThat(series.getFinishedAt()).isNotNull();
        assertThat(game(series, 3)).isNull();
    }

    @Test
    void showsWhomEachSeriesIsWaitingOnByNickname() {
        when(accessControlService.resolveDisplayNicknames(any()))
            .thenReturn(java.util.Map.of(ADMIN, "Ops", "member@example.com", "YOUR_USERNAME"));
        service.start(7L, List.of(lineup(1, "P", "P", "P", "P", "P", "P")), ADMIN, null, false);
        BalanceSeries series = storedSeries.getFirst();

        assertThat(service.list(7L, false).series().getFirst().createdByNickname()).isEqualTo("Ops");

        // Someone else enters game 1: the series now waits on them for game 2.
        record(game(series, 1), "HOME");
        progress.sync(series.getId(), "member@example.com");
        assertThat(service.list(7L, false).series().getFirst().createdByNickname()).isEqualTo("YOUR_USERNAME");

        // Over at 2:0, it keeps naming whoever set up its last game.
        record(game(series, 2), "HOME");
        progress.sync(series.getId(), ADMIN);
        assertThat(series.getStatus()).isEqualTo(BalanceSeriesStatus.COMPLETED);
        assertThat(service.list(7L, false).series().getFirst().createdByNickname()).isEqualTo("YOUR_USERNAME");
        assertThat(service.list(7L, false).toString()).doesNotContain("@");
    }

    @Test
    void namesWhoeverEnteredTheLastResultAsTheNextGamesCreator() {
        service.start(7L, List.of(lineup(1, "P", "P", "P", "P", "P", "P")), ADMIN, null, false);
        BalanceSeries series = storedSeries.getFirst();
        assertThat(game(series, 1).getCreatedByEmail()).isEqualTo(ADMIN);

        // Someone else enters game 1: game 2 is theirs to see through.
        record(game(series, 1), "HOME");
        progress.sync(series.getId(), "member@example.com");
        assertThat(game(series, 2).getCreatedByEmail()).isEqualTo("member@example.com");

        // A sync without anyone (a deleted game set up again) keeps the last one named.
        storedGames.remove(game(series, 2));
        progress.sync(series.getId());
        assertThat(game(series, 2).getCreatedByEmail()).isEqualTo(ADMIN);
    }

    @Test
    void endsAMixedSeriesAtTwoWinsToo() {
        service.start(7L, List.of(lineup(1, "PT", "PZ", "P", "PTZ", "P", "P")), ADMIN, null, false);
        BalanceSeries series = storedSeries.getFirst();
        assertThat(series.getFormat()).isEqualTo(MatchSeriesFormat.MIXED_THREE);

        playAndSync(series, 1, "HOME");
        assertThat(game(series, 2).getRaceComposition()).isEqualTo("PPT");
        playAndSync(series, 2, "HOME");

        // 2:0 is the end: nobody plays the Zerg game for a 3:0.
        assertThat(series.getStatus()).isEqualTo(BalanceSeriesStatus.COMPLETED);
        assertThat(series.getWinnerTeam()).isEqualTo("HOME");
        assertThat(series.getHomeWins()).isEqualTo(2);
        assertThat(series.getFinishedAt()).isNotNull();
        assertThat(game(series, 3)).isNull();
    }

    @Test
    void playsTheThirdMixedGameOnlyAtOneAll() {
        service.start(7L, List.of(lineup(1, "PT", "PZ", "P", "PTZ", "P", "P")), ADMIN, null, false);
        BalanceSeries series = storedSeries.getFirst();

        playAndSync(series, 1, "HOME");
        playAndSync(series, 2, "AWAY");

        assertThat(series.getStatus()).isEqualTo(BalanceSeriesStatus.IN_PROGRESS);
        assertThat(game(series, 3).getRaceComposition()).isEqualTo("PPZ");
        playAndSync(series, 3, "AWAY");
        assertThat(series.getStatus()).isEqualTo(BalanceSeriesStatus.COMPLETED);
        assertThat(series.getWinnerTeam()).isEqualTo("AWAY");
    }

    @Test
    void playsTheFormatChosenForEachMatch() {
        List<Long> mixedIds = addPlayers(1, "PT", "PZ", "P", "PTZ", "P", "P");
        List<Long> protossIds = addPlayers(2, "PT", "PZ", "P", "PTZ", "P", "P");

        service.start(
            7L,
            List.of(
                new BalanceSeriesLineupRequest(mixedIds.subList(0, 3), mixedIds.subList(3, 6), "MIXED_THREE"),
                new BalanceSeriesLineupRequest(protossIds.subList(0, 3), protossIds.subList(3, 6), "best_of_three")
            ),
            ADMIN,
            null,
            false
        );

        assertThat(storedSeries).extracting(BalanceSeries::getGameCompositions)
            .containsExactly("PPP,PPT,PPZ", "PPP,PPP,PPP");
        assertThat(storedSeries.getLast().getFormat()).isEqualTo(MatchSeriesFormat.BEST_OF_THREE);
        List<Long> others = addPlayers(3, "P", "P", "P", "P", "P", "P");
        assertThatThrownBy(() -> service.start(
            7L,
            List.of(new BalanceSeriesLineupRequest(others.subList(0, 3), others.subList(3, 6), "MIXED_THREE")),
            ADMIN,
            null,
            false
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.start(
            7L,
            List.of(new BalanceSeriesLineupRequest(others.subList(0, 3), others.subList(3, 6), "ROUND_ROBIN")),
            ADMIN,
            null,
            false
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void playsTwoVersusTwoAsPpPtAndPz() {
        service.start(7L, List.of(lineup(1, "PT", "PZ", "PTZ", "P")), ADMIN, null, false);
        BalanceSeries series = storedSeries.getFirst();

        assertThat(series.getTeamSize()).isEqualTo(2);
        assertThat(series.getGameCompositions()).isEqualTo("PP,PT,PZ");
        verify(groupMatchAdminService).createBalanceSeriesGameMatch(7L, List.of(11L, 12L), List.of(13L, 14L), 2, "PP", 100L, 1, ADMIN);
    }

    @Test
    void refusesPlayersAlreadyInARunningSeries() {
        service.start(7L, List.of(lineup(1, "P", "P", "P", "P", "P", "P")), ADMIN, null, false);
        List<BalanceSeriesLineupRequest> again = List.of(new BalanceSeriesLineupRequest(
            List.of(11L, 31L, 32L),
            List.of(33L, 34L, 35L)
        ));
        addPlayers(3, "P", "P", "P", "P", "P", "P");

        assertThatThrownBy(() -> service.start(7L, again, ADMIN, null, false))
            .isInstanceOf(MatchConflictException.class);
    }

    @Test
    void refusesUnevenTeamsAndRepeatedPlayers() {
        addPlayers(1, "P", "P", "P", "P", "P");

        assertThatThrownBy(() -> service.start(
            7L,
            List.of(new BalanceSeriesLineupRequest(List.of(11L, 12L, 13L), List.of(14L, 15L))),
            ADMIN,
            null,
            false
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.start(
            7L,
            List.of(new BalanceSeriesLineupRequest(List.of(11L, 12L), List.of(12L, 13L))),
            ADMIN,
            null,
            false
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.start(
            7L,
            List.of(new BalanceSeriesLineupRequest(List.of(11L, 12L), List.of(98L, 99L))),
            ADMIN,
            null,
            false
        )).isInstanceOf(IllegalArgumentException.class);
        assertThat(storedSeries).isEmpty();
    }

    @Test
    void cancellingKeepsPlayedGamesAndRemovesTheWaitingOne() {
        service.start(7L, List.of(lineup(1, "P", "P", "P", "P", "P", "P")), ADMIN, null, false);
        BalanceSeries series = storedSeries.getFirst();
        playAndSync(series, 1, "HOME");

        BalanceSeriesListResponse response = service.cancel(7L, series.getId(), ADMIN, "Ops", false);

        assertThat(series.getStatus()).isEqualTo(BalanceSeriesStatus.CANCELLED);
        assertThat(storedGames).singleElement().satisfies(game -> assertThat(game.getWinningTeam()).isEqualTo("HOME"));
        assertThat(response.series()).isEmpty();
        verify(operationAuditLogService).recordBalanceSeriesCancelled(ADMIN, "Ops", series.getId(), 7L);
        assertThatThrownBy(() -> service.cancel(7L, series.getId(), ADMIN, "Ops", false))
            .isInstanceOf(MatchConflictException.class);

        record(game(series, 1), "AWAY");
        progress.sync(series.getId());
        assertThat(series.getHomeWins()).isEqualTo(1);
    }

    @Test
    void keepsEarlierGamesFixedOnceALaterOneHasAResult() {
        service.start(7L, List.of(lineup(1, "P", "P", "P", "P", "P", "P")), ADMIN, null, false);
        BalanceSeries series = storedSeries.getFirst();
        playAndSync(series, 1, "HOME");
        playAndSync(series, 2, "AWAY");

        assertThatThrownBy(() -> progress.checkResultChange(game(series, 1), false))
            .isInstanceOf(MatchConflictException.class);
        assertThatThrownBy(() -> progress.checkDeletion(game(series, 1)))
            .isInstanceOf(MatchConflictException.class);
        assertThatCode(() -> progress.checkResultChange(game(series, 2), false)).doesNotThrowAnyException();
        assertThatCode(() -> progress.checkDeletion(game(series, 3))).doesNotThrowAnyException();
    }

    @Test
    void reopensTheSeriesWhenACorrectionUndoesTheDecidingWin() {
        service.start(7L, List.of(lineup(1, "P", "P", "P", "P", "P", "P")), ADMIN, null, false);
        BalanceSeries series = storedSeries.getFirst();
        playAndSync(series, 1, "HOME");
        playAndSync(series, 2, "HOME");

        game(series, 2).setWinningTeam("AWAY");
        progress.sync(series.getId());

        assertThat(series.getStatus()).isEqualTo(BalanceSeriesStatus.IN_PROGRESS);
        assertThat(series.getWinnerTeam()).isNull();
        assertThat(game(series, 3)).isNotNull();
    }

    @Test
    void startsNoSeriesWithAPlayerWhoseTierIsStillToBeSet() {
        BalanceSeriesLineupRequest first = lineup(1, "P", "P", "P", "P", "P", "P");
        players.get(15L).setTier("UNASSIGNED");

        assertThatThrownBy(() -> service.start(7L, List.of(first), ADMIN, "Ops", false))
            .isInstanceOf(MatchConflictException.class)
            .hasMessageContaining("배정 필요 선수(YOUR_USERNAME_15)");
        assertThat(storedSeries).isEmpty();
        verify(groupMatchAdminService, never()).createBalanceSeriesGameMatch(any(), any(), any(), anyInt(), any(), any(), anyInt(), any());
    }

    private BalanceSeriesLineupRequest lineup(int number, String... races) {
        List<Long> ids = addPlayers(number, races);
        int teamSize = ids.size() / 2;
        return new BalanceSeriesLineupRequest(ids.subList(0, teamSize), ids.subList(teamSize, ids.size()));
    }

    private List<Long> addPlayers(int number, String... races) {
        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < races.length; index++) {
            long playerId = number * 10L + index + 1;
            Player player = new Player();
            player.setId(playerId);
            player.setNickname("YOUR_USERNAME_" + playerId);
            player.setRace(races[index]);
            player.setMmr(1000);
            players.put(playerId, player);
            ids.add(playerId);
        }
        return ids;
    }

    private void playAndSync(BalanceSeries series, int number, String winner) {
        Match game = game(series, number);
        assertThat(game).as("game %d of series %d", number, series.getId()).isNotNull();
        record(game, winner);
        progress.sync(series.getId());
    }

    private void record(Match game, String winner) {
        game.setWinningTeam(winner);
        game.setStatus(MatchStatus.COMPLETED);
    }

    private Match game(BalanceSeries series, int number) {
        return storedGames.stream()
            .filter(game -> Objects.equals(game.getBalanceSeriesId(), series.getId()) && game.getSeriesGameNumber() == number)
            .findFirst()
            .orElse(null);
    }

    private Optional<BalanceSeries> findSeries(Long id) {
        return storedSeries.stream().filter(series -> series.getId().equals(id)).findFirst();
    }

    private List<Match> gamesWhere(Predicate<Match> filter) {
        return storedGames.stream()
            .filter(filter)
            .sorted(Comparator.comparing(Match::getSeriesGameNumber))
            .toList();
    }
}
