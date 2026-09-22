package com.centsight;

import com.centsight.security.JwtService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET = "a-test-secret-that-is-at-least-32-bytes-long-for-hmac";
    private final JwtService jwt = new JwtService(SECRET, 30);

    @Test
    void issuesATokenThatVerifiesBackToTheSameUser() {
        UUID id = UUID.randomUUID();
        String token = jwt.issue(id, "someone@example.com");
        assertEquals(id, jwt.verify(token).orElseThrow());
    }

    @Test
    void rejectsATokenSignedWithAnotherSecret() {
        String foreign = new JwtService("a-completely-different-secret-of-sufficient-length", 30)
                .issue(UUID.randomUUID(), "attacker@example.com");
        assertTrue(jwt.verify(foreign).isEmpty(), "a token we did not sign must not authenticate anyone");
    }

    @Test
    void rejectsATokenWhoseClaimsWereEdited() {
        UUID mine = UUID.randomUUID();
        UUID victim = UUID.randomUUID();
        String token = jwt.issue(mine, "me@example.com");

        // Swap the subject for someone else's id, keeping the original signature.
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace(mine.toString(), victim.toString());
        String forged = parts[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "." + parts[2];

        assertTrue(jwt.verify(forged).isEmpty(), "editing the subject must invalidate the signature");
    }

    @Test
    void rejectsATruncatedSignature() {
        String token = jwt.issue(UUID.randomUUID(), "someone@example.com");
        assertTrue(jwt.verify(token.substring(0, token.length() - 4)).isEmpty());
    }

    @Test
    void rejectsAnExpiredToken() throws Exception {
        JwtService shortLived = new JwtService(SECRET, 0); // expires immediately
        String token = shortLived.issue(UUID.randomUUID(), "someone@example.com");
        Thread.sleep(1100);
        assertTrue(shortLived.verify(token).isEmpty());
    }

    @Test
    void rejectsGarbage() {
        assertTrue(jwt.verify("not-a-jwt").isEmpty());
        assertTrue(jwt.verify("").isEmpty());
    }

    @Test
    void refusesAWeakSecret() {
        assertThrows(IllegalStateException.class, () -> new JwtService("too-short", 30));
    }
}
