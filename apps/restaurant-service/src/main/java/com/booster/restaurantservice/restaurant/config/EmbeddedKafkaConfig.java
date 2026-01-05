package com.booster.restaurantservice.restaurant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

@Profile("out")
@Configuration
public class EmbeddedKafkaConfig {
    @Bean
    public EmbeddedKafkaBroker embeddedKafkaBroker() {
        // Embedded Kafka를 9092 포트로 실행
        return new EmbeddedKafkaKraftBroker(1,1)
                .kafkaPorts(9092)
                .brokerListProperty("spring.kafka.bootstrap-servers");
    }
}
