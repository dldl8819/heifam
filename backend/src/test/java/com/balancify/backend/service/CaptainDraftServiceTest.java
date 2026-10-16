package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.CaptainDraftCreateRequest;
import com.balancify.backend.api.group.dto.CaptainDraftEntriesUpdateRequest;
import com.balancify.backend.api.group.dto.CaptainDraftEntryUpdateItemRequest;
import com.balancify.backend.api.group.dto.CaptainDraftPickRequest;
import com.balancify.backend.api.group.dto.CaptainDraftResponse;
import com.balancify.backend.api.group.dto.CaptainDraftRoundUpdateRequest;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private CaptainDraftService captainDraftService;

    @BeforeEach
    void setUp() {
        captainDraftService = new CaptainDraftService(
            groupRepository,
            playerRepository,
            captainDraftRepository,
            captainDraftParticipantRepository,
            captainDraftEntryRepository
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
            .containsExactly("1PPP:2", "1PPP:2", "2PPT:2", "2PPT:2", "3PPZ:2", "3PPZ:2", "4PTZ:2", "4PTZ:2");
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
            new CaptainDraftCreateRequest("정기 감전", List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L), List.of(1L, 2L), 3)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("setsPerRound must be between 1 and 2");
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
                List.of(new CaptainDraftRoundUpdateRequest(1, "ppz")),
                List.of(
                    new CaptainDraftEntryUpdateItemRequest(1, 1, List.of(1L, 3L, 5L), List.of(2L, 4L, 6L), " 핫티어 1 ", "AWAY"),
                    new CaptainDraftEntryUpdateItemRequest(2, 1, Arrays.asList(5L, null, 3L), List.of(), "", null)
                )
            )
        );

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
            new CaptainDraftEntryUpdateItemRequest(1, 1, List.of(4L), List.of(), null, null),
            "Entry player must belong to that side's team"
        );
        assertRefused(
            new CaptainDraftEntryUpdateItemRequest(1, 1, List.of(3L, 3L), List.of(), null, null),
            "A player can take only one slot of a set"
        );
        assertRefused(
            new CaptainDraftEntryUpdateItemRequest(1, 2, List.of(1L, 3L, 5L), List.of(), null, null),
            "A side of this set takes at most 2 players"
        );
        assertRefused(
            new CaptainDraftEntryUpdateItemRequest(1, 1, List.of(1L, 3L), List.of(2L, 4L, 6L), null, "HOME"),
            "Winner team requires both home and away players"
        );
        assertRefused(
            new CaptainDraftEntryUpdateItemRequest(1, 1, null, null, null, null),
            "Each set needs both sides' player lists; reload the page"
        );
        // One player in both sets of the same round.
        assertThatThrownBy(() -> captainDraftService.updateEntries(
            1L,
            100L,
            new CaptainDraftEntriesUpdateRequest(null, List.of(
                new CaptainDraftEntryUpdateItemRequest(1, 1, List.of(1L, 3L), List.of(), null, null),
                new CaptainDraftEntryUpdateItemRequest(1, 2, List.of(3L, 5L), List.of(), null, null)
            ))
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("A player can play only one set a round");
        assertThatThrownBy(() -> captainDraftService.updateEntries(
            1L,
            100L,
            new CaptainDraftEntriesUpdateRequest(
                List.of(new CaptainDraftRoundUpdateRequest(1, "TTT")),
                List.of(new CaptainDraftEntryUpdateItemRequest(1, 1, List.of(), List.of(), null, null))
            )
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("roundCode must be one of PPP, PPT, PPZ, PTZ");
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

    private void assertRefused(CaptainDraftEntryUpdateItemRequest item, String message) {
        assertThatThrownBy(() -> captainDraftService.updateEntries(
            1L,
            100L,
            new CaptainDraftEntriesUpdateRequest(null, List.of(item))
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(message);
    }
}
