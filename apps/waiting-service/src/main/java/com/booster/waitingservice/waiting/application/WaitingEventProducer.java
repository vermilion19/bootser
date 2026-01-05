package com.booster.waitingservice.waiting.application;

import com.booster.core.web.event.WaitingEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WaitingEventProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;
//    private static final String TOPIC = "waiting-events"; // 토픽 이름도 일반화

    @Value("${app.kafka.topics.waiting-events}")
    private String topic;

    public void send(WaitingEvent event) {
        log.info("🚀 [Kafka] 이벤트 발행: type={}, waitingId={}", event.type(), event.waitingId());
        kafkaTemplate.send(topic, event);
    }
}
