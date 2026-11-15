package com.stdiscm.sharedauth;

import com.stdiscm.sharedauth.AuthContext.Principal;
import com.stdiscm.sharedauth.AuthContext.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import java.security.PublicKey;

/**
 * Verifies a JWT's RS256 signature and expiry using the public key. It never talks to
 * the auth service, so every node keeps working when the auth node is down.
 */
public class JwtVerifier {

    private final io.jsonwebtoken.JwtParser parser;

    public JwtVerifier(PublicKey publicKey) {
        this.parser = Jwts.parser().verifyWith(publicKey).build();
    }

    /** @throws JwtException if the token is malformed, badly signed or expired */
    public Principal verify(String token) {
        Claims c = parser.parseSignedClaims(token).getPayload();
        try {
            return new Principal(
                    c.getSubject(),
                    Role.valueOf(c.get("role", String.class)),
                    c.get("name", String.class),
                    c.getId(),
                    c.getExpiration().toInstant());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new JwtException("token is missing required claims", e);
        }
    }
}
