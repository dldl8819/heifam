package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.GroupDashboardResponse;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchRepository;
import com.balancify.backend.repository.PlayerRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardQueryServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private MatchParticipantRepository matchParticipantRepository;

    @Mock
    private MatchRepository matchRepository;

    private DashboardQueryService dashboardQueryService;

    @BeforeEach
    void setUp() {
        dashboardQueryService = new DashboardQueryService(
            playerRepository,
            matchParticipantRepository,
            matchRepository
        );
    }

    @Test
    void returnsDashboardSummaryWithTopPreviewAndRecentBalancePreview() {
        Group group = new Group();
        group.setId(1L);

        Player p1 = player(1L, group, "Alpha", "P", 2000);
        Player p2 = player(2L, group, "Bravo", "T", 1800);
        Player p3 = player(3L, group, "Charlie", "Z", 1700);
        Player p4 = player(4L, group, "Delta", "P", 1600);
        Player p5 = player(5L, group, "Echo", "T", 1500);
        Player p6 = player(6L, group, "Foxtrot", "Z", 1400);

        Match m1 = match(101L, group, "HOME", OffsetDateTime.parse("2026-03-21T10:00:00+09:00"));
        Match m2 = match(102L, group, "AWAY", OffsetDateTime.parse("2026-03-22T10:00:00+09:00"));

        List<MatchParticipant> groupParticipants = List.of(
            participant(1001L, m1, p1, "HOME", null),
            participant(1002L, m1, p2, "AWAY", null),
            participant(1003L, m2, p1, "HOME", null),
            participant(1004L, m2, p2, "AWAY", null)
        );

        List<MatchParticipant> latestMatchParticipants = List.of(
            participant(2001L, m2, p1, "HOME", 1990),
            participant(2002L, m2, p3, "HOME", 1705),
            participant(2003L, m2, p2, "AWAY", 1790),
            participant(2004L, m2, p4, "AWAY", 1605)
        );

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L))
            .thenReturn(List.of(p1, p2, p3, p4, p5, p6));
        when(matchParticipantRepository.findByGroupIdOrderByPlayedAtDesc(1L))
            .thenReturn(groupParticipants);
        when(matchRepository.findTopByGroup_IdOrderByPlayedAtDescIdDesc(1L))
            .thenReturn(Optional.of(m2));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(102L))
            .thenReturn(latestMatchParticipants);

        GroupDashboardResponse response = dashboardQueryService.getGroupDashboard(1L);

        assertThat(response.kpiSummary().totalPlayers()).isEqualTo(6);
        assertThat(response.kpiSummary().topMmr()).isEqualTo(2000);
        assertThat(response.kpiSummary().averageMmr()).isEqualTo(1666.67);
        assertThat(response.kpiSummary().totalGames()).isEqualTo(2);

        assertThat(response.topRankingPreview()).hasSize(5);
        assertThat(response.topRankingPreview().get(0).rank()).isEqualTo(1);
        assertThat(response.topRankingPreview().get(0).nickname()).isEqualTo("Alpha");
        assertThat(response.topRankingPreview().get(0).winRate()).isEqualTo(50.0);
        assertThat(response.topRankingPreview().get(1).nickname()).isEqualTo("Bravo");
        assertThat(response.topRankingPreview().get(1).winRate()).isEqualTo(50.0);

        assertThat(response.recentBalancePreview()).isNotNull();
        assertThat(response.recentBalancePreview().matchId()).isEqualTo(102L);
        assertThat(response.recentBalancePreview().homeTeam()).hasSize(2);
        assertThat(response.recentBalancePreview().awayTeam()).hasSize(2);
        assertThat(response.recentBalancePreview().homeMmr()).isEqualTo(3695);
        assertThat(response.recentBalancePreview().awayMmr()).isEqualTo(3395);
        assertThat(response.recentBalancePreview().mmrDiff()).isEqualTo(300);
        assertThat(response.recentBalancePreview().createdAt())
            .isEqualTo(OffsetDateTime.parse("2026-03-22T10:00:00+09:00"));
    }

    @Test
    void returnsEmptyDashboardWhenGroupHasNoData() {
        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(99L)).thenReturn(List.of());
        when(matchParticipantRepository.findByGroupIdOrderByPlayedAtDesc(99L)).thenReturn(List.of());
        when(matchRepository.findTopByGroup_IdOrderByPlayedAtDescIdDesc(99L)).thenReturn(Optional.empty());

        GroupDashboardResponse response = dashboardQueryService.getGroupDashboard(99L);

        assertThat(response.kpiSummary().totalPlayers()).isZero();
        assertThat(response.kpiSummary().topMmr()).isZero();
        assertThat(response.kpiSummary().averageMmr()).isZero();
        assertThat(response.kpiSummary().totalGames()).isZero();
        assertThat(response.topRankingPreview()).isEmpty();
        assertThat(response.recentBalancePreview()).isNull();
    }

    private Player player(Long id, Group group, String nickname, String race, int mmr) {
        Player player = new Player();
        player.setId(id);
        player.setGroup(group);
        player.setNickname(nickname);
        player.setRace(race);
        player.setMmr(mmr);
        return player;
    }

    private Match match(Long id, Group group, String winningTeam, OffsetDateTime playedAt) {
        Match match = new Match();
        match.setId(id);
        match.setGroup(group);
        match.setWinningTeam(winningTeam);
        match.setPlayedAt(playedAt);
        return match;
    }

    private MatchParticipant participant(
        Long id,
        Match match,
        Player player,
        String team,
        Integer mmrBefore
    ) {
        MatchParticipant participant = new MatchParticipant();
        participant.setId(id);
        participant.setMatch(match);
        participant.setPlayer(player);
        participant.setTeam(team);
        participant.setMmrBefore(mmrBefore);
        return participant;
    }
}

