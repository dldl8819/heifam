package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.GroupPlayerResponse;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.PlayerRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlayerQueryServiceTest {

    private static final OffsetDateTime FIXED_NOW = OffsetDateTime.parse("2026-04-02T00:00:00Z");

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private MatchParticipantRepository matchParticipantRepository;

    private PlayerQueryService playerQueryService;

    @BeforeEach
    void setUp() {
        playerQueryService = new PlayerQueryService(
            playerRepository,
            matchParticipantRepository,
            true,
            30,
            1,
            Clock.fixed(FIXED_NOW.toInstant(), ZoneOffset.UTC)
        );
    }

    @Test
    void returnsPlayersSortedByCurrentMmrWithStats() {
        Group group = new Group();
        group.setId(1L);

        Player p1 = player(1L, group, "Alpha", "P", "A", 1500);
        Player p2 = player(2L, group, "Bravo", "T", null, 1700);
        Player p3 = player(3L, group, "Charlie", "Z", "b+", 1600);

        Match m1 = match(11L, group, "HOME");
        Match m2 = match(12L, group, "AWAY");

        List<MatchParticipant> participants = List.of(
            participant(101L, m1, p1, "HOME"),
            participant(102L, m1, p2, "AWAY"),
            participant(103L, m2, p1, "HOME"),
            participant(104L, m2, p2, "AWAY")
        );

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L))
            .thenReturn(List.of(p1, p2, p3));
        when(matchParticipantRepository.findByGroupIdOrderByPlayedAtDesc(1L))
            .thenReturn(participants);

        List<GroupPlayerResponse> response = playerQueryService.getGroupPlayers(1L);

        assertThat(response).hasSize(3);

        GroupPlayerResponse first = response.get(0);
        assertThat(first.id()).isEqualTo(2L);
        assertThat(first.nickname()).isEqualTo("Bravo");
        assertThat(first.currentMmr()).isEqualTo(1700);
        assertThat(first.tier()).isEqualTo("S");
        assertThat(first.wins()).isEqualTo(1);
        assertThat(first.losses()).isEqualTo(1);
        assertThat(first.games()).isEqualTo(2);

        GroupPlayerResponse second = response.get(1);
        assertThat(second.id()).isEqualTo(3L);
        assertThat(second.tier()).isEqualTo("S");
        assertThat(second.wins()).isZero();
        assertThat(second.losses()).isZero();
        assertThat(second.games()).isZero();

        GroupPlayerResponse third = response.get(2);
        assertThat(third.id()).isEqualTo(1L);
        assertThat(third.currentMmr()).isEqualTo(1500);
        assertThat(third.tier()).isEqualTo("S");
        assertThat(third.wins()).isEqualTo(1);
        assertThat(third.losses()).isEqualTo(1);
        assertThat(third.games()).isEqualTo(2);
    }

    @Test
    void returnsEmptyWhenGroupHasNoPlayers() {
        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(99L)).thenReturn(List.of());
        when(matchParticipantRepository.findByGroupIdOrderByPlayedAtDesc(99L)).thenReturn(List.of());

        List<GroupPlayerResponse> response = playerQueryService.getGroupPlayers(99L);
        assertThat(response).isEmpty();
    }

    @Test
    void demotesTierOneStepWhenPlayerIsDormantWithLowParticipation() {
        Group group = new Group();
        group.setId(1L);

        Player robo = player(9L, group, "로보", "P", "A+", 930);
        robo.setCreatedAt(OffsetDateTime.parse("2026-02-20T00:00:00Z"));

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L))
            .thenReturn(List.of(robo));
        when(matchParticipantRepository.findByGroupIdOrderByPlayedAtDesc(1L))
            .thenReturn(List.of());

        List<GroupPlayerResponse> response = playerQueryService.getGroupPlayers(1L);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).tier()).isEqualTo("A");
        assertThat(response.get(0).currentMmr()).isEqualTo(890);
        assertThat(response.get(0).games()).isZero();
    }

    @Test
    void doesNotDemoteDormantTierWhenParticipationIsAboveThreshold() {
        Group group = new Group();
        group.setId(1L);

        Player robo = player(9L, group, "로보", "P", "A+", 930);
        robo.setCreatedAt(OffsetDateTime.parse("2026-03-20T00:00:00Z"));

        Match m1 = match(21L, group, "HOME", "2026-03-20T00:00:00Z");
        Match m2 = match(22L, group, "HOME", "2026-03-21T00:00:00Z");
        Match m3 = match(23L, group, "HOME", "2026-03-22T00:00:00Z");
        Match m4 = match(24L, group, "HOME", "2026-03-23T00:00:00Z");

        List<MatchParticipant> participants = List.of(
            participant(201L, m4, robo, "HOME"),
            participant(202L, m3, robo, "HOME"),
            participant(203L, m2, robo, "HOME"),
            participant(204L, m1, robo, "HOME")
        );

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L))
            .thenReturn(List.of(robo));
        when(matchParticipantRepository.findByGroupIdOrderByPlayedAtDesc(1L))
            .thenReturn(participants);

        List<GroupPlayerResponse> response = playerQueryService.getGroupPlayers(1L);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).tier()).isEqualTo("A+");
        assertThat(response.get(0).games()).isEqualTo(4);
    }

    private Player player(
        Long id,
        Group group,
        String nickname,
        String race,
        String tier,
        int mmr
    ) {
        Player player = new Player();
        player.setId(id);
        player.setGroup(group);
        player.setNickname(nickname);
        player.setRace(race);
        player.setTier(tier);
        player.setMmr(mmr);
        player.setCreatedAt(FIXED_NOW);
        return player;
    }

    private Match match(Long id, Group group, String winningTeam) {
        Match match = new Match();
        match.setId(id);
        match.setGroup(group);
        match.setWinningTeam(winningTeam);
        return match;
    }

    private Match match(Long id, Group group, String winningTeam, String playedAtIso) {
        Match match = match(id, group, winningTeam);
        match.setPlayedAt(OffsetDateTime.parse(playedAtIso));
        return match;
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
