package com.stdiscm.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("gateway")
public record GatewayProperties(
        long deadlineMs,
        Jwt jwt,
        Cookie cookie,
        Cors cors,
        Services services) {

    public record Jwt(String publicKeyPath) { }
    public record Cookie(String name, boolean secure) { }
    public record Cors(List<String> allowedOrigins) { }
    public record Services(String auth, String course, String grade, String announcement) { }
}
