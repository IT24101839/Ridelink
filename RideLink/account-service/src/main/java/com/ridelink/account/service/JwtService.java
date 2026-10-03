package com.ridelink.account.service;

import com.ridelink.account.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(JwtProperties jwtProperties) {
        this.signingKey = Keys.hmacShaKeyFor(
                jwtProperties.secret().getBytes(StandardCharsets.UTF_8)
        );
        this.expirationMs = jwtProperties.expirationMs();
    }

    public String generateToken(
            String subject,
            String role,
            String userId) {

        long now = System.currentTimeMillis();

        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .claim("userId", userId)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractSubject(String token) {
        return extractAllClaims(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            String userId = claims.get("userId", String.class);
            String role = claims.get("role", String.class);
            return claims.getExpiration() != null && claims.getExpiration().after(new Date())
                    && claims.getSubject() != null && !claims.getSubject().isBlank()
                    && userId != null && !userId.isBlank()
                    && role != null && java.util.Set.of("PASSENGER", "DRIVER", "ADMIN").contains(role);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}