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
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
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
    void checksActiveUserOnEveryRequestWhileReusingJwks() throws Exception {
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

        Optional<SupabaseJwtVerifier.VerifiedUser> verifiedUser = verifier.verify("opaque-token");

        assertThat(verifiedUser).isPresent();
        assertThat(verifiedUser.orElseThrow().userId()).isEqualTo(PLACEHOLDER_USER_ID);
        assertThat(verifiedUser.orElseThrow().email()).isEqualTo("player@example.test");
        assertThat(verifiedUser.orElseThrow().nickname()).isEqualTo("PlayerAlpha");
        assertThat(authUserRequestCount.get()).isEqualTo(1);
    }

    @Test
    void rejectsCachedTokenWhenAuthUserBecomesUnavailable() throws Exception {
        String baseUrl = startJwksServer(new JWKSet().toString());
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

        SupabaseAuthProperties properties = new SupabaseAuthProperties();
        properties.setSupabaseUrl(baseUrl);
        properties.setApiKey(PLACEHOLDER_API_KEY);
        properties.setVerifyTimeoutMs(1000);
        properties.setVerificationCacheTtlSeconds(60);

        SupabaseJwtVerifier verifier = new SupabaseJwtVerifier(properties);

        assertThat(verifier.verify("placeholder-access-token")).isPresent();
        assertThat(verifier.verify("placeholder-access-token")).isEmpty();
        assertThat(authUserRequestCount.get()).isEqualTo(2);
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
