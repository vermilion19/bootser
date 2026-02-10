package com.booster.ddayservice.member.event;

import com.booster.core.web.event.MemberEvent;
import com.booster.ddayservice.member.application.MemberSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberEventConsumer {

    private final MemberSyncService memberSyncService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "member-events",
            groupId = "d-day-service",
            containerFactory = "stringKafkaListenerContainerFactory"
    )
    public void consume(String payload) {
        MemberEvent event = objectMapper.readValue(payload, MemberEvent.class);
        log.info("Member event received: type={}, memberId={}",
                event.eventType(), event.memberId());
        memberSyncService.process(event);
    }

}
