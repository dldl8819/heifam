package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.match.dto.MatchResultRequest;
import com.balancify.backend.api.match.dto.MatchResultUpdateRequest;
import com.balancify.backend.api.match.dto.MatchResultParticipantResponse;
import com.balancify.backend.api.match.dto.MatchResultResponse;
import com.balancify.backend.api.match.dto.ParticipantRaceRequest;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.MatchSource;
import com.balancify.backend.domain.MatchStatus;
import com.balancify.backend.domain.MmrHistory;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.GroupRepository;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchRepository;
import com.balancify.backend.repository.MmrHistoryRepository;
import com.balancify.backend.repository.PlayerRepository;
import com.balancify.backend.service.exception.MatchConflictException;
import com.balancify.backend.service.exception.MatchEditForbiddenException;
import com.balancify.backend.service.exception.MatchEditQuotaExceededException;
import java.time.OffsetDateTime;
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

    private static final int DEFAULT_BASE_K_FACTOR = 36;
    // rebuildGroupStats now runs on TransactionAfterCommit's background executor, so assertions
    // on it must poll instead of checking immediately after the service call returns.
    private static final long ASYNC_STATS_REBUILD_TIMEOUT_MS = 500L;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private MatchParticipantRepository matchParticipantRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private MmrHistoryRepository mmrHistoryRepository;

    @Mock
    private PlayerStatsRefreshService playerStatsRefreshService;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private OperationAuditLogService operationAuditLogService;

    @Mock
    private MatchResultEditQuotaService matchResultEditQuotaService;

    @Mock
    private PointService pointService;

    @Mock
    private TournamentProgressService tournamentProgressService;

    @Mock
    private BalanceSeriesProgressService balanceSeriesProgressService;

    @Mock
    private PredictionService predictionService;

    private MatchResultService matchResultService;

    @BeforeEach
    void setUp() {
        // Most tests exercise the admin-driven flow (matches the previous filter-level
        // admin-only gate); tests for the non-admin recorder path override this per-case.
        lenient().when(accessControlService.isAdminEmail(any())).thenReturn(true);
        matchResultService = createService(DEFAULT_BASE_K_FACTOR);
    }

    private MatchResultService createService(int baseKFactor) {
        return new MatchResultService(
            matchRepository,
            groupRepository,
            matchParticipantRepository,
            playerRepository,
            mmrHistoryRepository,
            baseKFactor,
            800,
            300,
            900,
            0.6,
            0.7,
            200,
            800,
            1.5,
            5,
            new GroupReadCacheService(0),
            playerStatsRefreshService,
            accessControlService,
            operationAuditLogService,
            matchResultEditQuotaService,
            pointService,
            tournamentProgressService,
            balanceSeriesProgressService,
            predictionService
        );
    }

    @Test
    void givesTheRecorderAPointForTheFirstResultOfABalancedMatch() {
        Match match = new Match();
        match.setId(1L);
        match.setStatus(MatchStatus.CONFIRMED);
        stubFirstResult(match);

        matchResultService.processMatchResult(1L, new MatchResultRequest("HOME"), "YOUR_USERNAME@example.com");

        verify(pointService).grantMatchResultPoint("your_username@example.com", 1L);
        verifyNoInteractions(tournamentProgressService);
    }

    @Test
    void movesTheTournamentOnWhenOneOfItsGamesIsRecorded() {
        Match match = new Match();
        match.setId(1L);
        match.setStatus(MatchStatus.CONFIRMED);
        match.setSeriesId(70L);
        match.setSeriesGameNumber(1);
        stubFirstResult(match);

        matchResultService.processMatchResult(1L, new MatchResultRequest("HOME"), "YOUR_USERNAME@example.com");

        verify(tournamentProgressService).checkResultChange(match, "your_username@example.com", true);
        verify(tournamentProgressService).sync(70L);
        verify(predictionService).settle(match);
    }

    @Test
    void checksTheTournamentBeforeDeletingOneOfItsGames() {
        Match match = new Match();
        match.setId(99L);
        match.setSeriesId(70L);
        match.setSeriesGameNumber(2);
        when(matchRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(match));

        matchResultService.deleteMatch(99L);

        verify(tournamentProgressService).checkDeletion(match);
        verify(tournamentProgressService).sync(70L);
        verify(predictionService).revoke(99L);
    }

    @Test
    void stopsBeforeChangingAnythingWhenTheTournamentRefusesTheResult() {
        Match match = new Match();
        match.setId(1L);
        match.setStatus(MatchStatus.CONFIRMED);
        match.setSeriesId(70L);
        when(matchRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(match));
        doThrow(new MatchEditForbiddenException("admins only"))
            .when(tournamentProgressService).checkResultChange(match, "member@example.com", true);

        assertThatThrownBy(() -> matchResultService.processMatchResult(1L, new MatchResultRequest("HOME"), "member@example.com"))
            .isInstanceOf(MatchEditForbiddenException.class);
        verify(playerRepository, never()).saveAll(any());
        verify(tournamentProgressService, never()).sync(any());
    }

    @Test
    void givesNoPointForManualMatchesOrResultsWithoutARecorder() {
        Match manualMatch = new Match();
        manualMatch.setId(1L);
        manualMatch.setStatus(MatchStatus.CONFIRMED);
        manualMatch.setSource(MatchSource.MANUAL);
        stubFirstResult(manualMatch);

        matchResultService.processMatchResult(1L, new MatchResultRequest("HOME"), "YOUR_USERNAME@example.com");

        Match importedMatch = new Match();
        importedMatch.setId(2L);
        importedMatch.setStatus(MatchStatus.CONFIRMED);
        stubFirstResult(importedMatch);

        matchResultService.processMatchResult(2L, new MatchResultRequest("HOME"));

        verify(pointService, never()).grantMatchResultPoint(any(), any());
    }

    private void stubFirstResult(Match match) {
        List<MatchParticipant> participants = buildParticipants(match);
        when(matchRepository.findByIdForUpdate(match.getId())).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(match.getId())).thenReturn(participants);
        lenient().when(matchParticipantRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(playerRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(mmrHistoryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void processesResultAndUpdatesMmrForAllParticipants() {
        Match match = new Match();
        match.setId(1L);
        match.setStatus(MatchStatus.CONFIRMED);

        List<MatchParticipant> participants = buildParticipants(match);
        Player anonymizedPlayer = participants.get(0).getPlayer();
        anonymizedPlayer.setAnonymizedAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
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

        double homeExpected = 1.0 / (1.0 + Math.pow(10.0, (950.0 - 1100.0) / 800.0));
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
        assertThat(response.participants().get(0).playerId()).isNull();
        assertThat(response.participants().get(0).nickname())
            .isEqualTo("\uD0C8\uD1F4\uD55C \uD68C\uC6D0");

        assertThat(anonymizedPlayer.getId()).isEqualTo(1L);
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
        verify(playerStatsRefreshService, timeout(ASYNC_STATS_REBUILD_TIMEOUT_MS)).rebuildGroupStats(7L);
    }

    @Test
    void masksLegacyInactivePlayerWithoutSkippingMmrAndHistoryProcessing() {
        Group group = new Group();
        group.setId(8L);

        Match match = new Match();
        match.setId(2L);
        match.setGroup(group);
        match.setStatus(MatchStatus.CONFIRMED);
        match.setTeamSize(3);

        Player inactivePlayer = player(7L, group, "LEGACY_NICKNAME", 1000);
        inactivePlayer.setActive(false);
        Player activePlayer = player(8L, group, "ACTIVE_NICKNAME", 1000);
        List<MatchParticipant> participants = List.of(
            participant(17L, match, inactivePlayer, "HOME"),
            participant(18L, match, activePlayer, "AWAY"),
            participant(19L, match, player(9L, group, "H2", 1000), "HOME"),
            participant(20L, match, player(10L, group, "H3", 1000), "HOME"),
            participant(21L, match, player(11L, group, "A2", 1000), "AWAY"),
            participant(22L, match, player(12L, group, "A3", 1000), "AWAY")
        );

        when(matchRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(2L)).thenReturn(participants);
        when(matchParticipantRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mmrHistoryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MatchResultResponse response = matchResultService.processMatchResult(
            2L,
            new MatchResultRequest("HOME")
        );

        assertThat(inactivePlayer.getAnonymizedAt()).isNull();
        assertThat(response.participants()).hasSize(6);
        assertThat(response.participants().get(0).playerId()).isNull();
        assertThat(response.participants().get(0).nickname())
            .isEqualTo(PlayerIdentityPolicy.HIDDEN_MEMBER_LABEL);
        assertThat(response.participants().get(1).playerId()).isEqualTo(8L);
        assertThat(response.participants().get(1).nickname()).isEqualTo("ACTIVE_NICKNAME");

        assertThat(inactivePlayer.getMmr()).isGreaterThan(1000);
        assertThat(activePlayer.getMmr()).isLessThan(1000);
        assertThat(participants.get(0).getMmrAfter()).isEqualTo(inactivePlayer.getMmr());
        assertThat(participants.get(1).getMmrAfter()).isEqualTo(activePlayer.getMmr());

        ArgumentCaptor<List<MmrHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(mmrHistoryRepository).saveAll(historyCaptor.capture());
        assertThat(historyCaptor.getValue()).hasSize(6);
        assertThat(historyCaptor.getValue())
            .extracting(history -> history.getPlayer().getId())
            .containsExactlyInAnyOrder(7L, 8L, 9L, 10L, 11L, 12L);
        verify(playerStatsRefreshService, timeout(ASYNC_STATS_REBUILD_TIMEOUT_MS)).rebuildGroupStats(8L);
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
    void updateMatchResultReturnsAuditSnapshotWithPreviousAndNextWinner() {
        Match match = new Match();
        match.setId(55L);
        match.setStatus(MatchStatus.COMPLETED);
        match.setWinningTeam("HOME");
        match.setRaceComposition("PPP");

        List<MatchParticipant> participants = buildParticipants(match);
        participants.forEach(participant -> {
            participant.setRace("P");
            participant.setAssignedRace("P");
        });

        when(matchRepository.findByIdForUpdate(55L)).thenReturn(Optional.of(match));
        when(groupRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(new Group()));
        when(matchRepository.findRecentDuplicateCandidatesExcludingMatch(
            any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(List.of());
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(55L)).thenReturn(participants);
        when(matchParticipantRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mmrHistoryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MatchResultService.MatchResultUpdateOutcome outcome = matchResultService.updateMatchResult(
            55L,
            new MatchResultUpdateRequest("AWAY", "PPT"),
            "ops@example.com",
            "OpsUser"
        );

        assertThat(outcome.response().winnerTeam()).isEqualTo("AWAY");
        assertThat(outcome.auditSnapshot()).isNotNull();
        assertThat(outcome.auditSnapshot().matchId()).isEqualTo(55L);
        assertThat(outcome.auditSnapshot().groupId()).isEqualTo(7L);
        assertThat(outcome.auditSnapshot().previousWinnerTeam()).isEqualTo("HOME");
        assertThat(outcome.auditSnapshot().nextWinnerTeam()).isEqualTo("AWAY");
        assertThat(outcome.auditSnapshot().previousRaceComposition()).isEqualTo("PPP");
        assertThat(outcome.auditSnapshot().nextRaceComposition()).isEqualTo("PPT");
        assertThat(match.getRaceComposition()).isEqualTo("PPT");
        assertThat(participants.stream().filter(participant -> "HOME".equals(participant.getTeam())))
            .extracting(MatchParticipant::getAssignedRace)
            .containsExactly("P", "P", "T");
        assertThat(participants.stream().filter(participant -> "AWAY".equals(participant.getTeam())))
            .extracting(MatchParticipant::getAssignedRace)
            .containsExactly("P", "P", "T");
        // A changed winner keeps the result confirmations; only deleting the match takes them back.
        verify(pointService, never()).reverseMatchConfirmPoints(any());
        verify(pointService, never()).reverseMatchResultPoints(any());
    }

    @Test
    void updatesRaceCompositionWithoutReprocessingMmrOrResultMetadataWhenWinnerIsUnchanged() {
        Match match = new Match();
        match.setId(56L);
        match.setStatus(MatchStatus.COMPLETED);
        match.setWinningTeam("HOME");
        match.setRaceComposition("PPP");
        match.setRacesRecorded(true);
        OffsetDateTime recordedAt = OffsetDateTime.parse("2026-08-01T01:00:00Z");
        match.setResultRecordedAt(recordedAt);
        match.setResultRecordedByEmail("recorded@example.test");
        match.setResultRecordedByNickname("Recorder");

        List<MatchParticipant> participants = buildParticipants(match);
        participants.forEach(participant -> {
            participant.setRace("P");
            participant.setAssignedRace("P");
            participant.setMmrBefore(participant.getPlayer().getMmr() - 10);
            participant.setMmrAfter(participant.getPlayer().getMmr());
            participant.setMmrDelta(10);
        });
        List<Integer> playerMmrBefore = participants.stream()
            .map(participant -> participant.getPlayer().getMmr())
            .toList();
        List<Integer> participantBefore = participants.stream()
            .map(MatchParticipant::getMmrBefore)
            .toList();
        List<Integer> participantAfter = participants.stream()
            .map(MatchParticipant::getMmrAfter)
            .toList();
        List<Integer> participantDelta = participants.stream()
            .map(MatchParticipant::getMmrDelta)
            .toList();

        when(matchRepository.findByIdForUpdate(56L)).thenReturn(Optional.of(match));
        when(groupRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(new Group()));
        when(matchRepository.findRecentDuplicateCandidatesExcludingMatch(
            any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(List.of());
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(56L)).thenReturn(participants);
        when(matchParticipantRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MatchResultService.MatchResultUpdateOutcome outcome = matchResultService.updateMatchResult(
            56L,
            new MatchResultUpdateRequest("HOME", "PPT"),
            "editor@example.test",
            "Editor"
        );

        assertThat(match.getRaceComposition()).isEqualTo("PPT");
        assertThat(match.isRacesRecorded()).isFalse();
        assertThat(match.getResultRecordedAt()).isEqualTo(recordedAt);
        assertThat(match.getResultRecordedByEmail()).isEqualTo("recorded@example.test");
        assertThat(match.getResultRecordedByNickname()).isEqualTo("Recorder");
        assertThat(participants.stream().map(participant -> participant.getPlayer().getMmr()).toList())
            .isEqualTo(playerMmrBefore);
        assertThat(participants.stream().map(MatchParticipant::getMmrBefore).toList())
            .isEqualTo(participantBefore);
        assertThat(participants.stream().map(MatchParticipant::getMmrAfter).toList())
            .isEqualTo(participantAfter);
        assertThat(participants.stream().map(MatchParticipant::getMmrDelta).toList())
            .isEqualTo(participantDelta);
        assertThat(outcome.response().winnerTeam()).isEqualTo("HOME");
        assertThat(outcome.auditSnapshot().previousRaceComposition()).isEqualTo("PPP");
        assertThat(outcome.auditSnapshot().nextRaceComposition()).isEqualTo("PPT");
        verify(operationAuditLogService).recordMatchResultUpdate("editor@example.test", "Editor", outcome.auditSnapshot());
        verify(matchResultEditQuotaService, never()).reserve(any(), any());
        verify(playerRepository, never()).saveAll(any());
        verify(mmrHistoryRepository, never()).findByMatch_Id(any());
        verify(mmrHistoryRepository, never()).saveAll(any());
        verify(playerStatsRefreshService, timeout(ASYNC_STATS_REBUILD_TIMEOUT_MS)).rebuildGroupStats(7L);
    }

    @Test
    void allowsNonAdminRecorderToEditRaceCompositionOnOwnMatch() {
        Match match = new Match();
        match.setId(65L);
        match.setStatus(MatchStatus.COMPLETED);
        match.setWinningTeam("HOME");
        match.setRaceComposition("PPP");
        match.setResultRecordedByEmail("recorder@example.test");
        match.setResultRecordedByNickname("Recorder");

        List<MatchParticipant> participants = buildParticipants(match);
        participants.forEach(participant -> {
            participant.setRace("P");
            participant.setAssignedRace("P");
        });

        when(accessControlService.isAdminEmail("recorder@example.test")).thenReturn(false);
        when(matchRepository.findByIdForUpdate(65L)).thenReturn(Optional.of(match));
        when(groupRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(new Group()));
        when(matchRepository.findRecentDuplicateCandidatesExcludingMatch(
            any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(List.of());
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(65L)).thenReturn(participants);
        when(matchParticipantRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MatchResultService.MatchResultUpdateOutcome outcome = matchResultService.updateMatchResult(
            65L,
            new MatchResultUpdateRequest("HOME", "PPT"),
            "recorder@example.test",
            "Recorder"
        );

        assertThat(match.getRaceComposition()).isEqualTo("PPT");
        assertThat(outcome.response().winnerTeam()).isEqualTo("HOME");
    }

    @Test
    void rejectsNonAdminEditingRaceCompositionOnMatchRecordedBySomeoneElse() {
        Match match = new Match();
        match.setId(66L);
        match.setStatus(MatchStatus.COMPLETED);
        match.setWinningTeam("HOME");
        match.setRaceComposition("PPP");
        match.setResultRecordedByEmail("recorder@example.test");

        when(accessControlService.isAdminEmail("outsider@example.test")).thenReturn(false);
        when(matchRepository.findByIdForUpdate(66L)).thenReturn(Optional.of(match));

        assertThatThrownBy(() -> matchResultService.updateMatchResult(
            66L,
            new MatchResultUpdateRequest("HOME", "PPT"),
            "outsider@example.test",
            "Outsider"
        )).isInstanceOf(MatchEditForbiddenException.class);

        assertThat(match.getRaceComposition()).isEqualTo("PPP");
        verify(matchParticipantRepository, never()).findByMatchIdWithPlayerAndMatch(any());
        verify(matchRepository, never()).save(any());
    }

    @Test
    void rejectsNonAdminChangingWinnerTeamEvenOnOwnMatch() {
        Match match = new Match();
        match.setId(67L);
        match.setStatus(MatchStatus.COMPLETED);
        match.setWinningTeam("HOME");
        match.setRaceComposition("PPP");
        match.setResultRecordedByEmail("recorder@example.test");

        when(accessControlService.isAdminEmail("recorder@example.test")).thenReturn(false);
        when(matchRepository.findByIdForUpdate(67L)).thenReturn(Optional.of(match));

        assertThatThrownBy(() -> matchResultService.updateMatchResult(
            67L,
            new MatchResultUpdateRequest("AWAY", "PPT"),
            "recorder@example.test",
            "Recorder"
        )).isInstanceOf(MatchEditForbiddenException.class);

        assertThat(match.getWinningTeam()).isEqualTo("HOME");
        assertThat(match.getRaceComposition()).isEqualTo("PPP");
        verify(matchParticipantRepository, never()).findByMatchIdWithPlayerAndMatch(any());
        verify(matchRepository, never()).save(any());
    }

    @Test
    void letsAMatchResultEditorChangeTheWinnerOfAMatchSomeoneElseRecorded() {
        Match match = new Match();
        match.setId(68L);
        match.setStatus(MatchStatus.COMPLETED);
        match.setWinningTeam("HOME");
        match.setResultRecordedByEmail("recorder@example.test");
        List<MatchParticipant> participants = buildParticipants(match);

        when(accessControlService.isAdminEmail("editor@example.test")).thenReturn(false);
        when(accessControlService.isMatchResultEditor("editor@example.test")).thenReturn(true);
        when(matchRepository.findByIdForUpdate(68L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(68L)).thenReturn(participants);
        when(matchParticipantRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mmrHistoryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MatchResultService.MatchResultUpdateOutcome outcome = matchResultService.updateMatchResult(
            68L,
            new MatchResultUpdateRequest("AWAY", null),
            "editor@example.test",
            "Editor"
        );

        assertThat(match.getWinningTeam()).isEqualTo("AWAY");
        assertThat(outcome.auditSnapshot().nextWinnerTeam()).isEqualTo("AWAY");
        verify(matchResultEditQuotaService).reserve("editor@example.test", 68L);
        verify(operationAuditLogService).recordMatchResultUpdate("editor@example.test", "Editor", outcome.auditSnapshot());
    }

    @Test
    void stopsAMatchResultEditorBeforeSavingOnceTheDailyLimitIsUsed() {
        Match match = new Match();
        match.setId(69L);
        match.setStatus(MatchStatus.COMPLETED);
        match.setWinningTeam("HOME");
        match.setResultRecordedByEmail("recorder@example.test");
        List<MatchParticipant> participants = buildParticipants(match);

        when(accessControlService.isAdminEmail("editor@example.test")).thenReturn(false);
        when(accessControlService.isMatchResultEditor("editor@example.test")).thenReturn(true);
        when(matchRepository.findByIdForUpdate(69L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(69L)).thenReturn(participants);
        doThrow(new MatchEditQuotaExceededException("limit"))
            .when(matchResultEditQuotaService).reserve("editor@example.test", 69L);

        assertThatThrownBy(() -> matchResultService.updateMatchResult(
            69L,
            new MatchResultUpdateRequest("AWAY", null),
            "editor@example.test",
            "Editor"
        )).isInstanceOf(MatchEditQuotaExceededException.class);

        assertThat(match.getWinningTeam()).isEqualTo("HOME");
        verify(matchRepository, never()).save(any());
        verify(playerRepository, never()).saveAll(any());
        verify(operationAuditLogService, never()).recordMatchResultUpdate(any(), any(), any());
    }

    @Test
    void doesNotCountAnAdminsEditAgainstTheEditorLimit() {
        Match match = new Match();
        match.setId(70L);
        match.setStatus(MatchStatus.COMPLETED);
        match.setWinningTeam("HOME");
        match.setResultRecordedByEmail("recorder@example.test");
        List<MatchParticipant> participants = buildParticipants(match);

        when(matchRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(70L)).thenReturn(participants);
        when(matchParticipantRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mmrHistoryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        matchResultService.updateMatchResult(
            70L,
            new MatchResultUpdateRequest("AWAY", null),
            "admin@example.test",
            "Admin"
        );

        verify(matchResultEditQuotaService, never()).reserve(any(), any());
        verify(accessControlService, never()).isMatchResultEditor(any());
    }

    @Test
    void skipsNoOpUpdateWhenWinnerAndRaceCompositionAreUnchanged() {
        Match match = new Match();
        match.setId(64L);
        match.setStatus(MatchStatus.COMPLETED);
        match.setWinningTeam("HOME");
        match.setRaceComposition("PPT");

        List<MatchParticipant> participants = buildParticipants(match);
        for (int index = 0; index < participants.size(); index++) {
            MatchParticipant participant = participants.get(index);
            participant.setRace("P");
            participant.setAssignedRace(index % 3 == 2 ? "T" : "P");
        }

        when(matchRepository.findByIdForUpdate(64L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(64L)).thenReturn(participants);

        MatchResultService.MatchResultUpdateOutcome omittedRaceOutcome =
            matchResultService.updateMatchResult(
                64L,
                new MatchResultUpdateRequest("HOME", null),
                null,
                null
            );
        MatchResultService.MatchResultUpdateOutcome repeatedRaceOutcome =
            matchResultService.updateMatchResult(
                64L,
                new MatchResultUpdateRequest("HOME", "PPT"),
                null,
                null
            );

        assertThat(match.getRaceComposition()).isEqualTo("PPT");
        assertThat(participants.stream().map(MatchParticipant::getAssignedRace).toList())
            .containsExactly("P", "P", "T", "P", "P", "T");
        assertThat(omittedRaceOutcome.auditSnapshot()).isNull();
        assertThat(repeatedRaceOutcome.auditSnapshot()).isNull();
        verify(operationAuditLogService, never()).recordMatchResultUpdate(any(), any(), any());
        verify(groupRepository, never()).findByIdForUpdate(any());
        verify(matchParticipantRepository, never()).saveAll(any());
        verify(matchRepository, never()).save(any());
        verify(playerRepository, never()).saveAll(any());
        verify(mmrHistoryRepository, never()).saveAll(any());
        verify(playerStatsRefreshService, after(ASYNC_STATS_REBUILD_TIMEOUT_MS).never()).rebuildGroupStats(any());
    }

    @Test
    void repairsAssignedRacesWhenCompositionTextIsUnchanged() {
        Match match = new Match();
        match.setId(65L);
        match.setStatus(MatchStatus.COMPLETED);
        match.setWinningTeam("HOME");
        match.setRaceComposition("PPT");

        List<MatchParticipant> participants = buildParticipants(match);
        for (int index = 0; index < participants.size(); index++) {
            MatchParticipant participant = participants.get(index);
            participant.setRace("P");
            participant.setAssignedRace(index % 3 == 2 ? "T" : "P");
        }
        participants.get(0).setAssignedRace("Z");

        when(matchRepository.findByIdForUpdate(65L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(65L)).thenReturn(participants);
        when(matchParticipantRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MatchResultService.MatchResultUpdateOutcome outcome = matchResultService.updateMatchResult(
            65L,
            new MatchResultUpdateRequest("HOME", "PPT"),
            null,
            null
        );

        assertThat(outcome.auditSnapshot()).isNotNull();
        assertThat(outcome.auditSnapshot().previousRaceComposition()).isEqualTo("PPT");
        assertThat(outcome.auditSnapshot().nextRaceComposition()).isEqualTo("PPT");
        assertThat(participants.stream().map(MatchParticipant::getAssignedRace).toList())
            .containsExactly("P", "P", "T", "P", "P", "T");
        verify(matchParticipantRepository).saveAll(participants);
        verify(matchRepository).save(match);
        verify(playerStatsRefreshService, timeout(ASYNC_STATS_REBUILD_TIMEOUT_MS)).rebuildGroupStats(7L);
        verify(playerRepository, never()).saveAll(any());
        verify(mmrHistoryRepository, never()).saveAll(any());
    }

    @Test
    void recordsWhoActuallyPlayedEachRaceWhenTheResultIsEntered() {
        Match match = new Match();
        match.setId(70L);
        match.setStatus(MatchStatus.CONFIRMED);
        match.setRaceComposition("PPT");

        List<MatchParticipant> participants = buildParticipants(match);
        for (int index = 0; index < participants.size(); index++) {
            participants.get(index).setAssignedRace(index % 3 == 2 ? "T" : "P");
        }

        when(matchRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(70L)).thenReturn(participants);
        when(matchParticipantRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mmrHistoryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MatchResultResponse response = matchResultService.processMatchResult(
            70L,
            new MatchResultRequest("HOME", swappedTerranRaces())
        );

        assertThat(participants.stream().map(MatchParticipant::getAssignedRace).toList())
            .containsExactly("T", "P", "P", "P", "T", "P");
        assertThat(response.participants().stream().map(MatchResultParticipantResponse::assignedRace).toList())
            .containsExactly("T", "P", "P", "P", "T", "P");
        assertThat(match.getRaceComposition()).isEqualTo("PPT");
        assertThat(match.isRacesRecorded()).isTrue();
    }

    @Test
    void rejectsParticipantRacesThatDoNotAddUpToTheRaceComposition() {
        Match match = new Match();
        match.setId(71L);
        match.setStatus(MatchStatus.CONFIRMED);
        match.setRaceComposition("PPT");
        List<MatchParticipant> participants = buildParticipants(match);

        when(matchRepository.findByIdForUpdate(71L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(71L)).thenReturn(participants);

        assertThatThrownBy(() -> matchResultService.processMatchResult(
            71L,
            new MatchResultRequest("HOME", List.of(
                new ParticipantRaceRequest(1L, "P"),
                new ParticipantRaceRequest(2L, "P"),
                new ParticipantRaceRequest(3L, "P"),
                new ParticipantRaceRequest(4L, "P"),
                new ParticipantRaceRequest(5L, "P"),
                new ParticipantRaceRequest(6L, "T")
            ))
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("PPT");

        assertThat(match.getWinningTeam()).isNull();
        assertThat(match.isRacesRecorded()).isFalse();
        verify(matchRepository, never()).save(any());
    }

    @Test
    void rejectsParticipantRaceForAPlayerWhoIsNotInTheMatch() {
        Match match = new Match();
        match.setId(72L);
        match.setStatus(MatchStatus.CONFIRMED);
        match.setRaceComposition("PPT");
        List<MatchParticipant> participants = buildParticipants(match);

        when(matchRepository.findByIdForUpdate(72L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(72L)).thenReturn(participants);

        assertThatThrownBy(() -> matchResultService.processMatchResult(
            72L,
            new MatchResultRequest("HOME", List.of(new ParticipantRaceRequest(99L, "T")))
        )).isInstanceOf(IllegalArgumentException.class);

        verify(matchRepository, never()).save(any());
    }

    @Test
    void updatesOnlyParticipantRacesWhenWinnerAndCompositionAreUnchanged() {
        Match match = new Match();
        match.setId(73L);
        match.setStatus(MatchStatus.COMPLETED);
        match.setWinningTeam("HOME");
        match.setRaceComposition("PPT");

        List<MatchParticipant> participants = buildParticipants(match);
        for (int index = 0; index < participants.size(); index++) {
            participants.get(index).setRace("PT");
            participants.get(index).setAssignedRace(index % 3 == 2 ? "T" : "P");
            participants.get(index).setMmrDelta(10);
        }

        when(matchRepository.findByIdForUpdate(73L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(73L)).thenReturn(participants);
        when(matchParticipantRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MatchResultService.MatchResultUpdateOutcome outcome = matchResultService.updateMatchResult(
            73L,
            new MatchResultUpdateRequest("HOME", "PPT", swappedTerranRaces()),
            null,
            null
        );

        assertThat(participants.stream().map(MatchParticipant::getAssignedRace).toList())
            .containsExactly("T", "P", "P", "P", "T", "P");
        assertThat(outcome.auditSnapshot().previousRaceComposition()).isEqualTo("PPT");
        assertThat(outcome.auditSnapshot().nextRaceComposition()).isEqualTo("PPT");
        assertThat(outcome.auditSnapshot().participantRacesChanged()).isTrue();
        assertThat(match.isRacesRecorded()).isTrue();
        assertThat(participants.stream().map(MatchParticipant::getMmrDelta).toList())
            .containsOnly(10);
        verify(matchParticipantRepository).saveAll(participants);
        verify(playerStatsRefreshService, timeout(ASYNC_STATS_REBUILD_TIMEOUT_MS)).rebuildGroupStats(7L);
        verify(playerRepository, never()).saveAll(any());
        verify(mmrHistoryRepository, never()).saveAll(any());
    }

    @Test
    void changesRaceCompositionThroughParticipantRaces() {
        Match match = new Match();
        match.setId(74L);
        match.setStatus(MatchStatus.COMPLETED);
        match.setWinningTeam("HOME");
        match.setRaceComposition("PPT");

        List<MatchParticipant> participants = buildParticipants(match);
        for (int index = 0; index < participants.size(); index++) {
            participants.get(index).setAssignedRace(index % 3 == 2 ? "T" : "P");
        }

        when(matchRepository.findByIdForUpdate(74L)).thenReturn(Optional.of(match));
        when(groupRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(new Group()));
        when(matchRepository.findRecentDuplicateCandidatesExcludingMatch(
            any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(List.of());
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(74L)).thenReturn(participants);
        when(matchParticipantRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MatchResultService.MatchResultUpdateOutcome outcome = matchResultService.updateMatchResult(
            74L,
            new MatchResultUpdateRequest("HOME", "PPZ", List.of(
                new ParticipantRaceRequest(1L, "Z"),
                new ParticipantRaceRequest(2L, "P"),
                new ParticipantRaceRequest(3L, "P"),
                new ParticipantRaceRequest(4L, "P"),
                new ParticipantRaceRequest(5L, "P"),
                new ParticipantRaceRequest(6L, "Z")
            )),
            null,
            null
        );

        assertThat(match.getRaceComposition()).isEqualTo("PPZ");
        assertThat(participants.stream().map(MatchParticipant::getAssignedRace).toList())
            .containsExactly("Z", "P", "P", "P", "P", "Z");
        assertThat(outcome.auditSnapshot().nextRaceComposition()).isEqualTo("PPZ");
    }

    private List<ParticipantRaceRequest> swappedTerranRaces() {
        return List.of(
            new ParticipantRaceRequest(1L, "T"),
            new ParticipantRaceRequest(2L, "P"),
            new ParticipantRaceRequest(3L, "P"),
            new ParticipantRaceRequest(4L, "P"),
            new ParticipantRaceRequest(5L, "T"),
            new ParticipantRaceRequest(6L, "P")
        );
    }

    @Test
    void rejectsRaceCompositionThatDoesNotMatchTeamSize() {
        Match match = new Match();
        match.setId(57L);
        match.setStatus(MatchStatus.COMPLETED);
        match.setWinningTeam("HOME");
        match.setTeamSize(3);
        List<MatchParticipant> participants = buildParticipants(match);

        when(matchRepository.findByIdForUpdate(57L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(57L)).thenReturn(participants);

        assertThatThrownBy(() -> matchResultService.updateMatchResult(
            57L,
            new MatchResultUpdateRequest("HOME", "PT"),
            null,
            null
        )).isInstanceOf(IllegalArgumentException.class);

        verify(matchParticipantRepository, never()).saveAll(any());
        verify(matchRepository, never()).save(any());
        verify(playerStatsRefreshService, after(ASYNC_STATS_REBUILD_TIMEOUT_MS).never()).rebuildGroupStats(any());
    }

    @Test
    void rejectsUpdateForCancelledMatchBeforeChangingRaceComposition() {
        Match match = new Match();
        match.setId(58L);
        match.setStatus(MatchStatus.CANCELLED);
        match.setWinningTeam("HOME");
        match.setRaceComposition("PPP");
        when(matchRepository.findByIdForUpdate(58L)).thenReturn(Optional.of(match));

        assertThatThrownBy(() -> matchResultService.updateMatchResult(
            58L,
            new MatchResultUpdateRequest("HOME", "PPT"),
            null,
            null
        )).isInstanceOf(MatchConflictException.class);

        assertThat(match.getRaceComposition()).isEqualTo("PPP");
        verify(matchParticipantRepository, never()).findByMatchIdWithPlayerAndMatch(any());
    }

    @Test
    void rejectsDuplicateRaceCompositionAtFiveMinuteBoundaryRegardlessOfSourceAndTeamSwap() {
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-08-08T01:00:00Z");
        Match match = new Match();
        match.setId(59L);
        match.setStatus(MatchStatus.COMPLETED);
        match.setWinningTeam("HOME");
        match.setTeamSize(3);
        match.setRaceComposition("PPP");
        match.setCreatedAt(createdAt);

        List<MatchParticipant> participants = buildParticipants(match);
        participants.forEach(participant -> {
            participant.setRace("P");
            participant.setAssignedRace("P");
        });

        Match duplicate = new Match();
        duplicate.setId(60L);
        duplicate.setStatus(MatchStatus.COMPLETED);
        duplicate.setSource(MatchSource.MANUAL);
        duplicate.setTeamSize(3);
        duplicate.setRaceComposition("PPT");
        duplicate.setCreatedAt(createdAt.plusMinutes(5));
        duplicate.setParticipantSignature("1-2-3-4-5-6");
        duplicate.setTeamSignature("HOME:4-5-6|AWAY:1-2-3");

        when(matchRepository.findByIdForUpdate(59L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(59L)).thenReturn(participants);
        when(groupRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(new Group()));
        when(matchRepository.findRecentDuplicateCandidatesExcludingMatch(
            eq(59L),
            eq(7L),
            eq(3),
            eq("1-2-3-4-5-6"),
            eq("PPT"),
            eq(createdAt.minusMinutes(5)),
            eq(createdAt.plusMinutes(5))
        )).thenReturn(List.of(duplicate));

        assertThatThrownBy(() -> matchResultService.updateMatchResult(
            59L,
            new MatchResultUpdateRequest("HOME", "PPT"),
            null,
            null
        ))
            .isInstanceOf(MatchConflictException.class)
            .hasMessage(GroupMatchAdminService.duplicateConflictMessage());

        assertThat(match.getRaceComposition()).isEqualTo("PPP");
        assertThat(participants).extracting(MatchParticipant::getAssignedRace)
            .containsOnly("P");
        verify(matchParticipantRepository, never()).saveAll(any());
        verify(matchRepository, never()).save(any());
        verify(playerStatsRefreshService, after(ASYNC_STATS_REBUILD_TIMEOUT_MS).never()).rebuildGroupStats(any());
    }

    @Test
    void allowsRaceCompositionUpdateForOutsideWindowAndDifferentTeamPartition() {
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-08-08T02:00:00Z");
        Match match = new Match();
        match.setId(61L);
        match.setStatus(MatchStatus.COMPLETED);
        match.setWinningTeam("HOME");
        match.setTeamSize(3);
        match.setRaceComposition("PPP");
        match.setCreatedAt(createdAt);

        List<MatchParticipant> participants = buildParticipants(match);
        participants.forEach(participant -> {
            participant.setRace("P");
            participant.setAssignedRace("P");
        });

        Match outsideWindow = new Match();
        outsideWindow.setId(62L);
        outsideWindow.setStatus(MatchStatus.COMPLETED);
        outsideWindow.setTeamSize(3);
        outsideWindow.setRaceComposition("PPT");
        outsideWindow.setCreatedAt(createdAt.plusMinutes(5).plusSeconds(1));
        outsideWindow.setParticipantSignature("1-2-3-4-5-6");
        outsideWindow.setTeamSignature("TEAM1:1-2-3|TEAM2:4-5-6");

        Match differentPartition = new Match();
        differentPartition.setId(63L);
        differentPartition.setStatus(MatchStatus.COMPLETED);
        differentPartition.setTeamSize(3);
        differentPartition.setRaceComposition("PPT");
        differentPartition.setCreatedAt(createdAt.plusMinutes(1));
        differentPartition.setParticipantSignature("1-2-3-4-5-6");
        differentPartition.setTeamSignature("TEAM1:1-2-4|TEAM2:3-5-6");

        when(matchRepository.findByIdForUpdate(61L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(61L)).thenReturn(participants);
        when(groupRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(new Group()));
        when(matchRepository.findRecentDuplicateCandidatesExcludingMatch(
            any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(List.of(outsideWindow, differentPartition));

        MatchResultService.MatchResultUpdateOutcome outcome = matchResultService.updateMatchResult(
            61L,
            new MatchResultUpdateRequest("HOME", "PPT"),
            null,
            null
        );

        assertThat(outcome.auditSnapshot().previousRaceComposition()).isEqualTo("PPP");
        assertThat(outcome.auditSnapshot().nextRaceComposition()).isEqualTo("PPT");
        assertThat(match.getRaceComposition()).isEqualTo("PPT");
        verify(playerStatsRefreshService, timeout(ASYNC_STATS_REBUILD_TIMEOUT_MS)).rebuildGroupStats(7L);
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

        assertThat(response.kFactor()).isEqualTo(25);
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
    void letsMmrGoBelowZeroSoLosersLoseWhatWinnersGain() {
        Match match = new Match();
        match.setId(24L);
        match.setStatus(MatchStatus.CONFIRMED);
        match.setTeamSize(3);

        Group group = new Group();
        group.setId(1L);

        List<MatchParticipant> participants = List.of(
            participant(61L, match, player(41L, group, "H1", 10), "HOME"),
            participant(62L, match, player(42L, group, "H2", 10), "HOME"),
            participant(63L, match, player(43L, group, "H3", 10), "HOME"),
            participant(64L, match, player(44L, group, "A1", 5), "AWAY"),
            participant(65L, match, player(45L, group, "A2", 5), "AWAY"),
            participant(66L, match, player(46L, group, "A3", 5), "AWAY")
        );

        when(matchRepository.findByIdForUpdate(24L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(24L)).thenReturn(participants);
        when(matchParticipantRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mmrHistoryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MatchResultResponse response = matchResultService.processMatchResult(
            24L,
            new MatchResultRequest("HOME")
        );

        int winnerGain = participantDelta(response, "HOME", "H1");
        assertThat(winnerGain).isGreaterThan(5);
        participants.stream()
            .filter(participant -> "AWAY".equals(participant.getTeam()))
            .forEach(participant -> {
                assertThat(participant.getMmrBefore()).isEqualTo(5);
                assertThat(participant.getMmrDelta()).isEqualTo(-winnerGain);
                assertThat(participant.getMmrAfter()).isEqualTo(5 - winnerGain).isNegative();
                assertThat(participant.getPlayer().getMmr()).isEqualTo(5 - winnerGain);
            });

        ArgumentCaptor<List<MmrHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(mmrHistoryRepository).saveAll(historyCaptor.capture());
        historyCaptor.getValue().stream()
            .filter(history -> List.of(44L, 45L, 46L).contains(history.getPlayer().getId()))
            .forEach(history -> {
                assertThat(history.getBeforeMmr()).isEqualTo(5);
                assertThat(history.getAfterMmr()).isEqualTo(5 - winnerGain);
                assertThat(history.getDelta()).isEqualTo(-winnerGain);
            });
    }

    @Test
    void placesAnUnassignedPlayerByScoreAfterTheirFirstRatedMatch() {
        Match match = new Match();
        match.setId(25L);
        match.setStatus(MatchStatus.CONFIRMED);
        match.setTeamSize(3);

        List<MatchParticipant> participants = buildParticipants(match);
        Player unassigned = participants.get(3).getPlayer();
        unassigned.setTier("UNASSIGNED");
        unassigned.setMmr(0);

        when(matchRepository.findByIdForUpdate(25L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(25L)).thenReturn(participants);
        when(matchParticipantRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mmrHistoryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        matchResultService.processMatchResult(25L, new MatchResultRequest("HOME"));

        assertThat(unassigned.getMmr()).isNegative();
        assertThat(unassigned.getTier()).isEqualTo("D");
        assertThat(participants.get(0).getPlayer().getTier()).isEqualTo("A");
    }

    @Test
    void recordsTwoVsTwoResultWithoutChangingMmr() {
        Match match = new Match();
        match.setId(25L);
        match.setStatus(MatchStatus.CONFIRMED);
        match.setTeamSize(2);

        Group group = new Group();
        group.setId(1L);

        // An upset: the lower-rated side wins, which would move ratings a lot in a rated match.
        List<MatchParticipant> participants = List.of(
            participant(71L, match, player(51L, group, "H1", 1200), "HOME"),
            participant(72L, match, player(52L, group, "H2", 1100), "HOME"),
            participant(73L, match, player(53L, group, "A1", 1000), "AWAY"),
            participant(74L, match, player(54L, group, "A2", 900), "AWAY")
        );

        when(matchRepository.findByIdForUpdate(25L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(25L)).thenReturn(participants);
        when(matchParticipantRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mmrHistoryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MatchResultResponse response = matchResultService.processMatchResult(
            25L,
            new MatchResultRequest("AWAY")
        );

        assertThat(match.getWinningTeam()).isEqualTo("AWAY");
        participants.forEach(participant -> {
            assertThat(participant.getMmrAfter()).isEqualTo(participant.getMmrBefore());
            assertThat(participant.getMmrDelta()).isZero();
            assertThat(participant.getPlayer().getMmr()).isEqualTo(participant.getMmrBefore());
        });
        assertThat(response.participants())
            .hasSize(4)
            .allSatisfy(participant -> assertThat(participant.mmrDelta()).isZero());

        ArgumentCaptor<List<MmrHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(mmrHistoryRepository).saveAll(historyCaptor.capture());
        assertThat(historyCaptor.getValue())
            .hasSize(4)
            .allSatisfy(history -> assertThat(history.getDelta()).isZero());
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

        when(matchRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(match));
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
        verify(pointService).reverseMatchResultPoints(99L);
        verify(pointService).reverseMatchConfirmPoints(99L);
        verify(matchRepository, never()).findById(99L);
        verify(playerStatsRefreshService, timeout(ASYNC_STATS_REBUILD_TIMEOUT_MS)).rebuildGroupStats(1L);
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
    void largeGapUnderdogWinAppliesBonusMultiplier() {
        Match match = new Match();
        match.setId(4L);
        match.setStatus(MatchStatus.CONFIRMED);

        Group group = new Group();
        group.setId(1L);

        List<MatchParticipant> participants = List.of(
            participant(41L, match, player(41L, group, "H1", 1700, "A+"), "HOME"),
            participant(42L, match, player(42L, group, "H2", 1700, "A+"), "HOME"),
            participant(43L, match, player(43L, group, "H3", 1700, "A+"), "HOME"),
            participant(44L, match, player(44L, group, "A1", 700, "B"), "AWAY"),
            participant(45L, match, player(45L, group, "A2", 700, "B"), "AWAY"),
            participant(46L, match, player(46L, group, "A3", 700, "B"), "AWAY")
        );

        when(matchRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(4L)).thenReturn(participants);
        when(matchParticipantRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mmrHistoryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MatchResultResponse response = matchResultService.processMatchResult(4L, new MatchResultRequest("AWAY"));

        double underdogExpected = 1.0 / (1.0 + Math.pow(10.0, (1700.0 - 700.0) / 800.0));
        int deltaWithoutBonus = (int) Math.round(response.kFactor() * (1.0 - underdogExpected));
        int underdogWinnerDelta = participantDelta(response, "AWAY", "A1");

        assertThat(underdogWinnerDelta).isGreaterThan(deltaWithoutBonus);
        assertThat(underdogWinnerDelta).isEqualTo(21);
        assertThat(participantDelta(response, "HOME", "H1")).isEqualTo(-21);
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
        MatchResultService legacyVolatilityService = createService(48);

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
