package com.booster.notificationservice.event;

import com.booster.core.web.event.WaitingEvent;
import com.booster.notificationservice.client.SlackClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final SlackClient slackClient;

    // yml에 설정한 토픽 이름과 그룹 ID 사용
    @KafkaListener(topics = "${app.kafka.topics.waiting-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleWaitingEvent(WaitingEvent event) {
        // 호출(CALLED) 이벤트일 때만 알림 발송
        if (event.type() == WaitingEvent.EventType.CALLED) { // eventType() -> type() 주의!
            log.info("호출 이벤트 수신! 슬랙 전송 시작...");

            // 메시지 만들기
            String message = String.format(
                    "[손님 호출]\n" +
                            "- 식당 ID: %d\n" +
                            "- 대기번호: %d번\n" +
                            "- 인원: %d명\n" +
                            "지금 바로 입장해 주세요!",
                    event.restaurantId(), event.waitingNumber(), event.partySize()
            );

            // 슬랙으로 전송
            slackClient.sendMessage(message);
        }
    }
}
