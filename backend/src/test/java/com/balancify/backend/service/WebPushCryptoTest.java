package com.balancify.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class WebPushCryptoTest {

    // RFC 8291, Appendix A.
    private static final String PLAINTEXT = "V2hlbiBJIGdyb3cgdXAsIEkgd2FudCB0byBiZSBhIHdhdGVybWVsb24";
    private static final String AS_PUBLIC = "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String AS_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String UA_PUBLIC = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
    private static final String AUTH_SECRET = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String CIPHERTEXT =
        "8pfeW0KbunFT06SuDKoJH9Ql87S1QUrdirN6GcG7sFz1y1sqLgVi1VhjVkHsUoEsbI_0LpXMuGvnzQ";

    @Test
    void encryptsTheRfc8291ExampleExactly() {
        byte[] body = WebPushCrypto.encrypt(
            WebPushCrypto.decode(PLAINTEXT),
            WebPushCrypto.decode(UA_PUBLIC),
            WebPushCrypto.decode(AUTH_SECRET),
            WebPushCrypto.privateKey(WebPushCrypto.decode(AS_PRIVATE)),
            WebPushCrypto.publicKey(WebPushCrypto.decode(AS_PUBLIC)),
            WebPushCrypto.decode(SALT)
        );

        byte[] header = Arrays.copyOfRange(body, 0, 86);
        assertThat(Arrays.copyOfRange(header, 0, 16)).isEqualTo(WebPushCrypto.decode(SALT));
        assertThat(Arrays.copyOfRange(header, 16, 21)).containsExactly(0, 0, 16, 0, 65);
        assertThat(Arrays.copyOfRange(header, 21, 86)).isEqualTo(WebPushCrypto.decode(AS_PUBLIC));
        assertThat(WebPushCrypto.encode(Arrays.copyOfRange(body, 86, body.length))).isEqualTo(CIPHERTEXT);
    }

    @Test
    void signsAVapidTokenForTheEndpointOrigin() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        var pair = generator.generateKeyPair();
        String publicKey = WebPushCrypto.encode(WebPushCrypto.encodePoint((ECPublicKey) pair.getPublic()));
        byte[] scalar = ((ECPrivateKey) pair.getPrivate()).getS().toByteArray();
        byte[] privateBytes = new byte[32];
        int length = Math.min(scalar.length, 32);
        System.arraycopy(scalar, scalar.length - length, privateBytes, 32 - length, length);
        WebPushCrypto.VapidKeys keys = WebPushCrypto.vapidKeys(publicKey, WebPushCrypto.encode(privateBytes));

        String authorization = WebPushCrypto.vapidAuthorization(
            "https://fcm.googleapis.com/fcm/send/YOUR_TOKEN",
            "mailto:YOUR_USERNAME@example.com",
            keys,
            Instant.ofEpochSecond(1_700_000_000L)
        );

        assertThat(authorization).startsWith("vapid t=").endsWith(", k=" + publicKey);
        String token = authorization.substring("vapid t=".length(), authorization.indexOf(", k="));
        String[] parts = token.split("\\.");
        String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(claims).isEqualTo(
            "{\"aud\":\"https://fcm.googleapis.com\",\"exp\":1700043200,\"sub\":\"mailto:YOUR_USERNAME@example.com\"}"
        );
        assertThat(WebPushCrypto.verify(
            keys.publicKey(),
            (parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII),
            WebPushCrypto.decode(parts[2])
        )).isTrue();
    }

    @Test
    void refusesKeysThatDoNotBelongTogether() {
        assertThatThrownBy(() -> WebPushCrypto.vapidKeys(UA_PUBLIC, AS_PRIVATE)).isInstanceOf(IllegalArgumentException.class);
        assertThat(WebPushCrypto.vapidKeys(AS_PUBLIC, AS_PRIVATE).publicKeyBase64()).isEqualTo(AS_PUBLIC);
    }

    @Test
    void checksSubscriptionKeys() {
        assertThat(WebPushCrypto.isValidSubscriptionKey(UA_PUBLIC, AUTH_SECRET)).isTrue();
        assertThat(WebPushCrypto.isValidSubscriptionKey(UA_PUBLIC, SALT + "AA")).isFalse();
        assertThat(WebPushCrypto.isValidSubscriptionKey("not-a-key", AUTH_SECRET)).isFalse();
    }
}
