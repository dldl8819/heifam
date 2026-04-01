package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.match.dto.MatchImportResponse;
import com.balancify.backend.api.match.dto.MatchImportRowRequest;
import com.balancify.backend.api.match.dto.MatchResultRequest;
import com.balancify.backend.api.match.dto.MatchResultResponse;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchParticipant;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchImportServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private MatchParticipantRepository matchParticipantRepository;

    @Mock
    private MatchResultService matchResultService;

    private MatchImportService matchImportService;

    @BeforeEach
    void setUp() {
        matchImportService = new MatchImportService(
            groupRepository,
            playerRepository,
            matchRepository,
            matchParticipantRepository,
            matchResultService
        );
    }

    @Test
    void importsValidRowsAndSkipsInvalidRows() {
        Group group = new Group();
        group.setId(1L);
        group.setName("Group 1");

        List<Player> players = List.of(
            player(1L, group, "소울", 600),
            player(2L, group, "새록", 800),
            player(3L, group, "민식", 600),
            player(4L, group, "땅땅", 700),
            player(5L, group, "대하", 400),
            player(6L, group, "백구", 900)
        );

        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(1L)).thenReturn(players);
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> {
            Match match = invocation.getArgument(0);
            match.setId(100L);
            return match;
        });
        when(matchParticipantRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchResultService.processMatchResult(eq(100L), any(MatchResultRequest.class)))
            .thenReturn(new MatchResultResponse(100L, "AWAY", 32, 0.5, 0.5, List.of()));

        List<MatchImportRowRequest> request = List.of(
            new MatchImportRowRequest(
                1L,
                "G001",
                "2026-03-21",
                List.of("소울", "새록", "민식"),
                List.of("땅땅", "대하", "백구"),
                "A"
            ),
            new MatchImportRowRequest(
                1L,
                "G002",
                "2026-03-21",
                List.of("보이", "새록", "민식"),
                List.of("땅땅", "대하", "백구"),
                "AWAY"
            )
        );

        MatchImportResponse response = matchImportService.importMatches(request);

        assertThat(response.totalRows()).isEqualTo(2);
        assertThat(response.importedCount()).isEqualTo(1);
        assertThat(response.failedCount()).isEqualTo(1);
        assertThat(response.failedRows()).hasSize(1);
        assertThat(response.failedRows().get(0).matchCode()).isEqualTo("G002");
        assertThat(response.failedRows().get(0).reason()).isEqualTo("Player not found: 보이");

        ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository, times(1)).save(matchCaptor.capture());
        assertThat(matchCaptor.getValue().getWinningTeam()).isNull();

        ArgumentCaptor<List<MatchParticipant>> participantCaptor = ArgumentCaptor.forClass(List.class);
        verify(matchParticipantRepository, times(1)).saveAll(participantCaptor.capture());
        assertThat(participantCaptor.getValue()).hasSize(6);
        assertThat(participantCaptor.getValue())
            .extracting(MatchParticipant::getTeam)
            .containsExactly("HOME", "HOME", "HOME", "AWAY", "AWAY", "AWAY");
        verify(matchResultService, times(1)).processMatchResult(eq(100L), any(MatchResultRequest.class));
    }

    @Test
    void createsGroupWhenGroupDoesNotExist() {
        Group createdGroup = new Group();
        createdGroup.setId(5L);
        createdGroup.setName("Group 5");

        when(groupRepository.findById(5L)).thenReturn(Optional.empty());
        when(groupRepository.save(any(Group.class))).thenReturn(createdGroup);
        when(playerRepository.findByGroup_IdOrderByMmrDescIdAsc(5L)).thenReturn(List.of(
            player(11L, createdGroup, "A", 1000),
            player(12L, createdGroup, "B", 900),
            player(13L, createdGroup, "C", 800),
            player(14L, createdGroup, "D", 700),
            player(15L, createdGroup, "E", 600),
            player(16L, createdGroup, "F", 500)
        ));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> {
            Match match = invocation.getArgument(0);
            match.setId(200L);
            return match;
        });
        when(matchParticipantRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchResultService.processMatchResult(eq(200L), any(MatchResultRequest.class)))
            .thenReturn(new MatchResultResponse(200L, "HOME", 32, 0.5, 0.5, List.of()));

        MatchImportResponse response = matchImportService.importMatches(List.of(
            new MatchImportRowRequest(
                5L,
                "G900",
                "2026-03-22T20:00:00+09:00",
                List.of("A", "B", "C"),
                List.of("D", "E", "F"),
                "HOME"
            )
        ));

        assertThat(response.importedCount()).isEqualTo(1);
        assertThat(response.failedCount()).isZero();
        verify(groupRepository, times(1)).save(any(Group.class));
        verify(matchResultService, times(1)).processMatchResult(eq(200L), any(MatchResultRequest.class));
    }

    private Player player(Long id, Group group, String nickname, int mmr) {
        Player player = new Player();
        player.setId(id);
        player.setGroup(group);
        player.setNickname(nickname);
        player.setMmr(mmr);
        player.setTier("A");
        player.setRace("P");
        return player;
    }
}
