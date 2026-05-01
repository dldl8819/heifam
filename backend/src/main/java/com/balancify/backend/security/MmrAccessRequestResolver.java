package com.balancify.backend.security;

import com.balancify.backend.service.AccessControlService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class MmrAccessRequestResolver {

    private final AccessControlService accessControlService;
    private final AuthenticatedRequestResolver authenticatedRequestResolver;

    public MmrAccessRequestResolver(
        AccessControlService accessControlService,
        AuthenticatedRequestResolver authenticatedRequestResolver
    ) {
        this.accessControlService = accessControlService;
        this.authenticatedRequestResolver = authenticatedRequestResolver;
    }

    public boolean canViewMmr(HttpServletRequest request) {
        if (request == null) {
            return false;
        }

        AuthenticatedRequestResolver.ResolvedRequestIdentity identity = authenticatedRequestResolver.resolve(request);
        if (!identity.isAuthenticated()) {
            return false;
        }

        return accessControlService.canViewMmr(identity.email());
    }
}
