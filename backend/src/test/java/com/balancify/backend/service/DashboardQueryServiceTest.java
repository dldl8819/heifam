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
import java.util.ArrayList;
import java.util.List;
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
            matchRepository,
            36
        );
    }

    @Test
    void returnsDashboardSummaryWithoutRankingAndRecentBalancePreviews() {
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

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L))
            .thenReturn(List.of(p1, p2, p3, p4, p5, p6));
        when(matchParticipantRepository.findByGroupIdOrderByPlayedAtDesc(1L))
            .thenReturn(groupParticipants);

        GroupDashboardResponse response = dashboardQueryService.getGroupDashboard(1L);

        assertThat(response.currentKFactor()).isEqualTo(36);
        assertThat(response.kpiSummary().totalPlayers()).isEqualTo(6);
        assertThat(response.kpiSummary().topMmr()).isEqualTo(2000);
        assertThat(response.kpiSummary().averageMmr()).isEqualTo(1666.67);
        assertThat(response.kpiSummary().totalGames()).isEqualTo(2);

        assertThat(response.topRankingPreview()).isEmpty();
        assertThat(response.recentBalancePreview()).isNull();
        assertThat(response.myRaceSummary().linked()).isFalse();
        assertThat(response.myGameTypeSummary().linked()).isFalse();
        assertThat(response.myTeammateSummary().linked()).isFalse();
    }

    @Test
    void returnsEmptyDashboardWhenGroupHasNoData() {
        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(99L)).thenReturn(List.of());
        when(matchParticipantRepository.findByGroupIdOrderByPlayedAtDesc(99L)).thenReturn(List.of());

        GroupDashboardResponse response = dashboardQueryService.getGroupDashboard(99L);

        assertThat(response.currentKFactor()).isEqualTo(36);
        assertThat(response.kpiSummary().totalPlayers()).isZero();
        assertThat(response.kpiSummary().topMmr()).isZero();
        assertThat(response.kpiSummary().averageMmr()).isZero();
        assertThat(response.kpiSummary().totalGames()).isZero();
        assertThat(response.topRankingPreview()).isEmpty();
        assertThat(response.recentBalancePreview()).isNull();
        assertThat(response.myRaceSummary().linked()).isFalse();
        assertThat(response.myGameTypeSummary().linked()).isFalse();
        assertThat(response.myTeammateSummary().linked()).isFalse();
    }

    @Test
    void returnsMyRaceSummaryWhenRequesterNicknameMatchesRosterPlayer() {
        Group group = new Group();
        group.setId(1L);

        Player alpha = player(1L, group, "Alpha", "P", 2000);
        Player bravo = player(2L, group, "Bravo", "T", 1800);
        Player charlie = player(3L, group, "Charlie", "P", 1700);
        Player delta = player(4L, group, "Delta", "Z", 1600);
        Player echo = player(5L, group, "Echo", "P", 1500);

        Match m1 = match(201L, group, "HOME", OffsetDateTime.parse("2026-03-20T10:00:00+09:00"));
        Match m2 = match(202L, group, "AWAY", OffsetDateTime.parse("2026-03-21T10:00:00+09:00"));
        Match m3 = match(203L, group, "HOME", OffsetDateTime.parse("2026-03-22T10:00:00+09:00"));

        List<MatchParticipant> groupParticipants = List.of(
            participant(3001L, m3, alpha, "HOME", null, "P"),
            participant(3002L, m3, delta, "HOME", null, "Z"),
            participant(3003L, m3, echo, "HOME", null, "P"),
            participant(3004L, m3, bravo, "AWAY", null, "T"),

            participant(3012L, m2, alpha, "HOME", null, "T"),
            participant(3005L, m2, bravo, "HOME", null, "T"),
            participant(3006L, m2, charlie, "HOME", null, "P"),
            participant(3007L, m2, delta, "AWAY", null, "Z"),

            participant(3013L, m1, alpha, "HOME", null, "P"),
            participant(3008L, m1, charlie, "HOME", null, "P"),
            participant(3009L, m1, echo, "HOME", null, "P"),
            participant(3010L, m1, bravo, "AWAY", null, "T"),
            participant(3011L, m1, delta, "AWAY", null, "Z")
        );

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L))
            .thenReturn(List.of(alpha, bravo, charlie, delta, echo));
        when(matchParticipantRepository.findByGroupIdOrderByPlayedAtDesc(1L))
            .thenReturn(groupParticipants);
        GroupDashboardResponse response = dashboardQueryService.getGroupDashboard(1L, "Alpha");

        assertThat(response.currentKFactor()).isEqualTo(36);
        assertThat(response.myRaceSummary().linked()).isTrue();
        assertThat(response.myRaceSummary().nickname()).isEqualTo("Alpha");
        assertThat(response.myRaceSummary().wins()).isEqualTo(2);
        assertThat(response.myRaceSummary().losses()).isEqualTo(1);
        assertThat(response.myRaceSummary().games()).isEqualTo(3);
        assertThat(response.myRaceSummary().winRate()).isEqualTo(66.67);
        assertThat(response.myRaceSummary().byRace()).hasSize(2);
        assertThat(response.myRaceSummary().byRace().get(0).race()).isEqualTo("P");
        assertThat(response.myRaceSummary().byRace().get(0).wins()).isEqualTo(2);
        assertThat(response.myRaceSummary().byRace().get(0).games()).isEqualTo(2);
        assertThat(response.myRaceSummary().byRace().get(1).race()).isEqualTo("T");
        assertThat(response.myRaceSummary().byRace().get(1).losses()).isEqualTo(1);

        assertThat(response.myGameTypeSummary().linked()).isTrue();
        assertThat(response.myGameTypeSummary().nickname()).isEqualTo("Alpha");
        assertThat(response.myGameTypeSummary().wins()).isEqualTo(2);
        assertThat(response.myGameTypeSummary().losses()).isEqualTo(1);
        assertThat(response.myGameTypeSummary().games()).isEqualTo(3);
        assertThat(response.myGameTypeSummary().winRate()).isEqualTo(66.67);
        assertThat(response.myGameTypeSummary().byGameType()).hasSize(3);
        assertThat(response.myGameTypeSummary().byGameType().get(0).gameType()).isEqualTo("PPP");
        assertThat(response.myGameTypeSummary().byGameType().get(0).wins()).isEqualTo(1);
        assertThat(response.myGameTypeSummary().byGameType().get(1).gameType()).isEqualTo("PPZ");
        assertThat(response.myGameTypeSummary().byGameType().get(1).wins()).isEqualTo(1);
        assertThat(response.myGameTypeSummary().byGameType().get(2).gameType()).isEqualTo("PTT");
        assertThat(response.myGameTypeSummary().byGameType().get(2).losses()).isEqualTo(1);
    }

    @Test
    void returnsMyTeammateSummaryWithMinimumTenSharedGames() {
        Group group = new Group();
        group.setId(1L);

        Player alpha = player(1L, group, "Alpha", "P", 2000);
        Player bravo = player(2L, group, "Bravo", "T", 1800);
        Player charlie = player(3L, group, "Charlie", "P", 1700);
        Player delta = player(4L, group, "Delta", "Z", 1600);
        Player echo = player(5L, group, "Echo", "P", 1500);

        List<MatchParticipant> groupParticipants = new ArrayList<>();
        addSharedMatches(groupParticipants, group, alpha, bravo, 400L, 4000L, "WWLLLWWWWWWW");
        addSharedMatches(groupParticipants, group, alpha, charlie, 500L, 5000L, "WWWWWLLWWW");
        addSharedMatches(groupParticipants, group, alpha, delta, 600L, 6000L, "WLLLLLLLWWWWWW");
        addSharedMatches(groupParticipants, group, alpha, echo, 700L, 7000L, "WWWWWWWWW");

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L))
            .thenReturn(List.of(alpha, bravo, charlie, delta, echo));
        when(matchParticipantRepository.findByGroupIdOrderByPlayedAtDesc(1L))
            .thenReturn(groupParticipants);

        GroupDashboardResponse response = dashboardQueryService.getGroupDashboard(1L, "Alpha");

        assertThat(response.myTeammateSummary().linked()).isTrue();
        assertThat(response.myTeammateSummary().minGames()).isEqualTo(10);
        assertThat(response.myTeammateSummary().bestDuos())
            .extracting("nickname")
            .containsExactly("Charlie", "Bravo", "Delta");
        assertThat(response.myTeammateSummary().frequentTeammates())
            .extracting("nickname")
            .containsExactly("Delta", "Bravo", "Charlie");
        assertThat(response.myTeammateSummary().streakPartners())
            .extracting("nickname")
            .containsExactly("Charlie", "Bravo", "Delta");
        assertThat(response.myTeammateSummary().streakPartners().get(0).currentWinStreak()).isEqualTo(5);
        assertThat(response.myTeammateSummary().bestDuos())
            .noneMatch(teammate -> "Echo".equals(teammate.nickname()));
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

    private MatchParticipant participant(
        Long id,
        Match match,
        Player player,
        String team,
        Integer mmrBefore,
        String race
    ) {
        MatchParticipant participant = participant(id, match, player, team, mmrBefore);
        participant.setRace(race);
        return participant;
    }

    private void addSharedMatches(
        List<MatchParticipant> participants,
        Group group,
        Player target,
        Player teammate,
        Long matchStartId,
        Long participantStartId,
        String newestToOldestResults
    ) {
        OffsetDateTime latestPlayedAt = OffsetDateTime.parse("2026-04-01T10:00:00+09:00");
        for (int index = 0; index < newestToOldestResults.length(); index++) {
            boolean win = newestToOldestResults.charAt(index) == 'W';
            Match match = match(
                matchStartId + index,
                group,
                win ? "HOME" : "AWAY",
                latestPlayedAt.minusMinutes(index)
            );
            participants.add(participant(participantStartId + (index * 2L), match, target, "HOME", null));
            participants.add(participant(participantStartId + (index * 2L) + 1, match, teammate, "HOME", null));
        }
    }
}
