package com.balancify.backend.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSAlgorithmFamilyJWSKeySelector;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SupabaseJwtVerifier {

    private static final long CLOCK_SKEW_SECONDS = 30L;

    private final SupabaseAuthProperties supabaseAuthProperties;
    private final ConcurrentMap<String, CachedVerification> verificationCache = new ConcurrentHashMap<>();
    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor;
    private final HttpClient authHttpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String expectedIssuer;
    private final Clock clock;

    @Autowired
    public SupabaseJwtVerifier(
        SupabaseAuthProperties supabaseAuthProperties
    ) {
        this(supabaseAuthProperties, Clock.systemUTC());
    }

    SupabaseJwtVerifier(SupabaseAuthProperties supabaseAuthProperties, Clock clock) {
        this.supabaseAuthProperties = supabaseAuthProperties;
        this.clock = clock;
        this.expectedIssuer = resolveExpectedIssuer();
        this.jwtProcessor = createJwtProcessor();
        this.authHttpClient = HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofMillis(supabaseAuthProperties.getVerifyTimeoutMs()))
            .build();
    }

    public Optional<VerifiedUser> verify(String bearerToken) {
        String normalizedToken = safeTrim(bearerToken);
        if (normalizedToken.isEmpty()) {
            return Optional.empty();
        }

        long now = clock.millis();

        if (expectedIssuer.isEmpty()) {
            return Optional.empty();
        }

        // A page load sends several API calls with the same token, and each check is a Supabase
        // Auth request that Supabase also logs. A positive answer is reused for a short while
        // (verification-cache-ttl-seconds), so a token revoked meanwhile still works until its
        // entry runs out. Deleting an account clears that user's entries at once (invalidateUser).
        String cacheKey = cacheKey(normalizedToken);
        CachedVerification cached = verificationCache.get(cacheKey);
        if (cached != null) {
            if (cached.expiresAtEpochMs() > now) {
                return Optional.of(cached.user());
            }
            verificationCache.remove(cacheKey, cached);
        }

        Optional<VerifiedUser> jwksVerifiedUser = jwtProcessor == null
            ? Optional.empty()
            : verifyWithJwks(normalizedToken, now);
        Optional<VerifiedUser> authApiVerifiedUser = verifyWithSupabaseAuthApi(normalizedToken);
        if (authApiVerifiedUser.isPresent()) {
            VerifiedUser activeUser = authApiVerifiedUser.get();
            if (jwksVerifiedUser.isPresent()) {
                VerifiedUser signedUser = jwksVerifiedUser.get();
                if (!signedUser.userId().equals(activeUser.userId())
                    || !signedUser.email().equals(activeUser.email())) {
                    verificationCache.remove(cacheKey);
                    return Optional.empty();
                }
                activeUser = new VerifiedUser(
                    signedUser.userId(),
                    activeUser.email(),
                    signedUser.nickname(),
                    signedUser.sessionId()
                );
            }
            cacheVerification(cacheKey, activeUser, now, tokenExpiresAt(normalizedToken));
            return Optional.of(activeUser);
        }

        verificationCache.remove(cacheKey);
        return Optional.empty();
    }

    public void invalidateUser(String userId) {
        String normalizedUserId = safeTrim(userId);
        if (normalizedUserId.isEmpty()) {
            return;
        }
        verificationCache.entrySet().removeIf(entry -> normalizedUserId.equals(entry.getValue().user().userId()));
    }

    private Optional<VerifiedUser> verifyWithJwks(String normalizedToken, long now) {
        try {
            JWTClaimsSet claims = jwtProcessor.process(normalizedToken, null);
            if (!isValidClaims(claims, now)) {
                return Optional.empty();
            }

            String userId = safeTrim(claims.getSubject());
            String email = normalizeEmail(claims.getStringClaim("email"));
            if (userId.isEmpty() || email.isEmpty()) {
                return Optional.empty();
            }

            String nickname = resolveNickname(claims.getClaim("user_metadata"));
            String sessionId = safeTrim(claims.getStringClaim("session_id"));
            return Optional.of(new VerifiedUser(userId, email, nickname, sessionId));
        } catch (BadJOSEException | JOSEException | ParseException exception) {
            return Optional.empty();
        }
    }

    private Optional<VerifiedUser> verifyWithSupabaseAuthApi(String normalizedToken) {
        if (expectedIssuer.isEmpty()) {
            return Optional.empty();
        }

        String apiKey = supabaseAuthProperties.getApiKey();
        if (apiKey.isEmpty()) {
            return Optional.empty();
        }

        try {
            HttpRequest request = HttpRequest
                .newBuilder(URI.create(expectedIssuer + "/user"))
                .timeout(Duration.ofMillis(supabaseAuthProperties.getVerifyTimeoutMs()))
                .header("Authorization", "Bearer " + normalizedToken)
                .header("apikey", apiKey)
                .header("Accept", "application/json")
                .GET()
                .build();
            HttpResponse<String> response = authHttpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }

            Map<String, Object> payload = objectMapper.readValue(
                response.body(),
                new TypeReference<>() {
                }
            );
            String userId = safeTrim(asString(payload.get("id")));
            String email = normalizeEmail(asString(payload.get("email")));
            if (userId.isEmpty() || email.isEmpty()) {
                return Optional.empty();
            }
            String nickname = resolveNickname(payload.get("user_metadata"));
            return Optional.of(new VerifiedUser(userId, email, nickname, ""));
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException | IllegalArgumentException runtimeException) {
            return Optional.empty();
        }
    }

    private void cacheVerification(
        String cacheKey,
        VerifiedUser verifiedUser,
        long now,
        long expirationBound
    ) {
        long ttlMillis = (long) supabaseAuthProperties.getVerificationCacheTtlSeconds() * 1000L;
        if (ttlMillis <= 0) {
            return;
        }

        long expiresAt = expirationBound > 0
            ? Math.min(now + ttlMillis, expirationBound)
            : now + ttlMillis;
        if (expiresAt <= now) {
            return;
        }
        verificationCache.values().removeIf(entry -> entry.expiresAtEpochMs() <= now);
        verificationCache.put(cacheKey, new CachedVerification(verifiedUser, expiresAt));
    }

    // Entries are keyed by a hash so the cache never holds raw access tokens.
    private static String cacheKey(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    // The Auth API has just accepted this token, so its expiry claim can bound the cache entry.
    private static long tokenExpiresAt(String token) {
        try {
            Date expirationTime = SignedJWT.parse(token).getJWTClaimsSet().getExpirationTime();
            return expirationTime == null ? 0L : expirationTime.getTime();
        } catch (ParseException exception) {
            return 0L;
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

    private String asString(Object value) {
        return value == null ? "" : value.toString();
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    public record VerifiedUser(
        String userId,
        String email,
        String nickname,
        String sessionId
    ) {
        public VerifiedUser(String email, String nickname) {
            this("", email, nickname, "");
        }
    }

    private record CachedVerification(
        VerifiedUser user,
        long expiresAtEpochMs
    ) {
    }
}
