package com.lordsai.lsi.security;

import com.lordsai.lsi.config.AppProperties;
import com.lordsai.lsi.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    static final String CLAIM_ROLE = "role";
    static final String CLAIM_TOKEN_VERSION = "tv";

    private final SecretKey key;
    private final String issuer;
    private final Duration accessTokenTtl;

    public JwtService(AppProperties properties) {
        String secret = properties.jwt().secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set and at least 32 bytes long. See backend/.env.example.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = properties.jwt().issuer();
        this.accessTokenTtl = Duration.ofMinutes(properties.jwt().accessTokenMinutes());
    }

    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }

    public String issueAccessToken(User user, String tokenId, Instant expiresAt) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(tokenId)
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_TOKEN_VERSION, user.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    /** Returns the verified claims, or empty for any tampered, expired or malformed token. */
    public Optional<Claims> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
