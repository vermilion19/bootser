package com.booster.ddayservice.member.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberDltConsumer {

    @KafkaListener(
            topics = "member-events.DLT",
            groupId = "d-day-service-dlt",
            containerFactory = "stringKafkaListenerContainerFactory"
    )
    public void consume(String payload) {
        log.error("[DLT] Failed member event: {}", payload);
        // 추후 확장: DB 저장, 슬랙 알림 등
    }


}
