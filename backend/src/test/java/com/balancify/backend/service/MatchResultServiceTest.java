package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.match.dto.MatchResultRequest;
import com.balancify.backend.api.match.dto.MatchResultParticipantResponse;
import com.balancify.backend.api.match.dto.MatchResultResponse;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.MatchStatus;
import com.balancify.backend.domain.MmrHistory;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchRepository;
import com.balancify.backend.repository.MmrHistoryRepository;
import com.balancify.backend.repository.PlayerRepository;
import com.balancify.backend.service.exception.MatchConflictException;
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

    private static final int DEFAULT_BASE_K_FACTOR = 24;

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
        matchResultService = createService(DEFAULT_BASE_K_FACTOR);
    }

    private MatchResultService createService(int baseKFactor) {
        return new MatchResultService(
            matchRepository,
            matchParticipantRepository,
            playerRepository,
            mmrHistoryRepository,
            baseKFactor,
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
        match.setStatus(MatchStatus.CONFIRMED);

        List<MatchParticipant> participants = buildParticipants(match);
        participants.get(0).setAssignedRace("P");
        participants.get(1).setAssignedRace("P");
        participants.get(2).setAssignedRace("T");
        participants.get(3).setAssignedRace("P");
        participants.get(4).setAssignedRace("P");
        participants.get(5).setAssignedRace("T");

        when(matchRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(match));
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
        int homeDelta = (int) Math.round(DEFAULT_BASE_K_FACTOR * (1.0 - homeExpected));
        int awayDelta = (int) Math.round(DEFAULT_BASE_K_FACTOR * (0.0 - (1.0 - homeExpected)));

        assertThat(response.matchId()).isEqualTo(1L);
        assertThat(response.winnerTeam()).isEqualTo("HOME");
        assertThat(response.kFactor()).isEqualTo(DEFAULT_BASE_K_FACTOR);
        assertThat(response.homeExpectedWinRate()).isEqualTo(Math.round(homeExpected * 10000.0) / 10000.0);
        assertThat(response.awayExpectedWinRate()).isEqualTo(Math.round((1.0 - homeExpected) * 10000.0) / 10000.0);
        assertThat(response.participants()).hasSize(6);
        assertThat(response.participants())
            .extracting(MatchResultParticipantResponse::assignedRace)
            .containsExactly("P", "P", "T", "P", "P", "T");

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
            assertThat(history.getDelta()).isNotNull();
        });
    }

    @Test
    void throwsWhenWinnerTeamIsInvalid() {
        Match match = new Match();
        match.setId(1L);
        match.setStatus(MatchStatus.CONFIRMED);

        when(matchRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(match));

        assertThatThrownBy(() -> matchResultService.processMatchResult(1L, new MatchResultRequest("BLUE")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("winnerTeam must be HOME or AWAY");

        verify(matchParticipantRepository, never()).findByMatchIdWithPlayerAndMatch(any());
        verify(mmrHistoryRepository, never()).saveAll(any());
    }

    @Test
    void rejectsSecondSubmissionWhenMatchAlreadyCompleted() {
        Match match = new Match();
        match.setId(1L);
        match.setStatus(MatchStatus.COMPLETED);
        match.setWinningTeam("HOME");
        when(matchRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(match));

        assertThatThrownBy(() ->
            matchResultService.processMatchResult(1L, new MatchResultRequest("AWAY"))
        )
            .isInstanceOf(MatchConflictException.class)
            .hasMessage("이미 결과가 확정된 경기입니다.");
    }

    @Test
    void reducesKFactorWhenLowTierPlayerIsIncluded() {
        Match match = new Match();
        match.setId(2L);
        match.setStatus(MatchStatus.CONFIRMED);

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

        when(matchRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(2L)).thenReturn(participants);
        when(matchParticipantRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mmrHistoryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MatchResultResponse response = matchResultService.processMatchResult(2L, new MatchResultRequest("HOME"));

        assertThat(response.kFactor()).isEqualTo(17);
    }

    @Test
    void processesTwoVsTwoResultAndUpdatesMmrForAllParticipants() {
        Match match = new Match();
        match.setId(22L);
        match.setStatus(MatchStatus.CONFIRMED);
        match.setTeamSize(2);

        Group group = new Group();
        group.setId(1L);

        List<MatchParticipant> participants = List.of(
            participant(41L, match, player(31L, group, "H1", 1200), "HOME"),
            participant(42L, match, player(32L, group, "H2", 1100), "HOME"),
            participant(43L, match, player(33L, group, "A1", 1000), "AWAY"),
            participant(44L, match, player(34L, group, "A2", 900), "AWAY")
        );

        when(matchRepository.findByIdForUpdate(22L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(22L)).thenReturn(participants);
        when(matchParticipantRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mmrHistoryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MatchResultResponse response = matchResultService.processMatchResult(
            22L,
            new MatchResultRequest("AWAY")
        );

        assertThat(response.matchId()).isEqualTo(22L);
        assertThat(response.participants()).hasSize(4);
        assertThat(match.getWinningTeam()).isEqualTo("AWAY");
        participants.forEach(participant -> assertThat(participant.getMmrAfter()).isNotNull());
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
        match.setStatus(MatchStatus.CONFIRMED);

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

        when(matchRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(3L)).thenReturn(participants);
        when(matchParticipantRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mmrHistoryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MatchResultResponse response = matchResultService.processMatchResult(3L, new MatchResultRequest("HOME"));

        assertThat(response.kFactor()).isLessThan(DEFAULT_BASE_K_FACTOR);
    }

    @Test
    void strongerTeamWinResultsInSmallerPositiveDeltaThanUnderdogWin() {
        MatchResultResponse favoredWin = processStandardResult(10L, "HOME", matchResultService);
        MatchResultResponse underdogWin = processStandardResult(11L, "AWAY", matchResultService);

        int favoredWinnerDelta = participantDelta(favoredWin, "HOME", "H1");
        int underdogWinnerDelta = participantDelta(underdogWin, "AWAY", "A1");

        assertThat(favoredWinnerDelta).isPositive();
        assertThat(underdogWinnerDelta).isPositive();
        assertThat(underdogWinnerDelta).isGreaterThan(favoredWinnerDelta);
    }

    @Test
    void strongerTeamLossResultsInLargerNegativeDeltaThanUnderdogLoss() {
        MatchResultResponse favoredWin = processStandardResult(12L, "HOME", matchResultService);
        MatchResultResponse underdogWin = processStandardResult(13L, "AWAY", matchResultService);

        int strongerTeamLossDelta = participantDelta(underdogWin, "HOME", "H1");
        int weakerTeamLossDelta = participantDelta(favoredWin, "AWAY", "A1");

        assertThat(strongerTeamLossDelta).isNegative();
        assertThat(weakerTeamLossDelta).isNegative();
        assertThat(Math.abs(strongerTeamLossDelta)).isGreaterThan(Math.abs(weakerTeamLossDelta));
    }

    @Test
    void loweringBaseKFactorReducesAbsoluteDeltaSizes() {
        MatchResultService legacyVolatilityService = createService(32);

        MatchResultResponse legacyResponse = processStandardResult(14L, "AWAY", legacyVolatilityService);
        MatchResultResponse loweredResponse = processStandardResult(15L, "AWAY", matchResultService);

        int legacyUpsetDelta = participantDelta(legacyResponse, "AWAY", "A1");
        int loweredUpsetDelta = participantDelta(loweredResponse, "AWAY", "A1");

        assertThat(Math.abs(loweredUpsetDelta)).isLessThan(Math.abs(legacyUpsetDelta));
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

    private MatchResultResponse processStandardResult(
        Long matchId,
        String winnerTeam,
        MatchResultService service
    ) {
        Match match = new Match();
        match.setId(matchId);
        match.setStatus(MatchStatus.CONFIRMED);

        List<MatchParticipant> participants = buildParticipants(match);

        when(matchRepository.findByIdForUpdate(matchId)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(matchId)).thenReturn(participants);
        when(matchParticipantRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mmrHistoryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        return service.processMatchResult(matchId, new MatchResultRequest(winnerTeam));
    }

    private int participantDelta(MatchResultResponse response, String team, String nickname) {
        return response.participants().stream()
            .filter(participant -> team.equals(participant.team()))
            .filter(participant -> nickname.equals(participant.nickname()))
            .findFirst()
            .orElseThrow()
            .mmrDelta();
    }
}
