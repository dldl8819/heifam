package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.tournament.dto.TeamTournamentResponse;
import com.balancify.backend.api.tournament.dto.TournamentGamePlayerResponse;
import com.balancify.backend.api.tournament.dto.TournamentGameResponse;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.MatchSeries;
import com.balancify.backend.domain.MatchSeriesFormat;
import com.balancify.backend.domain.MatchSeriesRound;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeamTournamentServiceTest {

    private static final Long GROUP_ID = 1L;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private PlayerRepository playerRepository;

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
    private TournamentProgressService tournamentProgressService;

    @Mock
    private OperationAuditLogService operationAuditLogService;

    private TeamTournamentService service;
    private Group group;
    private final Map<Long, Player> players = new HashMap<>();
    private final List<TournamentTeam> savedTeams = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new TeamTournamentService(
            groupRepository,
            playerRepository,
            teamTournamentRepository,
            tournamentTeamRepository,
            matchSeriesRepository,
            matchRepository,
            matchParticipantRepository,
            tournamentProgressService,
            operationAuditLogService
        );
        group = new Group();
        group.setId(GROUP_ID);
        when(groupRepository.findByIdForUpdate(GROUP_ID)).thenReturn(Optional.of(group));
        when(teamTournamentRepository.save(any(TeamTournament.class))).thenAnswer(invocation -> {
            TeamTournament tournament = invocation.getArgument(0);
            ReflectionTestUtils.setField(tournament, "id", 50L);
            return tournament;
        });
        when(tournamentTeamRepository.save(any(TournamentTeam.class))).thenAnswer(invocation -> {
            TournamentTeam team = invocation.getArgument(0);
            ReflectionTestUtils.setField(team, "id", 60L + savedTeams.size());
            savedTeams.add(team);
            return team;
        });
        when(tournamentTeamRepository.findByTournament_IdOrderByTeamNumberAsc(any())).thenAnswer(invocation -> savedTeams);
        when(playerRepository.findByGroup_IdAndIdIn(eq(GROUP_ID), anyList())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(1);
            return ids.stream().filter(players::containsKey).map(players::get).toList();
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
    }

    @Test
    void refusesASecondTournamentWhileOneIsRunning() {
        addPlayers(6);
        when(teamTournamentRepository.findFirstByGroup_IdAndStatusOrderByIdDesc(GROUP_ID, TeamTournamentStatus.IN_PROGRESS))
            .thenReturn(Optional.of(new TeamTournament()));

        assertThatThrownBy(() -> service.create(GROUP_ID, ids(6), "admin@example.com", null, false))
            .isInstanceOf(MatchConflictException.class);
        verify(teamTournamentRepository, never()).save(any());
    }

    @Test
    void refusesPlayerCountsThatDoNotMakeTeams() {
        addPlayers(15);

        assertThatThrownBy(() -> service.create(GROUP_ID, ids(15), "admin@example.com", null, false))
            .isInstanceOf(IllegalArgumentException.class);
        verify(groupRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void refusesPlayersOutsideTheGroup() {
        addPlayers(5);

        assertThatThrownBy(() -> service.create(GROUP_ID, ids(6), "admin@example.com", null, false))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void makesTwoTeamsAndSeatsWhoeverWaitedLastTime() {
        addPlayers(7);
        TeamTournament previous = new TeamTournament();
        previous.setWaitingPlayerIds(new LinkedHashSet<>(Set.of(7L)));
        when(teamTournamentRepository.findFirstByGroup_IdOrderByIdDesc(GROUP_ID)).thenReturn(Optional.of(previous));

        TeamTournamentResponse response = service.create(GROUP_ID, ids(7), "admin@example.com", "YOUR_USERNAME", true);

        ArgumentCaptor<TeamTournament> saved = ArgumentCaptor.forClass(TeamTournament.class);
        verify(teamTournamentRepository).save(saved.capture());
        assertThat(saved.getValue().getTeamCount()).isEqualTo(2);
        assertThat(saved.getValue().getWaitingPlayerIds()).hasSize(1).doesNotContain(7L);
        assertThat(savedTeams).extracting(TournamentTeam::getTeamNumber).containsExactly(1, 2);
        assertThat(savedTeams).allSatisfy(team -> assertThat(team.getMemberPlayerIds()).hasSize(3));
        verify(tournamentProgressService).openFirstRound(saved.getValue(), savedTeams);
        verify(operationAuditLogService).recordTournamentCreated("admin@example.com", "YOUR_USERNAME", 50L, GROUP_ID, 2, 1);
        assertThat(response.teams()).hasSize(2);
        assertThat(response.waitingPlayers()).hasSize(1);
        assertThat(response.teams().getFirst().totalMmr()).isNotNull();
    }

    @Test
    void keepsMmrFromThoseWhoCannotSeeIt() {
        addPlayers(6);

        TeamTournamentResponse response = service.create(GROUP_ID, ids(6), "admin@example.com", null, false);

        assertThat(response.teams()).allSatisfy(team -> {
            assertThat(team.totalMmr()).isNull();
            assertThat(team.members()).allSatisfy(member -> assertThat(member.mmr()).isNull());
        });
    }

    @Test
    void cancelsARunningTournamentAndClearsItsUnplayedGames() {
        TeamTournament tournament = tournament(TeamTournamentStatus.IN_PROGRESS);

        TeamTournamentResponse response = service.cancel(GROUP_ID, 50L, "admin@example.com", "YOUR_USERNAME", false);

        assertThat(tournament.getStatus()).isEqualTo(TeamTournamentStatus.CANCELLED);
        assertThat(tournament.getFinishedAt()).isNotNull();
        verify(tournamentProgressService).removeUnplayedGames(tournament);
        verify(operationAuditLogService).recordTournamentCancelled("admin@example.com", "YOUR_USERNAME", 50L, GROUP_ID);
        assertThat(response.status()).isEqualTo("CANCELLED");
    }

    @Test
    void refusesToCancelAFinishedTournament() {
        tournament(TeamTournamentStatus.COMPLETED);

        assertThatThrownBy(() -> service.cancel(GROUP_ID, 50L, "admin@example.com", null, false))
            .isInstanceOf(MatchConflictException.class);
        verify(tournamentProgressService, never()).removeUnplayedGames(any());
    }

    @Test
    void showsRecordedRacesForPlayedGamesAndPlannedOnesAfterThem() {
        TeamTournament tournament = tournament(TeamTournamentStatus.IN_PROGRESS);
        addPlayer(1, "PT");
        addPlayer(2, "P");
        addPlayer(3, "P");
        addPlayer(4, "PZ");
        addPlayer(5, "PT");
        addPlayer(6, "P");
        TournamentTeam home = team(tournament, 1, 1L, 2L, 3L);
        TournamentTeam away = team(tournament, 2, 4L, 5L, 6L);
        MatchSeries series = new MatchSeries();
        ReflectionTestUtils.setField(series, "id", 70L);
        series.setTournament(tournament);
        series.setRound(MatchSeriesRound.FINAL);
        series.setBracketSlot(1);
        series.setHomeTeam(home);
        series.setAwayTeam(away);
        series.setFormat(MatchSeriesFormat.MIXED_THREE);
        series.setGameCompositions("PPP,PPT,PPP");
        series.setHomeWins(1);
        when(matchSeriesRepository.findByTournament_IdOrderByIdAsc(50L)).thenReturn(List.of(series));
        Match firstGame = new Match();
        firstGame.setId(900L);
        firstGame.setSeriesId(70L);
        firstGame.setSeriesGameNumber(1);
        firstGame.setRaceComposition("PPP");
        firstGame.setWinningTeam("HOME");
        firstGame.setStatus(MatchStatus.COMPLETED);
        when(matchRepository.findBySeriesIdInOrderBySeriesGameNumberAsc(List.of(70L))).thenReturn(List.of(firstGame));
        List<MatchParticipant> participants = new ArrayList<>();
        for (long playerId = 1; playerId <= 6; playerId++) {
            participants.add(participant(firstGame, playerId, playerId <= 3 ? "HOME" : "AWAY"));
        }
        when(matchParticipantRepository.findByMatchIdInWithPlayerAndMatch(List.of(900L))).thenReturn(participants);

        TeamTournamentResponse response = service.get(GROUP_ID, 50L, false);

        List<TournamentGameResponse> games = response.series().getFirst().games();
        assertThat(games).extracting(TournamentGameResponse::status).containsExactly("PLAYED", "UPCOMING", "UPCOMING");
        assertThat(games.get(0).matchId()).isEqualTo(900L);
        assertThat(games.get(0).winnerTeam()).isEqualTo("HOME");
        assertThat(games.get(1).homePlayers()).extracting(TournamentGamePlayerResponse::assignedRace)
            .containsExactly("T", "P", "P");
        assertThat(games.get(1).awayPlayers()).extracting(TournamentGamePlayerResponse::assignedRace)
            .containsExactly("P", "T", "P");
        assertThat(response.series().getFirst().homeTeamNumber()).isEqualTo(1);
    }

    private TeamTournament tournament(TeamTournamentStatus status) {
        TeamTournament tournament = new TeamTournament();
        ReflectionTestUtils.setField(tournament, "id", 50L);
        tournament.setGroup(group);
        tournament.setTeamCount(2);
        tournament.setStatus(status);
        when(teamTournamentRepository.findByIdAndGroup_Id(50L, GROUP_ID)).thenReturn(Optional.of(tournament));
        when(teamTournamentRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(tournament));
        return tournament;
    }

    private TournamentTeam team(TeamTournament tournament, int number, Long... memberIds) {
        TournamentTeam team = new TournamentTeam();
        ReflectionTestUtils.setField(team, "id", 60L + number);
        team.setTournament(tournament);
        team.setTeamNumber(number);
        team.setMemberPlayerIds(new ArrayList<>(List.of(memberIds)));
        savedTeams.add(team);
        return team;
    }

    private MatchParticipant participant(Match match, long playerId, String side) {
        MatchParticipant participant = new MatchParticipant();
        participant.setMatch(match);
        participant.setPlayer(players.get(playerId));
        participant.setTeam(side);
        participant.setAssignedRace("P");
        return participant;
    }

    private void addPlayers(int count) {
        for (long id = 1; id <= count; id++) {
            addPlayer(id, "P");
        }
    }

    private void addPlayer(long id, String race) {
        Player player = new Player();
        player.setId(id);
        player.setGroup(group);
        player.setNickname("YOUR_USERNAME_" + id);
        player.setRace(race);
        player.setMmr(1000 + (int) id * 10);
        players.put(id, player);
    }

    private List<Long> ids(int count) {
        List<Long> ids = new ArrayList<>();
        for (long id = 1; id <= count; id++) {
            ids.add(id);
        }
        return ids;
    }
}
