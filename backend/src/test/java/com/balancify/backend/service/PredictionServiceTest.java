package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.prediction.dto.PredictionBoardResponse;
import com.balancify.backend.api.prediction.dto.PredictionMatchResponse;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.MatchPrediction;
import com.balancify.backend.domain.MatchStatus;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchPredictionRepository;
import com.balancify.backend.repository.MatchRepository;
import com.balancify.backend.service.exception.MatchConflictException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PredictionServiceTest {

    private static final String ADMIN = "admin@example.com";
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-04T12:00:00Z");

    @Mock
    private MatchPredictionRepository matchPredictionRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private MatchParticipantRepository matchParticipantRepository;

    @Mock
    private PointService pointService;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private OperationAuditLogService operationAuditLogService;

    private PredictionService service;
    private Group group;

    @BeforeEach
    void setUp() {
        service = new PredictionService(
            matchPredictionRepository,
            matchRepository,
            matchParticipantRepository,
            pointService,
            accessControlService,
            operationAuditLogService,
            false,
            3,
            Clock.fixed(Instant.from(NOW), ZoneOffset.UTC)
        );
        group = new Group();
        group.setId(1L);
        when(accessControlService.isAdminEmail(ADMIN)).thenReturn(true);
        when(matchPredictionRepository.save(any(MatchPrediction.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void letsOnlyAdminsPredictWhilePredictionsAreTriedOut() {
        assertThat(service.canPredict(" Admin@Example.com ")).isTrue();
        assertThat(service.canPredict("member@example.com")).isFalse();
        assertThat(service.canPredict(" ")).isFalse();
    }

    @Test
    void takesAPickWhileTheMatchIsOpen() {
        Match match = match(10L, NOW.minusMinutes(2));
        stubMatch(match, participants(match));

        PredictionMatchResponse response = service.predict(1L, 10L, ADMIN, null, "home");

        ArgumentCaptor<MatchPrediction> saved = ArgumentCaptor.forClass(MatchPrediction.class);
        verify(matchPredictionRepository).save(saved.capture());
        assertThat(saved.getValue().getPredictedTeam()).isEqualTo("HOME");
        assertThat(saved.getValue().getPredictorEmail()).isEqualTo(ADMIN);
        assertThat(response.state()).isEqualTo("OPEN");
        assertThat(response.myPick()).isEqualTo("HOME");
        assertThat(response.homePicks()).isNull();
        assertThat(response.closesAt()).isEqualTo(NOW.plusMinutes(1));
    }

    @Test
    void changesAnEarlierPick() {
        Match match = match(10L, NOW.minusMinutes(1));
        stubMatch(match, participants(match));
        MatchPrediction earlier = prediction(10L, ADMIN, "HOME");
        when(matchPredictionRepository.findByMatchIdAndPredictorEmail(10L, ADMIN)).thenReturn(Optional.of(earlier));

        service.predict(1L, 10L, ADMIN, null, "AWAY");

        assertThat(earlier.getPredictedTeam()).isEqualTo("AWAY");
        verify(matchPredictionRepository).save(earlier);
    }

    @Test
    void refusesPicksOnceTheWindowHasClosed() {
        Match late = match(10L, NOW.minusMinutes(3));
        stubMatch(late, participants(late));
        assertThatThrownBy(() -> service.predict(1L, 10L, ADMIN, null, "HOME")).isInstanceOf(MatchConflictException.class);

        Match closedEarly = match(11L, NOW.minusMinutes(1));
        closedEarly.setPredictionsClosedAt(NOW.minusSeconds(30));
        stubMatch(closedEarly, participants(closedEarly));
        assertThatThrownBy(() -> service.predict(1L, 11L, ADMIN, null, "HOME")).isInstanceOf(MatchConflictException.class);

        Match recorded = match(12L, NOW.minusMinutes(1));
        recorded.setWinningTeam("HOME");
        recorded.setStatus(MatchStatus.COMPLETED);
        stubMatch(recorded, participants(recorded));
        assertThatThrownBy(() -> service.predict(1L, 12L, ADMIN, null, "HOME")).isInstanceOf(MatchConflictException.class);

        verify(matchPredictionRepository, never()).save(any());
    }

    @Test
    void refusesPicksOnYourOwnMatch() {
        Match match = match(10L, NOW.minusMinutes(1));
        List<MatchParticipant> participants = participants(match);
        UUID linkedUser = UUID.randomUUID();
        participants.get(4).getPlayer().setAuthUserId(linkedUser);
        stubMatch(match, participants);

        assertThatThrownBy(() -> service.predict(1L, 10L, ADMIN, linkedUser.toString(), "HOME"))
            .isInstanceOf(IllegalArgumentException.class);

        when(accessControlService.resolveDisplayNickname(ADMIN)).thenReturn("player3");
        assertThatThrownBy(() -> service.predict(1L, 10L, ADMIN, null, "HOME"))
            .isInstanceOf(IllegalArgumentException.class);
        verify(matchPredictionRepository, never()).save(any());
    }

    @Test
    void refusesUnknownTeamsAndMatchesOfOtherGroups() {
        Match match = match(10L, NOW.minusMinutes(1));
        stubMatch(match, participants(match));

        assertThatThrownBy(() -> service.predict(1L, 10L, ADMIN, null, "DRAW")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.predict(2L, 10L, ADMIN, null, "HOME")).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void closesPicksEarlyAndLogsIt() {
        Match match = match(10L, NOW.minusMinutes(1));
        stubMatch(match, participants(match));

        service.close(1L, 10L, ADMIN, "YOUR_USERNAME");

        assertThat(match.getPredictionsClosedAt()).isEqualTo(NOW);
        verify(operationAuditLogService).recordPredictionsClosed(ADMIN, "YOUR_USERNAME", 10L, 1L);

        service.close(1L, 10L, ADMIN, "YOUR_USERNAME");
        verify(operationAuditLogService).recordPredictionsClosed(any(), any(), any(), any());
    }

    @Test
    void paysCorrectPicksButNotTheResultRecorders() {
        Match match = match(10L, NOW.minusMinutes(20));
        match.setWinningTeam("AWAY");
        match.setStatus(MatchStatus.COMPLETED);
        match.setResultRecordedByEmail("recorder@example.com");
        when(matchPredictionRepository.findByMatchIdOrderByPredictorEmailAsc(10L)).thenReturn(List.of(
            prediction(10L, "a@example.com", "AWAY"),
            prediction(10L, "b@example.com", "HOME"),
            prediction(10L, "recorder@example.com", "AWAY")
        ));

        service.settle(match);

        InOrder inOrder = Mockito.inOrder(pointService);
        inOrder.verify(pointService).syncPredictionPoint("a@example.com", 10L, true);
        inOrder.verify(pointService).syncPredictionPoint("b@example.com", 10L, false);
        inOrder.verify(pointService).syncPredictionPoint("recorder@example.com", 10L, false);
    }

    @Test
    void takesEveryPredictionPointBackWhenTheMatchGoes() {
        when(matchPredictionRepository.findByMatchIdOrderByPredictorEmailAsc(10L)).thenReturn(List.of(
            prediction(10L, "a@example.com", "AWAY"),
            prediction(10L, "b@example.com", "HOME")
        ));

        service.revoke(10L);

        verify(pointService).syncPredictionPoint("a@example.com", 10L, false);
        verify(pointService).syncPredictionPoint("b@example.com", 10L, false);
    }

    @Test
    void showsCountsOnlyOncePicksAreClosed() {
        Match open = match(10L, NOW.minusMinutes(1));
        Match closed = match(11L, NOW.minusMinutes(10));
        Match resolved = match(12L, NOW.minusMinutes(40));
        resolved.setWinningTeam("HOME");
        resolved.setStatus(MatchStatus.COMPLETED);
        resolved.setResultRecordedByEmail(ADMIN);
        when(matchRepository.findAwaitingResultSince(eq(1L), any())).thenReturn(List.of(open, closed));
        when(matchPredictionRepository.findResolvedByPredictor(eq(ADMIN), any()))
            .thenReturn(List.of(prediction(12L, ADMIN, "HOME")));
        when(matchRepository.findAllById(List.of(12L))).thenReturn(List.of(resolved));
        List<MatchParticipant> participants = new ArrayList<>();
        participants.addAll(participants(open));
        participants.addAll(participants(closed));
        participants.addAll(participants(resolved));
        when(matchParticipantRepository.findByMatchIdInWithPlayerAndMatch(anyList())).thenReturn(participants);
        when(matchPredictionRepository.findByMatchIdIn(anyList())).thenReturn(List.of(
            prediction(10L, ADMIN, "AWAY"),
            prediction(10L, "b@example.com", "HOME"),
            prediction(11L, "b@example.com", "HOME"),
            prediction(11L, "c@example.com", "AWAY"),
            prediction(11L, "d@example.com", "HOME"),
            prediction(12L, ADMIN, "HOME")
        ));
        when(matchPredictionRepository.summarizeResolved(ADMIN)).thenReturn(record(4, 3));

        PredictionBoardResponse board = service.board(1L, ADMIN, null);

        assertThat(board.now()).isEqualTo(NOW);
        assertThat(board.open()).singleElement().satisfies(response -> {
            assertThat(response.matchId()).isEqualTo(10L);
            assertThat(response.myPick()).isEqualTo("AWAY");
            assertThat(response.homePicks()).isNull();
            assertThat(response.homePlayers()).hasSize(3);
        });
        assertThat(board.closed()).singleElement().satisfies(response -> {
            assertThat(response.state()).isEqualTo("CLOSED");
            assertThat(response.homePicks()).isEqualTo(2);
            assertThat(response.awayPicks()).isEqualTo(1);
        });
        assertThat(board.history()).singleElement().satisfies(response -> {
            assertThat(response.state()).isEqualTo("RESOLVED");
            assertThat(response.hit()).isTrue();
            assertThat(response.pointsExcluded()).isTrue();
        });
        assertThat(board.stats().resolved()).isEqualTo(4);
        assertThat(board.stats().hits()).isEqualTo(3);
    }

    private void stubMatch(Match match, List<MatchParticipant> participants) {
        when(matchRepository.findByIdForUpdate(match.getId())).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(match.getId())).thenReturn(participants);
    }

    private Match match(Long id, OffsetDateTime createdAt) {
        Match match = new Match();
        match.setId(id);
        match.setGroup(group);
        match.setStatus(MatchStatus.CONFIRMED);
        match.setRaceComposition("PPP");
        match.setCreatedAt(createdAt);
        return match;
    }

    private List<MatchParticipant> participants(Match match) {
        List<MatchParticipant> participants = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            Player player = new Player();
            player.setId(match.getId() * 10 + index);
            player.setGroup(group);
            player.setNickname("player" + index);
            MatchParticipant participant = new MatchParticipant();
            ReflectionTestUtils.setField(participant, "id", match.getId() * 10 + index);
            participant.setMatch(match);
            participant.setPlayer(player);
            participant.setTeam(index < 3 ? "HOME" : "AWAY");
            participant.setAssignedRace("P");
            participants.add(participant);
        }
        return participants;
    }

    private MatchPrediction prediction(Long matchId, String email, String team) {
        MatchPrediction prediction = new MatchPrediction();
        prediction.setMatchId(matchId);
        prediction.setPredictorEmail(email);
        prediction.setPredictedTeam(team);
        return prediction;
    }

    private MatchPredictionRepository.PredictionRecord record(long total, long hits) {
        return new MatchPredictionRepository.PredictionRecord() {
            @Override
            public Long getTotal() {
                return total;
            }

            @Override
            public Long getHits() {
                return hits;
            }
        };
    }
}
