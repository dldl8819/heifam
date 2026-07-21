package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.CreateGroupMatchRequest;
import com.balancify.backend.api.group.dto.CreateGroupMatchResponse;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchSource;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.MatchStatus;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.GroupRepository;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchRepository;
import com.balancify.backend.repository.PlayerRepository;
import com.balancify.backend.service.exception.MatchConflictException;
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
class GroupMatchAdminServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private MatchParticipantRepository matchParticipantRepository;

    private GroupMatchAdminService groupMatchAdminService;

    @BeforeEach
    void setUp() {
        groupMatchAdminService = new GroupMatchAdminService(
            groupRepository,
            playerRepository,
            matchRepository,
            matchParticipantRepository,
            5
        );
    }

    @Test
    void createsMatchWithParticipantsWhenInputIsValid() {
        Group group = new Group();
        group.setId(1L);
        group.setName("Group 1");

        Match savedMatch = new Match();
        savedMatch.setId(500L);
        savedMatch.setGroup(group);

        List<Player> players = List.of(
            player(1L, group),
            player(2L, group),
            player(3L, group),
            player(4L, group),
            player(5L, group),
            player(6L, group)
        );

        when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(group));
        when(playerRepository.findByGroup_IdAndIdIn(1L, List.of(1L, 2L, 3L, 4L, 5L, 6L)))
            .thenReturn(players);
        when(matchRepository.findRecentDuplicateCandidates(any(), any(), any(), any(), any()))
            .thenReturn(List.of());
        when(matchRepository.save(any(Match.class))).thenReturn(savedMatch);

        CreateGroupMatchResponse response = groupMatchAdminService.createMatch(
            1L,
            new CreateGroupMatchRequest(List.of(1L, 2L, 3L), List.of(4L, 5L, 6L))
        );

        assertThat(response.matchId()).isEqualTo(500L);
        assertThat(response.confirmationStatus()).isEqualTo("CREATED");
        verify(matchRepository).findRecentDuplicateCandidates(
            eq(1L),
            eq(3),
            eq("1-2-3-4-5-6"),
            eq((String) null),
            any()
        );
        verify(matchParticipantRepository).saveAll(any());
    }

    @Test
    void createsTwoVsTwoBalancedMatchWhenTeamSizeIsProvided() {
        Group group = new Group();
        group.setId(1L);
        group.setName("Group 1");

        Match savedMatch = new Match();
        savedMatch.setId(501L);
        savedMatch.setGroup(group);

        List<Player> players = List.of(
            player(1L, group),
            player(2L, group),
            player(3L, group),
            player(4L, group)
        );

        when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(group));
        when(playerRepository.findByGroup_IdAndIdIn(1L, List.of(1L, 2L, 3L, 4L)))
            .thenReturn(players);
        when(matchRepository.findRecentDuplicateCandidates(any(), any(), any(), any(), any()))
            .thenReturn(List.of());
        when(matchRepository.save(any(Match.class))).thenReturn(savedMatch);

        CreateGroupMatchResponse response = groupMatchAdminService.createMatch(
            1L,
            new CreateGroupMatchRequest(List.of(1L, 2L), List.of(3L, 4L), 2)
        );

        assertThat(response.matchId()).isEqualTo(501L);
        assertThat(response.confirmationStatus()).isEqualTo("CREATED");
        verify(matchRepository).findRecentDuplicateCandidates(
            eq(1L),
            eq(2),
            eq("1-2-3-4"),
            eq((String) null),
            any()
        );
        verify(matchParticipantRepository).saveAll(any());
    }

    @Test
    void createsTwoVsTwoBalancedMatchWithRaceComposition() {
        Group group = new Group();
        group.setId(1L);
        group.setName("Group 1");

        Match savedMatch = new Match();
        savedMatch.setId(502L);
        savedMatch.setGroup(group);

        List<Player> players = List.of(
            player(1L, group, "PT"),
            player(2L, group, "P"),
            player(3L, group, "PT"),
            player(4L, group, "P")
        );

        when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(group));
        when(playerRepository.findByGroup_IdAndIdIn(1L, List.of(1L, 2L, 3L, 4L)))
            .thenReturn(players);
        when(matchRepository.findRecentDuplicateCandidates(any(), any(), any(), any(), any()))
            .thenReturn(List.of());
        when(matchRepository.save(any(Match.class))).thenReturn(savedMatch);

        CreateGroupMatchResponse response = groupMatchAdminService.createMatch(
            1L,
            new CreateGroupMatchRequest(List.of(1L, 2L), List.of(3L, 4L), 2, "PT")
        );

        assertThat(response.matchId()).isEqualTo(502L);
        assertThat(response.confirmationStatus()).isEqualTo("CREATED");

        ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(matchCaptor.capture());
        assertThat(matchCaptor.getValue().getTeamSize()).isEqualTo(2);
        assertThat(matchCaptor.getValue().getRaceComposition()).isEqualTo("PT");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MatchParticipant>> participantsCaptor = ArgumentCaptor.forClass(List.class);
        verify(matchParticipantRepository).saveAll(participantsCaptor.capture());
        assertThat(participantsCaptor.getValue())
            .extracting(MatchParticipant::getAssignedRace)
            .containsExactlyInAnyOrder("P", "T", "P", "T");
    }

    @Test
    void throwsWhenDuplicatePlayerExistsAcrossTeams() {
        assertThatThrownBy(() ->
            groupMatchAdminService.createMatch(
                1L,
                new CreateGroupMatchRequest(List.of(1L, 2L, 3L), List.of(3L, 4L, 5L))
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Players must be unique across both teams");
    }

    @Test
    void reusesExistingMatchWhenSameParticipantsAndSameTeamsRecentlyConfirmed() {
        Group group = new Group();
        group.setId(1L);

        List<Player> players = List.of(
            player(1L, group),
            player(2L, group),
            player(3L, group),
            player(4L, group),
            player(5L, group),
            player(6L, group)
        );

        Match existing = new Match();
        existing.setId(777L);
        existing.setGroup(group);
        existing.setPlayedAt(OffsetDateTime.now());
        existing.setStatus(MatchStatus.CONFIRMED);
        existing.setParticipantSignature("1-2-3-4-5-6");
        existing.setTeamSignature("HOME:1-2-3|AWAY:4-5-6");

        when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(group));
        when(playerRepository.findByGroup_IdAndIdIn(1L, List.of(1L, 2L, 3L, 4L, 5L, 6L)))
            .thenReturn(players);
        when(matchRepository.findRecentDuplicateCandidates(any(), any(), any(), any(), any()))
            .thenReturn(List.of(existing));

        CreateGroupMatchResponse response = groupMatchAdminService.createMatch(
            1L,
            new CreateGroupMatchRequest(List.of(1L, 2L, 3L), List.of(4L, 5L, 6L))
        );

        assertThat(response.matchId()).isEqualTo(777L);
        assertThat(response.confirmationStatus()).isEqualTo("REUSED_EXISTING");
    }

    @Test
    void allowsSameParticipantsWhenTeamPartitionDiffers() {
        Group group = new Group();
        group.setId(1L);
        List<Player> players = List.of(
            player(1L, group), player(2L, group), player(3L, group),
            player(4L, group), player(5L, group), player(6L, group)
        );
        Match existing = new Match();
        existing.setId(778L);
        existing.setGroup(group);
        existing.setStatus(MatchStatus.CONFIRMED);
        existing.setTeamSize(3);
        existing.setParticipantSignature("1-2-3-4-5-6");
        existing.setTeamSignature("HOME:1-4-5|AWAY:2-3-6");
        Match savedMatch = new Match();
        savedMatch.setId(779L);
        savedMatch.setGroup(group);
        when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(group));
        when(playerRepository.findByGroup_IdAndIdIn(1L, List.of(1L, 2L, 3L, 4L, 5L, 6L)))
            .thenReturn(players);
        when(matchRepository.findRecentDuplicateCandidates(any(), any(), any(), any(), any()))
            .thenReturn(List.of(existing));
        when(matchRepository.save(any(Match.class))).thenReturn(savedMatch);
        CreateGroupMatchResponse response = groupMatchAdminService.createMatch(
            1L,
            new CreateGroupMatchRequest(List.of(1L, 2L, 3L), List.of(4L, 5L, 6L))
        );
        assertThat(response.matchId()).isEqualTo(779L);
        assertThat(response.confirmationStatus()).isEqualTo("CREATED");
    }
    @Test
    void allowsDifferentParticipantsEvenAtSameTimestamp() {
        Group group = new Group();
        group.setId(1L);

        Match existing = new Match();
        existing.setId(801L);
        existing.setGroup(group);
        existing.setPlayedAt(OffsetDateTime.parse("2026-04-02T17:41:00+09:00"));
        existing.setStatus(MatchStatus.CONFIRMED);
        existing.setTeamSize(3);
        existing.setParticipantSignature("1-2-3-4-5-6");
        existing.setTeamSignature("HOME:1-2-3|AWAY:4-5-6");

        Match savedMatch = new Match();
        savedMatch.setId(802L);
        savedMatch.setGroup(group);

        List<Player> requestedPlayers = List.of(
            player(7L, group),
            player(8L, group),
            player(9L, group),
            player(10L, group),
            player(11L, group),
            player(12L, group)
        );

        when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(group));
        when(playerRepository.findByGroup_IdAndIdIn(1L, List.of(7L, 8L, 9L, 10L, 11L, 12L)))
            .thenReturn(requestedPlayers);
        when(matchRepository.findRecentDuplicateCandidates(any(), any(), any(), any(), any()))
            .thenReturn(List.of(existing));
        when(matchRepository.save(any(Match.class))).thenReturn(savedMatch);

        CreateGroupMatchResponse response = groupMatchAdminService.createMatch(
            1L,
            new CreateGroupMatchRequest(List.of(7L, 8L, 9L), List.of(10L, 11L, 12L))
        );

        assertThat(response.matchId()).isEqualTo(802L);
        assertThat(response.confirmationStatus()).isEqualTo("CREATED");
    }

    @Test
    void allowsSameParticipantsWhenExistingMatchIsOutsideDuplicateWindow() {
        Group group = new Group();
        group.setId(1L);

        List<Player> players = List.of(
            player(1L, group),
            player(2L, group),
            player(3L, group),
            player(4L, group),
            player(5L, group),
            player(6L, group)
        );

        Match oldMatch = new Match();
        oldMatch.setId(900L);
        oldMatch.setGroup(group);
        oldMatch.setCreatedAt(OffsetDateTime.now().minusMinutes(10));
        oldMatch.setStatus(MatchStatus.CONFIRMED);
        oldMatch.setTeamSize(3);
        oldMatch.setParticipantSignature("1-2-3-4-5-6");
        oldMatch.setTeamSignature("HOME:1-2-3|AWAY:4-5-6");

        Match savedMatch = new Match();
        savedMatch.setId(901L);
        savedMatch.setGroup(group);

        when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(group));
        when(playerRepository.findByGroup_IdAndIdIn(1L, List.of(1L, 2L, 3L, 4L, 5L, 6L)))
            .thenReturn(players);
        when(matchRepository.findRecentDuplicateCandidates(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            OffsetDateTime fromInclusive = invocation.getArgument(4);
            if (oldMatch.getCreatedAt().isBefore(fromInclusive)) {
                return List.of();
            }
            return List.of(oldMatch);
        });
        when(matchRepository.save(any(Match.class))).thenReturn(savedMatch);

        CreateGroupMatchResponse response = groupMatchAdminService.createMatch(
            1L,
            new CreateGroupMatchRequest(List.of(1L, 2L, 3L), List.of(4L, 5L, 6L))
        );

        assertThat(response.matchId()).isEqualTo(901L);
        assertThat(response.confirmationStatus()).isEqualTo("CREATED");
    }

    @Test
    void doesNotTreatSameParticipantsAsDuplicateWhenExistingMatchTeamSizeDiffers() {
        Group group = new Group();
        group.setId(1L);

        List<Player> players = List.of(
            player(1L, group),
            player(2L, group),
            player(3L, group),
            player(4L, group),
            player(5L, group),
            player(6L, group)
        );

        Match existing = new Match();
        existing.setId(910L);
        existing.setGroup(group);
        existing.setPlayedAt(OffsetDateTime.now());
        existing.setStatus(MatchStatus.CONFIRMED);
        existing.setTeamSize(2);
        existing.setParticipantSignature("1-2-3-4-5-6");
        existing.setTeamSignature("HOME:1-2-3|AWAY:4-5-6");

        Match savedMatch = new Match();
        savedMatch.setId(911L);
        savedMatch.setGroup(group);

        when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(group));
        when(playerRepository.findByGroup_IdAndIdIn(1L, List.of(1L, 2L, 3L, 4L, 5L, 6L)))
            .thenReturn(players);
        when(matchRepository.findRecentDuplicateCandidates(any(), any(), any(), any(), any()))
            .thenReturn(List.of(existing));
        when(matchRepository.save(any(Match.class))).thenReturn(savedMatch);

        CreateGroupMatchResponse response = groupMatchAdminService.createMatch(
            1L,
            new CreateGroupMatchRequest(List.of(1L, 2L, 3L), List.of(4L, 5L, 6L))
        );

        assertThat(response.matchId()).isEqualTo(911L);
        assertThat(response.confirmationStatus()).isEqualTo("CREATED");
    }

    @Test
    void rejectsNewConfirmationWhenExistingMatchIsRecentlyCompleted() {
        Group group = new Group();
        group.setId(1L);

        List<Player> players = List.of(
            player(1L, group),
            player(2L, group),
            player(3L, group),
            player(4L, group),
            player(5L, group),
            player(6L, group)
        );

        Match completed = new Match();
        completed.setId(920L);
        completed.setGroup(group);
        completed.setPlayedAt(OffsetDateTime.now());
        completed.setStatus(MatchStatus.COMPLETED);
        completed.setTeamSize(3);
        completed.setParticipantSignature("1-2-3-4-5-6");
        completed.setTeamSignature("HOME:1-2-3|AWAY:4-5-6");


        when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(group));
        when(playerRepository.findByGroup_IdAndIdIn(1L, List.of(1L, 2L, 3L, 4L, 5L, 6L)))
            .thenReturn(players);
        when(matchRepository.findRecentDuplicateCandidates(any(), any(), any(), any(), any()))
            .thenReturn(List.of(completed));

        CreateGroupMatchResponse response = groupMatchAdminService.createMatch(
            1L,
            new CreateGroupMatchRequest(List.of(1L, 2L, 3L), List.of(4L, 5L, 6L))
        );

        assertThat(response.matchId()).isNull();
        assertThat(response.confirmationStatus()).isEqualTo("DUPLICATE_REJECTED");
    }

    @Test
    void allowsNewConfirmationWhenExistingMatchIsCancelled() {
        Group group = new Group();
        group.setId(1L);

        List<Player> players = List.of(
            player(1L, group),
            player(2L, group),
            player(3L, group),
            player(4L, group),
            player(5L, group),
            player(6L, group)
        );

        Match cancelled = new Match();
        cancelled.setId(930L);
        cancelled.setGroup(group);
        cancelled.setPlayedAt(OffsetDateTime.now());
        cancelled.setStatus(MatchStatus.CANCELLED);
        cancelled.setTeamSize(3);
        cancelled.setParticipantSignature("1-2-3-4-5-6");
        cancelled.setTeamSignature("HOME:1-2-3|AWAY:4-5-6");

        Match savedMatch = new Match();
        savedMatch.setId(931L);
        savedMatch.setGroup(group);

        when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(group));
        when(playerRepository.findByGroup_IdAndIdIn(1L, List.of(1L, 2L, 3L, 4L, 5L, 6L)))
            .thenReturn(players);
        when(matchRepository.findRecentDuplicateCandidates(any(), any(), any(), any(), any()))
            .thenReturn(List.of(cancelled));
        when(matchRepository.save(any(Match.class))).thenReturn(savedMatch);

        CreateGroupMatchResponse response = groupMatchAdminService.createMatch(
            1L,
            new CreateGroupMatchRequest(List.of(1L, 2L, 3L), List.of(4L, 5L, 6L))
        );

        assertThat(response.matchId()).isEqualTo(931L);
        assertThat(response.confirmationStatus()).isEqualTo("CREATED");
    }

    @Test
    void treatsParticipantOrderVariationAsSameDuplicateIdentity() {
        Group group = new Group();
        group.setId(1L);

        List<Player> players = List.of(
            player(1L, group),
            player(2L, group),
            player(3L, group),
            player(4L, group),
            player(5L, group),
            player(6L, group)
        );

        Match existing = new Match();
        existing.setId(940L);
        existing.setGroup(group);
        existing.setPlayedAt(OffsetDateTime.now());
        existing.setStatus(MatchStatus.CONFIRMED);
        existing.setTeamSize(3);
        existing.setParticipantSignature("1-2-3-4-5-6");
        existing.setTeamSignature("HOME:1-2-3|AWAY:4-5-6");

        when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(group));
        when(playerRepository.findByGroup_IdAndIdIn(1L, List.of(3L, 2L, 1L, 6L, 5L, 4L)))
            .thenReturn(players);
        when(matchRepository.findRecentDuplicateCandidates(any(), any(), any(), any(), any()))
            .thenReturn(List.of(existing));

        CreateGroupMatchResponse response = groupMatchAdminService.createMatch(
            1L,
            new CreateGroupMatchRequest(List.of(3L, 2L, 1L), List.of(6L, 5L, 4L))
        );

        assertThat(response.matchId()).isEqualTo(940L);
        assertThat(response.confirmationStatus()).isEqualTo("REUSED_EXISTING");
    }

    @Test
    void createsManualTwoVsTwoMatchWithDuplicateProtection() {
        Group group = new Group();
        group.setId(1L);

        List<Player> players = List.of(
            player(1L, group),
            player(2L, group),
            player(3L, group),
            player(4L, group)
        );

        Match savedMatch = new Match();
        savedMatch.setId(950L);
        savedMatch.setGroup(group);

        when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(group));
        when(playerRepository.findByGroup_IdAndIdIn(1L, List.of(1L, 2L, 3L, 4L)))
            .thenReturn(players);
        when(matchRepository.save(any(Match.class))).thenReturn(savedMatch);

        Match created = groupMatchAdminService.createConfirmedMatch(
            1L,
            List.of(1L, 2L),
            List.of(3L, 4L),
            2,
            MatchSource.MANUAL,
            "리겜 수동 입력",
            null
        );

        assertThat(created.getId()).isEqualTo(950L);

        ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(matchCaptor.capture());
        Match persistedMatch = matchCaptor.getValue();
        assertThat(persistedMatch.getSource()).isEqualTo(MatchSource.MANUAL);
        assertThat(persistedMatch.getTeamSize()).isEqualTo(2);
        assertThat(persistedMatch.getStatus()).isEqualTo(MatchStatus.CONFIRMED);
        assertThat(persistedMatch.getParticipantSignature()).isEqualTo("1-2-3-4");
        assertThat(persistedMatch.getNote()).isEqualTo("리겜 수동 입력");
    }

    @Test
    void allowsManualMatchWhenTeamPartitionDiffers() {
        Group group = new Group();
        group.setId(1L);

        List<Player> players = List.of(
            player(1L, group),
            player(2L, group),
            player(3L, group),
            player(4L, group),
            player(5L, group),
            player(6L, group)
        );

        Match savedMatch = new Match();
        savedMatch.setId(970L);
        savedMatch.setGroup(group);

        when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(group));
        when(playerRepository.findByGroup_IdAndIdIn(1L, List.of(1L, 4L, 5L, 2L, 3L, 6L)))
            .thenReturn(players);
        when(matchRepository.save(any(Match.class))).thenReturn(savedMatch);

        Match created = groupMatchAdminService.createConfirmedMatch(
            1L,
            List.of(1L, 4L, 5L),
            List.of(2L, 3L, 6L),
            3,
            MatchSource.MANUAL,
            null,
            null
        );

        assertThat(created.getId()).isEqualTo(970L);
        verify(matchRepository).save(any(Match.class));
    }

    @Test
    void storesRaceCompositionWhenManualMatchMatchesSelection() {
        Group group = new Group();
        group.setId(1L);

        List<Player> players = List.of(
            player(1L, group, "P"),
            player(2L, group, "P"),
            player(3L, group, "T"),
            player(4L, group, "P"),
            player(5L, group, "P"),
            player(6L, group, "T")
        );

        Match savedMatch = new Match();
        savedMatch.setId(980L);
        savedMatch.setGroup(group);

        when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(group));
        when(playerRepository.findByGroup_IdAndIdIn(1L, List.of(1L, 2L, 3L, 4L, 5L, 6L)))
            .thenReturn(players);
        when(matchRepository.save(any(Match.class))).thenReturn(savedMatch);

        Match created = groupMatchAdminService.createConfirmedMatch(
            1L,
            List.of(1L, 2L, 3L),
            List.of(4L, 5L, 6L),
            3,
            MatchSource.MANUAL,
            null,
            "PPT"
        );

        assertThat(created.getId()).isEqualTo(980L);

        ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(matchCaptor.capture());
        assertThat(matchCaptor.getValue().getRaceComposition()).isEqualTo("PPT");
    }

    @Test
    void storesAssignedRaceForFlexiblePlayers() {
        Group group = new Group();
        group.setId(1L);

        List<Player> players = List.of(
            player(1L, group, "PT"),
            player(2L, group, "P"),
            player(3L, group, "P"),
            player(4L, group, "PT"),
            player(5L, group, "P"),
            player(6L, group, "P")
        );

        Match savedMatch = new Match();
        savedMatch.setId(981L);
        savedMatch.setGroup(group);

        when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(group));
        when(playerRepository.findByGroup_IdAndIdIn(1L, List.of(1L, 2L, 3L, 4L, 5L, 6L)))
            .thenReturn(players);
        when(matchRepository.save(any(Match.class))).thenReturn(savedMatch);

        groupMatchAdminService.createConfirmedMatch(
            1L,
            List.of(1L, 2L, 3L),
            List.of(4L, 5L, 6L),
            3,
            MatchSource.MANUAL,
            null,
            "PPT"
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MatchParticipant>> participantsCaptor = ArgumentCaptor.forClass(List.class);
        verify(matchParticipantRepository).saveAll(participantsCaptor.capture());

        List<MatchParticipant> participants = participantsCaptor.getValue();
        assertThat(participants).hasSize(6);
        assertThat(participants)
            .extracting(MatchParticipant::getAssignedRace)
            .containsExactlyInAnyOrder("P", "P", "T", "P", "P", "T");
    }

    @Test
    void allowsManualMatchWhenRaceCompositionDoesNotMatchRegisteredRaces() {
        Group group = new Group();
        group.setId(1L);

        List<Player> players = List.of(
            player(1L, group, "P"),
            player(2L, group, "P"),
            player(3L, group, "P"),
            player(4L, group, "P"),
            player(5L, group, "P"),
            player(6L, group, "P")
        );
        Match savedMatch = new Match();
        savedMatch.setId(982L);
        savedMatch.setGroup(group);

        when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(group));
        when(playerRepository.findByGroup_IdAndIdIn(1L, List.of(1L, 2L, 3L, 4L, 5L, 6L)))
            .thenReturn(players);
        when(matchRepository.save(any(Match.class))).thenReturn(savedMatch);

        groupMatchAdminService.createConfirmedMatch(
            1L,
            List.of(1L, 2L, 3L),
            List.of(4L, 5L, 6L),
            3,
            MatchSource.MANUAL,
            null,
            "PPT"
        );

        ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(matchCaptor.capture());
        assertThat(matchCaptor.getValue().getRaceComposition()).isEqualTo("PPT");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MatchParticipant>> participantsCaptor = ArgumentCaptor.forClass(List.class);
        verify(matchParticipantRepository).saveAll(participantsCaptor.capture());
        assertThat(participantsCaptor.getValue())
            .extracting(MatchParticipant::getAssignedRace)
            .containsExactly("P", "P", "T", "P", "P", "T");
    }

    @Test
    void allowsManualMatchWhenOnlyOneTeamNeedsRaceOverride() {
        Group group = new Group();
        group.setId(1L);

        List<Player> players = List.of(
            player(1L, group, "P"),
            player(2L, group, "P"),
            player(3L, group, "P"),
            player(4L, group, "P"),
            player(5L, group, "PTZ"),
            player(6L, group, "PZ")
        );
        Match savedMatch = new Match();
        savedMatch.setId(983L);
        savedMatch.setGroup(group);

        when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(group));
        when(playerRepository.findByGroup_IdAndIdIn(1L, List.of(1L, 2L, 3L, 4L, 5L, 6L)))
            .thenReturn(players);
        when(matchRepository.save(any(Match.class))).thenReturn(savedMatch);

        groupMatchAdminService.createConfirmedMatch(
            1L,
            List.of(1L, 2L, 3L),
            List.of(4L, 5L, 6L),
            3,
            MatchSource.MANUAL,
            null,
            "PPT"
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MatchParticipant>> participantsCaptor = ArgumentCaptor.forClass(List.class);
        verify(matchParticipantRepository).saveAll(participantsCaptor.capture());
        assertThat(participantsCaptor.getValue())
            .extracting(MatchParticipant::getAssignedRace)
            .containsExactly("P", "P", "T", "P", "T", "P");
    }

    @Test
    void rejectsBalancedMatchWhenRaceCompositionDoesNotMatchRegisteredRaces() {
        Group group = new Group();
        group.setId(1L);
        List<Player> players = List.of(
            player(1L, group, "P"), player(2L, group, "P"), player(3L, group, "P"),
            player(4L, group, "P"), player(5L, group, "P"), player(6L, group, "P")
        );
        when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(group));
        when(playerRepository.findByGroup_IdAndIdIn(1L, List.of(1L, 2L, 3L, 4L, 5L, 6L)))
            .thenReturn(players);

        assertThatThrownBy(() -> groupMatchAdminService.createMatch(
            1L,
            new CreateGroupMatchRequest(
                List.of(1L, 2L, 3L),
                List.of(4L, 5L, 6L),
                3,
                "PPT"
            )
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("선택한 종족 조합으로 매치를 구성할 수 없습니다");
    }

    @Test
    void treatsGlobalHomeAwaySwapAsSameDuplicateIdentity() {
        Group group = new Group();
        group.setId(1L);
        List<Player> players = List.of(
            player(1L, group), player(2L, group), player(3L, group),
            player(4L, group), player(5L, group), player(6L, group)
        );
        Match existing = new Match();
        existing.setId(982L);
        existing.setGroup(group);
        existing.setStatus(MatchStatus.CONFIRMED);
        existing.setTeamSize(3);
        existing.setParticipantSignature("1-2-3-4-5-6");
        existing.setTeamSignature("HOME:1-2-3|AWAY:4-5-6");
        when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(group));
        when(playerRepository.findByGroup_IdAndIdIn(1L, List.of(4L, 5L, 6L, 1L, 2L, 3L)))
            .thenReturn(players);
        when(matchRepository.findRecentDuplicateCandidates(any(), any(), any(), any(), any()))
            .thenReturn(List.of(existing));
        CreateGroupMatchResponse response = groupMatchAdminService.createMatch(
            1L,
            new CreateGroupMatchRequest(List.of(4L, 5L, 6L), List.of(1L, 2L, 3L))
        );
        assertThat(response.matchId()).isEqualTo(982L);
        assertThat(response.confirmationStatus()).isEqualTo("REUSED_EXISTING");
    }

    @Test
    void rejectsManualDuplicateRegardlessOfExistingSourceAndTeamOrder() {
        Group group = new Group();
        group.setId(1L);
        List<Player> players = List.of(
            player(1L, group, "P"), player(2L, group, "P"), player(3L, group, "T"),
            player(4L, group, "P"), player(5L, group, "P"), player(6L, group, "T")
        );
        Match existing = new Match();
        existing.setId(983L);
        existing.setGroup(group);
        existing.setStatus(MatchStatus.COMPLETED);
        existing.setTeamSize(3);
        existing.setParticipantSignature("1-2-3-4-5-6");
        existing.setTeamSignature("HOME:1-2-3|AWAY:4-5-6");
        existing.setRaceComposition("PPT");
        when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(group));
        when(playerRepository.findByGroup_IdAndIdIn(1L, List.of(3L, 2L, 1L, 6L, 5L, 4L)))
            .thenReturn(players);
        when(matchRepository.findRecentDuplicateCandidates(any(), any(), any(), any(), any()))
            .thenReturn(List.of(existing));
        for (MatchSource existingSource : List.of(MatchSource.BALANCED, MatchSource.MANUAL)) {
            existing.setSource(existingSource);
            assertThatThrownBy(() -> groupMatchAdminService.createConfirmedMatch(
                1L, List.of(3L, 2L, 1L), List.of(6L, 5L, 4L), 3,
                MatchSource.MANUAL, null, "PPT"
            ))
                .isInstanceOf(MatchConflictException.class)
                .hasMessageContaining("last 5 minutes");
        }
    }

    private Player player(Long id, Group group) {
        return player(id, group, "P");
    }

    private Player player(Long id, Group group, String race) {
        Player player = new Player();
        player.setId(id);
        player.setGroup(group);
        player.setNickname("P" + id);
        player.setRace(race);
        player.setTier("A");
        player.setMmr(800);
        return player;
    }

    private MatchParticipant participant(Match match, Player player, String team) {
        MatchParticipant participant = new MatchParticipant();
        participant.setMatch(match);
        participant.setPlayer(player);
        participant.setTeam(team);
        return participant;
    }
}
