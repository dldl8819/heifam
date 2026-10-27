package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.GroupPlayerTeammateStatResponse;
import com.balancify.backend.api.group.dto.GroupPlayerTeammateStatsResponse;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.PlayerRepository;
import com.balancify.backend.repository.TeammateMatchRow;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlayerTeammateStatsQueryServiceTest {

    private static final Group GROUP = group();

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private MatchParticipantRepository matchParticipantRepository;

    private PlayerTeammateStatsQueryService playerTeammateStatsQueryService;

    private static Group group() {
        Group group = new Group();
        group.setId(1L);
        return group;
    }

    @BeforeEach
    void setUp() {
        playerTeammateStatsQueryService = new PlayerTeammateStatsQueryService(
            playerRepository,
            matchParticipantRepository,
            new GroupReadCacheService(0L)
        );
    }

    private Player player(Long id, String nickname) {
        Player player = new Player();
        player.setId(id);
        player.setGroup(GROUP);
        player.setNickname(nickname);
        player.setMmr(1000);
        player.setTier("A");
        return player;
    }

    /** One match as the rows read it: two players at HOME, one AWAY. */
    private List<TeammateMatchRow> match(
        Long matchId,
        String winningTeam,
        Player home1,
        Player home2,
        Player away1
    ) {
        return List.of(
            new TeammateMatchRow(matchId, "HOME", winningTeam, home1.getId()),
            new TeammateMatchRow(matchId, "HOME", winningTeam, home2.getId()),
            new TeammateMatchRow(matchId, "AWAY", winningTeam, away1.getId())
        );
    }

    /** The roster rows the teammates' names are read from, by id. */
    @SuppressWarnings("unchecked")
    private void roster(Player... players) {
        when(playerRepository.findAllById(any())).thenAnswer(invocation -> {
            Set<Long> ids = new HashSet<>();
            ((Iterable<Long>) invocation.getArgument(0)).forEach(ids::add);
            return Arrays.stream(players).filter(player -> ids.contains(player.getId())).toList();
        });
    }

    @Test
    void countsOnlySameTeamMatesOfFinishedMatchesAndTracksTheCurrentWinStreak() {
        Player target = player(1L, "민식");
        Player partner = player(2L, "보이");
        Player rival = player(3L, "스톰");

        List<TeammateMatchRow> rows = new ArrayList<>();
        // Newest first: two wins together, then a loss together, then an unfinished match.
        rows.addAll(match(4L, "HOME", target, partner, rival));
        rows.addAll(match(3L, "HOME", target, partner, rival));
        rows.addAll(match(2L, "AWAY", target, partner, rival));
        rows.addAll(match(1L, null, target, partner, rival));
        when(playerRepository.findByIdAndGroup_Id(1L, 1L)).thenReturn(Optional.of(target));
        when(matchParticipantRepository.findTeammateMatchRows(1L, 1L)).thenReturn(rows);
        roster(target, partner, rival);

        GroupPlayerTeammateStatsResponse response = playerTeammateStatsQueryService.getTeammateStats(1L, 1L);

        // Only the teammate's own row is read for a name; the rival's is never needed.
        ArgumentCaptor<Iterable<Long>> readIds = ArgumentCaptor.captor();
        verify(playerRepository).findAllById(readIds.capture());
        assertThat(readIds.getValue()).containsExactly(2L);

        assertThat(response.nickname()).isEqualTo("민식");
        assertThat(response.games()).isEqualTo(3);
        assertThat(response.wins()).isEqualTo(2);
        assertThat(response.losses()).isEqualTo(1);
        assertThat(response.winRate()).isEqualTo(66.67);

        assertThat(response.teammates()).hasSize(1);
        GroupPlayerTeammateStatResponse teammate = response.teammates().get(0);
        assertThat(teammate.playerId()).isEqualTo(2L);
        assertThat(teammate.nickname()).isEqualTo("보이");
        assertThat(teammate.wins()).isEqualTo(2);
        assertThat(teammate.losses()).isEqualTo(1);
        assertThat(teammate.games()).isEqualTo(3);
        assertThat(teammate.winRate()).isEqualTo(66.67);
        assertThat(teammate.currentWinStreak()).isEqualTo(2);
    }

    @Test
    void sortsByWinRateThenByMatchesTogether() {
        Player target = player(1L, "민식");
        Player strongPartner = player(2L, "보이");
        Player frequentPartner = player(3L, "스톰");
        Player rival = player(4L, "리드");

        List<TeammateMatchRow> rows = new ArrayList<>();
        rows.addAll(match(5L, "HOME", target, strongPartner, rival));
        rows.addAll(match(4L, "HOME", target, frequentPartner, rival));
        rows.addAll(match(3L, "HOME", target, frequentPartner, rival));
        rows.addAll(match(2L, "AWAY", target, frequentPartner, rival));
        when(playerRepository.findByIdAndGroup_Id(1L, 1L)).thenReturn(Optional.of(target));
        when(matchParticipantRepository.findTeammateMatchRows(1L, 1L)).thenReturn(rows);
        roster(target, strongPartner, frequentPartner, rival);

        GroupPlayerTeammateStatsResponse response = playerTeammateStatsQueryService.getTeammateStats(1L, 1L);

        assertThat(response.teammates())
            .extracting(
                GroupPlayerTeammateStatResponse::nickname,
                GroupPlayerTeammateStatResponse::games,
                GroupPlayerTeammateStatResponse::winRate
            )
            .containsExactly(
                tuple("보이", 1, 100.0),
                tuple("스톰", 3, 66.67)
            );
    }

    @Test
    void leavesOutTeammatesWhoseIdentityIsHidden() {
        Player target = player(1L, "민식");
        Player partner = player(2L, "보이");
        Player withdrawn = player(3L, "떠난 사람");
        withdrawn.setActive(false);
        Player rival = player(4L, "리드");

        List<TeammateMatchRow> rows = new ArrayList<>();
        rows.addAll(match(3L, "HOME", target, withdrawn, rival));
        rows.addAll(match(2L, "HOME", target, partner, rival));
        when(playerRepository.findByIdAndGroup_Id(1L, 1L)).thenReturn(Optional.of(target));
        when(matchParticipantRepository.findTeammateMatchRows(1L, 1L)).thenReturn(rows);
        roster(target, partner, withdrawn, rival);

        GroupPlayerTeammateStatsResponse response = playerTeammateStatsQueryService.getTeammateStats(1L, 1L);

        // The withdrawn teammate drops out of the list, but the matches still count as the
        // player's own wins.
        assertThat(response.games()).isEqualTo(2);
        assertThat(response.wins()).isEqualTo(2);
        assertThat(response.teammates())
            .extracting(GroupPlayerTeammateStatResponse::nickname)
            .containsExactly("보이");
    }

    @Test
    void keepsOnlyTheTeammatesAMemberWinsWithInTheirOwnView() {
        Player target = player(1L, "민식");
        Player alwaysWins = player(2L, "보이");
        Player evenRecord = player(3L, "스톰");
        Player mostlyLoses = player(4L, "리드");
        Player rival = player(5L, "제이");

        List<TeammateMatchRow> rows = new ArrayList<>();
        rows.addAll(match(7L, "HOME", target, alwaysWins, rival));
        rows.addAll(match(6L, "HOME", target, alwaysWins, rival));
        rows.addAll(match(5L, "HOME", target, evenRecord, rival));
        rows.addAll(match(4L, "AWAY", target, evenRecord, rival));
        rows.addAll(match(3L, "HOME", target, mostlyLoses, rival));
        rows.addAll(match(2L, "AWAY", target, mostlyLoses, rival));
        rows.addAll(match(1L, "AWAY", target, mostlyLoses, rival));
        when(playerRepository.findByIdAndGroup_Id(1L, 1L)).thenReturn(Optional.of(target));
        when(matchParticipantRepository.findTeammateMatchRows(1L, 1L)).thenReturn(rows);
        roster(target, alwaysWins, evenRecord, mostlyLoses, rival);

        GroupPlayerTeammateStatsResponse response = playerTeammateStatsQueryService.getOwnTeammateStats(1L, 1L);

        // Their own record stays whole - only the teammate list is cut.
        assertThat(response.games()).isEqualTo(7);
        assertThat(response.wins()).isEqualTo(4);
        assertThat(response.losses()).isEqualTo(3);
        // An even record still counts as 50% or better, so it stays; the losing one goes.
        assertThat(response.teammates())
            .extracting(
                GroupPlayerTeammateStatResponse::nickname,
                GroupPlayerTeammateStatResponse::winRate
            )
            .containsExactly(
                tuple("보이", 100.0),
                tuple("스톰", 50.0)
            );
    }

    @Test
    void recognizesOnlyTheRosterRowTheAccountIsLinkedTo() {
        UUID account = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        UUID otherAccount = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
        Player target = player(1L, "민식");
        target.setAuthUserId(account);
        Player withdrawn = player(2L, "떠난 사람");
        withdrawn.setAuthUserId(account);
        withdrawn.setActive(false);
        when(playerRepository.findByIdAndGroup_Id(1L, 1L)).thenReturn(Optional.of(target));
        when(playerRepository.findByIdAndGroup_Id(2L, 1L)).thenReturn(Optional.of(withdrawn));
        when(playerRepository.findByIdAndGroup_Id(99L, 1L)).thenReturn(Optional.empty());

        assertThat(playerTeammateStatsQueryService.isOwnPlayer(1L, 1L, account)).isTrue();
        assertThat(playerTeammateStatsQueryService.isOwnPlayer(1L, 1L, otherAccount)).isFalse();
        // Signed out, or signed in without a verified account id.
        assertThat(playerTeammateStatsQueryService.isOwnPlayer(1L, 1L, null)).isFalse();
        assertThat(playerTeammateStatsQueryService.isOwnPlayer(1L, 2L, account)).isFalse();
        assertThat(playerTeammateStatsQueryService.isOwnPlayer(1L, 99L, account)).isFalse();
    }

    @Test
    void throwsWhenThePlayerIsNotInTheGroup() {
        when(playerRepository.findByIdAndGroup_Id(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> playerTeammateStatsQueryService.getTeammateStats(1L, 99L))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessage("Player not found");
    }
}
