package com.booster.coreobservability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityConfig {

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    @Value("${spring.application.name:unknown-app}")
    private String applicationName;

    // 1. @Observed 어노테이션을 사용하여 메서드 단위로 메트릭/트레이싱을 측정하고 싶을 때 필요
    @Bean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }

    // 2. (선택) 모든 메트릭에 공통 태그 추가 (예: region, env)
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
                .commonTags("env", activeProfile)  // 예: dev, prod
                .commonTags("app", applicationName); // 예: auth-service
    }
}
