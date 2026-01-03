package com.booster.storage.kafka.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaProducer {

    private final KafkaTemplate<String,Object> kafkaTemplate;

    public void send(KafkaTopic topic, Object payload) {
        log.info("sending payload='{}' to topic='{}'", payload, topic.getTopic());

        kafkaTemplate.send(topic.getTopic(),payload)
                .whenComplete((result,ex) ->{
                    if (ex != null) {
                        log.error("Unable to send message to topic=[{}]", topic.getTopic(), ex);
                    }else {
                        log.debug("Sent message=[{}] with offset=[{}]",
                                payload, result.getRecordMetadata().offset());
                    }
                });
    }

}
