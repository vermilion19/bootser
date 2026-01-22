package com.booster.minizuulservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "mini-zuul")
public record NettyProperties(
        int port,
        List<BackendServer> backends,
        HealthCheckConfig healthCheck
) {
    public record BackendServer(String host, int port) {}

    public record HealthCheckConfig(
            int failureThreshold,
            long recoveryTimeMs
    ) {
        public HealthCheckConfig {
            // 기본값 설정
            if (failureThreshold <= 0) failureThreshold = 3;
            if (recoveryTimeMs <= 0) recoveryTimeMs = 30000;
        }
    }
}
