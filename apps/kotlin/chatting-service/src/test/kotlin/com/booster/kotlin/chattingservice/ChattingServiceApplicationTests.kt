package com.booster.kotlin.chattingservice

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest(
    properties = [
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration," +
            "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration," +
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration," +
            "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration"
    ]
)
@Import(TestConfig::class)
class ChattingServiceApplicationTests {

    @Test
    fun contextLoads() {
    }
}
