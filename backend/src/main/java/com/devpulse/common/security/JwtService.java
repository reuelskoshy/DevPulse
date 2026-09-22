package com.devpulse.common.security;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import com.devpulse.auth.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
    }

    public String createAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenExpiration())))
                .signWith(signingKey)
                .compact();
    }

    public UserPrincipal parse(String token) {
        Claims claims = Jwts.parser().verifyWith(signingKey).build()
                .parseSignedClaims(token)
                .getPayload();
        return new UserPrincipal(
                UUID.fromString(claims.getSubject()),
                claims.get("email", String.class),
                claims.get("role", String.class));
    }
}
