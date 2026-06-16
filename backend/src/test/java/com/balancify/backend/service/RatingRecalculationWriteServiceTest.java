package com.balancify.backend.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.MatchRepository;
import com.balancify.backend.repository.MmrHistoryRepository;
import com.balancify.backend.repository.PlayerRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RatingRecalculationWriteServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private MatchParticipantRepository matchParticipantRepository;

    @Mock
    private MmrHistoryRepository mmrHistoryRepository;

    @Mock
    private PlayerStatsRefreshService playerStatsRefreshService;

    private RatingRecalculationWriteService writeService;

    @BeforeEach
    void setUp() {
        writeService = new RatingRecalculationWriteService(
            playerRepository,
            matchRepository,
            matchParticipantRepository,
            mmrHistoryRepository,
            new GroupReadCacheService(0),
            playerStatsRefreshService
        );
    }

    @Test
    void rebuildsPlayerStatsForAffectedGroupsAfterApplyingRecalculation() {
        Group group = new Group();
        group.setId(1L);
        Player player = new Player();
        player.setId(10L);
        player.setGroup(group);
        player.setNickname("PlayerAlpha");
        player.setTier("B");
        player.setMmr(1000);

        RatingReplayPlan plan = new RatingReplayPlan(
            List.of(new RatingReplayPlan.PlayerResult(10L, 1020, "B", 1000)),
            List.of(),
            0,
            1,
            0.0,
            List.of()
        );

        when(playerRepository.findAllById(plan.playerIds())).thenReturn(List.of(player));
        when(matchParticipantRepository.findAllById(plan.participantIds())).thenReturn(List.of());
        when(matchRepository.findAllById(plan.matchIds())).thenReturn(List.of());
        when(playerRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        writeService.apply(plan);

        verify(playerStatsRefreshService).rebuildGroupStats(1L);
    }
}
