package com.booster.notificationservice.application;

import com.booster.core.web.event.WaitingEvent;
import com.booster.notificationservice.client.SlackClient;
import com.booster.notificationservice.domain.Notification;
import com.booster.notificationservice.domain.NotificationRepository;
import com.booster.notificationservice.domain.NotificationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService 테스트")
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SlackClient slackClient;

    @InjectMocks
    private NotificationService notificationService;

    @Captor
    private ArgumentCaptor<String> messageCaptor;

    @Captor
    private ArgumentCaptor<List<Notification>> notificationListCaptor;

    @Nested
    @DisplayName("sendAsync 메서드")
    class SendAsync {

        @Test
        @DisplayName("성공: CALLED 이벤트에 대해 슬랙 메시지를 전송한다")
        void sendAsync_success() {
            // given
            WaitingEvent event = WaitingEvent.of(
                    100L,                               // restaurantId
                    "맛있는 식당",                        // restaurantName
                    1L,                                 // waitingId
                    "010-1234-5678",                    // guestPhone
                    5,                                  // waitingNumber
                    1L,                                 // rank
                    2,                                  // partySize
                    WaitingEvent.EventType.CALLED       // type
            );

            // when
            notificationService.sendAsync(event);

            // then
            verify(slackClient).sendMessage(messageCaptor.capture());
            String capturedMessage = messageCaptor.getValue();

            assertThat(capturedMessage).contains("대기번호 5번");
            assertThat(capturedMessage).contains("2명");
            assertThat(capturedMessage).contains("식당ID: 100");
            assertThat(capturedMessage).contains("호출");
        }

        @Test
        @DisplayName("실패: SlackClient에서 예외 발생 시 로그 후 예외를 다시 던진다")
        void sendAsync_fail_exception() {
            // given
            WaitingEvent event = WaitingEvent.of(
                    100L, "맛있는 식당", 1L, "010-1234-5678", 5, 1L, 2,
                    WaitingEvent.EventType.CALLED
            );

            doThrow(new RuntimeException("Slack API 오류"))
                    .when(slackClient).sendMessage(any());

            // when & then
            assertThatThrownBy(() -> notificationService.sendAsync(event))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Slack API 오류");
        }
    }

    @Nested
    @DisplayName("saveAll 메서드")
    class SaveAll {

        @Test
        @DisplayName("성공: Notification 리스트를 저장한다")
        void saveAll_success() {
            // given
            List<Notification> notifications = List.of(
                    Notification.builder()
                            .restaurantId(100L)
                            .waitingId(1L)
                            .target("SLACK")
                            .message("호출 알림 1")
                            .status(NotificationStatus.SENT)
                            .build(),
                    Notification.builder()
                            .restaurantId(100L)
                            .waitingId(2L)
                            .target("SLACK")
                            .message("호출 알림 2")
                            .status(NotificationStatus.SENT)
                            .build()
            );

            // when
            notificationService.saveAll(notifications);

            // then
            verify(notificationRepository).saveAll(notificationListCaptor.capture());
            List<Notification> saved = notificationListCaptor.getValue();

            assertThat(saved).hasSize(2);
        }

        @Test
        @DisplayName("성공: 빈 리스트도 저장할 수 있다")
        void saveAll_empty_list() {
            // given
            List<Notification> notifications = List.of();

            // when
            notificationService.saveAll(notifications);

            // then
            verify(notificationRepository).saveAll(notifications);
        }
    }

    @Nested
    @DisplayName("markAsFailedBulk 메서드")
    class MarkAsFailedBulk {

        @Test
        @DisplayName("성공: 빈 이벤트 리스트가 전달되면 아무 작업도 하지 않는다")
        void markAsFailedBulk_empty_list() {
            // given
            List<WaitingEvent> events = List.of();

            // when
            notificationService.markAsFailedBulk(events);

            // then
            verify(notificationRepository, never()).updateStatusFailedByWaitingIds(anyList());
            verify(notificationRepository, never()).findAllByWaitingIdIn(anyList());
            verify(notificationRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("성공: 모든 이벤트가 DB에 존재하면 상태만 FAILED로 업데이트한다")
        void markAsFailedBulk_all_exist() {
            // given
            List<WaitingEvent> events = List.of(
                    WaitingEvent.of(100L, "식당1", 1L, "010-1111-1111", 1, 1L, 2, WaitingEvent.EventType.CALLED),
                    WaitingEvent.of(100L, "식당1", 2L, "010-2222-2222", 2, 2L, 3, WaitingEvent.EventType.CALLED)
            );

            given(notificationRepository.updateStatusFailedByWaitingIds(anyList())).willReturn(2);

            // when
            notificationService.markAsFailedBulk(events);

            // then
            verify(notificationRepository).updateStatusFailedByWaitingIds(List.of(1L, 2L));
            verify(notificationRepository, never()).findAllByWaitingIdIn(anyList());
            verify(notificationRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("성공: 일부 이벤트가 DB에 없으면 누락된 건들을 신규 저장한다")
        void markAsFailedBulk_partial_missing() {
            // given
            List<WaitingEvent> events = List.of(
                    WaitingEvent.of(100L, "식당1", 1L, "010-1111-1111", 1, 1L, 2, WaitingEvent.EventType.CALLED),
                    WaitingEvent.of(100L, "식당1", 2L, "010-2222-2222", 2, 2L, 3, WaitingEvent.EventType.CALLED),
                    WaitingEvent.of(100L, "식당1", 3L, "010-3333-3333", 3, 3L, 4, WaitingEvent.EventType.CALLED)
            );

            // 1건만 업데이트됨 (2건 누락)
            given(notificationRepository.updateStatusFailedByWaitingIds(anyList())).willReturn(1);

            // 기존 데이터 조회 (waitingId=1만 존재)
            Notification existing = Notification.builder()
                    .restaurantId(100L)
                    .waitingId(1L)
                    .target("SLACK")
                    .message("호출 알림")
                    .status(NotificationStatus.FAILED)
                    .build();
            given(notificationRepository.findAllByWaitingIdIn(anyList())).willReturn(List.of(existing));

            // when
            notificationService.markAsFailedBulk(events);

            // then
            verify(notificationRepository).updateStatusFailedByWaitingIds(List.of(1L, 2L, 3L));
            verify(notificationRepository).findAllByWaitingIdIn(List.of(1L, 2L, 3L));
            verify(notificationRepository).saveAll(notificationListCaptor.capture());

            List<Notification> newLogs = notificationListCaptor.getValue();
            assertThat(newLogs).hasSize(2);
            assertThat(newLogs).extracting(Notification::getWaitingId)
                    .containsExactlyInAnyOrder(2L, 3L);
            assertThat(newLogs).allMatch(n -> n.getStatus() == NotificationStatus.FAILED);
            assertThat(newLogs).allMatch(n -> "SLACK".equals(n.getTarget()));
            assertThat(newLogs).allMatch(n -> n.getMessage().contains("DLQ"));
        }

        @Test
        @DisplayName("성공: 모든 이벤트가 DB에 없으면 모두 신규 저장한다")
        void markAsFailedBulk_all_missing() {
            // given
            List<WaitingEvent> events = List.of(
                    WaitingEvent.of(100L, "식당1", 1L, "010-1111-1111", 1, 1L, 2, WaitingEvent.EventType.CALLED),
                    WaitingEvent.of(100L, "식당1", 2L, "010-2222-2222", 2, 2L, 3, WaitingEvent.EventType.CALLED)
            );

            // 0건 업데이트됨 (모두 누락)
            given(notificationRepository.updateStatusFailedByWaitingIds(anyList())).willReturn(0);
            given(notificationRepository.findAllByWaitingIdIn(anyList())).willReturn(List.of());

            // when
            notificationService.markAsFailedBulk(events);

            // then
            verify(notificationRepository).saveAll(notificationListCaptor.capture());

            List<Notification> newLogs = notificationListCaptor.getValue();
            assertThat(newLogs).hasSize(2);
            assertThat(newLogs).extracting(Notification::getWaitingId)
                    .containsExactlyInAnyOrder(1L, 2L);
        }

        @Test
        @DisplayName("성공: 업데이트 수가 이벤트 수보다 적지만 조회 결과 모두 존재하면 저장하지 않는다")
        void markAsFailedBulk_count_mismatch_but_all_exist() {
            // given
            List<WaitingEvent> events = List.of(
                    WaitingEvent.of(100L, "식당1", 1L, "010-1111-1111", 1, 1L, 2, WaitingEvent.EventType.CALLED),
                    WaitingEvent.of(100L, "식당1", 2L, "010-2222-2222", 2, 2L, 3, WaitingEvent.EventType.CALLED)
            );

            // 1건만 업데이트됨 (실제로는 2건 모두 존재하지만, 이미 FAILED 상태여서 카운트가 안 됨)
            given(notificationRepository.updateStatusFailedByWaitingIds(anyList())).willReturn(1);

            // 조회 결과 모두 존재
            List<Notification> existingList = List.of(
                    Notification.builder().restaurantId(100L).waitingId(1L).target("SLACK")
                            .message("호출 알림").status(NotificationStatus.FAILED).build(),
                    Notification.builder().restaurantId(100L).waitingId(2L).target("SLACK")
                            .message("호출 알림").status(NotificationStatus.FAILED).build()
            );
            given(notificationRepository.findAllByWaitingIdIn(anyList())).willReturn(existingList);

            // when
            notificationService.markAsFailedBulk(events);

            // then
            verify(notificationRepository, never()).saveAll(anyList());
        }
    }
}
