package com.gyanwire.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;
    private final long accessSeconds;
    private final long refreshSeconds;

    public JwtService(
            @Value("${app.auth.jwt.secret}") String secret,
            @Value("${app.auth.jwt.access-expiration-seconds}") long accessSeconds,
            @Value("${app.auth.jwt.refresh-expiration-seconds}") long refreshSeconds
    ) {
        if (secret == null || secret.length() < 16) {
            throw new IllegalStateException("APP_AUTH_JWT_SECRET / JWT_SECRET must be 16+ characters.");
        }
        this.key = Keys.hmacShaKeyFor(toKeyBytes(secret));
        this.accessSeconds = accessSeconds;
        this.refreshSeconds = refreshSeconds;
    }

    private static byte[] toKeyBytes(String secret) {
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        if (raw.length >= 32) {
            return raw;
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(raw);
        } catch (Exception e) {
            byte[] padded = new byte[32];
            System.arraycopy(raw, 0, padded, 0, raw.length);
            return padded;
        }
    }

    public long getAccessSeconds() { return accessSeconds; }
    public long getRefreshSeconds() { return refreshSeconds; }

    public String generateAccessToken(UUID userId, String role, long tokenVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("tokenType", "access")
                .claim("role", role == null ? "user" : role)
                .claim("tokenVersion", tokenVersion)
                .claim("aud", "web")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessSeconds)))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(UUID userId, long tokenVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("tokenType", "refresh")
                .claim("tokenVersion", tokenVersion)
                .claim("deviceId", "browser")
                .id(UUID.randomUUID().toString())
                .claim("aud", "web")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(refreshSeconds)))
                .signWith(key)
                .compact();
    }

    public Claims parseAndValidate(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
