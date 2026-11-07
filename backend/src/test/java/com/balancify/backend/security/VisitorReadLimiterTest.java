package com.balancify.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancify.backend.api.group.GroupMatchController;
import com.balancify.backend.api.group.GroupNoticeController;
import com.balancify.backend.security.AuthenticatedRequestResolver.ResolvedRequestIdentity;
import com.balancify.backend.service.AccessControlService;
import com.balancify.backend.service.MatchQueryService;
import com.balancify.backend.service.NoticeService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class VisitorReadLimiterTest {

    private static final String MEMBER = "member@example.com";

    private long now = 1_000_000L;
    private final MockHttpServletRequest visitorRequest = new MockHttpServletRequest();
    private final MockHttpServletRequest memberRequest = new MockHttpServletRequest();
    private AuthenticatedRequestResolver authenticatedRequestResolver;
    private AccessControlService accessControlService;

    @BeforeEach
    void setUp() {
        authenticatedRequestResolver = mock(AuthenticatedRequestResolver.class);
        accessControlService = mock(AccessControlService.class);
        when(authenticatedRequestResolver.resolve(visitorRequest)).thenReturn(ResolvedRequestIdentity.empty());
        when(authenticatedRequestResolver.resolve(memberRequest)).thenReturn(new ResolvedRequestIdentity(MEMBER, "", true));
        when(accessControlService.isServiceAccessAllowed(MEMBER)).thenReturn(true);
    }

    @Test
    void allowsTheMinutesAnswersThenRefusesUntilTheNextMinute() {
        VisitorReadLimiter limiter = new VisitorReadLimiter(3, () -> now);

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
        assertThatThrownBy(limiter::requireAllowance)
            .isInstanceOfSatisfying(ResponseStatusException.class, refused ->
                assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
            );

        now += VisitorReadLimiter.WINDOW_MS - 1;
        assertThat(limiter.tryAcquire()).isFalse();
        now += 1;
        assertThat(limiter.tryAcquire()).isTrue();
    }

    @Test
    void countsOnlyVisitorsAskingForTheRecentResults() {
        MatchQueryService matchQueryService = mock(MatchQueryService.class);
        MmrAccessRequestResolver mmrAccessRequestResolver = mock(MmrAccessRequestResolver.class);
        when(matchQueryService.getVisitorRecentMatches(any(), any(), any())).thenReturn(List.of());
        when(matchQueryService.getRecentMatches(any(), any(), any(), any())).thenReturn(List.of());
        GroupMatchController controller = new GroupMatchController(
            matchQueryService,
            mmrAccessRequestResolver,
            authenticatedRequestResolver,
            accessControlService,
            new VisitorReadLimiter(1, () -> now)
        );

        assertThat(controller.getRecentMatches(1L, 10, 0, visitorRequest)).isEmpty();
        assertThatThrownBy(() -> controller.getRecentMatches(1L, 10, 0, visitorRequest))
            .isInstanceOfSatisfying(ResponseStatusException.class, refused ->
                assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
            );
        for (int ask = 0; ask < 3; ask++) {
            assertThat(controller.getRecentMatches(1L, 10, 0, memberRequest)).isEmpty();
        }
        verify(matchQueryService, times(1)).getVisitorRecentMatches(1L, 10, 0);
        verify(matchQueryService, times(3)).getRecentMatches(1L, 10, 0, MEMBER);
    }

    @Test
    void countsOnlyVisitorsAskingForTheNoticeTitles() {
        NoticeService noticeService = mock(NoticeService.class);
        when(noticeService.listTitles(1L)).thenReturn(List.of());
        GroupNoticeController controller = new GroupNoticeController(
            noticeService,
            authenticatedRequestResolver,
            accessControlService,
            new VisitorReadLimiter(1, () -> now)
        );

        assertThat(controller.getNoticeTitles(1L, visitorRequest)).isEmpty();
        assertThatThrownBy(() -> controller.getNoticeTitles(1L, visitorRequest))
            .isInstanceOfSatisfying(ResponseStatusException.class, refused ->
                assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
            );
        assertThat(controller.getNoticeTitles(1L, memberRequest)).isEmpty();
        verify(noticeService, times(2)).listTitles(1L);
    }
}
