package com.balancify.backend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "balancify.auth")
public class SupabaseAuthProperties {

    private boolean requireJwt = true;
    private boolean allowEmailHeaderFallback = false;
    private String supabaseUrl;
    private String apiKey;
    private int verifyTimeoutMs = 3000;
    private int verificationCacheTtlSeconds = 0;
    private String serviceRoleKey;

    public boolean isRequireJwt() {
        return requireJwt;
    }

    public void setRequireJwt(boolean requireJwt) {
        this.requireJwt = requireJwt;
    }

    public boolean isAllowEmailHeaderFallback() {
        return allowEmailHeaderFallback;
    }

    public void setAllowEmailHeaderFallback(boolean allowEmailHeaderFallback) {
        this.allowEmailHeaderFallback = allowEmailHeaderFallback;
    }

    public String getSupabaseUrl() {
        return supabaseUrl;
    }

    public void setSupabaseUrl(String supabaseUrl) {
        this.supabaseUrl = safeTrim(supabaseUrl);
    }

    public int getVerifyTimeoutMs() {
        return verifyTimeoutMs;
    }

    public void setVerifyTimeoutMs(int verifyTimeoutMs) {
        this.verifyTimeoutMs = Math.max(500, verifyTimeoutMs);
    }

    public int getVerificationCacheTtlSeconds() {
        return verificationCacheTtlSeconds;
    }

    public void setVerificationCacheTtlSeconds(int verificationCacheTtlSeconds) {
        this.verificationCacheTtlSeconds = 0;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = safeTrim(apiKey);
    }

    public String getServiceRoleKey() {
        return serviceRoleKey;
    }

    public void setServiceRoleKey(String serviceRoleKey) {
        this.serviceRoleKey = safeTrim(serviceRoleKey);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
