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
import com.balancify.backend.domain.CaptainDraft;
import com.balancify.backend.domain.CaptainDraftEntry;
import com.balancify.backend.domain.CaptainDraftParticipant;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.CaptainDraftEntryRepository;
import com.balancify.backend.repository.CaptainDraftParticipantRepository;
import com.balancify.backend.repository.CaptainDraftRepository;
import com.balancify.backend.repository.GroupRepository;
import com.balancify.backend.repository.PlayerRepository;
import java.util.ArrayList;
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
        assertThat(response.setsPerRound()).isEqualTo(4);
        assertThat(response.status()).isEqualTo("DRAFTING");
        assertThat(response.currentTurnTeam()).isEqualTo("HOME");
        assertThat(response.homeCaptainPlayerId()).isEqualTo(1L);
        assertThat(response.awayCaptainPlayerId()).isEqualTo(2L);
        assertThat(response.entries()).hasSize(16);
        assertThat(response.picks()).isEmpty();
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
    void updateEntriesRejectsOppositeTeamPlayer() {
        Group group = group(1L);
        CaptainDraft draft = draft(group, 100L, "READY", 4, "UNASSIGNED", 1L, 2L);

        List<CaptainDraftParticipant> participants = List.of(
            participant(draft, player(group, 1L), true, "HOME", null),
            participant(draft, player(group, 2L), true, "AWAY", null),
            participant(draft, player(group, 3L), false, "HOME", 1),
            participant(draft, player(group, 4L), false, "AWAY", 1)
        );
        List<CaptainDraftEntry> entries = List.of(entry(draft, 1, "PPP", 1));

        when(captainDraftRepository.findByIdAndGroup_Id(100L, 1L)).thenReturn(Optional.of(draft));
        when(captainDraftParticipantRepository.findByDraftIdWithPlayer(100L)).thenReturn(participants);
        when(captainDraftEntryRepository.findByDraftIdWithPlayers(100L)).thenReturn(entries);

        assertThatThrownBy(() -> captainDraftService.updateEntries(
            1L,
            100L,
            new CaptainDraftEntriesUpdateRequest(
                1L,
                List.of(new CaptainDraftEntryUpdateItemRequest(1, 1, 4L))
            )
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Entry player must belong to captain's team");
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

    private CaptainDraftEntry entry(CaptainDraft draft, int roundNumber, String roundCode, int setNumber) {
        CaptainDraftEntry entry = new CaptainDraftEntry();
        entry.setDraft(draft);
        entry.setRoundNumber(roundNumber);
        entry.setRoundCode(roundCode);
        entry.setSetNumber(setNumber);
        return entry;
    }
}
