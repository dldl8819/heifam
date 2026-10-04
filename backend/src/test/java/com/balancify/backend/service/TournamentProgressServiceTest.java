package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchSeries;
import com.balancify.backend.domain.MatchSeriesFormat;
import com.balancify.backend.domain.MatchSeriesRound;
import com.balancify.backend.domain.MatchSeriesStatus;
import com.balancify.backend.domain.MatchStatus;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

// The repositories keep their rows in memory here, so a whole tournament can be played through.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TournamentProgressServiceTest {

    private static final String ADMIN = "admin@example.com";

    @Mock
    private TeamTournamentRepository teamTournamentRepository;

    @Mock
    private TournamentTeamRepository tournamentTeamRepository;

    @Mock
    private MatchSeriesRepository matchSeriesRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private MatchParticipantRepository matchParticipantRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMatchAdminService groupMatchAdminService;

    @Mock
    private AccessControlService accessControlService;

    private TournamentProgressService service;
    private TeamTournament tournament;
    private final List<TournamentTeam> teams = new ArrayList<>();
    private final Map<Long, Player> players = new HashMap<>();
    private final List<MatchSeries> storedSeries = new ArrayList<>();
    private final List<Match> storedGames = new ArrayList<>();
    private long nextSeriesId = 100;
    private long nextMatchId = 1000;

    @BeforeEach
    void setUp() {
        service = new TournamentProgressService(
            teamTournamentRepository,
            tournamentTeamRepository,
            matchSeriesRepository,
            matchRepository,
            matchParticipantRepository,
            playerRepository,
            groupRepository,
            groupMatchAdminService,
            accessControlService,
            false
        );
        Group group = new Group();
        group.setId(7L);
        tournament = new TeamTournament();
        ReflectionTestUtils.setField(tournament, "id", 1L);
        tournament.setGroup(group);

        when(accessControlService.isAdminEmail(ADMIN)).thenReturn(true);
        when(groupRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(group));
        when(teamTournamentRepository.findByIdForUpdate(1L)).thenAnswer(invocation -> Optional.of(tournament));
        when(tournamentTeamRepository.findByTournament_IdOrderByTeamNumberAsc(1L)).thenAnswer(invocation -> teams);
        when(playerRepository.findAllById(any())).thenAnswer(invocation -> {
            List<Player> found = new ArrayList<>();
            for (Long id : invocation.<Iterable<Long>>getArgument(0)) {
                if (players.containsKey(id)) {
                    found.add(players.get(id));
                }
            }
            return found;
        });
        when(matchSeriesRepository.save(any(MatchSeries.class))).thenAnswer(invocation -> {
            MatchSeries series = invocation.getArgument(0);
            if (series.getId() == null) {
                ReflectionTestUtils.setField(series, "id", nextSeriesId++);
                storedSeries.add(series);
            }
            return series;
        });
        when(matchSeriesRepository.findByTournament_IdOrderByIdAsc(1L)).thenAnswer(invocation -> List.copyOf(storedSeries));
        when(matchSeriesRepository.findById(any())).thenAnswer(invocation -> storedSeries.stream()
            .filter(series -> series.getId().equals(invocation.getArgument(0)))
            .findFirst());
        doAnswer(invocation -> storedSeries.remove(invocation.<MatchSeries>getArgument(0)))
            .when(matchSeriesRepository).delete(any(MatchSeries.class));
        when(matchRepository.findBySeriesIdInOrderBySeriesGameNumberAsc(any())).thenAnswer(invocation -> {
            Collection<Long> seriesIds = invocation.getArgument(0);
            return gamesWhere(game -> seriesIds.contains(game.getSeriesId()));
        });
        when(matchRepository.findBySeriesIdOrderBySeriesGameNumberAsc(any())).thenAnswer(invocation ->
            gamesWhere(game -> Objects.equals(game.getSeriesId(), invocation.getArgument(0)))
        );
        doAnswer(invocation -> storedGames.remove(invocation.<Match>getArgument(0)))
            .when(matchRepository).delete(any(Match.class));
        when(groupMatchAdminService.createSeriesGameMatch(eq(7L), anyList(), anyList(), any(), any(), anyInt()))
            .thenAnswer(invocation -> {
                Match game = new Match();
                game.setId(nextMatchId++);
                game.setStatus(MatchStatus.CONFIRMED);
                game.setRaceComposition(invocation.getArgument(3));
                game.setSeriesId(invocation.getArgument(4));
                game.setSeriesGameNumber(invocation.getArgument(5));
                storedGames.add(game);
                return game;
            });
    }

    @Test
    void opensTheFinalWithItsFirstGameForTwoTeams() {
        startTournament(team(1, "P", "P", "P"), team(2, "P", "P", "P"));

        assertThat(storedSeries).singleElement().satisfies(series -> {
            assertThat(series.getRound()).isEqualTo(MatchSeriesRound.FINAL);
            assertThat(series.getFormat()).isEqualTo(MatchSeriesFormat.BEST_OF_THREE);
            assertThat(series.getGameCompositions()).isEqualTo("PPP,PPP,PPP");
        });
        verify(groupMatchAdminService).createSeriesGameMatch(7L, List.of(11L, 12L, 13L), List.of(21L, 22L, 23L), "PPP", 100L, 1);
    }

    @Test
    void opensBothSemifinalsAtOnceForFourTeams() {
        startTournament(team(1, "P", "P", "P"), team(2, "P", "P", "P"), team(3, "P", "P", "P"), team(4, "P", "P", "P"));

        assertThat(storedSeries).extracting(MatchSeries::getRound)
            .containsExactly(MatchSeriesRound.SEMIFINAL, MatchSeriesRound.SEMIFINAL);
        assertThat(storedGames).extracting(Match::getSeriesGameNumber).containsExactly(1, 1);
    }

    @Test
    void setsUpTheNextGameOnceAResultIsIn() {
        startTournament(team(1, "P", "P", "P"), team(2, "P", "P", "P"));
        MatchSeries series = storedSeries.getFirst();

        record(game(series, 1), "HOME");
        service.sync(series.getId());

        assertThat(series.getHomeWins()).isEqualTo(1);
        assertThat(series.getStatus()).isEqualTo(MatchSeriesStatus.IN_PROGRESS);
        assertThat(game(series, 2).getStatus()).isEqualTo(MatchStatus.CONFIRMED);
    }

    @Test
    void endsBestOfThreeAtTwoWinsAndRanksTheTeams() {
        startTournament(team(1, "P", "P", "P"), team(2, "P", "P", "P"));
        MatchSeries series = storedSeries.getFirst();

        playAndSync(series, 1, "HOME");
        playAndSync(series, 2, "HOME");

        assertThat(series.getStatus()).isEqualTo(MatchSeriesStatus.COMPLETED);
        assertThat(series.getWinnerTeam()).isSameAs(teams.get(0));
        assertThat(tournament.getStatus()).isEqualTo(TeamTournamentStatus.COMPLETED);
        assertThat(tournament.getFinishedAt()).isNotNull();
        assertThat(teams).extracting(TournamentTeam::getFinalRank).containsExactly(1, 2);
        assertThat(storedGames).hasSize(2);
    }

    @Test
    void playsAllThreeGamesOfAMixedSeries() {
        startTournament(team(1, "PT", "PZ", "P"), team(2, "PTZ", "P", "P"));
        MatchSeries series = storedSeries.getFirst();
        assertThat(series.getFormat()).isEqualTo(MatchSeriesFormat.MIXED_THREE);

        playAndSync(series, 1, "HOME");
        assertThat(game(series, 2).getRaceComposition()).isEqualTo("PPT");
        playAndSync(series, 2, "HOME");

        assertThat(series.getStatus()).isEqualTo(MatchSeriesStatus.IN_PROGRESS);
        assertThat(game(series, 3).getRaceComposition()).isEqualTo("PPZ");
        playAndSync(series, 3, "AWAY");
        assertThat(series.getStatus()).isEqualTo(MatchSeriesStatus.COMPLETED);
        assertThat(series.getWinnerTeam()).isSameAs(teams.get(0));
    }

    @Test
    void opensTheFinalAndThirdPlaceOnceBothSemifinalsEnd() {
        startTournament(team(1, "P", "P", "P"), team(2, "P", "P", "P"), team(3, "P", "P", "P"), team(4, "P", "P", "P"));
        MatchSeries first = storedSeries.get(0);
        MatchSeries second = storedSeries.get(1);

        playAndSync(first, 1, "HOME");
        playAndSync(first, 2, "HOME");
        assertThat(findRound(MatchSeriesRound.FINAL)).isNull();
        playAndSync(second, 1, "AWAY");
        playAndSync(second, 2, "AWAY");

        MatchSeries finalSeries = findRound(MatchSeriesRound.FINAL);
        MatchSeries thirdPlace = findRound(MatchSeriesRound.THIRD_PLACE);
        assertThat(finalSeries.getHomeTeam()).isSameAs(teams.get(0));
        assertThat(finalSeries.getAwayTeam()).isSameAs(teams.get(3));
        assertThat(thirdPlace.getHomeTeam()).isSameAs(teams.get(1));
        assertThat(thirdPlace.getAwayTeam()).isSameAs(teams.get(2));
        assertThat(game(finalSeries, 1)).isNotNull();
        assertThat(game(thirdPlace, 1)).isNotNull();
        assertThat(tournament.getStatus()).isEqualTo(TeamTournamentStatus.IN_PROGRESS);

        playAndSync(finalSeries, 1, "AWAY");
        playAndSync(finalSeries, 2, "AWAY");
        playAndSync(thirdPlace, 1, "HOME");
        playAndSync(thirdPlace, 2, "HOME");

        assertThat(tournament.getStatus()).isEqualTo(TeamTournamentStatus.COMPLETED);
        assertThat(teams).extracting(TournamentTeam::getFinalRank).containsExactly(2, 3, 4, 1);
    }

    @Test
    void sendsTheSemifinalWinnerToMeetTheWaitingTeamInTheFinal() {
        startTournament(team(1, "P", "P", "P"), team(2, "P", "P", "P"), team(3, "P", "P", "P"));
        assertThat(storedSeries).extracting(MatchSeries::getRound).containsExactly(MatchSeriesRound.SEMIFINAL);
        MatchSeries semifinal = storedSeries.getFirst();

        playAndSync(semifinal, 1, "AWAY");
        playAndSync(semifinal, 2, "AWAY");

        MatchSeries finalSeries = findRound(MatchSeriesRound.FINAL);
        assertThat(finalSeries.getHomeTeam()).isSameAs(teams.get(1));
        assertThat(finalSeries.getAwayTeam()).isSameAs(teams.get(2));
        assertThat(findRound(MatchSeriesRound.THIRD_PLACE)).isNull();

        playAndSync(finalSeries, 1, "AWAY");
        playAndSync(finalSeries, 2, "AWAY");

        assertThat(tournament.getStatus()).isEqualTo(TeamTournamentStatus.COMPLETED);
        assertThat(teams).extracting(TournamentTeam::getFinalRank).containsExactly(3, 2, 1);
    }

    @Test
    void letsOnlyAdminsRecordGamesWhileTournamentsAreTriedOut() {
        startTournament(team(1, "P", "P", "P"), team(2, "P", "P", "P"));
        Match firstGame = game(storedSeries.getFirst(), 1);

        assertThatThrownBy(() -> service.checkResultChange(firstGame, "member@example.com", true))
            .isInstanceOf(MatchEditForbiddenException.class);
        assertThatCode(() -> service.checkResultChange(firstGame, ADMIN, true)).doesNotThrowAnyException();
    }

    @Test
    void keepsEarlierGamesFixedOnceALaterOneHasAResult() {
        startTournament(team(1, "P", "P", "P"), team(2, "P", "P", "P"));
        MatchSeries series = storedSeries.getFirst();
        playAndSync(series, 1, "HOME");
        playAndSync(series, 2, "AWAY");

        assertThatThrownBy(() -> service.checkResultChange(game(series, 1), ADMIN, false))
            .isInstanceOf(MatchConflictException.class);
        assertThatThrownBy(() -> service.checkDeletion(game(series, 1)))
            .isInstanceOf(MatchConflictException.class);
        assertThatCode(() -> service.checkResultChange(game(series, 2), ADMIN, false)).doesNotThrowAnyException();
    }

    @Test
    void keepsSemifinalsFixedOnceTheFinalHasAResult() {
        startTournament(team(1, "P", "P", "P"), team(2, "P", "P", "P"), team(3, "P", "P", "P"), team(4, "P", "P", "P"));
        MatchSeries first = storedSeries.get(0);
        MatchSeries second = storedSeries.get(1);
        playAndSync(first, 1, "HOME");
        playAndSync(first, 2, "HOME");
        playAndSync(second, 1, "HOME");
        playAndSync(second, 2, "HOME");
        assertThatCode(() -> service.checkResultChange(game(first, 2), ADMIN, false)).doesNotThrowAnyException();

        playAndSync(findRound(MatchSeriesRound.FINAL), 1, "HOME");

        assertThatThrownBy(() -> service.checkResultChange(game(first, 2), ADMIN, false))
            .isInstanceOf(MatchConflictException.class);
    }

    @Test
    void reopensTheSeriesWhenItsDecidingGameIsDeleted() {
        startTournament(team(1, "P", "P", "P"), team(2, "P", "P", "P"));
        MatchSeries series = storedSeries.getFirst();
        playAndSync(series, 1, "HOME");
        playAndSync(series, 2, "HOME");

        storedGames.remove(game(series, 2));
        service.sync(series.getId());

        assertThat(series.getStatus()).isEqualTo(MatchSeriesStatus.IN_PROGRESS);
        assertThat(series.getWinnerTeam()).isNull();
        assertThat(game(series, 2).getWinningTeam()).isNull();
        assertThat(tournament.getStatus()).isEqualTo(TeamTournamentStatus.IN_PROGRESS);
        assertThat(teams).extracting(TournamentTeam::getFinalRank).containsOnlyNulls();
    }

    @Test
    void dropsTheThirdGameWhenACorrectionDecidesTheSeries() {
        startTournament(team(1, "P", "P", "P"), team(2, "P", "P", "P"));
        MatchSeries series = storedSeries.getFirst();
        playAndSync(series, 1, "HOME");
        playAndSync(series, 2, "AWAY");
        assertThat(game(series, 3)).isNotNull();

        game(series, 2).setWinningTeam("HOME");
        service.sync(series.getId());

        assertThat(series.getStatus()).isEqualTo(MatchSeriesStatus.COMPLETED);
        assertThat(game(series, 3)).isNull();
    }

    @Test
    void leavesCancelledTournamentsAlone() {
        startTournament(team(1, "P", "P", "P"), team(2, "P", "P", "P"));
        MatchSeries series = storedSeries.getFirst();
        tournament.setStatus(TeamTournamentStatus.CANCELLED);
        record(game(series, 1), "HOME");

        service.sync(series.getId());

        assertThat(game(series, 2)).isNull();
        assertThatCode(() -> service.checkResultChange(game(series, 1), "member@example.com", false))
            .doesNotThrowAnyException();
    }

    @Test
    void removesOnlyGamesThatWereNeverPlayed() {
        startTournament(team(1, "P", "P", "P"), team(2, "P", "P", "P"), team(3, "P", "P", "P"), team(4, "P", "P", "P"));
        MatchSeries first = storedSeries.get(0);
        playAndSync(first, 1, "HOME");

        service.removeUnplayedGames(tournament);

        assertThat(storedGames).singleElement().satisfies(game -> {
            assertThat(game.getSeriesId()).isEqualTo(first.getId());
            assertThat(game.getWinningTeam()).isEqualTo("HOME");
        });
    }

    private void startTournament(TournamentTeam... created) {
        teams.addAll(List.of(created));
        tournament.setTeamCount(created.length);
        service.openFirstRound(tournament, teams);
    }

    private TournamentTeam team(int number, String... races) {
        TournamentTeam team = new TournamentTeam();
        ReflectionTestUtils.setField(team, "id", (long) number);
        team.setTournament(tournament);
        team.setTeamNumber(number);
        List<Long> memberIds = new ArrayList<>();
        for (int index = 0; index < races.length; index++) {
            long playerId = number * 10L + index + 1;
            Player player = new Player();
            player.setId(playerId);
            player.setRace(races[index]);
            player.setMmr(1000);
            players.put(playerId, player);
            memberIds.add(playerId);
        }
        team.setMemberPlayerIds(memberIds);
        return team;
    }

    private void playAndSync(MatchSeries series, int number, String winner) {
        Match game = game(series, number);
        assertThat(game).as("game %d of series %d", number, series.getId()).isNotNull();
        record(game, winner);
        service.sync(series.getId());
    }

    private void record(Match game, String winner) {
        game.setWinningTeam(winner);
        game.setStatus(MatchStatus.COMPLETED);
    }

    private Match game(MatchSeries series, int number) {
        return storedGames.stream()
            .filter(game -> Objects.equals(game.getSeriesId(), series.getId()) && game.getSeriesGameNumber() == number)
            .findFirst()
            .orElse(null);
    }

    private MatchSeries findRound(MatchSeriesRound round) {
        return storedSeries.stream().filter(series -> series.getRound() == round).findFirst().orElse(null);
    }

    private List<Match> gamesWhere(java.util.function.Predicate<Match> filter) {
        return storedGames.stream()
            .filter(filter)
            .sorted(Comparator.comparing(Match::getSeriesGameNumber))
            .toList();
    }
}
