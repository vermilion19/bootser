package com.booster.waitingservice.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.kafka.consumer.group-id=test-group",
        "spring.jpa.hibernate.ddl-auto=create",  // 종료 시 drop 안 함!
        "spring.jpa.open-in-view=false"          // 종료 시 커넥션 바로 반납!
})
@Testcontainers(disabledWithoutDocker = true)
public abstract class IntegrationTestSupport {
}
