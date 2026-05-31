package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.RankingItemResponse;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.PlayerRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private MatchParticipantRepository matchParticipantRepository;

    @Mock
    private DormancyMmrDecayService dormancyMmrDecayService;

    @Mock
    private MonthlyTierRefreshService monthlyTierRefreshService;

    private RankingService rankingService;

    @BeforeEach
    void setUp() {
        rankingService = new RankingService(
            playerRepository,
            matchParticipantRepository,
            dormancyMmrDecayService,
            monthlyTierRefreshService
        );
    }

    @Test
    void returnsRankingTierFromCurrentMmrForNextMonthPreview() {
        Group group = new Group();
        group.setId(1L);
        Player player = player(1L, group, "PlayerAlpha", "P", "B", 1220);
        Player unassigned = player(2L, group, "PlayerBravo", "T", "B", 0);

        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L))
            .thenReturn(List.of(player, unassigned));
        when(matchParticipantRepository.findByGroupIdOrderByPlayedAtDesc(1L))
            .thenReturn(List.of());

        List<RankingItemResponse> response = rankingService.getGroupRanking(1L);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).tier()).isEqualTo("B+");
        assertThat(response.get(0).currentMmr()).isEqualTo(1220);
        assertThat(response.get(1).tier()).isEqualTo("UNASSIGNED");
        assertThat(response.get(1).currentMmr()).isEqualTo(0);

        InOrder ordered = inOrder(monthlyTierRefreshService, dormancyMmrDecayService);
        ordered.verify(monthlyTierRefreshService).applyMonthlyTierRefreshIfDue();
        ordered.verify(dormancyMmrDecayService).applyGroupDormancyDecay(1L);
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
        player.setActive(true);
        return player;
    }
}
