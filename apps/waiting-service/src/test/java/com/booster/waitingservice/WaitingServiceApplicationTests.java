package com.booster.waitingservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=localhost:9092", // 아무 값이나 넣어서 빈(Bean) 생성 에러 방지
        "spring.kafka.consumer.group-id=test-group"      // 컨슈머 설정이 있다면 그룹 ID도 필요할 수 있음
})
class WaitingServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
