package com.stdiscm.sharedauth;

import com.stdiscm.proto.auth.AuthServiceGrpc;
import com.stdiscm.proto.auth.LoginRequest;
import com.stdiscm.proto.auth.LoginResponse;
import com.stdiscm.proto.auth.LogoutRequest;
import com.stdiscm.proto.auth.LogoutResponse;
import com.stdiscm.sharedauth.AuthContext.Role;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthInterceptorTest {

    private KeyPair keys;
    private Server server;
    private ManagedChannel channel;
    private final AtomicReference<AuthContext.Principal> seen = new AtomicReference<>();

    /** Login is public. Logout is FACULTY-only (stands in for any role-protected method). */
    private class FakeService extends AuthServiceGrpc.AuthServiceImplBase {
        @Override
        public void login(LoginRequest r, StreamObserver<LoginResponse> out) {
            out.onNext(LoginResponse.getDefaultInstance());
            out.onCompleted();
        }

        @Override
        public void logout(LogoutRequest r, StreamObserver<LogoutResponse> out) {
            seen.set(RoleGuard.require(Role.FACULTY));
            out.onNext(LogoutResponse.getDefaultInstance());
            out.onCompleted();
        }
    }

    @BeforeEach
    void start() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keys = gen.generateKeyPair();

        String name = "test-" + UUID.randomUUID();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(new FakeService())
                .intercept(new AuthServerInterceptor(new JwtVerifier(keys.getPublic()),
                        Set.of("stdiscm.auth.AuthService/Login")))
                .build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    }

    @AfterEach
    void stop() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    private AuthServiceGrpc.AuthServiceBlockingStub stub(String authorization) {
        AuthServiceGrpc.AuthServiceBlockingStub stub = AuthServiceGrpc.newBlockingStub(channel);
        if (authorization == null) return stub;
        Metadata md = new Metadata();
        md.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), authorization);
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(md));
    }

    private String token(Role role, KeyPair signWith, long expiresInMs) {
        long now = System.currentTimeMillis();
        return Jwts.builder().id("jti-1").subject("22222222-2222-2222-2222-222222222221")
                .claim("role", role.name()).claim("name", "Prof. Reyes")
                .issuedAt(new Date(now - 60_000)).expiration(new Date(now + expiresInMs))
                .signWith(signWith.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private static Status.Code code(Runnable call) {
        return assertThrows(StatusRuntimeException.class, call::run).getStatus().getCode();
    }

    @Test
    void publicMethodWorksWithoutToken() {
        stub(null).login(LoginRequest.getDefaultInstance());
    }

    @Test
    void protectedMethodRejectsMissingToken() {
        assertEquals(Status.Code.UNAUTHENTICATED, code(() -> stub(null).logout(LogoutRequest.getDefaultInstance())));
    }

    @Test
    void protectedMethodRejectsGarbageToken() {
        assertEquals(Status.Code.UNAUTHENTICATED,
                code(() -> stub("Bearer not.a.jwt").logout(LogoutRequest.getDefaultInstance())));
    }

    @Test
    void rejectsExpiredToken() {
        String expired = token(Role.FACULTY, keys, -1_000);
        assertEquals(Status.Code.UNAUTHENTICATED,
                code(() -> stub("Bearer " + expired).logout(LogoutRequest.getDefaultInstance())));
    }

    @Test
    void rejectsTokenSignedByAnotherKey() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        String forged = token(Role.FACULTY, gen.generateKeyPair(), 60_000);
        assertEquals(Status.Code.UNAUTHENTICATED,
                code(() -> stub("Bearer " + forged).logout(LogoutRequest.getDefaultInstance())));
    }

    @Test
    void wrongRoleIsPermissionDenied() {
        String student = token(Role.STUDENT, keys, 60_000);
        assertEquals(Status.Code.PERMISSION_DENIED,
                code(() -> stub("Bearer " + student).logout(LogoutRequest.getDefaultInstance())));
    }

    @Test
    void validTokenWithRightRoleReachesServiceWithPrincipal() {
        stub("Bearer " + token(Role.FACULTY, keys, 60_000)).logout(LogoutRequest.getDefaultInstance());
        assertEquals("22222222-2222-2222-2222-222222222221", seen.get().userId());
        assertEquals(Role.FACULTY, seen.get().role());
        assertEquals("Prof. Reyes", seen.get().name());
        assertEquals("jti-1", seen.get().tokenId());
    }
}
