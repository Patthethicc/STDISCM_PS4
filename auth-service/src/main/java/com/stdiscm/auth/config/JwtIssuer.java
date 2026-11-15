package com.stdiscm.auth.config;

import com.stdiscm.auth.model.User;
import io.jsonwebtoken.Jwts;

import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/** Signs access tokens with the private key. Only the auth service holds that key. */
public class JwtIssuer {

    public record IssuedToken(String token, String tokenId, Instant expiresAt) { }

    private final PrivateKey privateKey;
    private final Duration ttl;

    public JwtIssuer(PrivateKey privateKey, Duration ttl) {
        this.privateKey = privateKey;
        this.ttl = ttl;
    }

    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        Instant exp = now.plus(ttl);
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .id(jti)
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .claim("name", user.getName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
        return new IssuedToken(token, jti, exp);
    }
}
