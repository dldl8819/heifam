package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.GroupRecentMatchResponse;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchQueryServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private MatchParticipantRepository matchParticipantRepository;

    private MatchQueryService matchQueryService;

    @BeforeEach
    void setUp() {
        matchQueryService = new MatchQueryService(matchRepository, matchParticipantRepository);
    }

    @Test
    void returnsRecentMatchesOrderedData() {
        Group group = new Group();
        group.setId(1L);

        Match match = new Match();
        match.setId(200L);
        match.setGroup(group);
        match.setWinningTeam("HOME");
        match.setPlayedAt(OffsetDateTime.parse("2026-03-23T08:00:00Z"));

        Player alpha = player(1L, group, "알파", 900);
        Player bravo = player(2L, group, "브라보", 850);

        MatchParticipant homeParticipant = participant(match, alpha, "HOME", 880);
        MatchParticipant awayParticipant = participant(match, bravo, "AWAY", 830);

        when(matchRepository.findRecentByGroupId(eq(1L), any())).thenReturn(List.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(200L))
            .thenReturn(List.of(homeParticipant, awayParticipant));

        List<GroupRecentMatchResponse> responses = matchQueryService.getRecentMatches(1L, 10);

        assertThat(responses).hasSize(1);
        GroupRecentMatchResponse response = responses.get(0);
        assertThat(response.matchId()).isEqualTo(200L);
        assertThat(response.winningTeam()).isEqualTo("HOME");
        assertThat(response.homeTeam()).hasSize(1);
        assertThat(response.awayTeam()).hasSize(1);
        assertThat(response.homeMmr()).isEqualTo(880);
        assertThat(response.awayMmr()).isEqualTo(830);
        assertThat(response.mmrDiff()).isEqualTo(50);
    }

    private Player player(Long id, Group group, String nickname, int mmr) {
        Player player = new Player();
        player.setId(id);
        player.setGroup(group);
        player.setNickname(nickname);
        player.setMmr(mmr);
        player.setRace("P");
        player.setTier("A");
        return player;
    }

    private MatchParticipant participant(Match match, Player player, String team, Integer mmrBefore) {
        MatchParticipant participant = new MatchParticipant();
        participant.setMatch(match);
        participant.setPlayer(player);
        participant.setTeam(team);
        participant.setMmrBefore(mmrBefore);
        return participant;
    }
}
