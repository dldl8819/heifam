package com.balancify.backend.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "balancify.security.jwt")
public class JwtProperties {

    private boolean enabled;
    private String issuer = "heifam";
    private String secret = "";
    private Duration accessTokenTtl = Duration.ofMinutes(15);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = safeTrim(issuer, "heifam");
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = safeTrim(secret, "");
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()
            ? Duration.ofMinutes(15)
            : accessTokenTtl;
    }

    public boolean hasSecret() {
        return !secret.isBlank();
    }

    private String safeTrim(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
