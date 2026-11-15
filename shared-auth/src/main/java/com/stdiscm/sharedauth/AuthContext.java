package com.stdiscm.sharedauth;

import io.grpc.Context;
import io.grpc.Status;

import java.time.Instant;

/**
 * Holds the authenticated caller for the duration of one gRPC call.
 * Populated by {@link AuthServerInterceptor}; read it from service code with
 * {@link #current()} or, when a token is mandatory, {@link #require()}.
 */
public final class AuthContext {

    public enum Role { STUDENT, FACULTY }

    public record Principal(String userId, Role role, String name, String tokenId, Instant expiresAt) { }

    static final Context.Key<Principal> KEY = Context.key("stdiscm-principal");

    private AuthContext() { }

    /** The caller, or null when the call carried no valid token (public methods only). */
    public static Principal current() {
        return KEY.get();
    }

    /** The caller; fails the call with UNAUTHENTICATED when there is none. */
    public static Principal require() {
        Principal p = KEY.get();
        if (p == null) {
            throw Status.UNAUTHENTICATED.withDescription("authentication required").asRuntimeException();
        }
        return p;
    }
}
