package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeamScoreServiceTest {

    @Mock
    private TeamTournamentRepository teamTournamentRepository;

    @Mock
    private TournamentTeamRepository tournamentTeamRepository;

    @Mock
    private MatchSeriesRepository matchSeriesRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private PlayerStatsRepository playerStatsRepository;

    private TeamScoreService service;
    private final Map<Long, Player> players = new HashMap<>();
    private final List<TournamentTeam> teams = new ArrayList<>();
    private final List<MatchSeries> series = new ArrayList<>();
    private final List<PlayerStats> stats = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new TeamScoreService(
            teamTournamentRepository,
            tournamentTeamRepository,
            matchSeriesRepository,
            playerRepository,
            playerStatsRepository
        );
        when(tournamentTeamRepository.findByTournament_IdIn(anyCollection())).thenReturn(teams);
        when(matchSeriesRepository.findByTournament_IdInAndStatus(anyCollection(), eq(MatchSeriesStatus.COMPLETED)))
            .thenReturn(series);
        when(playerRepository.findAllById(any())).thenAnswer(invocation -> {
            List<Player> found = new ArrayList<>();
            for (Long id : invocation.<Iterable<Long>>getArgument(0)) {
                if (players.containsKey(id)) {
                    found.add(players.get(id));
                }
            }
            return found;
        });
        when(playerStatsRepository.findByGroupId(1L)).thenReturn(stats);
    }

    @Test
    void scoresPlacesAndSeriesNextToThePersonalRecord() {
        TeamTournament finished = tournament(10L, TeamTournamentStatus.COMPLETED);
        TournamentTeam champions = team(finished, 100L, 1, 1L, 2L, 3L);
        TournamentTeam runnersUp = team(finished, 101L, 2, 4L, 5L, 6L);
        TournamentTeam third = team(finished, 102L, 3, 7L, 8L, 9L);
        series(finished, third, champions, champions);
        series(finished, champions, runnersUp, champions);
        when(teamTournamentRepository.findByGroup_Id(1L)).thenReturn(List.of(finished));
        stat(1L, 3, 7);

        TeamScoreBoardResponse board = service.board(1L);

        assertThat(board.entries()).extracting(
            TeamScoreEntryResponse::rank,
            TeamScoreEntryResponse::points,
            TeamScoreEntryResponse::seriesWins,
            TeamScoreEntryResponse::seriesLosses
        ).containsExactly(
            tuple(1, 3, 2, 0), tuple(1, 3, 2, 0), tuple(1, 3, 2, 0),
            tuple(4, 2, 0, 1), tuple(4, 2, 0, 1), tuple(4, 2, 0, 1),
            tuple(7, 1, 0, 1), tuple(7, 1, 0, 1), tuple(7, 1, 0, 1)
        );
        TeamScoreEntryResponse first = board.entries().getFirst();
        assertThat(first.nickname()).isEqualTo("YOUR_USERNAME_1");
        assertThat(first.championships()).isEqualTo(1);
        assertThat(first.tournaments()).isEqualTo(1);
        assertThat(first.seriesWinRate()).isEqualTo(100.0);
        assertThat(first.winRate()).isEqualTo(30.0);
        assertThat(board.entries().get(3).runnerUps()).isEqualTo(1);
        assertThat(board.entries().get(6).thirdPlaces()).isEqualTo(1);
    }

    @Test
    void countsSeriesOfACancelledTournamentButNoPlaces() {
        TeamTournament cancelled = tournament(11L, TeamTournamentStatus.CANCELLED);
        TournamentTeam home = team(cancelled, 110L, 1, 1L, 2L, 3L);
        TournamentTeam away = team(cancelled, 111L, 2, 4L, 5L, 6L);
        home.setFinalRank(null);
        series(cancelled, home, away, away);
        when(teamTournamentRepository.findByGroup_Id(1L)).thenReturn(List.of(cancelled));

        TeamScoreBoardResponse board = service.board(1L);

        assertThat(board.entries()).allSatisfy(entry -> {
            assertThat(entry.points()).isZero();
            assertThat(entry.tournaments()).isZero();
        });
        assertThat(board.entries().getFirst().seriesWins()).isEqualTo(1);
        assertThat(board.entries().getFirst().winRate()).isNull();
    }

    @Test
    void leavesOutPlayersWhoLeft() {
        TeamTournament finished = tournament(12L, TeamTournamentStatus.COMPLETED);
        TournamentTeam winners = team(finished, 120L, 1, 1L, 2L, 3L);
        TournamentTeam losers = team(finished, 121L, 2, 4L, 5L, 6L);
        series(finished, winners, losers, winners);
        players.get(2L).setActive(false);
        when(teamTournamentRepository.findByGroup_Id(1L)).thenReturn(List.of(finished));

        assertThat(service.board(1L).entries()).extracting(TeamScoreEntryResponse::playerId)
            .containsExactly(1L, 3L, 4L, 5L, 6L);
    }

    @Test
    void isEmptyBeforeAnyTournament() {
        when(teamTournamentRepository.findByGroup_Id(1L)).thenReturn(List.of());

        assertThat(service.board(1L).entries()).isEmpty();
    }

    private TeamTournament tournament(Long id, TeamTournamentStatus status) {
        TeamTournament tournament = new TeamTournament();
        ReflectionTestUtils.setField(tournament, "id", id);
        tournament.setStatus(status);
        return tournament;
    }

    private TournamentTeam team(TeamTournament tournament, Long id, int finalRank, Long... memberIds) {
        TournamentTeam team = new TournamentTeam();
        ReflectionTestUtils.setField(team, "id", id);
        team.setTournament(tournament);
        team.setFinalRank(finalRank);
        team.setMemberPlayerIds(new ArrayList<>(List.of(memberIds)));
        for (Long memberId : memberIds) {
            Player player = new Player();
            player.setId(memberId);
            player.setNickname("YOUR_USERNAME_" + memberId);
            players.put(memberId, player);
        }
        teams.add(team);
        return team;
    }

    private void series(TeamTournament tournament, TournamentTeam home, TournamentTeam away, TournamentTeam winner) {
        MatchSeries played = new MatchSeries();
        played.setTournament(tournament);
        played.setHomeTeam(home);
        played.setAwayTeam(away);
        played.setWinnerTeam(winner);
        played.setStatus(MatchSeriesStatus.COMPLETED);
        series.add(played);
    }

    private void stat(Long playerId, int wins, int losses) {
        PlayerStats stat = new PlayerStats();
        ReflectionTestUtils.setField(stat, "playerId", playerId);
        stat.setWins(wins);
        stat.setLosses(losses);
        stats.add(stat);
    }
}
