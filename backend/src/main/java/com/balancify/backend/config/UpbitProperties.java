package com.balancify.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "balancify.integrations.upbit")
public class UpbitProperties {

    private boolean enabled;
    private String baseUrl = "https://api.upbit.com";
    private String accessKey = "";
    private String secretKey = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = safeTrim(baseUrl, "https://api.upbit.com");
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = safeTrim(accessKey, "");
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = safeTrim(secretKey, "");
    }

    public boolean hasCredentials() {
        return !accessKey.isBlank() && !secretKey.isBlank();
    }

    private String safeTrim(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
