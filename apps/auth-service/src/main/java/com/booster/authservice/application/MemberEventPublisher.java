package com.booster.authservice.application;

import com.booster.authservice.domain.User;
import com.booster.authservice.domain.outbox.MemberOutboxEvent;
import com.booster.authservice.domain.outbox.MemberOutboxRepository;
import com.booster.core.web.event.MemberEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberEventPublisher {
    private final MemberOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void publish(User user, MemberEvent.EventType eventType) {
        MemberEvent event = MemberEvent.of(user.getId(), user.getName(), eventType);

        MemberOutboxEvent outbox = MemberOutboxEvent.builder()
                .aggregateType("MEMBER")
                .aggregateId(user.getId())
                .eventType(eventType.name())
                .payload(objectMapper.writeValueAsString(event))
                .build();

        outboxRepository.save(outbox);
    }


}
