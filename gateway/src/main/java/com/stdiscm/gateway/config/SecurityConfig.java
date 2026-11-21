package com.stdiscm.gateway.config;

import com.stdiscm.sharedauth.JwtVerifier;
import com.stdiscm.sharedauth.PemKeys;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * The gateway verifies tokens locally with the public key, like every other node.
 * It never asks the auth node, so logged-in users keep working when auth is down.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public JwtVerifier jwtVerifier(GatewayProperties props) {
        return new JwtVerifier(PemKeys.loadPublicKey(Path.of(props.jwt().publicKeyPath())));
    }
}
