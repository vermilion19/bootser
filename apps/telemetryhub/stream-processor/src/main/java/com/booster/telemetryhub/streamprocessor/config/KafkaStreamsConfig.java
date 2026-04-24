package com.booster.telemetryhub.streamprocessor.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.streams.StreamsConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.config.KafkaStreamsConfiguration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafkaStreams
public class KafkaStreamsConfig {

    @Bean(name = "defaultKafkaStreamsConfig")
    public KafkaStreamsConfiguration kafkaStreamsConfiguration(
            StreamProcessorProperties properties,
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers
    ) {
        Map<String, Object> config = new HashMap<>();
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, properties.getApplicationId());
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG, properties.getNumStreamThreads());
        config.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, properties.getProcessingGuarantee());
        config.put(StreamsConfig.STATE_DIR_CONFIG, properties.getStateDir());
        config.put(StreamsConfig.NUM_STANDBY_REPLICAS_CONFIG, properties.getNumStandbyReplicas());
        return new KafkaStreamsConfiguration(config);
    }
}
