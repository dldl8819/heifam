package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.dto.GroupPlayerTeammateStatResponse;
import com.balancify.backend.api.group.dto.GroupPlayerTeammateStatsResponse;
import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.Player;
import com.balancify.backend.repository.MatchParticipantRepository;
import com.balancify.backend.repository.PlayerRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private MatchParticipant participant(Match match, Player player, String team) {
        MatchParticipant participant = new MatchParticipant();
        participant.setMatch(match);
        participant.setPlayer(player);
        participant.setTeam(team);
        return participant;
    }

    /** Builds one match, newest first in the returned list, with HOME winning unless stated. */
    private List<MatchParticipant> match(
        Long matchId,
        String winningTeam,
        Player home1,
        Player home2,
        Player away1
    ) {
        Match match = new Match();
        match.setId(matchId);
        match.setGroup(GROUP);
        match.setWinningTeam(winningTeam);
        match.setPlayedAt(OffsetDateTime.parse("2026-09-01T10:00:00Z").plusDays(matchId));
        List<MatchParticipant> participants = new ArrayList<>();
        participants.add(participant(match, home1, "HOME"));
        participants.add(participant(match, home2, "HOME"));
        participants.add(participant(match, away1, "AWAY"));
        return participants;
    }

    @Test
    void countsOnlySameTeamMatesOfFinishedMatchesAndTracksTheCurrentWinStreak() {
        Player target = player(1L, "민식");
        Player partner = player(2L, "보이");
        Player rival = player(3L, "스톰");

        List<MatchParticipant> participants = new ArrayList<>();
        // Newest first: two wins together, then a loss together, then an unfinished match.
        participants.addAll(match(4L, "HOME", target, partner, rival));
        participants.addAll(match(3L, "HOME", target, partner, rival));
        participants.addAll(match(2L, "AWAY", target, partner, rival));
        participants.addAll(match(1L, null, target, partner, rival));
        when(playerRepository.findByIdAndGroup_Id(1L, 1L)).thenReturn(Optional.of(target));
        when(matchParticipantRepository.findByGroupIdAndPlayerMatchesOrderByPlayedAtDesc(1L, 1L))
            .thenReturn(participants);

        GroupPlayerTeammateStatsResponse response = playerTeammateStatsQueryService.getTeammateStats(1L, 1L);

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

        List<MatchParticipant> participants = new ArrayList<>();
        participants.addAll(match(5L, "HOME", target, strongPartner, rival));
        participants.addAll(match(4L, "HOME", target, frequentPartner, rival));
        participants.addAll(match(3L, "HOME", target, frequentPartner, rival));
        participants.addAll(match(2L, "AWAY", target, frequentPartner, rival));
        when(playerRepository.findByIdAndGroup_Id(1L, 1L)).thenReturn(Optional.of(target));
        when(matchParticipantRepository.findByGroupIdAndPlayerMatchesOrderByPlayedAtDesc(1L, 1L))
            .thenReturn(participants);

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

        List<MatchParticipant> participants = new ArrayList<>();
        participants.addAll(match(3L, "HOME", target, withdrawn, rival));
        participants.addAll(match(2L, "HOME", target, partner, rival));
        when(playerRepository.findByIdAndGroup_Id(1L, 1L)).thenReturn(Optional.of(target));
        when(matchParticipantRepository.findByGroupIdAndPlayerMatchesOrderByPlayedAtDesc(1L, 1L))
            .thenReturn(participants);

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

        List<MatchParticipant> participants = new ArrayList<>();
        participants.addAll(match(7L, "HOME", target, alwaysWins, rival));
        participants.addAll(match(6L, "HOME", target, alwaysWins, rival));
        participants.addAll(match(5L, "HOME", target, evenRecord, rival));
        participants.addAll(match(4L, "AWAY", target, evenRecord, rival));
        participants.addAll(match(3L, "HOME", target, mostlyLoses, rival));
        participants.addAll(match(2L, "AWAY", target, mostlyLoses, rival));
        participants.addAll(match(1L, "AWAY", target, mostlyLoses, rival));
        when(playerRepository.findByIdAndGroup_Id(1L, 1L)).thenReturn(Optional.of(target));
        when(matchParticipantRepository.findByGroupIdAndPlayerMatchesOrderByPlayedAtDesc(1L, 1L))
            .thenReturn(participants);

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
