package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.balancify.backend.service.TournamentTeamFormation.Candidate;
import com.balancify.backend.service.TournamentTeamFormation.Formation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TournamentTeamFormationTest {

    @Test
    void makesTwoToFourTeamsFromSixToFourteenPlayers() {
        assertThat(TournamentTeamFormation.teamCountFor(6)).isEqualTo(2);
        assertThat(TournamentTeamFormation.teamCountFor(8)).isEqualTo(2);
        assertThat(TournamentTeamFormation.teamCountFor(9)).isEqualTo(3);
        assertThat(TournamentTeamFormation.teamCountFor(11)).isEqualTo(3);
        assertThat(TournamentTeamFormation.teamCountFor(12)).isEqualTo(4);
        assertThat(TournamentTeamFormation.teamCountFor(14)).isEqualTo(4);
        for (int unsupported : new int[] {5, 15}) {
            assertThatThrownBy(() -> TournamentTeamFormation.teamCountFor(unsupported))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void findsTheClosestTeamTotals() {
        Formation formation = TournamentTeamFormation.form(
            List.of(
                player(1, 1600, "P"), player(2, 1500, "P"), player(3, 1400, "P"),
                player(4, 1300, "P"), player(5, 1200, "P"), player(6, 1100, "P")
            ),
            Set.of()
        );

        assertThat(formation.teams()).hasSize(2);
        assertThat(formation.waiting()).isEmpty();
        long gap = Math.abs(
            TournamentTeamFormation.totalMmr(formation.teams().get(0))
                - TournamentTeamFormation.totalMmr(formation.teams().get(1))
        );
        assertThat(gap).isEqualTo(100);
    }

    @Test
    void comparesEverySplitOfTwelvePlayers() {
        List<Candidate> players = new ArrayList<>();
        long id = 1;
        for (int mmr : new int[] {1000, 1100, 1200}) {
            for (int copy = 0; copy < 4; copy++) {
                players.add(player(id++, mmr, "P"));
            }
        }

        Formation formation = TournamentTeamFormation.form(players, Set.of());

        assertThat(formation.teams()).hasSize(4);
        assertThat(formation.teams()).allSatisfy(team -> {
            assertThat(team).hasSize(3);
            assertThat(TournamentTeamFormation.totalMmr(team)).isEqualTo(3300);
        });
    }

    @Test
    void spreadsPlayersWhoCanTakeTerranOrZergAcrossTeams() {
        Formation formation = TournamentTeamFormation.form(
            List.of(
                player(1, 1000, "PT"), player(2, 1000, "PZ"), player(3, 1000, "P"),
                player(4, 1000, "P"), player(5, 1000, "P"), player(6, 1000, "P")
            ),
            Set.of()
        );

        assertThat(formation.teams()).allSatisfy(team ->
            assertThat(team.stream().filter(player -> !player.capability().equals("P")).count()).isEqualTo(1)
        );
    }

    @Test
    void placesWhoeverSatOutLastTime() {
        List<Candidate> players = List.of(
            player(1, 1500, "P"), player(2, 1500, "P"), player(3, 1500, "P"), player(4, 1500, "P"),
            player(5, 1500, "P"), player(6, 1500, "P"), player(7, 100, "P"), player(8, 3000, "P")
        );

        Formation formation = TournamentTeamFormation.form(players, Set.of(7L, 8L));

        assertThat(formation.waiting()).hasSize(2);
        assertThat(formation.waiting()).extracting(Candidate::playerId).doesNotContain(7L, 8L);
        assertThat(formation.teams().stream().flatMap(List::stream).map(Candidate::playerId)).contains(7L, 8L);
    }

    @Test
    void leavesTwoOfFourteenWaiting() {
        List<Candidate> players = new ArrayList<>();
        for (long id = 1; id <= 14; id++) {
            players.add(player(id, 900 + (int) id * 10, id % 4 == 0 ? "PT" : "P"));
        }

        Formation formation = TournamentTeamFormation.form(players, Set.of());

        assertThat(formation.teams()).hasSize(4).allSatisfy(team -> assertThat(team).hasSize(3));
        assertThat(formation.waiting()).hasSize(2);
    }

    @Test
    void pairsAPlayerWhoCannotPlayProtossWithATeamThatCanMeetIt() {
        Formation formation = TournamentTeamFormation.form(
            List.of(
                player(1, 1000, "T"), player(2, 1000, "PT"), player(3, 1000, "P"),
                player(4, 1000, "P"), player(5, 1000, "P"), player(6, 1000, "P")
            ),
            Set.of()
        );

        assertThat(formation.teams()).allSatisfy(team ->
            assertThat(team.stream().filter(player -> player.capability().contains("T")).count()).isEqualTo(1)
        );
    }

    @Test
    void refusesPlayersNoTwoTeamsCouldPlayTogether() {
        List<Candidate> players = List.of(
            player(1, 1000, "T"), player(2, 1000, "T"), player(3, 1000, "T"),
            player(4, 1000, "P"), player(5, 1000, "P"), player(6, 1000, "P")
        );

        assertThatThrownBy(() -> TournamentTeamFormation.form(players, Set.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void makesThreeTeamsFromTenAndLeavesOneWaiting() {
        List<Candidate> players = new ArrayList<>();
        for (long id = 1; id <= 10; id++) {
            players.add(player(id, 1000 + (int) id * 20, id == 3 ? "PT" : "P"));
        }

        Formation formation = TournamentTeamFormation.form(players, Set.of());

        assertThat(formation.teams()).hasSize(3).allSatisfy(team -> assertThat(team).hasSize(3));
        assertThat(formation.waiting()).hasSize(1);
    }

    @Test
    void sendsTheClosestPairOfThreeTeamsToTheSemifinal() {
        List<Candidate> first = team(1, 3300);
        List<Candidate> second = team(4, 3400);
        List<Candidate> third = team(7, 3310);

        List<List<Candidate>> ordered = TournamentTeamFormation.bracketOrder(List.of(first, second, third));

        assertThat(ordered).containsExactly(first, third, second);
    }

    @Test
    void pairsTheClosestTeamsInTheSemifinals() {
        List<Candidate> first = team(1, 3300);
        List<Candidate> second = team(4, 3310);
        List<Candidate> third = team(7, 3290);
        List<Candidate> fourth = team(10, 3320);

        List<List<Candidate>> ordered = TournamentTeamFormation.bracketOrder(List.of(first, second, third, fourth));

        assertThat(ordered).containsExactly(first, third, second, fourth);
    }

    private static List<Candidate> team(long firstId, int total) {
        return List.of(player(firstId, total - 2000, "P"), player(firstId + 1, 1000, "P"), player(firstId + 2, 1000, "P"));
    }

    private static Candidate player(long id, int mmr, String capability) {
        return new Candidate(id, mmr, capability);
    }
}
