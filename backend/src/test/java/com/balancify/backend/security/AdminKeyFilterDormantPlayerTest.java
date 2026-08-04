package com.balancify.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.balancify.backend.service.AccessControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AdminKeyFilterDormantPlayerTest {

    private static final String ADMIN_PLACEHOLDER = "YOUR_ADMIN_USERNAME";
    private static final String SUPER_ADMIN_PLACEHOLDER = "YOUR_SUPER_ADMIN_USERNAME";
    private static final String MEMBER_PLACEHOLDER = "YOUR_MEMBER_USERNAME";

    private final AccessControlService accessControlService = mock(AccessControlService.class);
    private final AuthenticatedRequestResolver authenticatedRequestResolver =
        mock(AuthenticatedRequestResolver.class);

    private AdminKeyFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AdminKeyFilter(accessControlService, authenticatedRequestResolver);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/groups/1/players/dormant",
        "/api/groups/1/players/11/last-participation"
    })
    void allowsAdminForDormancyRoutes(String path) throws Exception {
        assertAllowed(path, ADMIN_PLACEHOLDER);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/groups/1/players/dormant",
        "/api/groups/1/players/11/last-participation"
    })
    void allowsSuperAdminForDormancyRoutes(String path) throws Exception {
        assertAllowed(path, SUPER_ADMIN_PLACEHOLDER);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/groups/1/players/dormant",
        "/api/groups/1/players/11/last-participation"
    })
    void rejectsMemberForDormancyRoutes(String path) throws Exception {
        MockHttpServletRequest request = request(path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(authenticatedRequestResolver.resolve(request)).thenReturn(
            new AuthenticatedRequestResolver.ResolvedRequestIdentity(
                MEMBER_PLACEHOLDER,
                "",
                true
            )
        );
        when(accessControlService.isAdminEmail(MEMBER_PLACEHOLDER)).thenReturn(false);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    private void assertAllowed(String path, String requester) throws Exception {
        MockHttpServletRequest request = request(path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(authenticatedRequestResolver.resolve(request)).thenReturn(
            new AuthenticatedRequestResolver.ResolvedRequestIdentity(requester, "", true)
        );
        when(accessControlService.isAdminEmail(requester)).thenReturn(true);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    private MockHttpServletRequest request(String path) {
        return new MockHttpServletRequest("GET", path);
    }
}
