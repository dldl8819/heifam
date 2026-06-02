package com.balancify.backend.api.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.balancify.backend.security.AdminRequestResolver;
import com.balancify.backend.security.AuthenticatedRequestResolver;
import com.balancify.backend.service.AccessControlService;
import com.balancify.backend.service.DashboardQueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class GroupDashboardControllerTest {

    @Test
    void rejectsDashboardBeforeResolvingIdentityWhenTemporarilyDisabled() {
        DashboardQueryService dashboardQueryService = mock(DashboardQueryService.class);
        AdminRequestResolver adminRequestResolver = mock(AdminRequestResolver.class);
        AccessControlService accessControlService = mock(AccessControlService.class);
        AuthenticatedRequestResolver authenticatedRequestResolver = mock(AuthenticatedRequestResolver.class);
        GroupDashboardController controller = new GroupDashboardController(
            dashboardQueryService,
            adminRequestResolver,
            accessControlService,
            authenticatedRequestResolver,
            false
        );

        assertThatThrownBy(() -> controller.getGroupDashboard(1L, mock(HttpServletRequest.class)))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
            );

        verifyNoInteractions(
            dashboardQueryService,
            adminRequestResolver,
            accessControlService,
            authenticatedRequestResolver
        );
    }
}
