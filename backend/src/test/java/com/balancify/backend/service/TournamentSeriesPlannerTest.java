package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.balancify.backend.domain.MatchSeriesFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class TournamentSeriesPlannerTest {

    @Test
    void playsBestOfThreePppWhenNeitherTeamCanTakeTerranOrZerg() {
        TournamentSeriesPlanner.SeriesPlan plan = TournamentSeriesPlanner.plan(
            List.of("P", "P", "P"),
            List.of("P", "P", "P")
        );

        assertThat(plan.format()).isEqualTo(MatchSeriesFormat.BEST_OF_THREE);
        assertThat(plan.compositions()).containsExactly("PPP", "PPP", "PPP");
    }

    @Test
    void playsPppPptAndPpzWhenBothTeamsCanFieldThem() {
        TournamentSeriesPlanner.SeriesPlan plan = TournamentSeriesPlanner.plan(
            List.of("PT", "PZ", "P"),
            List.of("PTZ", "P", "P")
        );

        assertThat(plan.format()).isEqualTo(MatchSeriesFormat.MIXED_THREE);
        assertThat(plan.compositions()).containsExactly("PPP", "PPT", "PPZ");
    }

    @Test
    void turnsAGameOnlyOneTeamCanFieldIntoPpp() {
        TournamentSeriesPlanner.SeriesPlan plan = TournamentSeriesPlanner.plan(
            List.of("PT", "PZ", "P"),
            List.of("PT", "P", "P")
        );

        assertThat(plan.format()).isEqualTo(MatchSeriesFormat.MIXED_THREE);
        assertThat(plan.compositions()).containsExactly("PPP", "PPT", "PPP");
    }

    @Test
    void staysBestOfThreeWhenTheTeamsShareNoOffRaceGame() {
        TournamentSeriesPlanner.SeriesPlan plan = TournamentSeriesPlanner.plan(
            List.of("PT", "P", "P"),
            List.of("PZ", "P", "P")
        );

        assertThat(plan.format()).isEqualTo(MatchSeriesFormat.BEST_OF_THREE);
        assertThat(plan.compositions()).containsExactly("PPP", "PPP", "PPP");
    }

    @Test
    void usesASharedCompositionWhenATeamCannotPlayPpp() {
        TournamentSeriesPlanner.SeriesPlan plan = TournamentSeriesPlanner.plan(
            List.of("T", "P", "P"),
            List.of("PT", "P", "P")
        );

        assertThat(plan.compositions()).containsExactly("PPT", "PPT", "PPT");
    }

    @Test
    void refusesTeamsWithNoCompositionInCommon() {
        assertThatThrownBy(() -> TournamentSeriesPlanner.plan(List.of("T", "T", "T"), List.of("P", "P", "P")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void endsBestOfThreeAtTwoWinsAndMixedAfterThreeGames() {
        assertThat(TournamentSeriesPlanner.isDecided(MatchSeriesFormat.BEST_OF_THREE, 2, 0, 2)).isTrue();
        assertThat(TournamentSeriesPlanner.isDecided(MatchSeriesFormat.BEST_OF_THREE, 1, 1, 2)).isFalse();
        assertThat(TournamentSeriesPlanner.isDecided(MatchSeriesFormat.MIXED_THREE, 2, 0, 2)).isFalse();
        assertThat(TournamentSeriesPlanner.isDecided(MatchSeriesFormat.MIXED_THREE, 2, 1, 3)).isTrue();
    }

    @Test
    void endsAMultiBalanceSeriesAtTwoWinsWhateverItsFormat() {
        assertThat(TournamentSeriesPlanner.isDecidedAtTwoWins(2, 0, 2)).isTrue();
        assertThat(TournamentSeriesPlanner.isDecidedAtTwoWins(0, 2, 2)).isTrue();
        assertThat(TournamentSeriesPlanner.isDecidedAtTwoWins(1, 1, 2)).isFalse();
        assertThat(TournamentSeriesPlanner.isDecidedAtTwoWins(1, 0, 1)).isFalse();
        assertThat(TournamentSeriesPlanner.isDecidedAtTwoWins(2, 1, 3)).isTrue();
    }

    @Test
    void plansTwoPlayerTeamsWithPpPtAndPz() {
        TournamentSeriesPlanner.SeriesPlan mixed = TournamentSeriesPlanner.plan(List.of("PT", "PZ"), List.of("PTZ", "P"));
        TournamentSeriesPlanner.SeriesPlan protoss = TournamentSeriesPlanner.plan(List.of("P", "P"), List.of("PT", "P"));

        assertThat(mixed.format()).isEqualTo(MatchSeriesFormat.MIXED_THREE);
        assertThat(mixed.compositions()).containsExactly("PP", "PT", "PZ");
        assertThat(protoss.format()).isEqualTo(MatchSeriesFormat.BEST_OF_THREE);
        assertThat(protoss.compositions()).containsExactly("PP", "PP", "PP");
    }

    @Test
    void letsTeamsThatCouldMixPlayProtossBestOfThreeInstead() {
        List<String> home = List.of("PT", "PZ", "P");
        List<String> away = List.of("PTZ", "P", "P");

        TournamentSeriesPlanner.SeriesPlan protoss = TournamentSeriesPlanner.plan(home, away, MatchSeriesFormat.BEST_OF_THREE);
        TournamentSeriesPlanner.SeriesPlan mixed = TournamentSeriesPlanner.plan(home, away, MatchSeriesFormat.MIXED_THREE);

        assertThat(protoss.format()).isEqualTo(MatchSeriesFormat.BEST_OF_THREE);
        assertThat(protoss.compositions()).containsExactly("PPP", "PPP", "PPP");
        assertThat(mixed.compositions()).containsExactly("PPP", "PPT", "PPZ");
        assertThat(TournamentSeriesPlanner.plan(home, away, null).format()).isEqualTo(MatchSeriesFormat.MIXED_THREE);
        assertThatThrownBy(() -> TournamentSeriesPlanner.plan(
            List.of("P", "P", "P"),
            List.of("P", "P", "P"),
            MatchSeriesFormat.MIXED_THREE
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void countsTheTerranAndZergGamesBothTeamsCanField() {
        assertThat(TournamentSeriesPlanner.sharedOffRaceGames(List.of("PT", "PZ", "P"), List.of("PTZ", "P", "P"))).isEqualTo(2);
        assertThat(TournamentSeriesPlanner.sharedOffRaceGames(List.of("PT", "P", "P"), List.of("PT", "P", "P"))).isEqualTo(1);
        assertThat(TournamentSeriesPlanner.sharedOffRaceGames(List.of("PT", "PT", "P"), List.of("P", "P", "P"))).isZero();
        assertThatThrownBy(() -> TournamentSeriesPlanner.plan(List.of("P", "P", "P"), List.of("P", "P")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assignsTheRaceTheCompositionNeeds() {
        assertThat(TournamentSeriesPlanner.assignRaces(List.of("P", "PT", "P"), "PPT")).containsExactly("P", "T", "P");
        assertThat(TournamentSeriesPlanner.assignRaces(List.of("P", "P", "P"), "PPT")).isNull();
    }
}
