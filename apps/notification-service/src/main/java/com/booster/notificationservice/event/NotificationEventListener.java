package com.booster.notificationservice.event;

import com.booster.core.web.event.WaitingEvent;
import com.booster.notificationservice.application.NotificationService;
import com.booster.notificationservice.client.SlackClient;
import com.booster.notificationservice.domain.Notification;
import com.booster.notificationservice.domain.NotificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @KafkaListener(topics = "${app.kafka.topics.waiting-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleWaitingEvents(List<WaitingEvent> events) {
        log.info("📦 배치 수신: {}개 이벤트 도착", events.size());

        // 1. 알림 전송 (이건 비동기로 빠르게 처리)
        // 병렬 스트림 등을 이용해 빠르게 쏘거나, AsyncService에 위임
        events.forEach(event -> {
            if (event.type() == WaitingEvent.EventType.CALLED) {
                notificationService.sendAsync(event); // Slack 전송 (DB 저장 X)
            }
        });

        // 2. DB 저장은 여기서 한 번에! (Bulk Insert)
        List<Notification> logs = events.stream()
                .filter(e -> e.type() == WaitingEvent.EventType.CALLED)
                .map(this::toEntity)
                .toList();

        if (!logs.isEmpty()) {
            notificationService.saveAll(logs); // JDBC Batch Insert
            log.info("💾 DB 벌크 저장 완료: {}건", logs.size());
        }
    }

    private Notification toEntity(WaitingEvent event) {
        return Notification.builder()
                .waitingId(event.waitingId())
                .restaurantId(event.restaurantId())
                .message("호출 알림")
                .status(NotificationStatus.SENT)
                .build();
    }
}
