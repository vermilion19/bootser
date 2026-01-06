package com.booster.notificationservice.application;

import com.booster.core.web.event.WaitingEvent;
import com.booster.notificationservice.client.SlackClient;
import com.booster.notificationservice.domain.Notification;
import com.booster.notificationservice.domain.NotificationRepository;
import com.booster.notificationservice.domain.NotificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SlackClient slackClient;

    @Async
    public void sendAsync(WaitingEvent event) {
        try {
            String message = String.format(
                    "[호출] 대기번호 %d번 손님(%d명), 지금 입장해주세요! (식당ID: %d)",
                    event.waitingNumber(), event.partySize(), event.restaurantId()
            );

            // 실제 슬랙 전송 (Network I/O)
            slackClient.sendMessage(message);

        } catch (Exception e) {
            // 비동기 메서드에서의 예외는 호출자에게 전파되지 않으므로 로그 필수!
            log.error("알림 전송 실패 (WaitingId={}): {}", event.waitingId(), e.getMessage());
        }
    }

    @Transactional
    public void saveAll(List<Notification> notifications) {
        notificationRepository.saveAll(notifications);
    }


}
