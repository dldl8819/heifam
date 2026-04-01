package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.match.dto.MultiBalanceMatchResponse;
import com.balancify.backend.api.match.dto.MultiBalanceRequest;
import com.balancify.backend.api.match.dto.MultiBalanceResponse;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchRepository;
import com.balancify.backend.repository.PlayerRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class MultiMatchBalancingServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private MatchParticipantRepository matchParticipantRepository;

    private final TeamBalancingService teamBalancingService = new TeamBalancingService(null);

    @BeforeEach
    void setUp() {
        lenient()
            .when(matchRepository.findRecentByGroupId(eq(1L), any(Pageable.class)))
            .thenReturn(List.of());
    }

    @Test
    void generatesTwo3v3AndOne2v2WhenSixteenPlayersAreProvided() {
        MultiMatchBalancingService service = createService();
        List<Player> players = createPlayers(1L, 16, 2400, 20, List.of("P", "T", "Z"));
        List<Long> playerIds = players.stream().map(Player::getId).toList();
        when(playerRepository.findByGroup_IdAndIdIn(1L, playerIds)).thenReturn(players);

        MultiBalanceResponse response = service.balance(new MultiBalanceRequest(1L, playerIds, "MMR_FIRST"));

        assertThat(response.totalPlayers()).isEqualTo(16);
        assertThat(response.assignedPlayers()).isEqualTo(16);
        assertThat(response.waitingPlayers()).isEmpty();
        assertThat(response.matchCount()).isEqualTo(3);
        assertThat(response.matches()).hasSize(3);
        assertThat(countMatchesByTeamSize(response.matches(), 3)).isEqualTo(2);
        assertThat(countMatchesByTeamSize(response.matches(), 2)).isEqualTo(1);
        assertMatchShapes(response.matches());
        assertThat(collectAssignedIds(response.matches())).hasSize(16);
    }

    @Test
    void generatesOne3v3AndTwo2v2WhenFourteenPlayersAreProvided() {
        MultiMatchBalancingService service = createService();
        List<Player> players = createPlayers(1L, 14, 2200, 18, List.of("P", "T", "Z"));
        List<Long> playerIds = players.stream().map(Player::getId).toList();
        when(playerRepository.findByGroup_IdAndIdIn(1L, playerIds)).thenReturn(players);

        MultiBalanceResponse response = service.balance(new MultiBalanceRequest(1L, playerIds, "MMR_FIRST"));

        assertThat(response.totalPlayers()).isEqualTo(14);
        assertThat(response.assignedPlayers()).isEqualTo(14);
        assertThat(response.waitingPlayers()).isEmpty();
        assertThat(response.matches()).hasSize(3);
        assertThat(countMatchesByTeamSize(response.matches(), 3)).isEqualTo(1);
        assertThat(countMatchesByTeamSize(response.matches(), 2)).isEqualTo(2);
        assertMatchShapes(response.matches());
    }

    @Test
    void generatesOne3v3AndOne2v2WhenTenPlayersAreProvided() {
        MultiMatchBalancingService service = createService();
        List<Player> players = createPlayers(1L, 10, 2100, 16, List.of("P", "T", "Z"));
        List<Long> playerIds = players.stream().map(Player::getId).toList();
        when(playerRepository.findByGroup_IdAndIdIn(1L, playerIds)).thenReturn(players);

        MultiBalanceResponse response = service.balance(new MultiBalanceRequest(1L, playerIds, "MMR_FIRST"));

        assertThat(response.totalPlayers()).isEqualTo(10);
        assertThat(response.assignedPlayers()).isEqualTo(10);
        assertThat(response.waitingPlayers()).isEmpty();
        assertThat(response.matches()).hasSize(2);
        assertThat(countMatchesByTeamSize(response.matches(), 3)).isEqualTo(1);
        assertThat(countMatchesByTeamSize(response.matches(), 2)).isEqualTo(1);
        assertMatchShapes(response.matches());
    }

    @Test
    void generatesThree3v3AndTwoWaitingWhenTwentyPlayersAreProvided() {
        MultiMatchBalancingService service = createService();
        List<Player> players = createPlayers(1L, 20, 2600, 14, List.of("P", "T", "Z"));
        List<Long> playerIds = players.stream().map(Player::getId).toList();
        when(playerRepository.findByGroup_IdAndIdIn(1L, playerIds)).thenReturn(players);

        MultiBalanceResponse response = service.balance(new MultiBalanceRequest(1L, playerIds, "MMR_FIRST"));

        assertThat(response.totalPlayers()).isEqualTo(20);
        assertThat(response.assignedPlayers()).isEqualTo(18);
        assertThat(response.waitingPlayers()).hasSize(2);
        assertThat(response.matches()).hasSize(3);
        assertThat(countMatchesByTeamSize(response.matches(), 3)).isEqualTo(3);
        assertThat(countMatchesByTeamSize(response.matches(), 2)).isZero();

        Set<Long> assignedIds = collectAssignedIds(response.matches());
        Set<Long> waitingIds = response.waitingPlayers().stream()
            .map(waiting -> waiting.id())
            .collect(HashSet::new, Set::add, Set::addAll);
        assertThat(assignedIds).hasSize(18);
        assertThat(waitingIds).hasSize(2);
        assertThat(assignedIds).doesNotContainAnyElementsOf(waitingIds);
    }

    @Test
    void generatesTwo2v2WhenEightPlayersAreProvided() {
        MultiMatchBalancingService service = createService();
        List<Player> players = createPlayers(1L, 8, 1800, 12, List.of("P", "T", "Z"));
        List<Long> playerIds = players.stream().map(Player::getId).toList();
        when(playerRepository.findByGroup_IdAndIdIn(1L, playerIds)).thenReturn(players);

        MultiBalanceResponse response = service.balance(new MultiBalanceRequest(1L, playerIds, "MMR_FIRST"));

        assertThat(response.totalPlayers()).isEqualTo(8);
        assertThat(response.assignedPlayers()).isEqualTo(8);
        assertThat(response.waitingPlayers()).isEmpty();
        assertThat(response.matches()).hasSize(2);
        assertThat(countMatchesByTeamSize(response.matches(), 2)).isEqualTo(2);
        assertThat(countMatchesByTeamSize(response.matches(), 3)).isZero();
        assertMatchShapes(response.matches());
    }

    @Test
    void generatesThree3v3WhenEighteenPlayersAreProvided() {
        MultiMatchBalancingService service = createService();
        List<Player> players = createPlayers(1L, 18, 2500, 14, List.of("P", "T", "Z"));
        List<Long> playerIds = players.stream().map(Player::getId).toList();
        when(playerRepository.findByGroup_IdAndIdIn(1L, playerIds)).thenReturn(players);

        MultiBalanceResponse response = service.balance(new MultiBalanceRequest(1L, playerIds, "MMR_FIRST"));

        assertThat(response.totalPlayers()).isEqualTo(18);
        assertThat(response.assignedPlayers()).isEqualTo(18);
        assertThat(response.waitingPlayers()).isEmpty();
        assertThat(response.matches()).hasSize(3);
        assertThat(countMatchesByTeamSize(response.matches(), 3)).isEqualTo(3);
        assertThat(countMatchesByTeamSize(response.matches(), 2)).isZero();
        assertMatchShapes(response.matches());
    }

    @Test
    void defaultsToMmrFirstWhenModeIsMissing() {
        MultiMatchBalancingService service = createService();
        List<Player> players = createPlayers(1L, 12, 2000, 15, List.of("P", "T", "Z"));
        List<Long> playerIds = players.stream().map(Player::getId).toList();
        when(playerRepository.findByGroup_IdAndIdIn(1L, playerIds)).thenReturn(players);

        MultiBalanceResponse response = service.balance(new MultiBalanceRequest(1L, playerIds));

        assertThat(response.balanceMode()).isEqualTo("MMR_FIRST");
        assertThat(response.matches()).hasSize(2);
    }

    @Test
    void diversityModePrefersLowerRepeatPenalties() {
        MultiMatchBalancingService service = createService();
        List<Player> players = createPlayers(1L, 6, 1000, 0, List.of("P", "P", "P", "P", "P", "P"));
        List<Long> playerIds = players.stream().map(Player::getId).toList();
        when(playerRepository.findByGroup_IdAndIdIn(1L, playerIds)).thenReturn(players);
        stubRecentMatch(1L, players, List.of(1L, 2L, 3L), List.of(4L, 5L, 6L));

        MultiBalanceResponse mmr = service.balance(new MultiBalanceRequest(1L, playerIds, "MMR_FIRST"));
        MultiBalanceResponse diversity = service.balance(new MultiBalanceRequest(1L, playerIds, "DIVERSITY_FIRST"));

        assertThat(diversity.matches()).hasSize(1);
        assertThat(diversity.matches().get(0).penaltySummary().repeatTeammatePenalty())
            .isLessThanOrEqualTo(mmr.matches().get(0).penaltySummary().repeatTeammatePenalty());
        assertThat(diversity.matches().get(0).penaltySummary().repeatMatchupPenalty())
            .isLessThanOrEqualTo(mmr.matches().get(0).penaltySummary().repeatMatchupPenalty());
    }

    @Test
    void raceModePrefersLowerRacePenalty() {
        MultiMatchBalancingService service = createService();
        List<Player> players = createPlayers(1L, 6, 1000, 0, List.of("P", "P", "P", "T", "Z", "Z"));
        List<Long> playerIds = players.stream().map(Player::getId).toList();
        when(playerRepository.findByGroup_IdAndIdIn(1L, playerIds)).thenReturn(players);

        MultiBalanceResponse mmr = service.balance(new MultiBalanceRequest(1L, playerIds, "MMR_FIRST"));
        MultiBalanceResponse race = service.balance(new MultiBalanceRequest(1L, playerIds, "RACE_DISTRIBUTION_FIRST"));

        assertThat(race.matches()).hasSize(1);
        assertThat(race.matches().get(0).penaltySummary().racePenalty())
            .isLessThanOrEqualTo(mmr.matches().get(0).penaltySummary().racePenalty());
    }

    @Test
    void throwsWhenDuplicatePlayerIdsAreProvided() {
        MultiMatchBalancingService service = createService();

        assertThatThrownBy(() ->
            service.balance(new MultiBalanceRequest(1L, List.of(1L, 2L, 3L, 4L, 4L), "MMR_FIRST"))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("playerIds must not contain duplicates");
    }

    private MultiMatchBalancingService createService() {
        return new MultiMatchBalancingService(
            playerRepository,
            matchRepository,
            matchParticipantRepository,
            teamBalancingService,
            10,
            1000.0,
            150.0,
            2.0,
            1.5,
            1.0
        );
    }

    private void assertMatchShapes(List<MultiBalanceMatchResponse> matches) {
        for (MultiBalanceMatchResponse match : matches) {
            assertThat(match.teamSize()).isIn(2, 3);
            assertThat(match.matchType()).isEqualTo(match.teamSize() == 3 ? "3v3" : "2v2");
            assertThat(match.homeTeam()).hasSize(match.teamSize());
            assertThat(match.awayTeam()).hasSize(match.teamSize());
        }
    }

    private int countMatchesByTeamSize(List<MultiBalanceMatchResponse> matches, int teamSize) {
        return (int) matches.stream().filter(match -> match.teamSize() == teamSize).count();
    }

    private Set<Long> collectAssignedIds(List<MultiBalanceMatchResponse> matches) {
        Set<Long> allIds = new HashSet<>();
        for (MultiBalanceMatchResponse match : matches) {
            match.homeTeam().forEach(player -> allIds.add(player.playerId()));
            match.awayTeam().forEach(player -> allIds.add(player.playerId()));
        }
        return allIds;
    }

    private void stubRecentMatch(
        Long groupId,
        List<Player> players,
        List<Long> homeIds,
        List<Long> awayIds
    ) {
        Match match = new Match();
        match.setId(999L);
        Group group = new Group();
        group.setId(groupId);
        match.setGroup(group);
        match.setWinningTeam("HOME");

        Map<Long, Player> playerById = new HashMap<>();
        for (Player player : players) {
            playerById.put(player.getId(), player);
        }

        List<MatchParticipant> participants = new ArrayList<>();
        long participantId = 1L;
        for (Long playerId : homeIds) {
            MatchParticipant participant = new MatchParticipant();
            participant.setId(participantId++);
            participant.setMatch(match);
            participant.setPlayer(playerById.get(playerId));
            participant.setTeam("HOME");
            participants.add(participant);
        }
        for (Long playerId : awayIds) {
            MatchParticipant participant = new MatchParticipant();
            participant.setId(participantId++);
            participant.setMatch(match);
            participant.setPlayer(playerById.get(playerId));
            participant.setTeam("AWAY");
            participants.add(participant);
        }

        when(matchRepository.findRecentByGroupId(eq(groupId), any(Pageable.class))).thenReturn(List.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(match.getId())).thenReturn(participants);
    }

    private List<Player> createPlayers(
        Long groupId,
        int count,
        int startMmr,
        int mmrStep,
        List<String> races
    ) {
        Group group = new Group();
        group.setId(groupId);

        List<Player> players = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            Player player = new Player();
            player.setId((long) (index + 1));
            player.setGroup(group);
            player.setNickname("P" + (index + 1));
            player.setRace(races.get(index % races.size()));
            player.setMmr(startMmr - (index * mmrStep));
            players.add(player);
        }
        return players;
    }
}
