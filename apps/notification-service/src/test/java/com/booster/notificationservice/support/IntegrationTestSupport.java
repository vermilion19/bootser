package com.booster.notificationservice.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.kafka.consumer.group-id=test-group",
        "app.slack.webhook-url=https://hooks.slack.com/test"
})
public abstract class IntegrationTestSupport {

    @MockitoBean
    protected KafkaTemplate<Object, Object> kafkaTemplate;
}
