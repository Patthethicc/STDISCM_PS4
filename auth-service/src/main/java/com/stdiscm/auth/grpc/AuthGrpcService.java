package com.stdiscm.auth.grpc;

import com.stdiscm.auth.config.JwtIssuer;
import com.stdiscm.auth.config.JwtIssuer.IssuedToken;
import com.stdiscm.auth.model.RevokedToken;
import com.stdiscm.auth.model.User;
import com.stdiscm.auth.repository.RevokedTokenRepository;
import com.stdiscm.auth.repository.UserRepository;
import com.stdiscm.proto.auth.AuthServiceGrpc;
import com.stdiscm.proto.auth.LoginRequest;
import com.stdiscm.proto.auth.LoginResponse;
import com.stdiscm.proto.auth.LogoutRequest;
import com.stdiscm.proto.auth.LogoutResponse;
import com.stdiscm.proto.auth.Role;
import com.stdiscm.sharedauth.AuthContext;
import com.stdiscm.sharedauth.AuthContext.Principal;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@GrpcService
public class AuthGrpcService extends AuthServiceGrpc.AuthServiceImplBase {

    private static final String BAD_CREDENTIALS = "invalid username or password";

    private final UserRepository users;
    private final RevokedTokenRepository revoked;
    private final JwtIssuer issuer;
    private final PasswordEncoder encoder;

    public AuthGrpcService(UserRepository users, RevokedTokenRepository revoked,
                           JwtIssuer issuer, PasswordEncoder encoder) {
        this.users = users;
        this.revoked = revoked;
        this.issuer = issuer;
        this.encoder = encoder;
    }

    @Override
    public void login(LoginRequest request, StreamObserver<LoginResponse> out) {
        if (request.getUsername().isBlank() || request.getPassword().isEmpty()) {
            out.onError(Status.INVALID_ARGUMENT.withDescription("username and password are required")
                    .asRuntimeException());
            return;
        }

        Optional<User> found = users.findByUsername(request.getUsername());
        // Same error for unknown user and wrong password so usernames can't be probed
        if (found.isEmpty() || !encoder.matches(request.getPassword(), found.get().getPasswordHash())) {
            out.onError(Status.UNAUTHENTICATED.withDescription(BAD_CREDENTIALS).asRuntimeException());
            return;
        }

        User user = found.get();
        IssuedToken issued = issuer.issue(user);
        out.onNext(LoginResponse.newBuilder()
                .setToken(issued.token())
                .setUserId(user.getId().toString())
                .setRole(Role.valueOf(user.getRole().name()))
                .setName(user.getName())
                .setExpiresAtEpochSeconds(issued.expiresAt().getEpochSecond())
                .build());
        out.onCompleted();
    }

    @Override
    @Transactional
    public void logout(LogoutRequest request, StreamObserver<LogoutResponse> out) {
        Principal caller = AuthContext.require();

        revoked.deleteExpired(Instant.now());
        if (!revoked.existsById(caller.tokenId())) {      // idempotent: logging out twice is fine
            revoked.save(new RevokedToken(caller.tokenId(), caller.expiresAt()));
        }
        out.onNext(LogoutResponse.getDefaultInstance());
        out.onCompleted();
    }
}
