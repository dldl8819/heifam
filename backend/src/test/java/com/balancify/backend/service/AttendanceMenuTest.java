package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AttendanceMenuTest {

    @Test
    void namesTheTenMenusByTheKeysThePagesSend() {
        assertThat(Arrays.stream(AttendanceMenu.values()).map(AttendanceMenu::key)).containsExactly(
            "notices", "free-board", "anonymous-board", "video-board",
            "players", "ranking", "predictions", "partners", "results", "point-policy"
        );
        for (AttendanceMenu menu : AttendanceMenu.values()) {
            assertThat(AttendanceMenu.fromKey(menu.key())).contains(menu);
        }
    }

    @Test
    void readsAKeyHoweverItIsWrittenAndNothingElse() {
        assertThat(AttendanceMenu.fromKey(" Free-Board ")).contains(AttendanceMenu.FREE_BOARD);
        assertThat(AttendanceMenu.fromKey("points")).isEmpty();
        assertThat(AttendanceMenu.fromKey("FREE_BOARD")).isEmpty();
        assertThat(AttendanceMenu.fromKey("")).isEmpty();
        assertThat(AttendanceMenu.fromKey(null)).isEmpty();
    }
}
