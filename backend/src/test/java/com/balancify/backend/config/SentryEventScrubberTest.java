package com.balancify.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.protocol.Message;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SentryEventScrubberTest {

    private final SentryEventScrubber scrubber = new SentryEventScrubber();

    @Test
    void keepsTheErrorAndThePathButNothingThatNamesAMember() {
        SentryEvent event = new SentryEvent();
        User user = new User();
        user.setEmail("YOUR_USERNAME@example.com");
        user.setIpAddress("{{auto}}");
        event.setUser(user);
        event.setServerName("YOUR_HOST");

        Request request = new Request();
        request.setMethod("POST");
        request.setUrl("https://YOUR_HOST/api/groups/1/boards/anonymous/posts?q=YOUR_SEARCH");
        request.setHeaders(Map.of("Authorization", "Bearer YOUR_API_KEY", "X-USER-EMAIL", "YOUR_USERNAME@example.com"));
        request.setCookies("session=YOUR_API_KEY");
        request.setQueryString("q=YOUR_SEARCH");
        request.setData("{\"content\":\"YOUR_TEXT\"}");
        request.setEnvs(Map.of("REMOTE_ADDR", "YOUR_IP"));
        event.setRequest(request);

        Message message = new Message();
        message.setMessage("No account for YOUR_USERNAME@example.com");
        message.setFormatted("No account for YOUR_USERNAME@example.com");
        message.setParams(List.of("YOUR_USERNAME@example.com"));
        event.setMessage(message);

        SentryException exception = new SentryException();
        exception.setType("IllegalStateException");
        exception.setValue("Cannot link YOUR_USERNAME@example.com to player 3");
        event.setExceptions(List.of(exception));

        Breadcrumb breadcrumb = new Breadcrumb();
        breadcrumb.setMessage("signed in as YOUR_USERNAME@example.com");
        breadcrumb.setData("url", "/api/boards/search?q=YOUR_SEARCH");
        event.addBreadcrumb(breadcrumb);

        SentryEvent sent = scrubber.execute(event, new Hint());

        assertThat(sent).isSameAs(event);
        assertThat(sent.getUser()).isNull();
        assertThat(sent.getServerName()).isNull();
        assertThat(sent.getRequest()).satisfies(kept -> {
            assertThat(kept.getMethod()).isEqualTo("POST");
            assertThat(kept.getUrl()).isEqualTo("https://YOUR_HOST/api/groups/1/boards/anonymous/posts");
            assertThat(kept.getHeaders()).isNull();
            assertThat(kept.getCookies()).isNull();
            assertThat(kept.getQueryString()).isNull();
            assertThat(kept.getData()).isNull();
            assertThat(kept.getEnvs()).isNull();
        });
        assertThat(sent.getMessage().getMessage()).isEqualTo("No account for [email]");
        assertThat(sent.getMessage().getFormatted()).isEqualTo("No account for [email]");
        assertThat(sent.getMessage().getParams()).isNull();
        assertThat(sent.getExceptions().get(0).getValue()).isEqualTo("Cannot link [email] to player 3");
        assertThat(sent.getExceptions().get(0).getType()).isEqualTo("IllegalStateException");
        assertThat(sent.getBreadcrumbs().get(0).getMessage()).isEqualTo("signed in as [email]");
        assertThat(sent.getBreadcrumbs().get(0).getData("url")).isEqualTo("/api/boards/search");
    }

    @Test
    void leavesAnEventWithoutRequestOrTextAsItIs() {
        SentryEvent event = new SentryEvent();

        assertThat(scrubber.execute(event, new Hint())).isSameAs(event);
        assertThat(SentryEventScrubber.mask(null)).isNull();
        assertThat(SentryEventScrubber.mask("Round 2 set 1 already has a result")).isEqualTo("Round 2 set 1 already has a result");
    }
}
