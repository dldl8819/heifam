package com.balancify.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import com.balancify.backend.service.AccessControlService;
import org.springframework.stereotype.Component;

@Component
public class AdminRequestResolver {

    private static final String USER_EMAIL_HEADER = "X-USER-EMAIL";

    private final AccessControlService accessControlService;

    public AdminRequestResolver(AccessControlService accessControlService) {
        this.accessControlService = accessControlService;
    }

    public boolean isAdminRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }

        String requestEmail = safeTrim(request.getHeader(USER_EMAIL_HEADER));
        return accessControlService.isAdminEmail(requestEmail);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
