package com.booster.notificationservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
public class KafkaRetryConfig {

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> template) {
        // 1. 실패 시 어디로 보낼지 결정 (기본값: 원본토픽명.DLT)
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template);

        // 2. 재시도 정책: 1초 간격으로 최대 3번 재시도
        // 3번 다 실패하면 recoverer(DLT) 실행
        FixedBackOff backOff = new FixedBackOff(1000L, 3L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        // 3. 특정 예외는 재시도하지 않고 바로 DLT로 보낼 수도 있음 (옵션)
        // errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);

        return errorHandler;
    }
}
