package com.balancify.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import com.balancify.backend.service.AccessControlService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

@Component
public class AdminRequestResolver {

    private static final String ADMIN_HEADER = "X-ADMIN-KEY";
    private static final String USER_EMAIL_HEADER = "X-USER-EMAIL";

    private final AccessControlService accessControlService;
    private final AdminKeyProperties adminKeyProperties;

    public AdminRequestResolver(
        AccessControlService accessControlService,
        AdminKeyProperties adminKeyProperties
    ) {
        this.accessControlService = accessControlService;
        this.adminKeyProperties = adminKeyProperties;
    }

    public boolean isAdminRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }

        String requestEmail = safeTrim(request.getHeader(USER_EMAIL_HEADER));
        if (!accessControlService.isAdminEmail(requestEmail)) {
            return false;
        }

        String configuredAdminKey = safeTrim(adminKeyProperties.getApiKey());
        String requestAdminKey = safeTrim(request.getHeader(ADMIN_HEADER));
        return isValidKey(configuredAdminKey, requestAdminKey);
    }

    private boolean isValidKey(String configuredKey, String requestKey) {
        if (configuredKey.isEmpty() || requestKey.isEmpty()) {
            return false;
        }

        return MessageDigest.isEqual(
            configuredKey.getBytes(StandardCharsets.UTF_8),
            requestKey.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
