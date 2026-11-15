package com.stdiscm.sharedauth;

import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Add the shared-auth dependency and set {@code auth.jwt.public-key-path}; every gRPC method
 * of the service is then protected. Public methods default to Login and the gRPC health check;
 * override with {@code auth.public-methods} (comma-separated full method names).
 */
@AutoConfiguration
@ConditionalOnProperty("auth.jwt.public-key-path")
public class SharedAuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public StatusExceptionAdvice statusExceptionAdvice() {
        return new StatusExceptionAdvice();
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtVerifier jwtVerifier(@Value("${auth.jwt.public-key-path}") String publicKeyPath) {
        return new JwtVerifier(PemKeys.loadPublicKey(Path.of(publicKeyPath)));
    }

    @Bean
    @GrpcGlobalServerInterceptor
    @ConditionalOnMissingBean
    public AuthServerInterceptor authServerInterceptor(
            JwtVerifier verifier,
            @Value("${auth.public-methods:stdiscm.auth.AuthService/Login,grpc.health.v1.Health/Check}")
            String publicMethods) {
        Set<String> methods = Arrays.stream(publicMethods.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        return new AuthServerInterceptor(verifier, methods);
    }
}
