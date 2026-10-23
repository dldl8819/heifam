package com.balancify.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.CannotCreateTransactionException;

class SentryFilterErrorReporterTest {

    private final List<Throwable> reported = new ArrayList<>();
    private final SentryFilterErrorReporter filter = new SentryFilterErrorReporter(reported::add);

    @Test
    void reportsAnErrorThrownInALaterFilterAndLetsItGoOn() {
        CannotCreateTransactionException databaseDown = new CannotCreateTransactionException("Could not open JPA EntityManager");

        assertThatThrownBy(() -> filter.doFilter(
            new MockHttpServletRequest("GET", "/api/groups/1/players"),
            new MockHttpServletResponse(),
            (request, response) -> {
                throw databaseDown;
            }
        )).isSameAs(databaseDown);
        assertThat(reported).containsExactly(databaseDown);
    }

    @Test
    void reportsTheCauseOfAServletException() {
        IllegalStateException cause = new IllegalStateException("broken");
        ServletException wrapped = new ServletException("Request processing failed", cause);

        assertThatThrownBy(() -> filter.doFilter(
            new MockHttpServletRequest("GET", "/api/notices"),
            new MockHttpServletResponse(),
            (request, response) -> {
                throw wrapped;
            }
        )).isSameAs(wrapped);
        assertThat(reported).containsExactly(cause);
    }

    @Test
    void leavesLostConnectionsAndAnsweredRequestsAlone() throws Exception {
        IOException lostConnection = new IOException("Broken pipe");

        assertThatThrownBy(() -> filter.doFilter(
            new MockHttpServletRequest("GET", "/api/notices"),
            new MockHttpServletResponse(),
            (request, response) -> {
                throw lostConnection;
            }
        )).isSameAs(lostConnection);
        filter.doFilter(new MockHttpServletRequest("GET", "/api/health"), new MockHttpServletResponse(), (request, response) -> { });
        assertThat(reported).isEmpty();
    }
}
