package com.campus.repair.security;

import com.campus.repair.entity.UserEntity;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

@Component
public class JwtUtils {
    private final JwtProperties properties;
    private final Clock clock;
    private final NimbusJwtEncoder encoder;
    private final NimbusJwtDecoder decoder;

    public JwtUtils(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        byte[] key;
        try {
            key = Base64.getDecoder().decode(properties.secret());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT_SECRET must be a Base64-encoded random secret");
        }
        if (key.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 random bytes");
        }
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(key, "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator timestamp = new JwtTimestampValidator(Duration.ZERO);
        timestamp.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamp,
                new JwtIssuerValidator(properties.issuer())));
    }

    public Jwt generate(UserEntity user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(properties.issuer())
                .subject(String.valueOf(user.getId())).issuedAt(now).notBefore(now)
                .expiresAt(now.plusSeconds(properties.ttlSeconds())).id(UUID.randomUUID().toString())
                .claim("ver", user.getTokenVersion()).claim("role", user.getRole().name()).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims));
    }

    public Jwt parse(String token) {
        Jwt jwt = decoder.decode(token);
        if (jwt.getExpiresAt() == null || jwt.getIssuedAt() == null || jwt.getSubject() == null
                || !jwt.getSubject().matches("[1-9][0-9]{0,18}")
                || !(jwt.getClaim("ver") instanceof Number version) || version.longValue() < 0) {
            throw new BadJwtException("Invalid token claims");
        }
        try {
            Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new BadJwtException("Invalid token subject");
        }
        return jwt;
    }
}
