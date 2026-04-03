package com.balancify.backend.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSAlgorithmFamilyJWSKeySelector;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class SupabaseJwtVerifier {

    private static final long CLOCK_SKEW_SECONDS = 30L;

    private final SupabaseAuthProperties supabaseAuthProperties;
    private final ConcurrentMap<String, CachedVerification> verificationCache = new ConcurrentHashMap<>();
    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor;
    private final String expectedIssuer;

    public SupabaseJwtVerifier(
        SupabaseAuthProperties supabaseAuthProperties
    ) {
        this.supabaseAuthProperties = supabaseAuthProperties;
        this.expectedIssuer = resolveExpectedIssuer();
        this.jwtProcessor = createJwtProcessor();
    }

    public Optional<VerifiedUser> verify(String bearerToken) {
        String normalizedToken = safeTrim(bearerToken);
        if (normalizedToken.isEmpty()) {
            return Optional.empty();
        }

        long now = System.currentTimeMillis();
        CachedVerification cachedVerification = verificationCache.get(normalizedToken);
        if (cachedVerification != null && cachedVerification.expiresAtEpochMs() > now) {
            return Optional.of(cachedVerification.user());
        }

        if (jwtProcessor == null || expectedIssuer.isEmpty()) {
            verificationCache.remove(normalizedToken);
            return Optional.empty();
        }

        try {
            JWTClaimsSet claims = jwtProcessor.process(normalizedToken, null);
            if (!isValidClaims(claims, now)) {
                verificationCache.remove(normalizedToken);
                return Optional.empty();
            }

            String email = normalizeEmail(claims.getStringClaim("email"));
            if (email.isEmpty()) {
                verificationCache.remove(normalizedToken);
                return Optional.empty();
            }

            String nickname = resolveNickname(claims.getClaim("user_metadata"));
            VerifiedUser verifiedUser = new VerifiedUser(email, nickname);

            long ttlMillis = (long) supabaseAuthProperties.getVerificationCacheTtlSeconds() * 1000L;
            if (ttlMillis > 0) {
                long expirationBound = resolveExpirationBound(claims, now);
                long expiresAt = expirationBound > 0
                    ? Math.min(now + ttlMillis, expirationBound)
                    : now + ttlMillis;
                verificationCache.put(normalizedToken, new CachedVerification(verifiedUser, expiresAt));
            }

            return Optional.of(verifiedUser);
        } catch (BadJOSEException | JOSEException | ParseException exception) {
            verificationCache.remove(normalizedToken);
            return Optional.empty();
        }
    }

    private ConfigurableJWTProcessor<SecurityContext> createJwtProcessor() {
        String jwksUrl = resolveJwksEndpoint();
        if (jwksUrl.isEmpty()) {
            return null;
        }

        try {
            DefaultResourceRetriever resourceRetriever = new DefaultResourceRetriever(
                supabaseAuthProperties.getVerifyTimeoutMs(),
                supabaseAuthProperties.getVerifyTimeoutMs()
            );
            JWKSource<SecurityContext> jwkSource = new RemoteJWKSet<>(new URL(jwksUrl), resourceRetriever);
            DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            JWSKeySelector<SecurityContext> keySelector =
                JWSAlgorithmFamilyJWSKeySelector.fromJWKSource(jwkSource);
            processor.setJWSKeySelector(keySelector);
            return processor;
        } catch (MalformedURLException malformedURLException) {
            return null;
        } catch (JOSEException joseException) {
            return null;
        }
    }

    private boolean isValidClaims(JWTClaimsSet claims, long nowEpochMs) {
        if (claims == null) {
            return false;
        }

        if (!expectedIssuer.equals(safeTrim(claims.getIssuer()))) {
            return false;
        }

        Date expirationTime = claims.getExpirationTime();
        if (expirationTime == null) {
            return false;
        }
        if (expirationTime.toInstant().isBefore(Instant.ofEpochMilli(nowEpochMs).minusSeconds(CLOCK_SKEW_SECONDS))) {
            return false;
        }

        Date notBeforeTime = claims.getNotBeforeTime();
        if (notBeforeTime != null && notBeforeTime.toInstant().isAfter(Instant.ofEpochMilli(nowEpochMs).plusSeconds(CLOCK_SKEW_SECONDS))) {
            return false;
        }

        return true;
    }

    private long resolveExpirationBound(JWTClaimsSet claims, long nowEpochMs) {
        Date expirationTime = claims.getExpirationTime();
        if (expirationTime == null) {
            return 0L;
        }
        long expirationEpochMs = expirationTime.getTime();
        return Math.max(nowEpochMs, expirationEpochMs);
    }

    private String resolveExpectedIssuer() {
        String configuredUrl = safeTrim(supabaseAuthProperties.getSupabaseUrl());
        if (configuredUrl.isEmpty()) {
            return "";
        }

        String normalizedBase = configuredUrl.endsWith("/")
            ? configuredUrl.substring(0, configuredUrl.length() - 1)
            : configuredUrl;

        if (normalizedBase.endsWith("/auth/v1")) {
            return normalizedBase;
        }
        return normalizedBase + "/auth/v1";
    }

    private String resolveJwksEndpoint() {
        String issuer = resolveExpectedIssuer();
        if (issuer.isEmpty()) {
            return "";
        }
        return issuer + "/.well-known/jwks.json";
    }

    private String resolveNickname(Object userMetadataClaim) {
        if (!(userMetadataClaim instanceof Map<?, ?> userMetadata)) {
            return "";
        }

        Object value = userMetadata.get("nickname");
        String normalized = safeTrim(value == null ? "" : value.toString());
        if (!normalized.isEmpty()) {
            return normalized;
        }

        return "";
    }

    private String normalizeEmail(String value) {
        return safeTrim(value).toLowerCase(Locale.ROOT);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    public record VerifiedUser(
        String email,
        String nickname
    ) {
    }

    private record CachedVerification(
        VerifiedUser user,
        long expiresAtEpochMs
    ) {
    }
}
