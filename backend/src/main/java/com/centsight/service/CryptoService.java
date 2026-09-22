package com.centsight.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * AES-256-GCM for Plaid access tokens at rest, wire-compatible with the original Node service:
 * stored as iv.tag.ciphertext with each part base64.
 */
@Service
public class CryptoService {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public CryptoService(@Value("${centsight.encryption-key}") String hexKey) {
        byte[] raw;
        try {
            raw = HexFormat.of().parseHex(hexKey.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("centsight.encryption-key must be hex characters", e);
        }
        if (raw.length != 32) {
            throw new IllegalStateException(
                "centsight.encryption-key must be 64 hex characters (32 bytes). Generate with: openssl rand -hex 32");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Java appends the tag to the ciphertext; split it out to match the iv.tag.ciphertext layout.
            int tagBytes = TAG_BITS / 8;
            int bodyLen = sealed.length - tagBytes;
            byte[] body = new byte[bodyLen];
            byte[] tag = new byte[tagBytes];
            System.arraycopy(sealed, 0, body, 0, bodyLen);
            System.arraycopy(sealed, bodyLen, tag, 0, tagBytes);

            Base64.Encoder b64 = Base64.getEncoder();
            return b64.encodeToString(iv) + "." + b64.encodeToString(tag) + "." + b64.encodeToString(body);
        } catch (Exception e) {
            throw new IllegalStateException("Could not encrypt access token", e);
        }
    }

    public String decrypt(String payload) {
        try {
            String[] parts = payload.split("\\.");
            if (parts.length != 3) throw new IllegalArgumentException("expected iv.tag.ciphertext");

            Base64.Decoder b64 = Base64.getDecoder();
            byte[] iv = b64.decode(parts[0]);
            byte[] tag = b64.decode(parts[1]);
            byte[] body = b64.decode(parts[2]);

            byte[] sealed = new byte[body.length + tag.length];
            System.arraycopy(body, 0, sealed, 0, body.length);
            System.arraycopy(tag, 0, sealed, body.length, tag.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(sealed), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Could not decrypt access token", e);
        }
    }
}
