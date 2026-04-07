package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @Mock
    private AccessControlService accessControlService;

    private MatchQueryService matchQueryService;

    @BeforeEach
    void setUp() {
        matchQueryService = new MatchQueryService(matchRepository, matchParticipantRepository, accessControlService);
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
        match.setResultRecordedByEmail("kim@hei.gg");
        match.setResultRecordedByNickname("김원섭");
        match.setRaceComposition("PTZ");

        Player alpha = player(1L, group, "알파", 900);
        Player bravo = player(2L, group, "브라보", 850);

        MatchParticipant homeParticipant = participant(match, alpha, "HOME", 880);
        MatchParticipant awayParticipant = participant(match, bravo, "AWAY", 830);

        when(matchRepository.findRecentByGroupId(eq(1L), any())).thenReturn(List.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(200L))
            .thenReturn(List.of(homeParticipant, awayParticipant));
        when(accessControlService.resolveAccessProfile(eq("kim@hei.gg")))
            .thenReturn(
                new AccessControlService.AccessProfile(
                    "kim@hei.gg",
                    "민식",
                    "MEMBER",
                    false,
                    false,
                    true,
                    null
                )
            );

        List<GroupRecentMatchResponse> responses = matchQueryService.getRecentMatches(1L, 10);

        assertThat(responses).hasSize(1);
        GroupRecentMatchResponse response = responses.get(0);
        assertThat(response.matchId()).isEqualTo(200L);
        assertThat(response.winningTeam()).isEqualTo("HOME");
        assertThat(response.homeTeam()).hasSize(1);
        assertThat(response.awayTeam()).hasSize(1);
        assertThat(response.resultRecordedByNickname()).isEqualTo("민식");
        assertThat(response.homeRaceComposition()).isEqualTo("PTZ");
        assertThat(response.awayRaceComposition()).isEqualTo("PTZ");
        assertThat(response.homeMmr()).isEqualTo(880);
        assertThat(response.awayMmr()).isEqualTo(830);
        assertThat(response.mmrDiff()).isEqualTo(50);
    }

    @Test
    void derivesTeamRaceCompositionFromAssignedRacesWhenStoredCompositionIsMissing() {
        Group group = new Group();
        group.setId(1L);

        Match match = new Match();
        match.setId(203L);
        match.setGroup(group);
        match.setWinningTeam("HOME");
        match.setPlayedAt(OffsetDateTime.parse("2026-03-23T11:00:00Z"));
        match.setResultRecordedByEmail("kim@hei.gg");

        Player homeOne = player(7L, group, "용이", 800);
        Player homeTwo = player(8L, group, "헌터", 780);
        Player awayOne = player(9L, group, "보이", 900);
        Player awayTwo = player(10L, group, "잡종", 760);

        MatchParticipant homeP = participant(match, homeOne, "HOME", 805);
        homeP.setAssignedRace("P");
        MatchParticipant homeT = participant(match, homeTwo, "HOME", 775);
        homeT.setAssignedRace("T");
        MatchParticipant awayP = participant(match, awayOne, "AWAY", 905);
        awayP.setAssignedRace("P");
        MatchParticipant awayT = participant(match, awayTwo, "AWAY", 755);
        awayT.setAssignedRace("T");

        when(matchRepository.findRecentByGroupId(eq(1L), any())).thenReturn(List.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(203L))
            .thenReturn(List.of(homeP, homeT, awayP, awayT));
        when(accessControlService.resolveAccessProfile(eq("kim@hei.gg")))
            .thenReturn(
                new AccessControlService.AccessProfile(
                    "kim@hei.gg",
                    "민식",
                    "MEMBER",
                    false,
                    false,
                    true,
                    null
                )
            );

        List<GroupRecentMatchResponse> responses = matchQueryService.getRecentMatches(1L, 10);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).homeRaceComposition()).isEqualTo("PT");
        assertThat(responses.get(0).awayRaceComposition()).isEqualTo("PT");
    }

    @Test
    void hidesLegacyRecordedNameWhenNicknameCannotBeResolvedFromEmail() {
        Group group = new Group();
        group.setId(1L);

        Match match = new Match();
        match.setId(201L);
        match.setGroup(group);
        match.setWinningTeam("AWAY");
        match.setPlayedAt(OffsetDateTime.parse("2026-03-23T09:00:00Z"));
        match.setResultRecordedByEmail("legacy@hei.gg");
        match.setResultRecordedByNickname("김원섭");

        Player alpha = player(3L, group, "소울", 600);
        Player bravo = player(4L, group, "보이", 900);

        when(matchRepository.findRecentByGroupId(eq(1L), any())).thenReturn(List.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(201L))
            .thenReturn(List.of(
                participant(match, alpha, "HOME", 610),
                participant(match, bravo, "AWAY", 880)
            ));
        when(accessControlService.resolveAccessProfile(eq("legacy@hei.gg")))
            .thenReturn(
                new AccessControlService.AccessProfile(
                    "legacy@hei.gg",
                    null,
                    "BLOCKED",
                    false,
                    false,
                    false,
                    null
                )
            );

        List<GroupRecentMatchResponse> responses = matchQueryService.getRecentMatches(1L, 10);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).resultRecordedByNickname()).isNull();
    }

    @Test
    void hidesRecordedNameWhenRecordedEmailIsMissing() {
        Group group = new Group();
        group.setId(1L);

        Match match = new Match();
        match.setId(202L);
        match.setGroup(group);
        match.setWinningTeam("HOME");
        match.setPlayedAt(OffsetDateTime.parse("2026-03-23T10:00:00Z"));
        match.setResultRecordedByNickname("이민식");

        Player alpha = player(5L, group, "아크", 400);
        Player bravo = player(6L, group, "웃음", 500);

        when(matchRepository.findRecentByGroupId(eq(1L), any())).thenReturn(List.of(match));
        when(matchParticipantRepository.findByMatchIdWithPlayerAndMatch(202L))
            .thenReturn(List.of(
                participant(match, alpha, "HOME", 405),
                participant(match, bravo, "AWAY", 495)
            ));

        List<GroupRecentMatchResponse> responses = matchQueryService.getRecentMatches(1L, 10);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).resultRecordedByNickname()).isNull();
        verify(accessControlService, never()).resolveAccessProfile(any());
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
