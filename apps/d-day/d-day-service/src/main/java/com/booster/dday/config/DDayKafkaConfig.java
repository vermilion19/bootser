package com.booster.dday.config;

import com.booster.storage.kafka.core.KafkaTopic;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * d-day 의 Kafka 소비 설정.
 *
 * <h2>왜 {@code libs/storage-kafka} 의 것을 그냥 못 쓰나</h2>
 *
 * <p>{@code libs} 의 컨슈머는 값을 {@code JacksonJsonDeserializer} 로 푼다. 그것은
 * <b>타입 헤더가 실려 온다는 가정</b>인데, 우리 Outbox 릴레이는
 * {@code stringKafkaTemplate} 로 <b>본문만</b> 보낸다 — 페이로드를 열어 보지 않기로
 * 했으므로(§3.3) 릴레이는 그것이 무슨 타입인지 모르고, 모르는 것을 헤더에 적을 수
 * 없다.
 *
 * <p>그래서 문자열로 받아 우리가 푼다. <b>보내는 쪽과 받는 쪽의 가정을 맞추는 것</b>이
 * 여기서 하는 일의 전부다 — 안 맞으면 컨슈머가 첫 메시지에서 죽고, 그 죽음은
 * 역직렬화 예외라서 재시도해도 계속 죽는다.
 *
 * <h2>실패는 세 번 재시도하고 DLT 로 간다</h2>
 *
 * <p>{@code notification-service/config/KafkaRetryConfig} 와 같은 모양이다
 * (ARCHITECTURE §3.4 「다」의 2번). 펼치기가 독립적으로 재시도되는 것이 2단 발행의
 * 이득 중 하나였고, 그 이득이 이 에러 핸들러에서 실현된다.
 */
@Slf4j
@Configuration
public class DDayKafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:d-day-service}")
    private String groupId;

    @Value("${spring.kafka.consumer.auto-offset-reset:latest}")
    private String autoOffsetReset;

    @Bean
    public ConsumerFactory<String, String> ddayStringConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        return new DefaultKafkaConsumerFactory<>(config,
                new StringDeserializer(), new StringDeserializer());
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> ddayStringListenerFactory(
            @Qualifier("stringKafkaTemplate") KafkaTemplate<String, String> template) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(ddayStringConsumerFactory());

        /* 기본값이 「원본토픽명.DLT」라 KafkaTopic 의 이름과 맞는다 */
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template);
        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(recoverer, new FixedBackOff(1_000L, 3L));

        /* 본문을 못 푸는 것은 재시도로 안 낫는다 — 세 번 더 죽을 뿐이다.
           바로 DLT 로 보내고 컨슈머는 다음 메시지로 간다 */
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);

        factory.setCommonErrorHandler(errorHandler);
        log.info("[Kafka] d-day 문자열 컨슈머 준비 — DLT 는 {}",
                KafkaTopic.DDAY_RELEASE_CHANGED_DLT.getTopic());
        return factory;
    }
}
