package com.campus.repair.security;

import static org.junit.jupiter.api.Assertions.*;

import com.campus.repair.entity.UserEntity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtUtilsTest {
    private static final Instant NOW = Instant.parse("2026-09-30T00:00:00Z");
    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);
    private final JwtProperties properties = new JwtProperties(SECRET, "campus-repair", 7200);
    private final JwtUtils jwt = new JwtUtils(properties, Clock.fixed(NOW, ZoneOffset.UTC));

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setRole(UserRole.STUDENT);
        user.setTokenVersion(3L);
        return user;
    }

    @Test void validTokenRetainsIdentityVersionAndExpiration() {
        var token = jwt.parse(jwt.generate(user()).getTokenValue());
        assertEquals("1", token.getSubject());
        assertEquals(3L, ((Number) token.getClaim("ver")).longValue());
        assertEquals(NOW.plusSeconds(7200), token.getExpiresAt());
    }

    @Test void tamperedTokenIsRejected() {
        String token = jwt.generate(user()).getTokenValue();
        String[] parts = token.split("\\.");
        parts[1] = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"2\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(JwtException.class, () -> jwt.parse(String.join(".", parts)));
    }

    @Test void expirationIsEnforcedWithoutGracePeriod() {
        String token = jwt.generate(user()).getTokenValue();
        JwtUtils expired = new JwtUtils(properties, Clock.fixed(NOW.plusSeconds(7201), ZoneOffset.UTC));
        assertThrows(JwtException.class, () -> expired.parse(token));
    }

    @Test void futureTokenIsRejected() {
        JwtUtils future = new JwtUtils(properties, Clock.fixed(NOW.plusSeconds(300), ZoneOffset.UTC));
        assertThrows(JwtException.class, () -> jwt.parse(future.generate(user()).getTokenValue()));
    }

    @Test void wrongIssuerIsRejected() {
        JwtUtils other = new JwtUtils(new JwtProperties(SECRET, "another-app", 7200), Clock.fixed(NOW, ZoneOffset.UTC));
        assertThrows(JwtException.class, () -> jwt.parse(other.generate(user()).getTokenValue()));
    }

    @Test void wrongSigningKeyIsRejected() {
        byte[] key = new byte[32];
        key[0] = 1;
        JwtUtils other = new JwtUtils(new JwtProperties(Base64.getEncoder().encodeToString(key), "campus-repair", 7200), Clock.fixed(NOW, ZoneOffset.UTC));
        assertThrows(JwtException.class, () -> jwt.parse(other.generate(user()).getTokenValue()));
    }

    @Test void malformedTokenIsRejected() {
        assertThrows(JwtException.class, () -> jwt.parse("not-a-jwt"));
    }

    @Test void missingOrWeakSecretIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new JwtUtils(new JwtProperties("placeholder", "campus-repair", 7200), Clock.systemUTC()));
        assertThrows(IllegalArgumentException.class, () -> new JwtUtils(new JwtProperties(Base64.getEncoder().encodeToString(new byte[16]), "campus-repair", 7200), Clock.systemUTC()));
    }
}
