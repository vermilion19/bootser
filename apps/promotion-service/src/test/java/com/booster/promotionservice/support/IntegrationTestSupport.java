package com.booster.promotionservice.support;

import com.booster.storage.redis.RedisTestConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.kafka.consumer.group-id=test-group"
})
@Import(RedisTestConfig.class)
@Testcontainers(disabledWithoutDocker = true)
public abstract class IntegrationTestSupport {
}
