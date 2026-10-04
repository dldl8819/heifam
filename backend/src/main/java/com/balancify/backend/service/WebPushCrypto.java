package com.balancify.backend.service;

import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Web Push with the JDK alone: the aes128gcm message encryption of RFC 8291 and the VAPID
 * authorization of RFC 8292, both on P-256 keys in their uncompressed base64url form.
 */
public final class WebPushCrypto {

    static final int RECORD_SIZE = 4096;
    // Header (salt, record size, key id length, key id), the padding delimiter and the GCM tag.
    private static final int OVERHEAD = 16 + 4 + 1 + 65 + 1 + 16;
    private static final Duration VAPID_LIFETIME = Duration.ofHours(12);
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ECParameterSpec P256 = p256();

    private WebPushCrypto() {
    }

    /** The application server's VAPID key pair. */
    public record VapidKeys(ECPublicKey publicKey, ECPrivateKey privateKey) {

        public String publicKeyBase64() {
            return encode(encodePoint(publicKey));
        }
    }

    /** Reads the keys and checks that they belong together; throws IllegalArgumentException otherwise. */
    public static VapidKeys vapidKeys(String publicKeyBase64, String privateKeyBase64) {
        ECPublicKey publicKey = publicKey(decode(publicKeyBase64));
        byte[] privateBytes = decode(privateKeyBase64);
        if (privateBytes.length != 32) {
            throw new IllegalArgumentException("VAPID private key must be 32 bytes");
        }
        VapidKeys keys = new VapidKeys(publicKey, privateKey(privateBytes));
        byte[] probe = "vapid".getBytes(StandardCharsets.US_ASCII);
        if (!verify(keys.publicKey(), probe, sign(keys.privateKey(), probe))) {
            throw new IllegalArgumentException("VAPID keys do not belong together");
        }
        return keys;
    }

    /** The RFC 8291 body for one message to a subscription's p256dh key and auth secret. */
    public static byte[] encrypt(byte[] plaintext, String userAgentPublicKey, String authSecret) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(P256, RANDOM);
            KeyPair ephemeral = generator.generateKeyPair();
            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);
            return encrypt(
                plaintext,
                decode(userAgentPublicKey),
                decode(authSecret),
                (ECPrivateKey) ephemeral.getPrivate(),
                (ECPublicKey) ephemeral.getPublic(),
                salt
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Web Push encryption is unavailable", exception);
        }
    }

    static byte[] encrypt(
        byte[] plaintext,
        byte[] userAgentPublic,
        byte[] authSecret,
        ECPrivateKey serverPrivate,
        ECPublicKey serverPublic,
        byte[] salt
    ) {
        if (plaintext.length + OVERHEAD > RECORD_SIZE) {
            throw new IllegalArgumentException("Push message is too long");
        }
        if (authSecret.length != 16 || salt.length != 16) {
            throw new IllegalArgumentException("Push auth secret and salt must be 16 bytes");
        }
        try {
            byte[] serverPublicBytes = encodePoint(serverPublic);
            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(serverPrivate);
            agreement.doPhase(publicKey(userAgentPublic), true);
            byte[] ecdhSecret = agreement.generateSecret();

            byte[] prkKey = hmac(authSecret, ecdhSecret);
            byte[] keyInfo = concat(ascii("WebPush: info"), new byte[] {0}, userAgentPublic, serverPublicBytes);
            byte[] ikm = hmac(prkKey, concat(keyInfo, new byte[] {1}));
            byte[] prk = hmac(salt, ikm);
            byte[] contentKey = Arrays.copyOf(hmac(prk, concat(ascii("Content-Encoding: aes128gcm"), new byte[] {0, 1})), 16);
            byte[] nonce = Arrays.copyOf(hmac(prk, concat(ascii("Content-Encoding: nonce"), new byte[] {0, 1})), 12);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(contentKey, "AES"), new GCMParameterSpec(128, nonce));
            // A single record: the plaintext followed by the last-record delimiter.
            byte[] ciphertext = cipher.doFinal(concat(plaintext, new byte[] {2}));

            ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + serverPublicBytes.length);
            header.put(salt).putInt(RECORD_SIZE).put((byte) serverPublicBytes.length).put(serverPublicBytes);
            return concat(header.array(), ciphertext);
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("Push subscription keys are not usable", exception);
        }
    }

    /** The Authorization header value for a push endpoint: a signed JWT for its origin, and the public key. */
    public static String vapidAuthorization(String endpoint, String subject, VapidKeys keys, Instant now) {
        URI uri = URI.create(endpoint);
        String audience = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
        String header = encode(ascii("{\"typ\":\"JWT\",\"alg\":\"ES256\"}"));
        String claims = encode(("{\"aud\":\"" + jsonText(audience) + "\",\"exp\":"
            + now.plus(VAPID_LIFETIME).getEpochSecond() + ",\"sub\":\"" + jsonText(subject) + "\"}")
            .getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + claims;
        String signature = encode(sign(keys.privateKey(), ascii(signingInput)));
        return "vapid t=" + signingInput + "." + signature + ", k=" + keys.publicKeyBase64();
    }

    /** Checks a p256dh key and auth secret as browsers send them. */
    public static boolean isValidSubscriptionKey(String userAgentPublicKey, String authSecret) {
        try {
            publicKey(decode(userAgentPublicKey));
            return decode(authSecret).length == 16;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static byte[] sign(ECPrivateKey privateKey, byte[] data) {
        try {
            // JOSE wants R and S side by side, not DER.
            Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
            signer.initSign(privateKey);
            signer.update(data);
            return signer.sign();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("VAPID signing is unavailable", exception);
        }
    }

    static boolean verify(ECPublicKey publicKey, byte[] data, byte[] signature) {
        try {
            Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
            verifier.initVerify(publicKey);
            verifier.update(data);
            return verifier.verify(signature);
        } catch (GeneralSecurityException exception) {
            return false;
        }
    }

    static ECPublicKey publicKey(byte[] uncompressed) {
        if (uncompressed.length != 65 || uncompressed[0] != 4) {
            throw new IllegalArgumentException("Expected an uncompressed P-256 public key");
        }
        try {
            ECPoint point = new ECPoint(
                new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65))
            );
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, P256));
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("Not a P-256 public key", exception);
        }
    }

    static ECPrivateKey privateKey(byte[] scalar) {
        try {
            return (ECPrivateKey) KeyFactory.getInstance("EC")
                .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, scalar), P256));
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("Not a P-256 private key", exception);
        }
    }

    static byte[] encodePoint(ECPublicKey key) {
        byte[] encoded = new byte[65];
        encoded[0] = 4;
        copyUnsigned(key.getW().getAffineX(), encoded, 1);
        copyUnsigned(key.getW().getAffineY(), encoded, 33);
        return encoded;
    }

    static byte[] decode(String base64Url) {
        if (base64Url == null || base64Url.isBlank()) {
            throw new IllegalArgumentException("Missing key");
        }
        return DECODER.decode(base64Url.trim().replace('+', '-').replace('/', '_').replace("=", ""));
    }

    static String encode(byte[] bytes) {
        return ENCODER.encodeToString(bytes);
    }

    private static void copyUnsigned(BigInteger value, byte[] target, int offset) {
        byte[] bytes = value.toByteArray();
        int length = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - length, target, offset + 32 - length, length);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        byte[] joined = new byte[length];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, joined, offset, part.length);
            offset += part.length;
        }
        return joined;
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static String jsonText(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("P-256 is unavailable", exception);
        }
    }
}
