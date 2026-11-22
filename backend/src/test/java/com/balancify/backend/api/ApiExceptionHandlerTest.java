package com.balancify.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

class ApiExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders
        .standaloneSetup(new FailingController())
        .setControllerAdvice(new ApiExceptionHandler())
        .build();

    @Test
    void answersADeadlockAsAConflictToTryAgain() throws Exception {
        // What production threw on 2026-11-22 when two results with the same players were saved at once.
        mockMvc.perform(post("/test/deadlock/4894"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.message").value(ApiExceptionHandler.LOCK_CONFLICT_MESSAGE))
            .andExpect(jsonPath("$.path").value("/test/deadlock/4894"));
    }

    @Test
    void answersALockTimeoutTheSameWay() throws Exception {
        mockMvc.perform(post("/test/lock-timeout"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(ApiExceptionHandler.LOCK_CONFLICT_MESSAGE));
    }

    @Test
    void leavesOtherDatabaseErrorsAsServerErrors() {
        // Not a lock: trying again would fail the same way, so it stays a 500 that Sentry reports.
        assertThatThrownBy(() -> mockMvc.perform(post("/test/integrity")))
            .hasRootCauseInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void keepsTheDatabaseDetailOutOfTheAnswer() {
        assertThat(ApiExceptionHandler.LOCK_CONFLICT_MESSAGE)
            .doesNotContain("deadlock")
            .doesNotContain("players");
    }

    @RestController
    static class FailingController {

        @PostMapping("/test/deadlock/{id}")
        void deadlock(@PathVariable("id") Long id) {
            throw new CannotAcquireLockException(
                "could not execute statement [ERROR: deadlock detected] [update players set mmr=? where id=? and version=?]",
                new SQLException("ERROR: deadlock detected", "40P01")
            );
        }

        @PostMapping("/test/lock-timeout")
        void lockTimeout() {
            throw new PessimisticLockingFailureException(
                "could not obtain lock",
                new SQLException("ERROR: canceling statement due to lock timeout", "55P03")
            );
        }

        @PostMapping("/test/integrity")
        void integrity() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
        }
    }
}
