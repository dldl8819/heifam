package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.match.dto.MatchResultRequest;
import com.balancify.backend.api.match.dto.MatchResultResponse;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.MmrHistory;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchRepository;
import com.balancify.backend.repository.MmrHistoryRepository;
import com.balancify.backend.repository.PlayerRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchResultServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private MatchParticipantRepository matchParticipantRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private MmrHistoryRepository mmrHistoryRepository;

    private MatchResultService matchResultService;

    @BeforeEach
    void setUp() {
        matchResultService = new MatchResultService(
            matchRepository,
            matchParticipantRepository,
            playerRepository,
            mmrHistoryRepository,
            32,
            300,
            900,
            0.6,
            0.7
        );
    }

    @Test
    void processesResultAndUpdatesMmrForAllParticipants() {
        Match match = new Match();
        match.setId(1L);

        List<MatchParticipant> participants = buildParticipants(match);

        when(matchRepository.findById(1L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(1L)).thenReturn(participants);
        when(matchParticipantRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mmrHistoryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MatchResultResponse response = matchResultService.processMatchResult(
            1L,
            new MatchResultRequest("HOME")
        );

        double homeExpected = 1.0 / (1.0 + Math.pow(10.0, (950.0 - 1100.0) / 400.0));
        int homeDelta = (int) Math.round(32 * (1.0 - homeExpected));
        int awayDelta = (int) Math.round(32 * (0.0 - (1.0 - homeExpected)));

        assertThat(response.matchId()).isEqualTo(1L);
        assertThat(response.winnerTeam()).isEqualTo("HOME");
        assertThat(response.kFactor()).isEqualTo(32);
        assertThat(response.homeExpectedWinRate()).isEqualTo(Math.round(homeExpected * 10000.0) / 10000.0);
        assertThat(response.awayExpectedWinRate()).isEqualTo(Math.round((1.0 - homeExpected) * 10000.0) / 10000.0);
        assertThat(response.participants()).hasSize(6);

        participants.stream()
            .filter(participant -> "HOME".equals(participant.getTeam()))
            .forEach(participant -> {
                assertThat(participant.getMmrBefore()).isNotNull();
                assertThat(participant.getMmrAfter()).isEqualTo(participant.getMmrBefore() + homeDelta);
                assertThat(participant.getMmrDelta()).isEqualTo(homeDelta);
                assertThat(participant.getPlayer().getMmr()).isEqualTo(participant.getMmrAfter());
            });

        participants.stream()
            .filter(participant -> "AWAY".equals(participant.getTeam()))
            .forEach(participant -> {
                assertThat(participant.getMmrBefore()).isNotNull();
                assertThat(participant.getMmrAfter()).isEqualTo(participant.getMmrBefore() + awayDelta);
                assertThat(participant.getMmrDelta()).isEqualTo(awayDelta);
                assertThat(participant.getPlayer().getMmr()).isEqualTo(participant.getMmrAfter());
            });

        assertThat(match.getWinningTeam()).isEqualTo("HOME");

        ArgumentCaptor<List<MmrHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(mmrHistoryRepository).saveAll(historyCaptor.capture());
        assertThat(historyCaptor.getValue()).hasSize(6);
        historyCaptor.getValue().forEach(history -> {
            assertThat(history.getMatch()).isSameAs(match);
            assertThat(history.getPlayer()).isNotNull();
            assertThat(history.getBeforeMmr()).isNotNull();
            assertThat(history.getAfterMmr()).isNotNull();
        });
    }

    @Test
    void throwsWhenWinnerTeamIsInvalid() {
        Match match = new Match();
        match.setId(1L);

        when(matchRepository.findById(1L)).thenReturn(Optional.of(match));

        assertThatThrownBy(() -> matchResultService.processMatchResult(1L, new MatchResultRequest("BLUE")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("winnerTeam must be HOME or AWAY");

        verify(matchParticipantRepository, never()).findByMatchIdWithPlayerAndMatch(any());
        verify(mmrHistoryRepository, never()).saveAll(any());
    }

    @Test
    void allowsReprocessingWhenMatchAlreadyHasResult() {
        Match match = new Match();
        match.setId(1L);
        match.setWinningTeam("HOME");

        Group group = new Group();
        group.setId(1L);

        List<MatchParticipant> participants = List.of(
            participant(11L, match, player(1L, group, "H1", 1013), "HOME"),
            participant(12L, match, player(2L, group, "H2", 1013), "HOME"),
            participant(13L, match, player(3L, group, "H3", 1013), "HOME"),
            participant(14L, match, player(4L, group, "A1", 987), "AWAY"),
            participant(15L, match, player(5L, group, "A2", 987), "AWAY"),
            participant(16L, match, player(6L, group, "A3", 987), "AWAY")
        );
        participants.stream()
            .filter(participant -> "HOME".equals(participant.getTeam()))
            .forEach(participant -> {
                participant.setMmrBefore(1000);
                participant.setMmrAfter(1013);
                participant.setMmrDelta(13);
            });
        participants.stream()
            .filter(participant -> "AWAY".equals(participant.getTeam()))
            .forEach(participant -> {
                participant.setMmrBefore(1000);
                participant.setMmrAfter(987);
                participant.setMmrDelta(-13);
            });

        List<MmrHistory> histories = participants.stream().map(participant -> {
            MmrHistory history = new MmrHistory();
            history.setMatch(match);
            history.setPlayer(participant.getPlayer());
            history.setBeforeMmr(1000);
            history.setAfterMmr(participant.getMmrAfter());
            return history;
        }).toList();

        when(matchRepository.findById(1L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(1L)).thenReturn(participants);
        when(mmrHistoryRepository.findByMatch_Id(1L)).thenReturn(histories);
        when(matchParticipantRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mmrHistoryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MatchResultResponse response = matchResultService.processMatchResult(1L, new MatchResultRequest("AWAY"));

        assertThat(response.winnerTeam()).isEqualTo("AWAY");
        assertThat(match.getWinningTeam()).isEqualTo("AWAY");
        participants.stream()
            .filter(participant -> "HOME".equals(participant.getTeam()))
            .forEach(participant -> assertThat(participant.getPlayer().getMmr()).isEqualTo(984));
        participants.stream()
            .filter(participant -> "AWAY".equals(participant.getTeam()))
            .forEach(participant -> assertThat(participant.getPlayer().getMmr()).isEqualTo(1016));
    }

    @Test
    void reducesKFactorWhenLowTierPlayerIsIncluded() {
        Match match = new Match();
        match.setId(2L);

        Group group = new Group();
        group.setId(1L);

        List<MatchParticipant> participants = List.of(
            participant(21L, match, player(11L, group, "H1", 1000, "A"), "HOME"),
            participant(22L, match, player(12L, group, "H2", 1000, "A"), "HOME"),
            participant(23L, match, player(13L, group, "H3", 1000, "A"), "HOME"),
            participant(24L, match, player(14L, group, "A1", 350, "C+"), "AWAY"),
            participant(25L, match, player(15L, group, "A2", 1000, "A"), "AWAY"),
            participant(26L, match, player(16L, group, "A3", 1000, "A"), "AWAY")
        );

        when(matchRepository.findById(2L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(2L)).thenReturn(participants);
        when(matchParticipantRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mmrHistoryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MatchResultResponse response = matchResultService.processMatchResult(2L, new MatchResultRequest("HOME"));

        assertThat(response.kFactor()).isEqualTo(22);
    }

    @Test
    void deletesMatchAndRollsBackPlayerMmr() {
        Match match = new Match();
        match.setId(99L);

        Group group = new Group();
        group.setId(1L);

        List<MatchParticipant> participants = List.of(
            participant(1L, match, player(1L, group, "H1", 1010), "HOME"),
            participant(2L, match, player(2L, group, "H2", 1010), "HOME"),
            participant(3L, match, player(3L, group, "H3", 1010), "HOME"),
            participant(4L, match, player(4L, group, "A1", 990), "AWAY"),
            participant(5L, match, player(5L, group, "A2", 990), "AWAY"),
            participant(6L, match, player(6L, group, "A3", 990), "AWAY")
        );
        participants.stream()
            .filter(participant -> "HOME".equals(participant.getTeam()))
            .forEach(participant -> participant.setMmrDelta(10));
        participants.stream()
            .filter(participant -> "AWAY".equals(participant.getTeam()))
            .forEach(participant -> participant.setMmrDelta(-10));

        when(matchRepository.findById(99L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(99L)).thenReturn(participants);
        when(playerRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        matchResultService.deleteMatch(99L);

        participants.stream()
            .filter(participant -> "HOME".equals(participant.getTeam()))
            .forEach(participant -> assertThat(participant.getPlayer().getMmr()).isEqualTo(1000));
        participants.stream()
            .filter(participant -> "AWAY".equals(participant.getTeam()))
            .forEach(participant -> assertThat(participant.getPlayer().getMmr()).isEqualTo(1000));

        verify(mmrHistoryRepository).deleteByMatch_Id(99L);
        verify(matchParticipantRepository).deleteByMatch_Id(99L);
        verify(matchRepository).delete(match);
    }

    @Test
    void reducesKFactorWhenMmrGapIsVeryLarge() {
        Match match = new Match();
        match.setId(3L);

        Group group = new Group();
        group.setId(1L);

        List<MatchParticipant> participants = List.of(
            participant(31L, match, player(21L, group, "H1", 1700, "A+"), "HOME"),
            participant(32L, match, player(22L, group, "H2", 1700, "A+"), "HOME"),
            participant(33L, match, player(23L, group, "H3", 1700, "A+"), "HOME"),
            participant(34L, match, player(24L, group, "A1", 700, "B"), "AWAY"),
            participant(35L, match, player(25L, group, "A2", 700, "B"), "AWAY"),
            participant(36L, match, player(26L, group, "A3", 700, "B"), "AWAY")
        );

        when(matchRepository.findById(3L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(3L)).thenReturn(participants);
        when(matchParticipantRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mmrHistoryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MatchResultResponse response = matchResultService.processMatchResult(3L, new MatchResultRequest("HOME"));

        assertThat(response.kFactor()).isLessThan(32);
    }

    private List<MatchParticipant> buildParticipants(Match match) {
        Group group = new Group();
        group.setId(7L);

        Player p1 = player(1L, group, "H1", 1200);
        Player p2 = player(2L, group, "H2", 1100);
        Player p3 = player(3L, group, "H3", 1000);
        Player p4 = player(4L, group, "A1", 1000);
        Player p5 = player(5L, group, "A2", 950);
        Player p6 = player(6L, group, "A3", 900);

        return List.of(
            participant(11L, match, p1, "HOME"),
            participant(12L, match, p2, "HOME"),
            participant(13L, match, p3, "HOME"),
            participant(14L, match, p4, "AWAY"),
            participant(15L, match, p5, "AWAY"),
            participant(16L, match, p6, "AWAY")
        );
    }

    private Player player(Long id, Group group, String nickname, int mmr) {
        Player player = new Player();
        player.setId(id);
        player.setGroup(group);
        player.setNickname(nickname);
        player.setMmr(mmr);
        player.setTier("A");
        return player;
    }

    private Player player(Long id, Group group, String nickname, int mmr, String tier) {
        Player player = player(id, group, nickname, mmr);
        player.setTier(tier);
        return player;
    }

    private MatchParticipant participant(Long id, Match match, Player player, String team) {
        MatchParticipant participant = new MatchParticipant();
        participant.setId(id);
        participant.setMatch(match);
        participant.setPlayer(player);
        participant.setTeam(team);
        return participant;
    }
}
