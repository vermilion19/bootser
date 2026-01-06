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
import java.util.Set;
import java.util.stream.Collectors;

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
            throw e;
        }
    }

    @Transactional
    public void saveAll(List<Notification> notifications) {
        notificationRepository.saveAll(notifications);
    }

    @Transactional
    public void markAsFailedBulk(List<WaitingEvent> events) {
        if (events.isEmpty()) return;

        // 1. 전체 waitingId 추출
        List<Long> allWaitingIds = events.stream()
                .map(WaitingEvent::waitingId)
                .toList();

        // 2. 일단 업데이트 시도 (존재하는 건들은 FAILED로 변경됨)
        int updatedCount = notificationRepository.updateStatusFailedByWaitingIds(allWaitingIds);

        // 3. 업데이트된 개수가 전체보다 적다면? -> 누락된 데이터(Insert 안 된 애들)가 있음
        if (updatedCount < events.size()) {

            // A. DB에 실제로 존재하는 ID들을 조회해서 Set으로 만듦 (검색 속도 O(1))
            Set<Long> existingIds = notificationRepository.findAllByWaitingIdIn(allWaitingIds).stream()
                    .map(Notification::getWaitingId)
                    .collect(Collectors.toSet());

            // B. 누락된 데이터만 걸러서 엔티티 생성 (INSERT 대상)
            List<Notification> newLogs = events.stream()
                    .filter(event -> !existingIds.contains(event.waitingId())) // 존재하는 건 제외
                    .map(this::createFailedEntity) // 엔티티 변환 메서드 호출
                    .toList();

            // C. 누락된 건들 벌크 저장
            if (!newLogs.isEmpty()) {
                notificationRepository.saveAll(newLogs);
                log.info("💾 (DLQ) 누락된 실패 로그 {}건 신규 저장 완료", newLogs.size());
            }
        }
    }

    // 🛠️ 엔티티 변환 편의 메서드
    private Notification createFailedEntity(WaitingEvent event) {
        return Notification.builder()
                .waitingId(event.waitingId())
                .restaurantId(event.restaurantId())
                .target("SLACK") // 혹은 event에서 가져올 정보
                .message("발송 실패 (DLQ 수신)") // 실패 사유가 명확하지 않으므로 일반적인 메시지
                .status(NotificationStatus.FAILED)
                .build();
    }
}
