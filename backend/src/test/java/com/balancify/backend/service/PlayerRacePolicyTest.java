package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PlayerRacePolicyTest {

    @Test
    void ptPlayersCanFillTerranSlotsWhenNeeded() {
        PlayerRacePolicy.TeamRaceAssignment assignment = PlayerRacePolicy.assignToComposition(
            List.of("PT", "P", "P"),
            "PPT"
        );

        assertThat(assignment).isNotNull();
        assertThat(assignment.assignedRaces()).containsExactlyInAnyOrder("P", "P", "T");
    }

    @Test
    void ptzPlayerCanFillAnyRemainingSlot() {
        PlayerRacePolicy.TeamRaceAssignment assignment = PlayerRacePolicy.assignToComposition(
            List.of("PTZ", "P"),
            "PZ"
        );

        assertThat(assignment).isNotNull();
        assertThat(assignment.assignedRaces()).containsExactlyInAnyOrder("P", "Z");
    }

    @Test
    void returnsNullWhenCompositionCannotBeSatisfied() {
        PlayerRacePolicy.TeamRaceAssignment assignment = PlayerRacePolicy.assignToComposition(
            List.of("P", "P", "P"),
            "PPT"
        );

        assertThat(assignment).isNull();
    }
}
