package com.balancify.backend.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.balancify.backend.service.AccessControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class AdminRequestResolverTest {

    private static final String ADMIN_EMAIL = "admin@hei.gg";
    private static final String ADMIN_KEY = "test-admin-key";

    @Mock
    private AccessControlService accessControlService;

    private AdminRequestResolver adminRequestResolver;

    @BeforeEach
    void setUp() {
        AdminKeyProperties adminKeyProperties = new AdminKeyProperties();
        adminKeyProperties.setApiKey(ADMIN_KEY);
        adminRequestResolver = new AdminRequestResolver(accessControlService, adminKeyProperties);
    }

    @Test
    void returnsFalseWhenRequestIsNull() {
        assertFalse(adminRequestResolver.isAdminRequest(null));
    }

    @Test
    void returnsFalseWhenEmailIsNotAdmin() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-USER-EMAIL", "member@hei.gg");
        request.addHeader("X-ADMIN-KEY", ADMIN_KEY);

        when(accessControlService.isAdminEmail("member@hei.gg")).thenReturn(false);

        assertFalse(adminRequestResolver.isAdminRequest(request));
    }

    @Test
    void returnsFalseWhenAdminEmailButMissingAdminKey() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-USER-EMAIL", ADMIN_EMAIL);

        when(accessControlService.isAdminEmail(ADMIN_EMAIL)).thenReturn(true);

        assertFalse(adminRequestResolver.isAdminRequest(request));
    }

    @Test
    void returnsFalseWhenAdminEmailButInvalidAdminKey() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-USER-EMAIL", ADMIN_EMAIL);
        request.addHeader("X-ADMIN-KEY", "wrong-key");

        when(accessControlService.isAdminEmail(ADMIN_EMAIL)).thenReturn(true);

        assertFalse(adminRequestResolver.isAdminRequest(request));
    }

    @Test
    void returnsTrueWhenAdminEmailAndValidAdminKey() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-USER-EMAIL", ADMIN_EMAIL);
        request.addHeader("X-ADMIN-KEY", ADMIN_KEY);

        when(accessControlService.isAdminEmail(ADMIN_EMAIL)).thenReturn(true);

        assertTrue(adminRequestResolver.isAdminRequest(request));
    }
}
