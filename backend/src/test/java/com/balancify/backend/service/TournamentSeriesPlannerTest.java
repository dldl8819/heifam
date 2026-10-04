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
    void assignsTheRaceTheCompositionNeeds() {
        assertThat(TournamentSeriesPlanner.assignRaces(List.of("P", "PT", "P"), "PPT")).containsExactly("P", "T", "P");
        assertThat(TournamentSeriesPlanner.assignRaces(List.of("P", "P", "P"), "PPT")).isNull();
    }
}
