package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.points.dto.MatchConfirmationListResponse;
import com.balancify.backend.api.points.dto.MatchConfirmationPlayerResponse;
import com.balancify.backend.api.points.dto.MatchConfirmationResponse;
import com.balancify.backend.config.PointProperties;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.MatchSource;
import com.balancify.backend.domain.MatchStatus;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchRepository;
import com.balancify.backend.service.exception.MatchConfirmationForbiddenException;
import com.balancify.backend.service.exception.MatchConflictException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MatchConfirmationServiceTest {

    private static final String ME = "member@example.com";
    private static final UUID MY_LOGIN = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String MY_NICKNAME = "YOUR_USERNAME";
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-04T12:00:00Z");

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private MatchParticipantRepository matchParticipantRepository;

    @Mock
    private PointService pointService;

    @Mock
    private AccessControlService accessControlService;

    private MatchConfirmationService service;
    private Group group;

    @BeforeEach
    void setUp() {
        service = new MatchConfirmationService(
            matchRepository,
            matchParticipantRepository,
            pointService,
            accessControlService,
            new PointProperties(),
            Clock.fixed(Instant.from(NOW), ZoneOffset.UTC)
        );
        group = new Group();
        group.setId(1L);
        lenient().when(accessControlService.resolveDisplayNickname(ME)).thenReturn(MY_NICKNAME);
        lenient().when(matchRepository.findBalancedResultsRecordedSince(eq(1L), any())).thenReturn(List.of());
        lenient().when(pointService.getMatchConfirmState(eq(ME), any())).thenReturn(new PointService.MatchConfirmState(Set.of(), 0));
    }

    @Test
    void listsTheMatchesYouPlayedWhileTheyCanBeConfirmed() {
        Match linked = recorded(10L, NOW.minusHours(1), "HOME");
        List<MatchParticipant> linkedPlayers = participants(linked, 3);
        linkedPlayers.get(4).getPlayer().setAuthUserId(MY_LOGIN);
        Match seriesGame = recorded(11L, NOW.minusHours(2), "AWAY");
        seriesGame.setBalanceSeriesId(5L);
        seriesGame.setSeriesGameNumber(2);
        List<MatchParticipant> seriesPlayers = participants(seriesGame, 3);
        seriesPlayers.get(1).getPlayer().setNickname(" your_username ");
        Match someoneElses = recorded(12L, NOW.minusHours(3), "HOME");
        Match twoOnTwo = recorded(13L, NOW.minusHours(4), "HOME");
        List<MatchParticipant> twoOnTwoPlayers = participants(twoOnTwo, 2);
        twoOnTwoPlayers.get(0).getPlayer().setAuthUserId(MY_LOGIN);
        Match manual = recorded(14L, NOW.minusHours(5), "HOME");
        manual.setSource(MatchSource.MANUAL);
        List<MatchParticipant> manualPlayers = participants(manual, 3);
        manualPlayers.get(0).getPlayer().setAuthUserId(MY_LOGIN);
        Match expired = recorded(15L, NOW.minusHours(48), "HOME");
        List<MatchParticipant> expiredPlayers = participants(expired, 3);
        expiredPlayers.get(0).getPlayer().setAuthUserId(MY_LOGIN);

        when(matchRepository.findBalancedResultsRecordedSince(1L, NOW.minusHours(48)))
            .thenReturn(List.of(linked, seriesGame, someoneElses, twoOnTwo, manual, expired));
        List<MatchParticipant> all = new ArrayList<>();
        all.addAll(linkedPlayers);
        all.addAll(seriesPlayers);
        all.addAll(participants(someoneElses, 3));
        all.addAll(twoOnTwoPlayers);
        all.addAll(manualPlayers);
        all.addAll(expiredPlayers);
        when(matchParticipantRepository.findByMatchIdInWithPlayerAndMatch(anyList())).thenReturn(all);
        when(pointService.getMatchConfirmState(ME, List.of(10L, 11L)))
            .thenReturn(new PointService.MatchConfirmState(Set.of(10L), 3));

        MatchConfirmationListResponse response = service.list(1L, ME, MY_LOGIN.toString());

        assertThat(response.matches()).extracting(
            MatchConfirmationResponse::matchId,
            MatchConfirmationResponse::myTeam,
            MatchConfirmationResponse::winnerTeam,
            MatchConfirmationResponse::confirmed
        ).containsExactly(
            tuple(10L, "AWAY", "HOME", true),
            tuple(11L, "HOME", "AWAY", false)
        );
        MatchConfirmationResponse first = response.matches().get(0);
        assertThat(first.confirmDeadline()).isEqualTo(NOW.minusHours(1).plusHours(48));
        assertThat(first.homePlayers()).extracting(MatchConfirmationPlayerResponse::nickname)
            .containsExactly("p10-0", "p10-1", "p10-2");
        assertThat(response.matches().get(1).seriesGameNumber()).isEqualTo(2);
        assertThat(response.confirmedToday()).isEqualTo(3);
        assertThat(response.dailyCap()).isEqualTo(10);
        assertThat(response.points()).isEqualTo(1);
        assertThat(response.windowHours()).isEqualTo(48);
        assertThat(response.toString()).doesNotContain("@");
    }

    @Test
    void confirmsAMatchYouPlayedForAPoint() {
        Match match = recorded(10L, NOW.minusHours(1), "HOME");
        List<MatchParticipant> players = participants(match, 3);
        players.get(2).getPlayer().setNickname(MY_NICKNAME);
        stub(match, players);
        when(pointService.grantMatchConfirmPoint(ME, 10L)).thenReturn(PointService.MatchConfirmOutcome.CONFIRMED);

        assertThat(service.confirm(1L, 10L, ME, null)).isNotNull();

        verify(pointService).grantMatchConfirmPoint(ME, 10L);
    }

    @Test
    void confirmingTheSameMatchAgainIsFine() {
        Match match = recorded(10L, NOW.minusHours(1), "HOME");
        List<MatchParticipant> players = participants(match, 3);
        players.get(0).getPlayer().setAuthUserId(MY_LOGIN);
        stub(match, players);
        when(pointService.grantMatchConfirmPoint(ME, 10L)).thenReturn(PointService.MatchConfirmOutcome.ALREADY_CONFIRMED);

        assertThat(service.confirm(1L, 10L, ME, MY_LOGIN.toString())).isNotNull();
    }

    @Test
    void refusesMatchesYouDidNotPlay() {
        Match match = recorded(10L, NOW.minusHours(1), "HOME");
        stub(match, participants(match, 3));

        assertThatThrownBy(() -> service.confirm(1L, 10L, ME, MY_LOGIN.toString()))
            .isInstanceOf(MatchConfirmationForbiddenException.class);
        verify(pointService, never()).grantMatchConfirmPoint(any(), anyLong());
    }

    @Test
    void refusesManualEntriesTwoOnTwoGamesAndMatchesWithoutAResult() {
        Match manual = recorded(10L, NOW.minusHours(1), "HOME");
        manual.setSource(MatchSource.MANUAL);
        Match twoOnTwo = recorded(11L, NOW.minusHours(1), "HOME");
        Match waiting = recorded(12L, NOW.minusHours(1), null);
        waiting.setResultRecordedAt(null);
        for (Match match : List.of(manual, twoOnTwo, waiting)) {
            List<MatchParticipant> players = participants(match, match == twoOnTwo ? 2 : 3);
            players.get(0).getPlayer().setAuthUserId(MY_LOGIN);
            stub(match, players);

            assertThatThrownBy(() -> service.confirm(1L, match.getId(), ME, MY_LOGIN.toString()))
                .isInstanceOf(IllegalArgumentException.class);
        }
        verify(pointService, never()).grantMatchConfirmPoint(any(), anyLong());
    }

    @Test
    void refusesAfterTheWindowUnlessAlreadyConfirmed() {
        Match match = recorded(10L, NOW.minusHours(48), "HOME");
        List<MatchParticipant> players = participants(match, 3);
        players.get(0).getPlayer().setAuthUserId(MY_LOGIN);
        stub(match, players);

        assertThatThrownBy(() -> service.confirm(1L, 10L, ME, MY_LOGIN.toString()))
            .isInstanceOf(IllegalArgumentException.class);

        when(pointService.hasConfirmedMatch(ME, 10L)).thenReturn(true);
        assertThat(service.confirm(1L, 10L, ME, MY_LOGIN.toString())).isNotNull();
        verify(pointService, never()).grantMatchConfirmPoint(any(), anyLong());
    }

    @Test
    void reportsTheDailyCapAsAConflict() {
        Match match = recorded(10L, NOW.minusHours(1), "HOME");
        List<MatchParticipant> players = participants(match, 3);
        players.get(0).getPlayer().setAuthUserId(MY_LOGIN);
        stub(match, players);
        when(pointService.grantMatchConfirmPoint(ME, 10L)).thenReturn(PointService.MatchConfirmOutcome.DAILY_CAP_REACHED);

        assertThatThrownBy(() -> service.confirm(1L, 10L, ME, MY_LOGIN.toString()))
            .isInstanceOf(MatchConflictException.class);
    }

    @Test
    void treatsMatchesOfOtherGroupsAsMissing() {
        Match match = recorded(10L, NOW.minusHours(1), "HOME");
        Group other = new Group();
        other.setId(2L);
        match.setGroup(other);
        when(matchRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(match));

        assertThatThrownBy(() -> service.confirm(1L, 10L, ME, MY_LOGIN.toString()))
            .isInstanceOf(NoSuchElementException.class);
    }

    private void stub(Match match, List<MatchParticipant> participants) {
        when(matchRepository.findByIdForUpdate(match.getId())).thenReturn(Optional.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(match.getId())).thenReturn(participants);
    }

    private Match recorded(Long id, OffsetDateTime recordedAt, String winner) {
        Match match = new Match();
        match.setId(id);
        match.setGroup(group);
        match.setSource(MatchSource.BALANCED);
        match.setStatus(winner == null ? MatchStatus.CONFIRMED : MatchStatus.COMPLETED);
        match.setRaceComposition("PPP");
        match.setWinningTeam(winner);
        match.setResultRecordedAt(recordedAt);
        return match;
    }

    private List<MatchParticipant> participants(Match match, int teamSize) {
        List<MatchParticipant> participants = new ArrayList<>();
        for (int index = 0; index < teamSize * 2; index++) {
            Player player = new Player();
            player.setId(match.getId() * 10 + index);
            player.setGroup(group);
            player.setNickname("p" + match.getId() + "-" + index);
            MatchParticipant participant = new MatchParticipant();
            ReflectionTestUtils.setField(participant, "id", match.getId() * 10 + index);
            participant.setMatch(match);
            participant.setPlayer(player);
            participant.setTeam(index < teamSize ? "HOME" : "AWAY");
            participant.setAssignedRace("P");
            participants.add(participant);
        }
        return participants;
    }
}
