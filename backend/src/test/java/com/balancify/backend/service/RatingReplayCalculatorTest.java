package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.balancify.backend.domain.Group;
import com.balancify.backend.domain.Match;
import com.balancify.backend.domain.MatchParticipant;
import com.balancify.backend.domain.MatchSource;
import com.balancify.backend.domain.MatchStatus;
import com.balancify.backend.domain.Player;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RatingReplayCalculatorTest {

    @Test
    void replayIsDeterministicAndUsesChronologicalOrder() {
        RatingReplayCalculator calculator = new RatingReplayCalculator(24, 300, 900, 0.6, 0.7);
        ReplayFixture fixture = standardFixture();

        RatingReplayPlan chronologicalPlan = calculator.calculate(
            fixture.players(),
            List.of(fixture.match1(), fixture.match2()),
            fixture.participantsByMatchId()
        );
        RatingReplayPlan reversedInputPlan = calculator.calculate(
            fixture.players(),
            List.of(fixture.match2(), fixture.match1()),
            fixture.participantsByMatchId()
        );
        RatingReplayPlan repeatedPlan = calculator.calculate(
            fixture.players(),
            List.of(fixture.match2(), fixture.match1()),
            fixture.participantsByMatchId()
        );

        assertThat(finalMmrByPlayerId(chronologicalPlan)).isEqualTo(finalMmrByPlayerId(reversedInputPlan));
        assertThat(participantDeltaById(chronologicalPlan)).isEqualTo(participantDeltaById(repeatedPlan));
    }

    @Test
    void loweringKFactorChangesReplayDeltas() {
        ReplayFixture fixture = standardFixture();

        RatingReplayCalculator legacyCalculator = new RatingReplayCalculator(32, 300, 900, 0.6, 0.7);
        RatingReplayCalculator loweredCalculator = new RatingReplayCalculator(24, 300, 900, 0.6, 0.7);

        RatingReplayPlan legacyPlan = legacyCalculator.calculate(
            fixture.players(),
            List.of(fixture.match1()),
            fixture.participantsByMatchId()
        );
        RatingReplayPlan loweredPlan = loweredCalculator.calculate(
            fixture.players(),
            List.of(fixture.match1()),
            fixture.participantsByMatchId()
        );

        int legacyHomeWinnerDelta = deltaForPlayer(legacyPlan, fixture.match1().getId(), 1L);
        int loweredHomeWinnerDelta = deltaForPlayer(loweredPlan, fixture.match1().getId(), 1L);

        assertThat(Math.abs(loweredHomeWinnerDelta)).isLessThan(Math.abs(legacyHomeWinnerDelta));
    }

    @Test
    void usesBaseMmrAsReplaySeed() {
        RatingReplayCalculator calculator = new RatingReplayCalculator(24, 300, 900, 0.6, 0.7);
        ReplayFixture fixture = standardFixture();

        fixture.players().stream()
            .filter(player -> player.getId().equals(1L))
            .findFirst()
            .orElseThrow()
            .setMmr(1500);

        RatingReplayPlan plan = calculator.calculate(
            fixture.players(),
            List.of(fixture.match1()),
            fixture.participantsByMatchId()
        );

        RatingReplayPlan.ParticipantResult participant = plan.participants().stream()
            .filter(result -> result.matchId().equals(fixture.match1().getId()))
            .filter(result -> result.playerId().equals(1L))
            .findFirst()
            .orElseThrow();

        assertThat(participant.beforeMmr()).isEqualTo(1200);
    }

    private Map<Long, Integer> finalMmrByPlayerId(RatingReplayPlan plan) {
        Map<Long, Integer> values = new LinkedHashMap<>();
        for (RatingReplayPlan.PlayerResult player : plan.players()) {
            values.put(player.playerId(), player.finalMmr());
        }
        return values;
    }

    private Map<Long, Integer> participantDeltaById(RatingReplayPlan plan) {
        Map<Long, Integer> values = new LinkedHashMap<>();
        for (RatingReplayPlan.ParticipantResult participant : plan.participants()) {
            values.put(participant.participantId(), participant.delta());
        }
        return values;
    }

    private int deltaForPlayer(RatingReplayPlan plan, Long matchId, Long playerId) {
        return plan.participants().stream()
            .filter(result -> result.matchId().equals(matchId))
            .filter(result -> result.playerId().equals(playerId))
            .findFirst()
            .orElseThrow()
            .delta();
    }

    private ReplayFixture standardFixture() {
        Group group = new Group();
        group.setId(1L);

        Player h1 = player(1L, group, "H1", 1200);
        Player h2 = player(2L, group, "H2", 1100);
        Player h3 = player(3L, group, "H3", 1000);
        Player a1 = player(4L, group, "A1", 1000);
        Player a2 = player(5L, group, "A2", 950);
        Player a3 = player(6L, group, "A3", 900);

        Match match1 = match(101L, "HOME", OffsetDateTime.parse("2026-04-01T10:00:00Z"));
        Match match2 = match(102L, "AWAY", OffsetDateTime.parse("2026-04-02T10:00:00Z"));

        Map<Long, List<MatchParticipant>> participantsByMatchId = new LinkedHashMap<>();
        participantsByMatchId.put(match1.getId(), List.of(
            participant(1001L, match1, h1, "HOME"),
            participant(1002L, match1, h2, "HOME"),
            participant(1003L, match1, h3, "HOME"),
            participant(1004L, match1, a1, "AWAY"),
            participant(1005L, match1, a2, "AWAY"),
            participant(1006L, match1, a3, "AWAY")
        ));
        participantsByMatchId.put(match2.getId(), List.of(
            participant(1007L, match2, h1, "HOME"),
            participant(1008L, match2, h2, "HOME"),
            participant(1009L, match2, h3, "HOME"),
            participant(1010L, match2, a1, "AWAY"),
            participant(1011L, match2, a2, "AWAY"),
            participant(1012L, match2, a3, "AWAY")
        ));

        return new ReplayFixture(
            List.of(h1, h2, h3, a1, a2, a3),
            match1,
            match2,
            participantsByMatchId
        );
    }

    private Match match(Long id, String winnerTeam, OffsetDateTime playedAt) {
        Match match = new Match();
        match.setId(id);
        match.setPlayedAt(playedAt);
        match.setWinningTeam(winnerTeam);
        match.setStatus(MatchStatus.COMPLETED);
        match.setSource(MatchSource.BALANCED);
        match.setTeamSize(3);
        match.setResultRecordedAt(playedAt.plusMinutes(5));
        return match;
    }

    private Player player(Long id, Group group, String nickname, int baseMmr) {
        Player player = new Player();
        player.setId(id);
        player.setGroup(group);
        player.setNickname(nickname);
        player.setBaseMmr(baseMmr);
        player.setMmr(baseMmr);
        return player;
    }

    private MatchParticipant participant(Long id, Match match, Player player, String team) {
        MatchParticipant participant = new MatchParticipant();
        participant.setId(id);
        participant.setMatch(match);
        participant.setPlayer(player);
        participant.setTeam(team);
        participant.setMmrBefore(player.getMmr());
        participant.setMmrAfter(player.getMmr());
        participant.setMmrDelta(0);
        return participant;
    }

    private record ReplayFixture(
        List<Player> players,
        Match match1,
        Match match2,
        Map<Long, List<MatchParticipant>> participantsByMatchId
    ) {
    }
}
