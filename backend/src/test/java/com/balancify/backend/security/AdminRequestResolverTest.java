package com.balancify.backend.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.balancify.backend.service.AccessControlService;
import com.balancify.backend.security.AuthenticatedRequestResolver.ResolvedRequestIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class AdminRequestResolverTest {

    private static final String ADMIN_EMAIL = "admin@hei.gg";

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private AuthenticatedRequestResolver authenticatedRequestResolver;

    private AdminRequestResolver adminRequestResolver;

    @BeforeEach
    void setUp() {
        adminRequestResolver = new AdminRequestResolver(accessControlService, authenticatedRequestResolver);
    }

    @Test
    void returnsFalseWhenRequestIsNull() {
        assertFalse(adminRequestResolver.isAdminRequest(null));
    }

    @Test
    void returnsFalseWhenEmailIsNotAdmin() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        when(authenticatedRequestResolver.resolve(request))
            .thenReturn(new ResolvedRequestIdentity("member@hei.gg", "", true));
        when(accessControlService.isAdminEmail("member@hei.gg")).thenReturn(false);

        assertFalse(adminRequestResolver.isAdminRequest(request));
    }

    @Test
    void returnsFalseWhenEmailIsMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        when(authenticatedRequestResolver.resolve(request))
            .thenReturn(ResolvedRequestIdentity.empty());

        assertFalse(adminRequestResolver.isAdminRequest(request));
    }

    @Test
    void returnsTrueWhenAdminEmailIsAuthenticated() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        when(authenticatedRequestResolver.resolve(request))
            .thenReturn(new ResolvedRequestIdentity(ADMIN_EMAIL, "", true));
        when(accessControlService.isAdminEmail(ADMIN_EMAIL)).thenReturn(true);

        assertTrue(adminRequestResolver.isAdminRequest(request));
    }
}
