package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.CaptainDraftCreateRequest;
import com.balancify.backend.api.group.dto.CaptainDraftEntriesUpdateRequest;
import com.balancify.backend.api.group.dto.CaptainDraftEntryUpdateItemRequest;
import com.balancify.backend.api.group.dto.CaptainDraftListResponse;
import com.balancify.backend.api.group.dto.CaptainDraftPickRequest;
import com.balancify.backend.api.group.dto.CaptainDraftResponse;
import com.balancify.backend.api.group.dto.CaptainDraftSummaryResponse;
import com.balancify.backend.api.group.dto.CaptainDraftUnpickRequest;
import com.balancify.backend.domain.CaptainDraft;
import com.balancify.backend.domain.CaptainDraftEntry;
import com.balancify.backend.domain.CaptainDraftParticipant;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Player;
import com.balancify.backend.domain.PlayerLifecycleStatus;
import com.balancify.backend.repository.CaptainDraftEntryRepository;
import com.balancify.backend.repository.CaptainDraftParticipantRepository;
import com.balancify.backend.repository.CaptainDraftRepository;
import com.balancify.backend.repository.GroupRepository;
import com.balancify.backend.repository.PlayerRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CaptainDraftServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private CaptainDraftRepository captainDraftRepository;

    @Mock
    private CaptainDraftParticipantRepository captainDraftParticipantRepository;

    @Mock
    private CaptainDraftEntryRepository captainDraftEntryRepository;

    @Mock
    private OperationAuditLogService operationAuditLogService;

    private CaptainDraftService captainDraftService;

    @BeforeEach
    void setUp() {
        captainDraftService = new CaptainDraftService(
            groupRepository,
            playerRepository,
            captainDraftRepository,
            captainDraftParticipantRepository,
            captainDraftEntryRepository,
            operationAuditLogService
        );
    }

    @Test
    void createDraftBuildsInitialDraftState() {
        Group group = group(1L);
        List<Player> players = players(group, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);

        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(playerRepository.findByGroup_IdAndIdIn(
            1L,
            List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L)
        )).thenReturn(players);
        when(captainDraftRepository.save(any(CaptainDraft.class))).thenAnswer(invocation -> {
            CaptainDraft draft = invocation.getArgument(0);
            draft.setId(100L);
            return draft;
        });
        when(captainDraftParticipantRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(captainDraftEntryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CaptainDraftResponse response = captainDraftService.createDraft(
            1L,
            new CaptainDraftCreateRequest(
                "토요 정기 감전",
                List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L),
                List.of(1L, 2L),
                null,
                null,
                null
            )
        );

        assertThat(response.draftId()).isEqualTo(100L);
        assertThat(response.participantCount()).isEqualTo(8);
        // Teams of four: everyone plays each round in two 2:2 sets.
        assertThat(response.setsPerRound()).isEqualTo(2);
        assertThat(response.status()).isEqualTo("DRAFTING");
        assertThat(response.currentTurnTeam()).isEqualTo("HOME");
        assertThat(response.homeCaptainPlayerId()).isEqualTo(1L);
        assertThat(response.awayCaptainPlayerId()).isEqualTo(2L);
        assertThat(response.entries()).hasSize(8);
        assertThat(response.entries())
            .extracting(entry -> entry.roundNumber() + entry.roundCode() + ":" + entry.teamSize())
            .containsExactly("1PP:2", "1PP:2", "2PT:2", "2PT:2", "3PZ:2", "3PZ:2", "4PR:2", "4PR:2");
        assertThat(response.entries()).allSatisfy(entry -> {
            assertThat(entry.winnerTeam()).isNull();
            assertThat(entry.homePlayers()).containsExactly(null, null);
        });
        assertThat(response.picks()).isEmpty();
    }

    @Test
    void sizesTheSetsOfARoundFromThePlayersOfATeam() {
        assertThat(CaptainDraftService.setTeamSizes(5, 1)).containsExactly(3);
        assertThat(CaptainDraftService.setTeamSizes(5, 2)).containsExactly(3, 2);
        assertThat(CaptainDraftService.setTeamSizes(4, 1)).containsExactly(3);
        assertThat(CaptainDraftService.setTeamSizes(4, 2)).containsExactly(2, 2);
        assertThat(CaptainDraftService.setTeamSizes(6, 2)).containsExactly(3, 3);
        assertThat(CaptainDraftService.setTeamSizes(7, 3)).containsExactly(3, 2, 2);
        assertThat(CaptainDraftService.setTeamSizes(9, 3)).containsExactly(3, 3, 3);
    }

    @Test
    void createDraftRefusesMoreSetsThanTheTeamsCanFill() {
        Group group = group(1L);
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(playerRepository.findByGroup_IdAndIdIn(1L, List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L)))
            .thenReturn(players(group, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L));

        // Teams of four field two sets at most (2:2 and 2:2).
        assertThatThrownBy(() -> captainDraftService.createDraft(
            1L,
            new CaptainDraftCreateRequest("정기 감전", List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L), List.of(1L, 2L), 3, null, null)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("setsPerRound must be between 1 and 2");
    }

    @Test
    void createDraftRefusesATitleLongerThanItsColumn() {
        List<Long> eight = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);

        assertThatThrownBy(() -> captainDraftService.createDraft(
            1L,
            new CaptainDraftCreateRequest("가".repeat(121), eight, List.of(1L, 2L), null, null, null)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("title must be at most 120 characters");
        verify(captainDraftRepository, never()).save(any());
    }

    @Test
    void createsTheHostsLayoutOfSetsForTheRoundsAsked() {
        Group group = group(1L);
        List<Long> ten = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(playerRepository.findByGroup_IdAndIdIn(1L, ten))
            .thenReturn(players(group, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L));
        when(captainDraftRepository.save(any(CaptainDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(captainDraftParticipantRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(captainDraftEntryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Five a side: a 3:3 and two 1:1 games, over five rounds (best of five).
        CaptainDraftResponse response = captainDraftService.createDraft(
            1L,
            new CaptainDraftCreateRequest("청백전", ten, List.of(1L, 2L), null, 5, List.of(3, 1, 1))
        );

        assertThat(response.setsPerRound()).isEqualTo(3);
        assertThat(response.entries())
            .extracting(entry -> entry.roundNumber() + "-" + entry.setNumber() + ":" + entry.teamSize() + entry.roundCode())
            .containsExactly(
                "1-1:3PPP", "1-2:1P", "1-3:1P",
                "2-1:3PPT", "2-2:1P", "2-3:1P",
                "3-1:3PPZ", "3-2:1P", "3-3:1P",
                "4-1:3PTZ", "4-2:1P", "4-3:1P",
                "5-1:3PPP", "5-2:1P", "5-3:1P"
            );

        // Three rounds of one 3:3 game, two a side resting.
        CaptainDraftResponse shortDraft = captainDraftService.createDraft(
            1L,
            new CaptainDraftCreateRequest("짧게", ten, List.of(1L, 2L), null, 3, List.of(3))
        );
        assertThat(shortDraft.entries()).extracting(entry -> entry.roundCode()).containsExactly("PPP", "PPT", "PPZ");
    }

    @Test
    void refusesALayoutTheTeamsCannotFill() {
        Group group = group(1L);
        List<Long> ten = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(playerRepository.findByGroup_IdAndIdIn(1L, ten))
            .thenReturn(players(group, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L));

        assertThatThrownBy(() -> captainDraftService.createDraft(
            1L, new CaptainDraftCreateRequest("t", ten, List.of(1L, 2L), null, null, List.of(3, 3))
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("The sets need 6 players a team, but a team has 5");
        assertThatThrownBy(() -> captainDraftService.createDraft(
            1L, new CaptainDraftCreateRequest("t", ten, List.of(1L, 2L), null, null, List.of(4))
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Each set is 3:3, 2:2 or 1:1");
        assertThatThrownBy(() -> captainDraftService.createDraft(
            1L, new CaptainDraftCreateRequest("t", ten, List.of(1L, 2L), null, 8, List.of(3))
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("roundCount must be between 1 and 7");
    }

    @Test
    void playsSmallerSetsWithTheRoundsRacesCutDown() {
        assertThat(CaptainDraftService.setComposition("PPP", 3)).isEqualTo("PPP");
        assertThat(CaptainDraftService.setComposition("PPT", 2)).isEqualTo("PT");
        assertThat(CaptainDraftService.setComposition("PPT", 1)).isEqualTo("P");
        assertThat(CaptainDraftService.setComposition("PTZ", 2)).isEqualTo("PR");
        assertThat(CaptainDraftService.setComposition("PTZ", 1)).isEqualTo("P");
        assertThat(CaptainDraftService.setComposition("PPZ", 2)).isEqualTo("PZ");
        assertThat(CaptainDraftService.setComposition("PPR", 2)).isEqualTo("PR");
        assertThat(CaptainDraftService.setComposition("RRR", 2)).isEqualTo("RR");
        assertThat(CaptainDraftService.setComposition("PRR", 1)).isEqualTo("R");
    }

    @Test
    void listsDraftsNewestFirstWithTheirRoundAndSetScores() {
        Group group = group(1L);
        CaptainDraft played = draft(group, 100L, "READY", 2, "UNASSIGNED", 1L, 2L);
        CaptainDraft running = draft(group, 101L, "DRAFTING", 1, "HOME", 3L, 4L);
        // Round 1 goes to home (2:0), round 2 is level (1:1), round 3 still waits for a result.
        List<CaptainDraftEntry> entries = List.of(
            result(entry(played, 1, "PPP", 1, 3), "HOME"),
            result(entry(played, 1, "P", 2, 1), "HOME"),
            result(entry(played, 2, "PPT", 1, 3), "HOME"),
            result(entry(played, 2, "P", 2, 1), "AWAY"),
            result(entry(played, 3, "PPZ", 1, 3), "AWAY"),
            entry(played, 3, "P", 2, 1),
            entry(running, 1, "PPP", 1, 3)
        );

        when(captainDraftRepository.findRecentWithCaptains(eq(1L), any())).thenReturn(List.of(running, played));
        when(captainDraftEntryRepository.findByDraftIds(List.of(101L, 100L))).thenReturn(entries);
        when(captainDraftParticipantRepository.countByDraftIds(List.of(101L, 100L)))
            .thenReturn(List.of(new ParticipantCount(100L, 10L), new ParticipantCount(101L, 8L)));

        CaptainDraftListResponse response = captainDraftService.listDrafts(1L);

        assertThat(response.drafts()).extracting(CaptainDraftSummaryResponse::draftId).containsExactly(101L, 100L);
        assertThat(response.drafts().get(1)).satisfies(summary -> {
            assertThat(summary.homeCaptainNickname()).isEqualTo("P1");
            assertThat(summary.awayCaptainNickname()).isEqualTo("P2");
            assertThat(summary.participantCount()).isEqualTo(10);
            assertThat(summary.roundCount()).isEqualTo(3);
            assertThat(List.of(summary.homeRoundWins(), summary.awayRoundWins())).containsExactly(1, 0);
            assertThat(List.of(summary.homeSetWins(), summary.awaySetWins())).containsExactly(3, 2);
            assertThat(List.of(summary.decidedSets(), summary.totalSets())).containsExactly(5, 6);
        });
        assertThat(response.drafts().get(0)).satisfies(summary -> {
            assertThat(summary.status()).isEqualTo("DRAFTING");
            assertThat(summary.participantCount()).isEqualTo(8);
            assertThat(summary.decidedSets()).isZero();
        });
    }

    @Test
    void listsNoDraftsForAGroupWithoutAny() {
        when(captainDraftRepository.findRecentWithCaptains(eq(1L), any())).thenReturn(List.of());

        assertThat(captainDraftService.listDrafts(1L).drafts()).isEmpty();
    }

    @Test
    void deletesADraftWithItsSetsAndParticipantsAndLogsWhoDidIt() {
        Group group = group(1L);
        CaptainDraft draft = draft(group, 100L, "READY", 2, null, 1L, 2L);
        when(captainDraftRepository.findByIdAndGroup_Id(100L, 1L)).thenReturn(Optional.of(draft));
        when(captainDraftEntryRepository.deleteByDraftId(100L)).thenReturn(8);
        when(captainDraftParticipantRepository.deleteByDraftId(100L)).thenReturn(10);
        when(captainDraftRepository.findRecentWithCaptains(eq(1L), any())).thenReturn(List.of());

        CaptainDraftListResponse response = captainDraftService.deleteDraft(1L, 100L, "admin@hei.gg", "Ops");

        assertThat(response.drafts()).isEmpty();
        InOrder order = inOrder(captainDraftEntryRepository, captainDraftParticipantRepository, captainDraftRepository);
        order.verify(captainDraftEntryRepository).deleteByDraftId(100L);
        order.verify(captainDraftParticipantRepository).deleteByDraftId(100L);
        order.verify(captainDraftRepository).deleteDraft(100L);
        verify(operationAuditLogService).recordCaptainDraftDeleted(
            "admin@hei.gg", "Ops", 1L, 100L, "정기 감전", "participants=10, sets=8"
        );
    }

    @Test
    void deletesNothingForADraftOfAnotherGroup() {
        when(captainDraftRepository.findByIdAndGroup_Id(100L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> captainDraftService.deleteDraft(2L, 100L, "admin@hei.gg", "Ops"))
            .isInstanceOf(NoSuchElementException.class);
        verify(captainDraftEntryRepository, never()).deleteByDraftId(any());
        verify(captainDraftParticipantRepository, never()).deleteByDraftId(any());
        verify(captainDraftRepository, never()).deleteDraft(any());
        verifyNoInteractions(operationAuditLogService);
    }

    @Test
    void pickPlayerAssignsTeamAndChangesTurn() {
        Group group = group(1L);
        CaptainDraft draft = draft(group, 100L, "DRAFTING", 4, "HOME", 1L, 2L);

        List<CaptainDraftParticipant> participants = new ArrayList<>();
        participants.add(participant(draft, player(group, 1L), true, "HOME", null));
        participants.add(participant(draft, player(group, 2L), true, "AWAY", null));
        participants.add(participant(draft, player(group, 3L), false, null, null));
        participants.add(participant(draft, player(group, 4L), false, null, null));
        participants.add(participant(draft, player(group, 5L), false, null, null));
        participants.add(participant(draft, player(group, 6L), false, null, null));
        participants.add(participant(draft, player(group, 7L), false, null, null));
        participants.add(participant(draft, player(group, 8L), false, null, null));

        when(captainDraftRepository.findByIdAndGroup_Id(100L, 1L)).thenReturn(Optional.of(draft));
        when(captainDraftParticipantRepository.findByDraftIdWithPlayer(100L)).thenReturn(participants);
        when(captainDraftParticipantRepository.save(any(CaptainDraftParticipant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(captainDraftRepository.save(any(CaptainDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(captainDraftEntryRepository.findByDraftIdWithPlayers(100L)).thenReturn(List.of());

        CaptainDraftResponse response = captainDraftService.pickPlayer(
            1L,
            100L,
            new CaptainDraftPickRequest(1L, 3L)
        );

        assertThat(response.currentTurnTeam()).isEqualTo("AWAY");
        assertThat(response.picks()).hasSize(1);
        assertThat(response.picks().get(0).pickedPlayerId()).isEqualTo(3L);
        assertThat(response.picks().get(0).team()).isEqualTo("HOME");
        assertThat(response.participants())
            .anySatisfy(participant -> {
                if (participant.playerId().equals(3L)) {
                    assertThat(participant.team()).isEqualTo("HOME");
                    assertThat(participant.pickOrder()).isEqualTo(1);
                }
            });
    }

    @Test
    void pickPlayerRejectsDraftContainingInactiveParticipant() {
        Group group = group(1L);
        CaptainDraft draft = draft(group, 100L, "DRAFTING", 4, "HOME", 1L, 2L);
        Player inactive = player(group, 3L);
        inactive.setActive(false);
        inactive.setLifecycleStatus(PlayerLifecycleStatus.INACTIVE);
        List<CaptainDraftParticipant> participants = List.of(
            participant(draft, player(group, 1L), true, "HOME", null),
            participant(draft, player(group, 2L), true, "AWAY", null),
            participant(draft, inactive, false, null, null)
        );

        when(captainDraftRepository.findByIdAndGroup_Id(100L, 1L)).thenReturn(Optional.of(draft));
        when(captainDraftParticipantRepository.findByDraftIdWithPlayer(100L)).thenReturn(participants);

        assertThatThrownBy(() -> captainDraftService.pickPlayer(
            1L,
            100L,
            new CaptainDraftPickRequest(1L, 3L)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Draft contains an inactive participant");
    }

    @Test
    void savesTeamSetsWithTheRoundCompositionTheTierNoteAndTheResult() {
        Group group = group(1L);
        CaptainDraft draft = draft(group, 100L, "READY", 1, "UNASSIGNED", 1L, 2L);
        List<CaptainDraftParticipant> participants = teamsOfThree(draft, group);
        CaptainDraftEntry firstRound = entry(draft, 1, "PPP", 1, 3);
        CaptainDraftEntry secondRound = entry(draft, 2, "PPT", 1, 3);

        when(captainDraftRepository.findByIdAndGroup_Id(100L, 1L)).thenReturn(Optional.of(draft));
        when(captainDraftParticipantRepository.findByDraftIdWithPlayer(100L)).thenReturn(participants);
        when(captainDraftEntryRepository.findByDraftIdWithPlayers(100L)).thenReturn(List.of(firstRound, secondRound));

        CaptainDraftResponse response = captainDraftService.updateEntries(
            1L,
            100L,
            new CaptainDraftEntriesUpdateRequest(
                List.of(
                    new CaptainDraftEntryUpdateItemRequest(1, 1, "zpp", List.of(1L, 3L, 5L), List.of(2L, 4L, 6L), " 핫티어 1 ", "AWAY"),
                    new CaptainDraftEntryUpdateItemRequest(2, 1, null, Arrays.asList(5L, null, 3L), List.of(), "", null)
                )
            )
        );

        // Races given are put in P, T, Z, R order; races left out are kept.
        assertThat(response.entries()).extracting(entry -> entry.roundCode()).containsExactly("PPZ", "PPT");
        assertThat(response.entries().get(0)).satisfies(set -> {
            assertThat(set.teamSize()).isEqualTo(3);
            assertThat(set.requiredTier()).isEqualTo("핫티어 1");
            assertThat(set.homePlayers()).extracting(slot -> slot.playerId()).containsExactly(1L, 3L, 5L);
            assertThat(set.awayPlayers()).extracting(slot -> slot.playerId()).containsExactly(2L, 4L, 6L);
            assertThat(set.winnerTeam()).isEqualTo("AWAY");
        });
        assertThat(response.entries().get(1)).satisfies(set -> {
            assertThat(set.requiredTier()).isNull();
            assertThat(set.homePlayers()).extracting(slot -> slot == null ? null : slot.playerId()).containsExactly(5L, null, 3L);
            assertThat(set.awayPlayers()).containsExactly(null, null, null);
        });
    }

    @Test
    void refusesSetsThatBreakTheRules() {
        Group group = group(1L);
        CaptainDraft draft = draft(group, 100L, "READY", 2, "UNASSIGNED", 1L, 2L);
        List<CaptainDraftParticipant> participants = teamsOfThree(draft, group);

        when(captainDraftRepository.findByIdAndGroup_Id(100L, 1L)).thenReturn(Optional.of(draft));
        when(captainDraftParticipantRepository.findByDraftIdWithPlayer(100L)).thenReturn(participants);
        when(captainDraftEntryRepository.findByDraftIdWithPlayers(100L))
            .thenAnswer(invocation -> List.of(entry(draft, 1, "PPP", 1, 3), entry(draft, 1, "PPP", 2, 2)));

        assertRefused(
            new CaptainDraftEntryUpdateItemRequest(1, 1, null, List.of(4L), List.of(), null, null),
            "Entry player must belong to that side's team"
        );
        assertRefused(
            new CaptainDraftEntryUpdateItemRequest(1, 1, null, List.of(3L, 3L), List.of(), null, null),
            "A player can take only one slot of a set"
        );
        assertRefused(
            new CaptainDraftEntryUpdateItemRequest(1, 2, null, List.of(1L, 3L, 5L), List.of(), null, null),
            "A side of this set takes at most 2 players"
        );
        assertRefused(
            new CaptainDraftEntryUpdateItemRequest(1, 1, null, List.of(1L, 3L), List.of(2L, 4L, 6L), null, "HOME"),
            "Winner team requires both home and away players"
        );
        assertRefused(
            new CaptainDraftEntryUpdateItemRequest(1, 1, null, null, null, null, null),
            "Each set needs both sides' player lists; reload the page"
        );
        // One player in both sets of the same round.
        assertThatThrownBy(() -> captainDraftService.updateEntries(
            1L,
            100L,
            new CaptainDraftEntriesUpdateRequest(List.of(
                new CaptainDraftEntryUpdateItemRequest(1, 1, null, List.of(1L, 3L), List.of(), null, null),
                new CaptainDraftEntryUpdateItemRequest(1, 2, null, List.of(3L, 5L), List.of(), null, null)
            ))
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("A player can play only one set a round");
        assertRefused(
            new CaptainDraftEntryUpdateItemRequest(1, 1, "PX", List.of(), List.of(), null, null),
            "raceCode must be one to three of P, T, Z and R"
        );
        assertRefused(
            new CaptainDraftEntryUpdateItemRequest(1, 1, "PPTZ", List.of(), List.of(), null, null),
            "raceCode must be one to three of P, T, Z and R"
        );
        assertRefused(
            new CaptainDraftEntryUpdateItemRequest(9, 1, null, List.of(), List.of(), null, null),
            "Invalid round/set combination"
        );
    }

    @Test
    void picksForEitherCaptainWhateverTheTurnSoACaptainCanTakeTwoInARow() {
        Group group = group(1L);
        CaptainDraft draft = draft(group, 100L, "DRAFTING", 4, "HOME", 1L, 2L);
        List<CaptainDraftParticipant> participants = new ArrayList<>();
        participants.add(participant(draft, player(group, 1L), true, "HOME", null));
        participants.add(participant(draft, player(group, 2L), true, "AWAY", null));
        for (long id = 3L; id <= 8L; id++) {
            participants.add(participant(draft, player(group, id), false, null, null));
        }

        when(captainDraftRepository.findByIdAndGroup_Id(100L, 1L)).thenReturn(Optional.of(draft));
        when(captainDraftParticipantRepository.findByDraftIdWithPlayer(100L)).thenReturn(participants);
        when(captainDraftParticipantRepository.save(any(CaptainDraftParticipant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(captainDraftRepository.save(any(CaptainDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(captainDraftEntryRepository.findByDraftIdWithPlayers(100L)).thenReturn(List.of());

        // Home's turn, yet the away captain picks, twice in a row.
        captainDraftService.pickPlayer(1L, 100L, new CaptainDraftPickRequest(2L, 3L));
        CaptainDraftResponse response = captainDraftService.pickPlayer(1L, 100L, new CaptainDraftPickRequest(2L, 4L));

        assertThat(response.picks())
            .extracting(pick -> pick.pickedPlayerId() + ":" + pick.team() + ":" + pick.pickOrder())
            .containsExactly("3:AWAY:1", "4:AWAY:2");
        assertThat(response.status()).isEqualTo("DRAFTING");
    }

    @Test
    void takesAPickBackOutClearingItsEntriesAndReopeningTheDraft() {
        Group group = group(1L);
        CaptainDraft draft = draft(group, 100L, "READY", 4, "UNASSIGNED", 1L, 2L);
        Player picked = player(group, 3L);
        Player opponent = player(group, 4L);
        List<CaptainDraftParticipant> participants = List.of(
            participant(draft, player(group, 1L), true, "HOME", null),
            participant(draft, player(group, 2L), true, "AWAY", null),
            participant(draft, picked, false, "HOME", 1),
            participant(draft, opponent, false, "AWAY", 2)
        );
        CaptainDraftEntry named = entry(draft, 1, "PPP", 1, 2);
        named.setSidePlayers("HOME", Arrays.asList(player(group, 1L), picked));
        named.setSidePlayers("AWAY", Arrays.asList(player(group, 2L), opponent));
        named.setWinnerTeam("HOME");
        CaptainDraftEntry other = entry(draft, 2, "PPT", 1, 2);
        other.setSidePlayers("AWAY", Arrays.asList(opponent, null));
        other.setWinnerTeam(null);

        when(captainDraftRepository.findByIdAndGroup_Id(100L, 1L)).thenReturn(Optional.of(draft));
        when(captainDraftParticipantRepository.findByDraftIdWithPlayer(100L)).thenReturn(participants);
        when(captainDraftEntryRepository.findByDraftIdWithPlayers(100L)).thenReturn(List.of(named, other));

        CaptainDraftResponse response = captainDraftService.unpickPlayer(1L, 100L, new CaptainDraftUnpickRequest(3L));

        assertThat(response.participants())
            .filteredOn(participant -> participant.playerId().equals(3L))
            .singleElement()
            .satisfies(participant -> {
                assertThat(participant.team()).isEqualTo("UNASSIGNED");
                assertThat(participant.pickOrder()).isNull();
            });
        assertThat(response.picks()).extracting(pick -> pick.pickedPlayerId()).containsExactly(4L);
        assertThat(response.entries().get(0)).satisfies(set -> {
            assertThat(set.homePlayers()).extracting(slot -> slot == null ? null : slot.playerId()).containsExactly(1L, null);
            assertThat(set.awayPlayers()).extracting(slot -> slot.playerId()).containsExactly(2L, 4L);
            assertThat(set.winnerTeam()).isNull();
        });
        assertThat(response.entries().get(1).awayPlayers())
            .extracting(slot -> slot == null ? null : slot.playerId())
            .containsExactly(4L, null);
        assertThat(response.status()).isEqualTo("DRAFTING");
        assertThat(response.currentTurnTeam()).isEqualTo("HOME");

        assertThatThrownBy(() -> captainDraftService.unpickPlayer(1L, 100L, new CaptainDraftUnpickRequest(1L)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("A captain cannot be taken out of their team");
        assertThatThrownBy(() -> captainDraftService.unpickPlayer(1L, 100L, new CaptainDraftUnpickRequest(3L)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Player is not picked");
        assertThatThrownBy(() -> captainDraftService.unpickPlayer(1L, 100L, new CaptainDraftUnpickRequest(99L)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void responseMasksAnonymizedPlayersWithoutChangingDraftReferences() {
        Group group = group(1L);
        Player anonymizedCaptain = player(group, 1L);
        anonymizedCaptain.setAnonymizedAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        Player awayCaptain = player(group, 2L);
        Player pickedPlayer = player(group, 3L);

        CaptainDraft draft = draft(group, 100L, "READY", 4, null, 1L, 2L);
        draft.setHomeCaptain(anonymizedCaptain);
        draft.setAwayCaptain(awayCaptain);

        List<CaptainDraftParticipant> participants = List.of(
            participant(draft, anonymizedCaptain, true, "HOME", null),
            participant(draft, awayCaptain, true, "AWAY", null),
            participant(draft, pickedPlayer, false, "HOME", 1)
        );
        CaptainDraftEntry entry = entry(draft, 1, "PPP", 1, 1);
        entry.setSidePlayers("HOME", List.of(anonymizedCaptain));
        entry.setSidePlayers("AWAY", List.of(pickedPlayer));

        when(captainDraftRepository.findByIdAndGroup_Id(100L, 1L)).thenReturn(Optional.of(draft));
        when(captainDraftParticipantRepository.findByDraftIdWithPlayer(100L)).thenReturn(participants);
        when(captainDraftEntryRepository.findByDraftIdWithPlayers(100L)).thenReturn(List.of(entry));

        CaptainDraftResponse response = captainDraftService.getDraft(1L, 100L);

        assertThat(response.homeCaptainPlayerId()).isNull();
        assertThat(response.homeCaptainNickname())
            .isEqualTo("\uD0C8\uD1F4\uD55C \uD68C\uC6D0");
        assertThat(response.participants())
            .filteredOn(participantResponse -> participantResponse.captain()
                && "HOME".equals(participantResponse.team()))
            .singleElement()
            .satisfies(participantResponse -> {
                assertThat(participantResponse.playerId()).isNull();
                assertThat(participantResponse.nickname())
                    .isEqualTo("\uD0C8\uD1F4\uD55C \uD68C\uC6D0");
                assertThat(participantResponse.race()).isNull();
            });
        assertThat(response.picks()).singleElement().satisfies(pick -> {
            assertThat(pick.captainPlayerId()).isNull();
            assertThat(pick.captainNickname())
                .isEqualTo("\uD0C8\uD1F4\uD55C \uD68C\uC6D0");
            assertThat(pick.pickedPlayerId()).isEqualTo(3L);
        });
        assertThat(response.entries()).singleElement().satisfies(responseEntry -> {
            assertThat(responseEntry.homePlayers()).singleElement().satisfies(slot -> {
                assertThat(slot.playerId()).isNull();
                assertThat(slot.nickname()).isEqualTo("\uD0C8\uD1F4\uD55C \uD68C\uC6D0");
            });
            assertThat(responseEntry.awayPlayers()).singleElement().satisfies(slot -> assertThat(slot.playerId()).isEqualTo(3L));
        });

        assertThat(anonymizedCaptain.getId()).isEqualTo(1L);
        assertThat(entry.sidePlayers("HOME")).containsExactly(anonymizedCaptain);
    }

    private Group group(Long id) {
        Group group = new Group();
        group.setId(id);
        group.setName("G" + id);
        return group;
    }

    private List<Player> players(Group group, Long... ids) {
        List<Player> players = new ArrayList<>();
        for (Long id : ids) {
            players.add(player(group, id));
        }
        return players;
    }

    private Player player(Group group, Long id) {
        Player player = new Player();
        player.setId(id);
        player.setGroup(group);
        player.setNickname("P" + id);
        player.setRace("P");
        player.setMmr(800);
        return player;
    }

    private CaptainDraft draft(
        Group group,
        Long id,
        String status,
        int setsPerRound,
        String currentTurnTeam,
        Long homeCaptainId,
        Long awayCaptainId
    ) {
        CaptainDraft draft = new CaptainDraft();
        draft.setId(id);
        draft.setGroup(group);
        draft.setTitle("정기 감전");
        draft.setStatus(status);
        draft.setSetsPerRound(setsPerRound);
        draft.setCurrentTurnTeam(currentTurnTeam);
        draft.setHomeCaptain(player(group, homeCaptainId));
        draft.setAwayCaptain(player(group, awayCaptainId));
        return draft;
    }

    private CaptainDraftParticipant participant(
        CaptainDraft draft,
        Player player,
        boolean captain,
        String team,
        Integer pickOrder
    ) {
        CaptainDraftParticipant participant = new CaptainDraftParticipant();
        participant.setDraft(draft);
        participant.setPlayer(player);
        participant.setCaptain(captain);
        participant.setTeam(team);
        participant.setPickOrder(pickOrder);
        return participant;
    }

    private CaptainDraftEntry entry(CaptainDraft draft, int roundNumber, String roundCode, int setNumber, int teamSize) {
        CaptainDraftEntry entry = new CaptainDraftEntry();
        entry.setDraft(draft);
        entry.setRoundNumber(roundNumber);
        entry.setRoundCode(roundCode);
        entry.setSetNumber(setNumber);
        entry.setTeamSize(teamSize);
        return entry;
    }

    // Captains 1 (home) and 2 (away); home also 3 and 5, away 4 and 6.
    private List<CaptainDraftParticipant> teamsOfThree(CaptainDraft draft, Group group) {
        return List.of(
            participant(draft, player(group, 1L), true, "HOME", null),
            participant(draft, player(group, 2L), true, "AWAY", null),
            participant(draft, player(group, 3L), false, "HOME", 1),
            participant(draft, player(group, 4L), false, "AWAY", 2),
            participant(draft, player(group, 5L), false, "HOME", 3),
            participant(draft, player(group, 6L), false, "AWAY", 4)
        );
    }

    private CaptainDraftEntry result(CaptainDraftEntry entry, String winnerTeam) {
        entry.setWinnerTeam(winnerTeam);
        return entry;
    }

    private record ParticipantCount(Long getDraftId, Long getParticipants)
        implements CaptainDraftParticipantRepository.DraftParticipantCount {
    }

    private void assertRefused(CaptainDraftEntryUpdateItemRequest item, String message) {
        assertThatThrownBy(() -> captainDraftService.updateEntries(
            1L,
            100L,
            new CaptainDraftEntriesUpdateRequest(List.of(item))
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(message);
    }
}
