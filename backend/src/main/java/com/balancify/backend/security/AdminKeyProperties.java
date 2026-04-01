package com.balancify.backend.security;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "balancify.admin")
public class AdminKeyProperties {

    private static final String DEFAULT_SUPER_ADMIN_EMAIL = "minsiklee2@gmail.com";

    private String apiKey;
    private String emails;
    private String superEmails = DEFAULT_SUPER_ADMIN_EMAIL;
    private String allowedEmails;
    private Set<String> normalizedAdminEmails = Set.of();
    private Set<String> normalizedSuperAdminEmails = Set.of(DEFAULT_SUPER_ADMIN_EMAIL);
    private Set<String> normalizedAllowedEmails = Set.of();

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getEmails() {
        return emails;
    }

    public void setEmails(String emails) {
        this.emails = emails;
        this.normalizedAdminEmails = Arrays
            .stream(safeValue(emails).split(","))
            .map(this::normalizeEmail)
            .filter(email -> !email.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    }

    public String getSuperEmails() {
        return superEmails;
    }

    public void setSuperEmails(String superEmails) {
        String normalized = safeValue(superEmails).trim();
        this.superEmails = normalized.isEmpty() ? DEFAULT_SUPER_ADMIN_EMAIL : normalized;
        this.normalizedSuperAdminEmails = Arrays
            .stream(this.superEmails.split(","))
            .map(this::normalizeEmail)
            .filter(email -> !email.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    }

    public String getAllowedEmails() {
        return allowedEmails;
    }

    public void setAllowedEmails(String allowedEmails) {
        this.allowedEmails = allowedEmails;
        this.normalizedAllowedEmails = Arrays
            .stream(safeValue(allowedEmails).split(","))
            .map(this::normalizeEmail)
            .filter(email -> !email.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAllowedAdminEmail(String email) {
        return isConfiguredSuperAdminEmail(email) || isConfiguredAdminEmail(email);
    }

    public boolean isConfiguredAdminEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        return !normalizedEmail.isEmpty() && normalizedAdminEmails.contains(normalizedEmail);
    }

    public boolean isConfiguredSuperAdminEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        return !normalizedEmail.isEmpty() && normalizedSuperAdminEmails.contains(normalizedEmail);
    }

    public boolean isConfiguredAllowedEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        return !normalizedEmail.isEmpty() && normalizedAllowedEmails.contains(normalizedEmail);
    }

    public Set<String> getNormalizedAdminEmails() {
        return normalizedAdminEmails;
    }

    public Set<String> getNormalizedSuperAdminEmails() {
        return normalizedSuperAdminEmails;
    }

    public Set<String> getNormalizedAllowedEmails() {
        return normalizedAllowedEmails;
    }

    private String normalizeEmail(String value) {
        return safeValue(value).trim().toLowerCase(Locale.ROOT);
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }
}
