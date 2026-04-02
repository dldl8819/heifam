package com.balancify.backend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "balancify.auth")
public class SupabaseAuthProperties {

    private boolean requireJwt = true;
    private boolean allowEmailHeaderFallback = false;
    private String supabaseUrl;
    private String supabaseApiKey;
    private int verifyTimeoutMs = 3000;
    private int verificationCacheTtlSeconds = 15;

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

    public String getSupabaseApiKey() {
        return supabaseApiKey;
    }

    public void setSupabaseApiKey(String supabaseApiKey) {
        this.supabaseApiKey = safeTrim(supabaseApiKey);
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
        this.verificationCacheTtlSeconds = Math.max(0, verificationCacheTtlSeconds);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}

