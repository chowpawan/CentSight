package com.centsight;

import com.centsight.service.CryptoService;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CryptoServiceTest {

    private static final String KEY_A = "0".repeat(64);
    private static final String KEY_B = "f".repeat(64);

    private final CryptoService crypto = new CryptoService(KEY_A);

    @Test
    void roundTripsAnAccessToken() {
        String token = "access-sandbox-11111111-2222-3333-4444-555555555555";
        assertEquals(token, crypto.decrypt(crypto.encrypt(token)));
    }

    @Test
    void producesDifferentCiphertextEachTime() {
        String token = "access-sandbox-abc";
        assertNotEquals(crypto.encrypt(token), crypto.encrypt(token), "a fresh IV must be used per encryption");
    }

    @Test
    void usesTheIvTagCiphertextLayout() {
        String[] parts = crypto.encrypt("hello").split("\\.");
        assertEquals(3, parts.length);
        assertEquals(12, Base64.getDecoder().decode(parts[0]).length, "12-byte IV");
        assertEquals(16, Base64.getDecoder().decode(parts[1]).length, "16-byte GCM tag");
    }

    @Test
    void refusesToDecryptWithTheWrongKey() {
        String sealed = crypto.encrypt("access-sandbox-abc");
        CryptoService other = new CryptoService(KEY_B);
        assertThrows(IllegalStateException.class, () -> other.decrypt(sealed));
    }

    @Test
    void detectsTamperedCiphertext() {
        String[] parts = crypto.encrypt("access-sandbox-abc").split("\\.");
        byte[] body = Base64.getDecoder().decode(parts[2]);
        body[0] ^= 0x01; // flip one bit
        String tampered = parts[0] + "." + parts[1] + "." + Base64.getEncoder().encodeToString(body);
        assertThrows(IllegalStateException.class, () -> crypto.decrypt(tampered));
    }

    @Test
    void rejectsAKeyOfTheWrongLength() {
        assertThrows(IllegalStateException.class, () -> new CryptoService("abcd"));
    }
}
