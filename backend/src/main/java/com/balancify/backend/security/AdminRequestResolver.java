package com.balancify.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import com.balancify.backend.service.AccessControlService;
import org.springframework.stereotype.Component;

@Component
public class AdminRequestResolver {

    private final AccessControlService accessControlService;
    private final AuthenticatedRequestResolver authenticatedRequestResolver;

    public AdminRequestResolver(
        AccessControlService accessControlService,
        AuthenticatedRequestResolver authenticatedRequestResolver
    ) {
        this.accessControlService = accessControlService;
        this.authenticatedRequestResolver = authenticatedRequestResolver;
    }

    public boolean isAdminRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }

        AuthenticatedRequestResolver.ResolvedRequestIdentity identity = authenticatedRequestResolver.resolve(request);
        if (!identity.isAuthenticated()) {
            return false;
        }

        return accessControlService.isAdminEmail(identity.email());
    }
}
