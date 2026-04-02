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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SupabaseJwtVerifierTest {

    private HttpServer jwksServer;

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

        SupabaseAuthProperties properties = new SupabaseAuthProperties();
        properties.setSupabaseUrl(baseUrl);
        properties.setVerifyTimeoutMs(1000);
        properties.setVerificationCacheTtlSeconds(60);

        SupabaseJwtVerifier verifier = new SupabaseJwtVerifier(properties);

        String token = createToken(
            signingKey,
            baseUrl + "/auth/v1",
            "member@hei.gg",
            Map.of("full_name", "민식")
        );

        Optional<SupabaseJwtVerifier.VerifiedUser> verifiedUser = verifier.verify(token);

        assertThat(verifiedUser).isPresent();
        assertThat(verifiedUser.orElseThrow().email()).isEqualTo("member@hei.gg");
        assertThat(verifiedUser.orElseThrow().nickname()).isEqualTo("민식");
    }

    @Test
    void rejectsTokenWithUnexpectedIssuer() throws Exception {
        ECKey signingKey = new ECKeyGenerator(Curve.P_256)
            .keyID("hei-test-key")
            .generate();
        String baseUrl = startJwksServer(new JWKSet(signingKey.toPublicJWK()).toString());

        SupabaseAuthProperties properties = new SupabaseAuthProperties();
        properties.setSupabaseUrl(baseUrl);
        properties.setVerifyTimeoutMs(1000);
        properties.setVerificationCacheTtlSeconds(60);

        SupabaseJwtVerifier verifier = new SupabaseJwtVerifier(properties);

        String token = createToken(
            signingKey,
            "https://wrong.example.com/auth/v1",
            "member@hei.gg",
            Map.of("full_name", "민식")
        );

        assertThat(verifier.verify(token)).isEmpty();
    }

    private String startJwksServer(String responseBody) throws IOException {
        jwksServer = HttpServer.create(new InetSocketAddress(0), 0);
        jwksServer.createContext("/auth/v1/.well-known/jwks.json", new StaticBodyHandler(responseBody));
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
        SignedJWT jwt = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(signingKey.getKeyID())
                .type(JOSEObjectType.JWT)
                .build(),
            new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("test-user-id")
                .audience("authenticated")
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(600)))
                .claim("email", email)
                .claim("user_metadata", userMetadata)
                .build()
        );
        jwt.sign(new ECDSASigner(signingKey));
        return jwt.serialize();
    }

    private static class StaticBodyHandler implements HttpHandler {

        private final byte[] responseBody;

        private StaticBodyHandler(String responseBody) {
            this.responseBody = responseBody.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBody.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBody);
            }
        }
    }
}
