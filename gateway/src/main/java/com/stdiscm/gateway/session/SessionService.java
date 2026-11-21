package com.stdiscm.gateway.session;

import com.stdiscm.gateway.config.GatewayProperties;
import com.stdiscm.gateway.error.UnauthorizedException;
import com.stdiscm.sharedauth.AuthContext.Principal;
import com.stdiscm.sharedauth.JwtVerifier;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads the session from the HttpOnly cookie and verifies the JWT locally.
 *
 * Logout: the auth service adds the token ID to its revocation list, and the gateway
 * also remembers it here until it expires, so reusing an old cookie after logout sends
 * the user back to the login page. The service nodes only check signature and expiry,
 * so a stolen token still works against them for at most 15 minutes (the trade-off).
 */
@Service
public class SessionService {

    public record Session(String token, Principal principal) { }

    private final JwtVerifier verifier;
    private final GatewayProperties.Cookie cookie;
    private final Map<String, Instant> loggedOut = new ConcurrentHashMap<>();

    public SessionService(JwtVerifier verifier, GatewayProperties props) {
        this.verifier = verifier;
        this.cookie = props.cookie();
    }

    public Optional<Session> current(HttpServletRequest request) {
        String token = readCookie(request);
        if (token == null) {
            return Optional.empty();
        }
        try {
            Principal p = verifier.verify(token);
            if (loggedOut.containsKey(p.tokenId())) {
                return Optional.empty();
            }
            return Optional.of(new Session(token, p));
        } catch (JwtException e) {
            return Optional.empty(); // bad signature or expired
        }
    }

    public Session require(HttpServletRequest request) {
        return current(request).orElseThrow(UnauthorizedException::new);
    }

    public Principal verify(String token) {
        return verifier.verify(token);
    }

    public void markLoggedOut(Principal p) {
        Instant now = Instant.now();
        loggedOut.values().removeIf(expiry -> expiry.isBefore(now));
        loggedOut.put(p.tokenId(), p.expiresAt());
    }

    public ResponseCookie sessionCookie(String token, Instant expiresAt) {
        long seconds = Math.max(0, Duration.between(Instant.now(), expiresAt).getSeconds());
        return base(token).maxAge(seconds).build();
    }

    public ResponseCookie clearedCookie() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(cookie.name(), value)
                .httpOnly(true)
                .secure(cookie.secure())
                .sameSite("Lax")
                .path("/");
    }

    private String readCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie c : request.getCookies()) {
            if (cookie.name().equals(c.getName()) && !c.getValue().isBlank()) {
                return c.getValue();
            }
        }
        return null;
    }
}
