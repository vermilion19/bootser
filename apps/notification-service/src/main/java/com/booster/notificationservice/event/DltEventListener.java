package com.booster.notificationservice.event;

import com.booster.core.web.event.WaitingEvent;
import com.booster.notificationservice.application.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DltEventListener {
    private final NotificationService notificationService;

    @KafkaListener(
            topics = "${app.kafka.topics.waiting-events}.DLT",
            groupId = "notification-service-dlt-group",
            containerFactory = "kafkaListenerContainerFactory" // 배치 팩토리 사용 확인
    )
    public void handleDlt(List<WaitingEvent> events) {
        log.warn("🚨 [DLQ 수신] 총 {}건의 메시지가 최종 실패하여 격리되었습니다.", events.size());
        notificationService.markAsFailedBulk(events);
    }
}
