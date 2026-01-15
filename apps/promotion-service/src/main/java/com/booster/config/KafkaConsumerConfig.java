package com.booster.config;

import com.booster.storage.kafka.config.KafkaConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
@Import(KafkaConfig.class)
public class KafkaConsumerConfig {
    /**
     * 공통 라이브러리(libs:storage-kafka)에 있는 Bean들을 그대로 가져와서 씁니다.
     * consumerFactory: 공통 라이브러리의 KafkaConfig에서 만든 것
     * kafkaTemplate: 공통 라이브러리의 KafkaConfig에서 만든 것
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate
    ) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();

        // 1. 공통 설정의 ConsumerFactory 연결 (직렬화 설정 등 유지)
        factory.setConsumerFactory(consumerFactory);

        // -------------------------------------------------------
        // 여기서부터가 PromotionService만을 위한 추가 설정 (DLQ & Retry)
        // -------------------------------------------------------

        // 2. DLQ 전송 전략 설정
        // 실패 시 "원래토픽명.DLT" 로 메시지를 쏘도록 설정
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (r, e) -> {
                    log.error("3회 실패하여 DLQ로 이동합니다. Topic: {}, Error: {}", r.topic(), e.getMessage());
                    return new TopicPartition(r.topic() + ".DLT", r.partition());
                });

        // 3. 에러 핸들러 설정 (1초 간격, 최대 3회 재시도)
        // 3회 실패 후에는 위에서 만든 recoverer가 동작하여 DLQ로 보냄
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));

        // (옵션) 재시도할 필요 없는 치명적 에러(예: 메시지 형식이 아예 깨짐)는 즉시 DLQ로 보내거나 버림
        // errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);

        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

}
