package com.stdiscm.sharedauth;

import com.stdiscm.sharedauth.AuthContext.Principal;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.jsonwebtoken.JwtException;

import java.util.Set;

/**
 * Reads {@code authorization: Bearer <jwt>}, verifies it with the public key and puts the
 * {@link Principal} in the gRPC {@link Context}. Methods in {@code publicMethods}
 * (full names like {@code stdiscm.auth.AuthService/Login}) are let through without a token.
 * Role checks are done in the service with {@link RoleGuard}.
 */
public class AuthServerInterceptor implements ServerInterceptor {

    static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private static final String BEARER = "Bearer ";

    private final JwtVerifier verifier;
    private final Set<String> publicMethods;

    public AuthServerInterceptor(JwtVerifier verifier, Set<String> publicMethods) {
        this.verifier = verifier;
        this.publicMethods = publicMethods;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        boolean isPublic = publicMethods.contains(call.getMethodDescriptor().getFullMethodName());
        String header = headers.get(AUTHORIZATION);

        if (header == null || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            if (isPublic) {
                return next.startCall(call, headers);
            }
            return reject(call, "missing or malformed authorization header");
        }

        Principal principal;
        try {
            principal = verifier.verify(header.substring(BEARER.length()).trim());
        } catch (JwtException | IllegalArgumentException e) {
            if (isPublic) {
                return next.startCall(call, headers);
            }
            return reject(call, "invalid or expired token");
        }

        Context ctx = Context.current().withValue(AuthContext.KEY, principal);
        return new StatusPropagatingListener<>(call, Contexts.interceptCall(ctx, call, headers, next));
    }

    /**
     * Service code (e.g. {@link RoleGuard}) signals failures by throwing StatusRuntimeException.
     * Plain gRPC would report those as UNKNOWN, so close the call with the thrown status instead.
     */
    private static final class StatusPropagatingListener<ReqT>
            extends io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {

        private final ServerCall<ReqT, ?> call;

        StatusPropagatingListener(ServerCall<ReqT, ?> call, ServerCall.Listener<ReqT> delegate) {
            super(delegate);
            this.call = call;
        }

        @Override
        public void onMessage(ReqT message) {
            try {
                super.onMessage(message);
            } catch (io.grpc.StatusRuntimeException e) {
                closeWith(e);
            }
        }

        @Override
        public void onHalfClose() {
            try {
                super.onHalfClose();
            } catch (io.grpc.StatusRuntimeException e) {
                closeWith(e);
            }
        }

        private void closeWith(io.grpc.StatusRuntimeException e) {
            try {
                Metadata trailers = e.getTrailers() != null ? e.getTrailers() : new Metadata();
                call.close(e.getStatus(), trailers);
            } catch (IllegalStateException alreadyClosed) {
                // the service had already closed the call; nothing more to report
            }
        }
    }

    private static <ReqT, RespT> ServerCall.Listener<ReqT> reject(ServerCall<ReqT, RespT> call, String why) {
        call.close(Status.UNAUTHENTICATED.withDescription(why), new Metadata());
        return new ServerCall.Listener<>() { };
    }
}
