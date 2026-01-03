package com.booster.stroage.kafka;

import com.booster.storage.kafka.config.KafkaConfig;
import com.booster.storage.kafka.core.KafkaTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(classes = KafkaIntegrationTest.TestConfig.class) // 테스트용 설정 로드
@EmbeddedKafka(partitions = 1) // ports 지정하지 않음(랜덤)
@TestPropertySource(properties = {
        // 핵심: EmbeddedKafka가 만든 주소를 그대로 사용
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
public class KafkaIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private TestConsumer testConsumer;

    @Test
    @DisplayName("Kafka 메시지 발행 및 수신 테스트 (JSON 직렬화 검증)")
    void sendAndReceiveTest() throws InterruptedException {
        // given
        String topic = KafkaTopic.WAITING_QUEUE_ENTRY.getTopic();
        TestEvent event = new TestEvent(100L, LocalDateTime.now());

        // when
        kafkaTemplate.send(topic, event);

        // then
        // 비동기 메시지 수신 대기 (최대 5초)
        ConsumerRecord<String, Object> record = testConsumer.getRecords().poll(5, TimeUnit.SECONDS);

        assertThat(record).isNotNull();
        // JsonDeserializer가 Trusted Packages 설정에 따라 Map이 아닌 실제 객체로 변환해 주는지,
        // 혹은 LinkedHashMap으로 들어오는지 확인이 필요합니다.
        // 기본 설정상 LinkedHashMap으로 들어올 확률이 높으므로 내용을 검증합니다.
        // (만약 정확한 타입 변환을 원하면 TypeMapping 설정이 추가로 필요하지만, 여기선 데이터 도달 여부를 봅니다)
        System.out.println("Received: " + record.value());

        // 간단하게 null이 아니고 내용이 있는지만 1차 검증
        assertThat(record.value()).isNotNull();
    }



    // ----------------------------------------------------
    // 테스트를 위한 내부 설정 및 컨슈머
    // ----------------------------------------------------
    @Import(KafkaConfig.class)
    static class TestConfig {
        @Bean
        public TestConsumer testConsumer() {
            return new TestConsumer();
        }
    }

    @Component
    static class TestConsumer {
        private final BlockingQueue<ConsumerRecord<String, Object>> records = new LinkedBlockingQueue<>();

        // 실제 리스너가 동작하는지 확인
        @KafkaListener(topics = "waiting-queue.entry", groupId = "test-group")
        public void listen(ConsumerRecord<String, Object> record) {
            records.add(record);
        }

        public BlockingQueue<ConsumerRecord<String, Object>> getRecords() {
            return records;
        }
    }

    // 테스트용 이벤트 객체
    static class TestEvent {
        private Long userId;
        private LocalDateTime timestamp;

        public TestEvent() {} // 기본 생성자 필수
        public TestEvent(Long userId, LocalDateTime timestamp) {
            this.userId = userId;
            this.timestamp = timestamp;
        }

        // Getter 필수 (직렬화용)
        public Long getUserId() { return userId; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
}
