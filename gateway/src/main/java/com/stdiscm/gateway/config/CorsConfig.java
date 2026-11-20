package com.stdiscm.gateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * In production the React app and /api are served from the same origin (nginx proxies /api),
 * so CORS isn't needed. This only allows the configured dev origins.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final GatewayProperties props;

    public CorsConfig(GatewayProperties props) {
        this.props = props;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(props.cors().allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST")
                .allowCredentials(true);
    }
}
