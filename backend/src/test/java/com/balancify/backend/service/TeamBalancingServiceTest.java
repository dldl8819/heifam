package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.match.dto.BalancePlayerDto;
import com.balancify.backend.api.match.dto.BalanceRequest;
import com.balancify.backend.api.match.dto.BalanceResponse;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.PlayerRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TeamBalancingServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    private TeamBalancingService service;

    @BeforeEach
    void setUp() {
        service = new TeamBalancingService(playerRepository);
    }

    @Test
    void generatesBalancedTwoVsTwoFromPlayerIds() {
        List<Player> players = createPlayers(1L, List.of(
            new PlayerSeed(1L, "A", 1600, "P"),
            new PlayerSeed(2L, "B", 1520, "P"),
            new PlayerSeed(3L, "C", 1490, "T"),
            new PlayerSeed(4L, "D", 1440, "T")
        ));
        when(playerRepository.findByGroup_IdAndIdIn(1L, List.of(1L, 2L, 3L, 4L)))
            .thenReturn(players);

        BalanceResponse response = service.balance(
            new BalanceRequest(1L, List.of(1L, 2L, 3L, 4L), 2, null, "PT")
        );

        assertThat(response.teamSize()).isEqualTo(2);
        assertThat(response.homeTeam()).hasSize(2);
        assertThat(response.awayTeam()).hasSize(2);
        assertThat(response.homeMmr() + response.awayMmr()).isEqualTo(6050);
        assertThat(response.mmrDiff()).isEqualTo(findMinimumDiff(allPlayers(response), 2));
        assertThat(response.expectedHomeWinRate()).isBetween(0.0, 1.0);
    }

    @Test
    void rejectsMissingRaceComposition() {
        BalanceRequest request = new BalanceRequest(List.of(
            new BalancePlayerDto("A", 1400),
            new BalancePlayerDto("B", 1300),
            new BalancePlayerDto("C", 1200),
            new BalancePlayerDto("D", 1100),
            new BalancePlayerDto("E", 1000),
            new BalancePlayerDto("F", 900)
        ));

        assertThatThrownBy(() -> service.balance(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("종족 조합을 선택해 주세요.");
    }

    @Test
    void respectsRaceCompositionForThreeVsThree() {
        List<Player> players = createPlayers(1L, List.of(
            new PlayerSeed(1L, "A", 1500, "P"),
            new PlayerSeed(2L, "B", 1480, "P"),
            new PlayerSeed(3L, "C", 1460, "T"),
            new PlayerSeed(4L, "D", 1440, "P"),
            new PlayerSeed(5L, "E", 1420, "P"),
            new PlayerSeed(6L, "F", 1400, "T")
        ));
        when(playerRepository.findByGroup_IdAndIdIn(1L, List.of(1L, 2L, 3L, 4L, 5L, 6L)))
            .thenReturn(players);

        BalanceResponse response = service.balance(
            new BalanceRequest(1L, List.of(1L, 2L, 3L, 4L, 5L, 6L), 3, null, "PPT")
        );

        assertThat(canonicalRaceSummary(response.homeTeam(), players)).isEqualTo("PPT");
        assertThat(canonicalRaceSummary(response.awayTeam(), players)).isEqualTo("PPT");
    }

    @Test
    void supportsFlexibleRaceSlotsForThreeVsThreeComposition() {
        List<Player> players = createPlayers(1L, List.of(
            new PlayerSeed(1L, "A", 1500, "PT"),
            new PlayerSeed(2L, "B", 1480, "P"),
            new PlayerSeed(3L, "C", 1460, "P"),
            new PlayerSeed(4L, "D", 1440, "PT"),
            new PlayerSeed(5L, "E", 1420, "P"),
            new PlayerSeed(6L, "F", 1400, "P")
        ));
        when(playerRepository.findByGroup_IdAndIdIn(1L, List.of(1L, 2L, 3L, 4L, 5L, 6L)))
            .thenReturn(players);

        BalanceResponse response = service.balance(
            new BalanceRequest(1L, List.of(1L, 2L, 3L, 4L, 5L, 6L), 3, null, "PPT")
        );

        assertThat(canonicalRaceSummary(response.homeTeam(), players)).isEqualTo("PPT");
        assertThat(canonicalRaceSummary(response.awayTeam(), players)).isEqualTo("PPT");
        assertThat(response.homeTeam()).allMatch(player -> player.assignedRace() != null);
        assertThat(response.awayTeam()).allMatch(player -> player.assignedRace() != null);
    }

    @Test
    void supportsFlexibleRaceSlotsForTwoVsTwoComposition() {
        List<Player> players = createPlayers(1L, List.of(
            new PlayerSeed(1L, "A", 1600, "PT"),
            new PlayerSeed(2L, "B", 1520, "P"),
            new PlayerSeed(3L, "C", 1490, "PT"),
            new PlayerSeed(4L, "D", 1440, "P")
        ));
        when(playerRepository.findByGroup_IdAndIdIn(1L, List.of(1L, 2L, 3L, 4L)))
            .thenReturn(players);

        BalanceResponse response = service.balance(
            new BalanceRequest(1L, List.of(1L, 2L, 3L, 4L), 2, null, "PT")
        );

        assertThat(canonicalRaceSummary(response.homeTeam(), players)).isEqualTo("PT");
        assertThat(canonicalRaceSummary(response.awayTeam(), players)).isEqualTo("PT");
    }

    @Test
    void rejectsRaceCompositionWhenNoValidSplitExists() {
        List<Player> players = createPlayers(1L, List.of(
            new PlayerSeed(1L, "A", 1500, "P"),
            new PlayerSeed(2L, "B", 1480, "P"),
            new PlayerSeed(3L, "C", 1460, "P"),
            new PlayerSeed(4L, "D", 1440, "P"),
            new PlayerSeed(5L, "E", 1420, "P"),
            new PlayerSeed(6L, "F", 1400, "T")
        ));
        when(playerRepository.findByGroup_IdAndIdIn(1L, List.of(1L, 2L, 3L, 4L, 5L, 6L)))
            .thenReturn(players);

        assertThatThrownBy(() ->
            service.balance(new BalanceRequest(1L, List.of(1L, 2L, 3L, 4L, 5L, 6L), 3, null, "PPT"))
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("선택한 종족 조합으로 매치를 구성할 수 없습니다");
    }

    @Test
    void rejectsInvalidTwoVsTwoPlayerCount() {
        assertThatThrownBy(() ->
            service.balance(new BalanceRequest(1L, List.of(1L, 2L, 3L), 2))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("teamSize=2 requires exactly 4 players");
    }

    @Test
    void rejectsInvalidThreeVsThreePlayerCount() {
        assertThatThrownBy(() ->
            service.balance(new BalanceRequest(1L, List.of(1L, 2L, 3L, 4L), 3))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("teamSize=3 requires exactly 6 players");
    }

    @Test
    void rejectsDuplicatePlayerIds() {
        assertThatThrownBy(() ->
            service.balance(new BalanceRequest(1L, List.of(1L, 2L, 2L, 3L), 2))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("playerIds must not contain duplicates");
    }

    @Test
    void rejectsMissingRaceCompositionWhenTeamSizeDefaultsToThree() {
        BalanceRequest request = new BalanceRequest(List.of(
            new BalancePlayerDto("A", 1000),
            new BalancePlayerDto("B", 1000),
            new BalancePlayerDto("C", 1000),
            new BalancePlayerDto("D", 1000),
            new BalancePlayerDto("E", 1000),
            new BalancePlayerDto("F", 1000)
        ));

        assertThatThrownBy(() -> service.balance(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("종족 조합을 선택해 주세요.");
    }

    private List<Player> createPlayers(Long groupId, List<PlayerSeed> seeds) {
        Group group = new Group();
        group.setId(groupId);

        List<Player> players = new ArrayList<>();
        for (PlayerSeed seed : seeds) {
            Player player = new Player();
            player.setId(seed.id());
            player.setGroup(group);
            player.setNickname(seed.nickname());
            player.setRace(seed.race());
            player.setMmr(seed.mmr());
            players.add(player);
        }
        return players;
    }

    private String canonicalRaceSummary(List<BalancePlayerDto> team, List<Player> players) {
        return team.stream()
            .map(player -> player.assignedRace() != null
                ? player.assignedRace()
                : players.stream()
                    .filter(candidate -> candidate.getNickname().equals(player.name()))
                    .findFirst()
                    .orElseThrow()
                    .getRace()
            )
            .sorted()
            .reduce("", String::concat);
    }

    private List<BalancePlayerDto> allPlayers(BalanceResponse response) {
        List<BalancePlayerDto> merged = new ArrayList<>();
        merged.addAll(response.homeTeam());
        merged.addAll(response.awayTeam());
        return merged;
    }

    private int findMinimumDiff(List<BalancePlayerDto> players, int teamSize) {
        int minDiff = Integer.MAX_VALUE;
        int totalMasks = 1 << players.size();
        for (int mask = 0; mask < totalMasks; mask++) {
            if (Integer.bitCount(mask) != teamSize || (mask & 1) == 0) {
                continue;
            }

            int homeMmr = 0;
            int awayMmr = 0;
            for (int index = 0; index < players.size(); index++) {
                if ((mask & (1 << index)) != 0) {
                    homeMmr += players.get(index).mmr();
                } else {
                    awayMmr += players.get(index).mmr();
                }
            }

            int diff = Math.abs(homeMmr - awayMmr);
            minDiff = Math.min(minDiff, diff);
        }
        return minDiff;
    }

    private record PlayerSeed(Long id, String nickname, int mmr, String race) {
    }
}
