package com.stdiscm.auth.config;

import com.stdiscm.sharedauth.PemKeys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.file.Path;
import java.time.Duration;

@Configuration
public class JwtConfig {

    @Bean
    public JwtIssuer jwtIssuer(@Value("${auth.jwt.private-key-path}") String privateKeyPath,
                               @Value("${auth.jwt.ttl-minutes}") long ttlMinutes) {
        return new JwtIssuer(PemKeys.loadPrivateKey(Path.of(privateKeyPath)), Duration.ofMinutes(ttlMinutes));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
