package com.balancify.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.balancify.backend.service.NoticeImageService;
import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestBodyLimitFilterTest {

    private final RequestBodyLimitFilter filter = new RequestBodyLimitFilter();

    @Test
    void refusesABodyThatSaysItIsTooLargeBeforeReadingIt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/groups/1/boards/free/posts");
        request.setContent(new byte[(int) RequestBodyLimitFilter.MAX_BODY_BYTES + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger calls = new AtomicInteger();

        filter.doFilter(request, response, (req, res) -> calls.incrementAndGet());

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(calls).hasValue(0);
    }

    @Test
    void passesABodyWithinTheLimitWhole() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/groups/1/boards/free/posts");
        request.setContent("{\"title\":\"YOUR_TITLE\"}".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> read = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> read.set(new String(req.getInputStream().readAllBytes())));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(read.get()).isEqualTo("{\"title\":\"YOUR_TITLE\"}");
    }

    @Test
    void stopsReadingABodyThatDoesNotSayItsLengthAtTheLimitAndAnswers413() throws Exception {
        byte[] body = new byte[(int) RequestBodyLimitFilter.MAX_BODY_BYTES + 10];
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/groups/1/boards/free/posts") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent(body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain readsEverything = (req, res) -> req.getInputStream().readAllBytes();

        filter.doFilter(request, response, readsEverything);

        assertThat(response.getStatus()).isEqualTo(413);
        int leftUnread = request.getInputStream().readAllBytes().length;
        assertThat(body.length - leftUnread).isEqualTo((int) RequestBodyLimitFilter.MAX_BODY_BYTES + 1);
    }

    @Test
    void leavesTheErrorToWhoeverAlreadyAnswered() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/groups/1/boards/free/posts") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent(new byte[(int) RequestBodyLimitFilter.MAX_BODY_BYTES + 10]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain answersThenReads = (req, res) -> {
            res.flushBuffer();
            req.getInputStream().readAllBytes();
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, answersThenReads))
            .isInstanceOf(RequestBodyLimitFilter.BodyTooLargeException.class);
    }

    @Test
    void givesANoticeImageItsOwnLargerLimitAndNothingElse() {
        MockHttpServletRequest upload = new MockHttpServletRequest("POST", "/api/groups/1/notice-images");
        MockHttpServletRequest other = new MockHttpServletRequest("POST", "/api/groups/1/notices");
        MockHttpServletRequest read = new MockHttpServletRequest("GET", "/api/groups/1/notice-images");

        assertThat(RequestBodyLimitFilter.limitFor(upload)).isEqualTo(NoticeImageService.MAX_IMAGE_BYTES);
        assertThat(RequestBodyLimitFilter.limitFor(other)).isEqualTo(RequestBodyLimitFilter.MAX_BODY_BYTES);
        assertThat(RequestBodyLimitFilter.limitFor(read)).isEqualTo(RequestBodyLimitFilter.MAX_BODY_BYTES);
        assertThat(NoticeImageService.MAX_IMAGE_BYTES).isGreaterThan((int) RequestBodyLimitFilter.MAX_BODY_BYTES);
    }
}
