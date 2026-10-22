package com.balancify.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SupabaseJwtVerifierTest {

    private static final String PLACEHOLDER_API_KEY = "YOUR_SUPABASE_PUBLISHABLE_KEY";
    private static final String PLACEHOLDER_USER_ID =
        "00000000-0000-0000-0000-000000000001";

    private HttpServer jwksServer;
    private final AtomicInteger jwksRequestCount = new AtomicInteger();
    private final AtomicInteger authUserRequestCount = new AtomicInteger();

    @AfterEach
    void tearDown() {
        if (jwksServer != null) {
            jwksServer.stop(0);
        }
    }

    @Test
    void verifiesTokenUsingSupabaseJwks() throws Exception {
        ECKey signingKey = new ECKeyGenerator(Curve.P_256)
            .keyID("hei-test-key")
            .generate();
        String baseUrl = startJwksServer(new JWKSet(signingKey.toPublicJWK()).toString());
        registerActiveUserEndpoint();

        SupabaseAuthProperties properties = new SupabaseAuthProperties();
        properties.setSupabaseUrl(baseUrl);
        properties.setApiKey(PLACEHOLDER_API_KEY);
        properties.setVerifyTimeoutMs(1000);
        properties.setVerificationCacheTtlSeconds(60);

        SupabaseJwtVerifier verifier = new SupabaseJwtVerifier(properties);

        String token = createToken(
            signingKey,
            baseUrl + "/auth/v1",
            "member@example.test",
            Map.of("nickname", "민식")
        );

        Optional<SupabaseJwtVerifier.VerifiedUser> verifiedUser = verifier.verify(token);

        assertThat(verifiedUser).isPresent();
        assertThat(verifiedUser.orElseThrow().userId()).isEqualTo(PLACEHOLDER_USER_ID);
        assertThat(verifiedUser.orElseThrow().email()).isEqualTo("member@example.test");
        assertThat(verifiedUser.orElseThrow().nickname()).isEqualTo("민식");
        assertThat(jwksRequestCount.get()).isEqualTo(1);
        assertThat(authUserRequestCount.get()).isEqualTo(1);
    }

    @Test
    void rejectsTokenWithUnexpectedIssuer() throws Exception {
        ECKey signingKey = new ECKeyGenerator(Curve.P_256)
            .keyID("hei-test-key")
            .generate();
        String baseUrl = startJwksServer(new JWKSet(signingKey.toPublicJWK()).toString());

        SupabaseAuthProperties properties = new SupabaseAuthProperties();
        properties.setSupabaseUrl(baseUrl);
        properties.setApiKey(PLACEHOLDER_API_KEY);
        properties.setVerifyTimeoutMs(1000);
        properties.setVerificationCacheTtlSeconds(60);

        SupabaseJwtVerifier verifier = new SupabaseJwtVerifier(properties);

        String token = createToken(
            signingKey,
            "https://wrong.example.com/auth/v1",
            "member@example.test",
            Map.of("nickname", "민식")
        );

        assertThat(verifier.verify(token)).isEmpty();
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        ECKey signingKey = new ECKeyGenerator(Curve.P_256)
            .keyID("hei-test-key")
            .generate();
        String baseUrl = startJwksServer(new JWKSet(signingKey.toPublicJWK()).toString());

        SupabaseAuthProperties properties = new SupabaseAuthProperties();
        properties.setSupabaseUrl(baseUrl);
        properties.setApiKey(PLACEHOLDER_API_KEY);
        properties.setVerifyTimeoutMs(1000);
        properties.setVerificationCacheTtlSeconds(60);

        SupabaseJwtVerifier verifier = new SupabaseJwtVerifier(properties);

        String token = createToken(
            signingKey,
            baseUrl + "/auth/v1",
            "member@example.test",
            Map.of("nickname", "민식"),
            Instant.now().minusSeconds(120)
        );

        assertThat(verifier.verify(token)).isEmpty();
    }

    @Test
    void rejectsTokenWithInvalidSignature() throws Exception {
        ECKey trustedKey = new ECKeyGenerator(Curve.P_256)
            .keyID("hei-trusted-key")
            .generate();
        ECKey attackerKey = new ECKeyGenerator(Curve.P_256)
            .keyID("hei-attacker-key")
            .generate();
        String baseUrl = startJwksServer(new JWKSet(trustedKey.toPublicJWK()).toString());

        SupabaseAuthProperties properties = new SupabaseAuthProperties();
        properties.setSupabaseUrl(baseUrl);
        properties.setApiKey(PLACEHOLDER_API_KEY);
        properties.setVerifyTimeoutMs(1000);
        properties.setVerificationCacheTtlSeconds(60);

        SupabaseJwtVerifier verifier = new SupabaseJwtVerifier(properties);

        String token = createToken(
            attackerKey,
            baseUrl + "/auth/v1",
            "member@example.test",
            Map.of("nickname", "민식")
        );

        assertThat(verifier.verify(token)).isEmpty();
        assertThat(jwksRequestCount.get()).isBetween(1, 2);
    }

    @Test
    void reusesTheActiveUserCheckForTheSameTokenWithinTheCacheWindow() throws Exception {
        ECKey signingKey = new ECKeyGenerator(Curve.P_256)
            .keyID("hei-test-key")
            .generate();
        String baseUrl = startJwksServer(new JWKSet(signingKey.toPublicJWK()).toString());
        registerActiveUserEndpoint();

        SupabaseAuthProperties properties = new SupabaseAuthProperties();
        properties.setSupabaseUrl(baseUrl);
        properties.setApiKey(PLACEHOLDER_API_KEY);
        properties.setVerifyTimeoutMs(1000);
        properties.setVerificationCacheTtlSeconds(60);

        SupabaseJwtVerifier verifier = new SupabaseJwtVerifier(properties);

        String token = createToken(
            signingKey,
            baseUrl + "/auth/v1",
            "member@example.test",
            Map.of("nickname", "민식")
        );

        assertThat(verifier.verify(token)).isPresent();
        assertThat(verifier.verify(token)).isPresent();
        assertThat(jwksRequestCount.get()).isEqualTo(1);
        assertThat(authUserRequestCount.get()).isEqualTo(1);
    }

    @Test
    void checksTheActiveUserOnEveryRequestWhenCachingIsOff() throws Exception {
        ECKey signingKey = new ECKeyGenerator(Curve.P_256)
            .keyID("hei-test-key")
            .generate();
        String baseUrl = startJwksServer(new JWKSet(signingKey.toPublicJWK()).toString());
        registerActiveUserEndpoint();

        SupabaseAuthProperties properties = new SupabaseAuthProperties();
        properties.setSupabaseUrl(baseUrl);
        properties.setApiKey(PLACEHOLDER_API_KEY);
        properties.setVerifyTimeoutMs(1000);
        properties.setVerificationCacheTtlSeconds(0);

        SupabaseJwtVerifier verifier = new SupabaseJwtVerifier(properties);

        String token = createToken(
            signingKey,
            baseUrl + "/auth/v1",
            "member@example.test",
            Map.of("nickname", "민식")
        );

        assertThat(verifier.verify(token)).isPresent();
        assertThat(verifier.verify(token)).isPresent();
        assertThat(jwksRequestCount.get()).isEqualTo(1);
        assertThat(authUserRequestCount.get()).isEqualTo(2);
    }

    @Test
    void neverReusesACheckPastTheTokenExpiry() throws Exception {
        ECKey signingKey = new ECKeyGenerator(Curve.P_256)
            .keyID("hei-test-key")
            .generate();
        String baseUrl = startJwksServer(new JWKSet(signingKey.toPublicJWK()).toString());
        registerActiveUserEndpoint();

        SupabaseAuthProperties properties = new SupabaseAuthProperties();
        properties.setSupabaseUrl(baseUrl);
        properties.setApiKey(PLACEHOLDER_API_KEY);
        properties.setVerifyTimeoutMs(1000);
        properties.setVerificationCacheTtlSeconds(60);

        MutableClock clock = new MutableClock(Instant.now());
        SupabaseJwtVerifier verifier = new SupabaseJwtVerifier(properties, clock);

        String token = createToken(
            signingKey,
            baseUrl + "/auth/v1",
            "member@example.test",
            Map.of("nickname", "민식"),
            clock.instant().plusSeconds(40)
        );

        assertThat(verifier.verify(token)).isPresent();
        clock.advanceSeconds(41);
        verifier.verify(token);
        assertThat(authUserRequestCount.get()).isEqualTo(2);
    }

    @Test
    void fallsBackToSupabaseAuthUserEndpointWhenJwksVerificationFails() throws Exception {
        String baseUrl = startJwksServer(new JWKSet().toString());
        jwksServer.createContext(
            "/auth/v1/user",
            new StaticBodyHandler(
                """
                    {
                      "id": "00000000-0000-0000-0000-000000000001",
                      "email": "player@example.test",
                      "user_metadata": {
                        "nickname": "PlayerAlpha"
                      }
                    }
                    """,
                authUserRequestCount
            )
        );

        SupabaseAuthProperties properties = new SupabaseAuthProperties();
        properties.setSupabaseUrl(baseUrl);
        properties.setApiKey(PLACEHOLDER_API_KEY);
        properties.setVerifyTimeoutMs(1000);
        properties.setVerificationCacheTtlSeconds(60);

        SupabaseJwtVerifier verifier = new SupabaseJwtVerifier(properties);

        Optional<SupabaseJwtVerifier.VerifiedUser> verifiedUser = verifier.verify(unverifiableToken("player@example.test"));

        assertThat(verifiedUser).isPresent();
        assertThat(verifiedUser.orElseThrow().userId()).isEqualTo(PLACEHOLDER_USER_ID);
        assertThat(verifiedUser.orElseThrow().email()).isEqualTo("player@example.test");
        assertThat(verifiedUser.orElseThrow().nickname()).isEqualTo("PlayerAlpha");
        assertThat(authUserRequestCount.get()).isEqualTo(1);
    }

    @Test
    void verifiesNobodyWhenTheAuthUserAnswerCannotBeRead() throws Exception {
        String baseUrl = startJwksServer(new JWKSet().toString());
        AtomicReference<String> answer = new AtomicReference<>("");
        jwksServer.createContext("/auth/v1/user", exchange -> {
            authUserRequestCount.incrementAndGet();
            byte[] bytes = answer.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(bytes);
                }
            }
            exchange.close();
        });

        SupabaseAuthProperties properties = new SupabaseAuthProperties();
        properties.setSupabaseUrl(baseUrl);
        properties.setApiKey(PLACEHOLDER_API_KEY);
        properties.setVerifyTimeoutMs(1000);
        properties.setVerificationCacheTtlSeconds(60);

        SupabaseJwtVerifier verifier = new SupabaseJwtVerifier(properties);

        // Jackson 3 reports these with unchecked exceptions; to the filters they must read as
        // "not signed in", never as a failure of the request.
        String[] unreadable = {"{\"id\": \"" + PLACEHOLDER_USER_ID + "\", \"email\":", "[]", "\"text\"", "", "<html>"};
        for (int index = 0; index < unreadable.length; index++) {
            answer.set(unreadable[index]);

            assertThat(verifier.verify(unverifiableToken("player" + index + "@example.test"))).as(unreadable[index]).isEmpty();
        }
        assertThat(authUserRequestCount.get()).isEqualTo(unreadable.length);
    }

    @Test
    void rejectsATokenAsSoonAsTheAuthUserIsGoneWhenCachingIsOff() throws Exception {
        String baseUrl = startJwksServer(new JWKSet().toString());
        registerAuthUserThatDisappearsAfterFirstCheck();

        SupabaseAuthProperties properties = new SupabaseAuthProperties();
        properties.setSupabaseUrl(baseUrl);
        properties.setApiKey(PLACEHOLDER_API_KEY);
        properties.setVerifyTimeoutMs(1000);
        properties.setVerificationCacheTtlSeconds(0);

        SupabaseJwtVerifier verifier = new SupabaseJwtVerifier(properties);
        String token = unverifiableToken("player@example.test");

        assertThat(verifier.verify(token)).isPresent();
        assertThat(verifier.verify(token)).isEmpty();
        assertThat(authUserRequestCount.get()).isEqualTo(2);
    }

    @Test
    void checksAgainOnceTheCacheWindowEnds() throws Exception {
        String baseUrl = startJwksServer(new JWKSet().toString());
        registerAuthUserThatDisappearsAfterFirstCheck();

        SupabaseAuthProperties properties = new SupabaseAuthProperties();
        properties.setSupabaseUrl(baseUrl);
        properties.setApiKey(PLACEHOLDER_API_KEY);
        properties.setVerifyTimeoutMs(1000);
        properties.setVerificationCacheTtlSeconds(60);

        MutableClock clock = new MutableClock(Instant.now());
        SupabaseJwtVerifier verifier = new SupabaseJwtVerifier(properties, clock);
        String token = unverifiableToken("player@example.test");

        assertThat(verifier.verify(token)).isPresent();
        clock.advanceSeconds(30);
        assertThat(verifier.verify(token)).isPresent();
        clock.advanceSeconds(31);
        assertThat(verifier.verify(token)).isEmpty();
        assertThat(authUserRequestCount.get()).isEqualTo(2);
    }

    @Test
    void dropsCachedChecksForADeletedAccountAtOnce() throws Exception {
        String baseUrl = startJwksServer(new JWKSet().toString());
        registerAuthUserThatDisappearsAfterFirstCheck();

        SupabaseAuthProperties properties = new SupabaseAuthProperties();
        properties.setSupabaseUrl(baseUrl);
        properties.setApiKey(PLACEHOLDER_API_KEY);
        properties.setVerifyTimeoutMs(1000);
        properties.setVerificationCacheTtlSeconds(60);

        SupabaseJwtVerifier verifier = new SupabaseJwtVerifier(properties);
        String token = unverifiableToken("player@example.test");

        assertThat(verifier.verify(token)).isPresent();
        verifier.invalidateUser(PLACEHOLDER_USER_ID);
        assertThat(verifier.verify(token)).isEmpty();
        assertThat(authUserRequestCount.get()).isEqualTo(2);
    }

    @Test
    void capsTheCacheWindowAtFiveMinutes() {
        SupabaseAuthProperties properties = new SupabaseAuthProperties();

        properties.setVerificationCacheTtlSeconds(3600);
        assertThat(properties.getVerificationCacheTtlSeconds()).isEqualTo(300);
        properties.setVerificationCacheTtlSeconds(-5);
        assertThat(properties.getVerificationCacheTtlSeconds()).isZero();
    }

    private void registerAuthUserThatDisappearsAfterFirstCheck() {
        jwksServer.createContext(
            "/auth/v1/user",
            exchange -> {
                int requestNumber = authUserRequestCount.incrementAndGet();
                if (requestNumber > 1) {
                    exchange.sendResponseHeaders(401, -1);
                    exchange.close();
                    return;
                }

                byte[] activeUserBody = """
                    {
                      "id": "00000000-0000-0000-0000-000000000001",
                      "email": "placeholder.user@example.test",
                      "user_metadata": {
                        "nickname": "PlaceholderNickname"
                      }
                    }
                    """.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, activeUserBody.length);
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(activeUserBody);
                }
            }
        );
    }

    @Test
    void doesNotFetchTheKeySetAgainForEachTokenNamingAnUnknownKey() throws Exception {
        ECKey signingKey = new ECKeyGenerator(Curve.P_256)
            .keyID("hei-test-key")
            .generate();
        ECKey unknownKey = new ECKeyGenerator(Curve.P_256)
            .keyID("unknown-key")
            .generate();
        String baseUrl = startJwksServer(new JWKSet(signingKey.toPublicJWK()).toString());

        SupabaseAuthProperties properties = new SupabaseAuthProperties();
        properties.setSupabaseUrl(baseUrl);
        properties.setApiKey(PLACEHOLDER_API_KEY);
        properties.setVerifyTimeoutMs(1000);
        properties.setVerificationCacheTtlSeconds(60);

        SupabaseJwtVerifier verifier = new SupabaseJwtVerifier(properties);
        verifier.verify(createToken(signingKey, baseUrl + "/auth/v1", "member@example.test", Map.of()));

        // Supabase Auth stays the judge of every token (there is no user endpoint here, so all are
        // refused). What is checked is the key set: tokens naming a key it does not hold must not
        // make each request fetch it again.
        for (int index = 0; index < 5; index++) {
            String forged = createToken(unknownKey, baseUrl + "/auth/v1", "forged" + index + "@example.test", Map.of());
            assertThat(verifier.verify(forged)).isEmpty();
        }
        assertThat(jwksRequestCount.get()).isLessThanOrEqualTo(2);
    }

    @Test
    void neverAsksSupabaseAuthAboutATokenThatCannotBeAnAccessToken() throws Exception {
        String baseUrl = startJwksServer(new JWKSet().toString());
        registerActiveUserEndpoint();
        SupabaseJwtVerifier verifier = new SupabaseJwtVerifier(defaultProperties(baseUrl));
        ECKey key = new ECKeyGenerator(Curve.P_256).keyID("not-published").generate();

        SignedJWT withoutUser = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.getKeyID()).build(),
            new JWTClaimsSet.Builder()
                .issuer(baseUrl + "/auth/v1")
                .expirationTime(Date.from(Instant.now().plusSeconds(600)))
                .build()
        );
        withoutUser.sign(new ECDSASigner(key));
        String expired = createToken(key, baseUrl + "/auth/v1", "member@example.test", Map.of(), Instant.now().minusSeconds(120));

        for (String token : new String[] {"YOUR_ACCESS_TOKEN", "a.b.c", expired, withoutUser.serialize()}) {
            assertThat(verifier.verify(token)).isEmpty();
        }
        assertThat(authUserRequestCount.get()).isZero();

        // A token shaped like one is still put to Supabase Auth, which is the judge.
        assertThat(verifier.verify(unverifiableToken("member@example.test"))).isPresent();
        assertThat(authUserRequestCount.get()).isEqualTo(1);
    }

    @Test
    void remembersForAMinuteThatSupabaseAuthRefusedAToken() throws Exception {
        String baseUrl = startJwksServer(new JWKSet().toString());
        AtomicInteger status = new AtomicInteger(401);
        jwksServer.createContext("/auth/v1/user", exchange -> {
            authUserRequestCount.incrementAndGet();
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        MutableClock clock = new MutableClock(Instant.now());
        SupabaseJwtVerifier verifier = new SupabaseJwtVerifier(defaultProperties(baseUrl), clock);
        String refused = unverifiableToken("member@example.test");

        assertThat(verifier.verify(refused)).isEmpty();
        assertThat(verifier.verify(refused)).isEmpty();
        assertThat(authUserRequestCount.get()).isEqualTo(1);
        clock.advanceSeconds(61);
        assertThat(verifier.verify(refused)).isEmpty();
        assertThat(authUserRequestCount.get()).isEqualTo(2);

        // No answer to go by (overloaded, failing) is not remembered: the next request asks again.
        for (int unanswered : new int[] {429, 500}) {
            status.set(unanswered);
            String token = unverifiableToken("member" + unanswered + "@example.test");
            int before = authUserRequestCount.get();
            assertThat(verifier.verify(token)).isEmpty();
            assertThat(verifier.verify(token)).isEmpty();
            assertThat(authUserRequestCount.get()).isEqualTo(before + 2);
        }
    }

    @Test
    void limitsAuthChecksForTokensTheKeySetDoesNotVerifyOnceItHasVerifiedARealOne() throws Exception {
        ECKey signingKey = new ECKeyGenerator(Curve.P_256)
            .keyID("hei-test-key")
            .generate();
        String baseUrl = startJwksServer(new JWKSet(signingKey.toPublicJWK()).toString());
        registerActiveUserEndpoint();
        SupabaseAuthProperties properties = defaultProperties(baseUrl);
        properties.setUnverifiedAuthChecksPerMinute(2);
        MutableClock clock = new MutableClock(Instant.now());
        SupabaseJwtVerifier verifier = new SupabaseJwtVerifier(properties, clock);

        assertThat(verifier.verify(createToken(signingKey, baseUrl + "/auth/v1", "member@example.test", Map.of()))).isPresent();
        assertThat(authUserRequestCount.get()).isEqualTo(1);

        // The key set has shown it signs this project's tokens: ones it does not verify get two Auth
        // checks a minute (here), and the rest are refused without one.
        for (int index = 0; index < 5; index++) {
            verifier.verify(unverifiableToken("forged" + index + "@example.test"));
        }
        assertThat(authUserRequestCount.get()).isEqualTo(3);
        assertThat(verifier.verify(unverifiableToken("forged5@example.test"))).isEmpty();
        assertThat(authUserRequestCount.get()).isEqualTo(3);

        clock.advanceSeconds(61);
        verifier.verify(unverifiableToken("forged6@example.test"));
        assertThat(authUserRequestCount.get()).isEqualTo(4);

        // Tokens the key set verifies are never held back by it.
        assertThat(verifier.verify(createToken(signingKey, baseUrl + "/auth/v1", "member@example.test", Map.of("nickname", "민식")))).isPresent();
        assertThat(authUserRequestCount.get()).isEqualTo(5);
    }

    @Test
    void limitsNothingWhileTheKeySetHasVerifiedNoRealToken() throws Exception {
        String baseUrl = startJwksServer(new JWKSet().toString());
        registerActiveUserEndpoint();
        SupabaseAuthProperties properties = defaultProperties(baseUrl);
        properties.setUnverifiedAuthChecksPerMinute(2);
        SupabaseJwtVerifier verifier = new SupabaseJwtVerifier(properties);

        // As on a project still signing with a shared secret: every token goes to Supabase Auth.
        for (int index = 0; index < 5; index++) {
            assertThat(verifier.verify(unverifiableToken("member" + index + "@example.test"))).isPresent();
        }
        assertThat(authUserRequestCount.get()).isEqualTo(5);
    }

    private SupabaseAuthProperties defaultProperties(String baseUrl) {
        SupabaseAuthProperties properties = new SupabaseAuthProperties();
        properties.setSupabaseUrl(baseUrl);
        properties.setApiKey(PLACEHOLDER_API_KEY);
        properties.setVerifyTimeoutMs(1000);
        properties.setVerificationCacheTtlSeconds(60);
        return properties;
    }

    // A well-formed access token that a key set without its key cannot verify, as a project on a
    // shared secret sends, or a forger: only Supabase Auth can tell whether it is good.
    private String unverifiableToken(String email) throws Exception {
        ECKey key = new ECKeyGenerator(Curve.P_256).keyID("not-published").generate();
        return createToken(key, "https://YOUR_PROJECT.supabase.co/auth/v1", email, Map.of());
    }

    private void registerActiveUserEndpoint() {
        jwksServer.createContext(
            "/auth/v1/user",
            new StaticBodyHandler(
                """
                    {
                      "id": "00000000-0000-0000-0000-000000000001",
                      "email": "member@example.test",
                      "user_metadata": {
                        "nickname": "PlaceholderNickname"
                      }
                    }
                    """,
                authUserRequestCount
            )
        );
    }

    private String startJwksServer(String responseBody) throws IOException {
        jwksRequestCount.set(0);
        authUserRequestCount.set(0);
        jwksServer = HttpServer.create(new InetSocketAddress(0), 0);
        jwksServer.createContext("/auth/v1/.well-known/jwks.json", new StaticBodyHandler(responseBody, jwksRequestCount));
        jwksServer.start();
        int port = jwksServer.getAddress().getPort();
        return "http://127.0.0.1:" + port;
    }

    private String createToken(
        ECKey signingKey,
        String issuer,
        String email,
        Map<String, Object> userMetadata
    ) throws Exception {
        return createToken(signingKey, issuer, email, userMetadata, Instant.now().plusSeconds(600));
    }

    private String createToken(
        ECKey signingKey,
        String issuer,
        String email,
        Map<String, Object> userMetadata,
        Instant expirationTime
    ) throws Exception {
        SignedJWT jwt = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(signingKey.getKeyID())
                .type(JOSEObjectType.JWT)
                .build(),
            new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(PLACEHOLDER_USER_ID)
                .audience("authenticated")
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(expirationTime))
                .claim("email", email)
                .claim("user_metadata", userMetadata)
                .build()
        );
        jwt.sign(new ECDSASigner(signingKey));
        return jwt.serialize();
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant start) {
            this.instant = start;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private class StaticBodyHandler implements HttpHandler {

        private final byte[] responseBody;
        private final AtomicInteger requestCount;

        private StaticBodyHandler(String responseBody, AtomicInteger requestCount) {
            this.responseBody = responseBody.getBytes(StandardCharsets.UTF_8);
            this.requestCount = requestCount;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            requestCount.incrementAndGet();
            if (exchange.getRequestURI().getPath().endsWith("/user")
                && !PLACEHOLDER_API_KEY.equals(exchange.getRequestHeaders().getFirst("apikey"))) {
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
                return;
            }

            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBody.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBody);
            }
        }
    }
}
