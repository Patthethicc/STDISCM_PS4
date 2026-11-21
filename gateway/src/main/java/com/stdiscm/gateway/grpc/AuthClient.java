package com.stdiscm.gateway.grpc;

import com.stdiscm.proto.auth.AuthServiceGrpc.AuthServiceBlockingStub;
import com.stdiscm.proto.auth.LoginRequest;
import com.stdiscm.proto.auth.LoginResponse;
import com.stdiscm.proto.auth.LogoutRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class AuthClient {

    private final AuthServiceBlockingStub stub;
    private final ServiceChannel channel;

    public AuthClient(AuthServiceBlockingStub stub, @Qualifier("authChannel") ServiceChannel channel) {
        this.stub = stub;
        this.channel = channel;
    }

    public LoginResponse login(String username, String password) {
        return Calls.run(channel, () -> stub.login(
                LoginRequest.newBuilder().setUsername(username).setPassword(password).build()));
    }

    public void logout(String token) {
        Calls.run(channel, () -> Calls.withToken(stub, token).logout(LogoutRequest.getDefaultInstance()));
    }
}
