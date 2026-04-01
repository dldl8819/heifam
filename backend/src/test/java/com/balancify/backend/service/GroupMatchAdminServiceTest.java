package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.CreateGroupMatchRequest;
import com.balancify.backend.api.group.dto.CreateGroupMatchResponse;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.GroupRepository;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchRepository;
import com.balancify.backend.repository.PlayerRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
            matchParticipantRepository
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

        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(playerRepository.findByGroup_IdAndIdIn(1L, List.of(1L, 2L, 3L, 4L, 5L, 6L)))
            .thenReturn(players);
        when(matchRepository.save(any(Match.class))).thenReturn(savedMatch);

        CreateGroupMatchResponse response = groupMatchAdminService.createMatch(
            1L,
            new CreateGroupMatchRequest(List.of(1L, 2L, 3L), List.of(4L, 5L, 6L))
        );

        assertThat(response.matchId()).isEqualTo(500L);
        verify(matchParticipantRepository).saveAll(any());
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

    private Player player(Long id, Group group) {
        Player player = new Player();
        player.setId(id);
        player.setGroup(group);
        player.setNickname("P" + id);
        player.setRace("P");
        player.setTier("A");
        player.setMmr(800);
        return player;
    }
}
