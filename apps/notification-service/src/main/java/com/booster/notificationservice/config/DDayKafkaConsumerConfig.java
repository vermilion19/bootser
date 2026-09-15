package com.booster.notificationservice.config;

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
 * d-day 의 알림 요청을 받기 위한 컨슈머.
 *
 * <h2>왜 기본 팩토리를 못 쓰나 — 둘이 걸린다</h2>
 *
 * <ol>
 *   <li><b>기본 팩토리는 배치다.</b> {@code app.kafka.listener.type: batch} 이고
 *       {@code WaitingEvent} 리스너가 {@code List<WaitingEvent>} 를 받는다.
 *       d-day 알림은 건수가 적어 묶을 이득이 없고, <b>한 건이 실패해도 나머지가
 *       나가야</b> 한다</li>
 *   <li><b>값에 타입 헤더가 없다.</b> d-day 의 Outbox 릴레이가 본문만 보낸다 —
 *       페이로드를 열어 보지 않기로 했으므로 무슨 타입인지 모르고, 모르는 것을
 *       헤더에 적을 수 없다. {@code JacksonJsonDeserializer} 는 그 헤더를 기대하므로
 *       첫 메시지에서 죽는다</li>
 * </ol>
 *
 * <p>그래서 문자열로 받는 팩토리를 따로 둔다. <b>보내는 쪽과 받는 쪽의 가정을
 * 맞추는 것</b>이 여기서 하는 일의 전부다.
 */
@Slf4j
@Configuration
public class DDayKafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.auto-offset-reset:latest}")
    private String autoOffsetReset;

    @Bean
    public ConsumerFactory<String, String> ddayStringConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "notification-service-dday-group");
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

        /* 본문을 못 푸는 것은 재시도로 안 낫는다 — 세 번 더 죽으며 그 파티션의
           뒤 알림을 붙들 뿐이다. 바로 격리한다 */
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);

        factory.setCommonErrorHandler(errorHandler);
        log.info("[Kafka] d-day 알림 컨슈머 준비 — DLT 는 {}",
                KafkaTopic.DDAY_NOTIFICATION_REQUESTED_DLT.getTopic());
        return factory;
    }
}
