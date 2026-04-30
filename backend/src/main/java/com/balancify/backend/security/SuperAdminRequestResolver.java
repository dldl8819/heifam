package com.balancify.backend.security;

import com.balancify.backend.service.AccessControlService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class SuperAdminRequestResolver {

    private final AccessControlService accessControlService;
    private final AuthenticatedRequestResolver authenticatedRequestResolver;

    public SuperAdminRequestResolver(
        AccessControlService accessControlService,
        AuthenticatedRequestResolver authenticatedRequestResolver
    ) {
        this.accessControlService = accessControlService;
        this.authenticatedRequestResolver = authenticatedRequestResolver;
    }

    public boolean isSuperAdminRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }

        AuthenticatedRequestResolver.ResolvedRequestIdentity identity = authenticatedRequestResolver.resolve(request);
        if (!identity.isAuthenticated()) {
            return false;
        }

        return accessControlService.isSuperAdminEmail(identity.email());
    }
}
