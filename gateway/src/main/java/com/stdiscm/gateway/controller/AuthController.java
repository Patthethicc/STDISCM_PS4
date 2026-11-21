package com.stdiscm.gateway.controller;

import com.stdiscm.gateway.dto.LoginDto;
import com.stdiscm.gateway.dto.SessionDto;
import com.stdiscm.gateway.error.ServiceCallException;
import com.stdiscm.gateway.grpc.AuthClient;
import com.stdiscm.gateway.session.SessionService;
import com.stdiscm.proto.auth.LoginResponse;
import com.stdiscm.sharedauth.AuthContext.Principal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthClient auth;
    private final SessionService sessions;

    public AuthController(AuthClient auth, SessionService sessions) {
        this.auth = auth;
        this.sessions = sessions;
    }

    /** Calls the auth node; on success stores the JWT in an HttpOnly cookie. */
    @PostMapping("/login")
    public ResponseEntity<SessionDto> login(@Valid @RequestBody LoginDto body) {
        LoginResponse res = auth.login(body.username(), body.password());
        Principal p = sessions.verify(res.getToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        sessions.sessionCookie(res.getToken(), Instant.ofEpochSecond(res.getExpiresAtEpochSeconds())).toString())
                .body(SessionDto.from(p));
    }

    /**
     * Revokes the token on the auth node and always clears the cookie. If the auth node is
     * down, the gateway still forgets the token, so the user is logged out of the website.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        sessions.current(request).ifPresent(s -> {
            sessions.markLoggedOut(s.principal());
            try {
                auth.logout(s.token());
            } catch (ServiceCallException e) {
                log.warn("auth service logout failed ({}); cookie cleared anyway", e.grpcError().getStatus().getCode());
            }
        });
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessions.clearedCookie().toString())
                .build();
    }

    /** Who is logged in. Read from the token, so it works even when the auth node is down. */
    @GetMapping("/me")
    public SessionDto me(HttpServletRequest request) {
        return SessionDto.from(sessions.require(request).principal());
    }
}
