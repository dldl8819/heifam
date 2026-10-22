package com.balancify.backend.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
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
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SupabaseJwtVerifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(SupabaseJwtVerifier.class);
    private static final long CLOCK_SKEW_SECONDS = 30L;
    // A token Supabase Auth refused is refused again without asking for this long.
    private static final long REJECTED_TOKEN_TTL_MS = 60_000L;
    // Forged tokens could fill the list of refused ones, so it holds this many at most.
    private static final int MAX_REJECTED_TOKENS = 2_000;
    private static final long UNVERIFIED_WINDOW_MS = 60_000L;

    private final SupabaseAuthProperties supabaseAuthProperties;
    private final ConcurrentMap<String, CachedVerification> verificationCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> rejectedTokens = new ConcurrentHashMap<>();
    // Set once a token has passed both the key set and Supabase Auth: the project signs with the keys
    // the key set publishes, so a token the key set does not verify is all but certainly forged. Until
    // then (a project still on a shared secret, say) every token goes to Supabase Auth as before.
    private final AtomicBoolean keySetProven = new AtomicBoolean(false);
    private final Object unverifiedChecksLock = new Object();
    private long unverifiedWindowStart;
    private int unverifiedChecksInWindow;
    private boolean unverifiedLimitLogged;
    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor;
    private final HttpClient authHttpClient;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
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

        // Each check below costs nothing but this server's time; only what passes them all is put to
        // Supabase Auth, so that tokens refused anyway cannot turn every request into an Auth call.
        if (isRejectedRecently(cacheKey, now) || !looksLikeLiveAccessToken(normalizedToken, now)) {
            return Optional.empty();
        }

        Optional<VerifiedUser> jwksVerifiedUser = jwtProcessor == null
            ? Optional.empty()
            : verifyWithJwks(normalizedToken, now);
        if (jwksVerifiedUser.isEmpty() && keySetProven.get() && !takeUnverifiedCheck(now)) {
            return Optional.empty();
        }
        AuthAnswer authAnswer = verifyWithSupabaseAuthApi(normalizedToken);
        if (authAnswer.user().isPresent()) {
            VerifiedUser activeUser = authAnswer.user().get();
            if (jwksVerifiedUser.isPresent()) {
                VerifiedUser signedUser = jwksVerifiedUser.get();
                if (!signedUser.userId().equals(activeUser.userId())
                    || !signedUser.email().equals(activeUser.email())) {
                    verificationCache.remove(cacheKey);
                    return Optional.empty();
                }
                keySetProven.set(true);
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

        if (authAnswer.refused()) {
            rememberRejected(cacheKey, now);
        }
        verificationCache.remove(cacheKey);
        return Optional.empty();
    }

    // A Supabase access token is a signed JWT naming its user, with an expiry not yet passed.
    // Nothing else could be accepted by Supabase Auth, so nothing else is put to it.
    private boolean looksLikeLiveAccessToken(String token, long nowEpochMs) {
        try {
            JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();
            Date expirationTime = claims.getExpirationTime();
            return !safeTrim(claims.getSubject()).isEmpty()
                && expirationTime != null
                && !expirationTime.toInstant().isBefore(Instant.ofEpochMilli(nowEpochMs).minusSeconds(CLOCK_SKEW_SECONDS));
        } catch (ParseException exception) {
            return false;
        }
    }

    private boolean isRejectedRecently(String cacheKey, long now) {
        Long refusedUntil = rejectedTokens.get(cacheKey);
        if (refusedUntil == null) {
            return false;
        }
        if (refusedUntil > now) {
            return true;
        }
        rejectedTokens.remove(cacheKey, refusedUntil);
        return false;
    }

    // When the list is full, refusals that ran out go first; if none has, this one is not remembered.
    private void rememberRejected(String cacheKey, long now) {
        if (rejectedTokens.size() >= MAX_REJECTED_TOKENS) {
            rejectedTokens.values().removeIf(refusedUntil -> refusedUntil <= now);
            if (rejectedTokens.size() >= MAX_REJECTED_TOKENS) {
                return;
            }
        }
        rejectedTokens.put(cacheKey, now + REJECTED_TOKEN_TTL_MS);
    }

    // One of this minute's Supabase Auth checks for a token the key set does not verify, if any is left.
    private boolean takeUnverifiedCheck(long now) {
        int limit = supabaseAuthProperties.getUnverifiedAuthChecksPerMinute();
        synchronized (unverifiedChecksLock) {
            if (now - unverifiedWindowStart >= UNVERIFIED_WINDOW_MS) {
                unverifiedWindowStart = now;
                unverifiedChecksInWindow = 0;
                unverifiedLimitLogged = false;
            }
            if (unverifiedChecksInWindow >= limit) {
                if (!unverifiedLimitLogged) {
                    unverifiedLimitLogged = true;
                    LOGGER.warn(
                        "Tokens the key set does not verify used this minute's {} Supabase Auth checks; the rest are refused",
                        limit
                    );
                }
                return false;
            }
            unverifiedChecksInWindow++;
            return true;
        }
    }

    /** What Supabase Auth said: the user it accepts the token for, or whether it refused the token outright. */
    private record AuthAnswer(Optional<VerifiedUser> user, boolean refused) {

        static AuthAnswer accepted(VerifiedUser user) {
            return new AuthAnswer(Optional.of(user), false);
        }

        static AuthAnswer refusedToken() {
            return new AuthAnswer(Optional.empty(), true);
        }

        // No answer to go by (unreachable, timed out, overloaded, unreadable): not remembered.
        static AuthAnswer none() {
            return new AuthAnswer(Optional.empty(), false);
        }
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

    private AuthAnswer verifyWithSupabaseAuthApi(String normalizedToken) {
        if (expectedIssuer.isEmpty()) {
            return AuthAnswer.none();
        }

        String apiKey = supabaseAuthProperties.getApiKey();
        if (apiKey.isEmpty()) {
            return AuthAnswer.none();
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
            int status = response.statusCode();
            if (status == 400 || status == 401 || status == 403) {
                return AuthAnswer.refusedToken();
            }
            if (status < 200 || status >= 300) {
                return AuthAnswer.none();
            }

            Map<String, Object> payload = objectMapper.readValue(
                response.body(),
                new TypeReference<>() {
                }
            );
            String userId = safeTrim(asString(payload.get("id")));
            String email = normalizeEmail(asString(payload.get("email")));
            if (userId.isEmpty() || email.isEmpty()) {
                return AuthAnswer.none();
            }
            String nickname = resolveNickname(payload.get("user_metadata"));
            return AuthAnswer.accepted(new VerifiedUser(userId, email, nickname, ""));
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return AuthAnswer.none();
        } catch (IOException | JacksonException | IllegalArgumentException runtimeException) {
            // An answer that cannot be read verifies nobody; Jackson 3 reports it unchecked.
            return AuthAnswer.none();
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
            // Cached and rate limited (nimbus defaults): tokens naming unknown keys cannot make every
            // request fetch the key set again.
            JWKSource<SecurityContext> jwkSource = JWKSourceBuilder
                .<SecurityContext>create(URI.create(jwksUrl).toURL(), resourceRetriever)
                .build();
            DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            JWSKeySelector<SecurityContext> keySelector =
                JWSAlgorithmFamilyJWSKeySelector.fromJWKSource(jwkSource);
            processor.setJWSKeySelector(keySelector);
            return processor;
        } catch (MalformedURLException | IllegalArgumentException malformedUrl) {
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
