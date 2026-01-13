package com.booster.notificationservice.event;

import com.booster.core.web.event.WaitingEvent;
import com.booster.notificationservice.application.NotificationService;
import com.booster.notificationservice.domain.Notification;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationEventListener 테스트")
class NotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationEventListener notificationEventListener;

    @Captor
    private ArgumentCaptor<WaitingEvent> eventCaptor;

    @Captor
    private ArgumentCaptor<List<Notification>> notificationListCaptor;

    @Nested
    @DisplayName("handleWaitingEvents 메서드")
    class HandleWaitingEvents {

        @Test
        @DisplayName("성공: CALLED 이벤트가 수신되면 비동기 알림 전송 및 DB 저장이 수행된다")
        void handleWaitingEvents_called_success() {
            // given
            List<WaitingEvent> events = List.of(
                    WaitingEvent.of(100L, "식당1", 1L, "010-1111-1111", 1, 1L, 2, WaitingEvent.EventType.CALLED)
            );

            // when
            notificationEventListener.handleWaitingEvents(events);

            // then
            verify(notificationService).sendAsync(eventCaptor.capture());
            WaitingEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.waitingId()).isEqualTo(1L);
            assertThat(capturedEvent.type()).isEqualTo(WaitingEvent.EventType.CALLED);

            verify(notificationService).saveAll(notificationListCaptor.capture());
            List<Notification> savedNotifications = notificationListCaptor.getValue();
            assertThat(savedNotifications).hasSize(1);
            assertThat(savedNotifications.getFirst().getWaitingId()).isEqualTo(1L);
            assertThat(savedNotifications.getFirst().getStatus()).isEqualTo(NotificationStatus.SENT);
        }

        @Test
        @DisplayName("성공: 여러 CALLED 이벤트가 수신되면 각각 비동기 알림 전송 후 일괄 저장된다")
        void handleWaitingEvents_multiple_called_success() {
            // given
            List<WaitingEvent> events = List.of(
                    WaitingEvent.of(100L, "식당1", 1L, "010-1111-1111", 1, 1L, 2, WaitingEvent.EventType.CALLED),
                    WaitingEvent.of(100L, "식당1", 2L, "010-2222-2222", 2, 2L, 3, WaitingEvent.EventType.CALLED),
                    WaitingEvent.of(100L, "식당1", 3L, "010-3333-3333", 3, 3L, 4, WaitingEvent.EventType.CALLED)
            );

            // when
            notificationEventListener.handleWaitingEvents(events);

            // then
            verify(notificationService, times(3)).sendAsync(any(WaitingEvent.class));

            verify(notificationService).saveAll(notificationListCaptor.capture());
            List<Notification> savedNotifications = notificationListCaptor.getValue();
            assertThat(savedNotifications).hasSize(3);
            assertThat(savedNotifications).extracting(Notification::getWaitingId)
                    .containsExactlyInAnyOrder(1L, 2L, 3L);
        }

        @Test
        @DisplayName("성공: CALLED가 아닌 이벤트는 알림 전송 및 DB 저장이 수행되지 않는다")
        void handleWaitingEvents_non_called_ignored() {
            // given
            List<WaitingEvent> events = List.of(
                    WaitingEvent.of(100L, "식당1", 1L, "010-1111-1111", 1, 1L, 2, WaitingEvent.EventType.REGISTER),
                    WaitingEvent.of(100L, "식당1", 2L, "010-2222-2222", 2, 2L, 3, WaitingEvent.EventType.ENTER),
                    WaitingEvent.of(100L, "식당1", 3L, "010-3333-3333", 3, 3L, 4, WaitingEvent.EventType.CANCEL)
            );

            // when
            notificationEventListener.handleWaitingEvents(events);

            // then
            verify(notificationService, never()).sendAsync(any(WaitingEvent.class));
            verify(notificationService, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("성공: CALLED와 다른 이벤트가 혼합되면 CALLED만 처리된다")
        void handleWaitingEvents_mixed_events() {
            // given
            List<WaitingEvent> events = List.of(
                    WaitingEvent.of(100L, "식당1", 1L, "010-1111-1111", 1, 1L, 2, WaitingEvent.EventType.REGISTER),
                    WaitingEvent.of(100L, "식당1", 2L, "010-2222-2222", 2, 2L, 3, WaitingEvent.EventType.CALLED),
                    WaitingEvent.of(100L, "식당1", 3L, "010-3333-3333", 3, 3L, 4, WaitingEvent.EventType.ENTER),
                    WaitingEvent.of(100L, "식당1", 4L, "010-4444-4444", 4, 4L, 5, WaitingEvent.EventType.CALLED)
            );

            // when
            notificationEventListener.handleWaitingEvents(events);

            // then
            verify(notificationService, times(2)).sendAsync(eventCaptor.capture());
            List<WaitingEvent> capturedEvents = eventCaptor.getAllValues();
            assertThat(capturedEvents).extracting(WaitingEvent::waitingId)
                    .containsExactly(2L, 4L);

            verify(notificationService).saveAll(notificationListCaptor.capture());
            List<Notification> savedNotifications = notificationListCaptor.getValue();
            assertThat(savedNotifications).hasSize(2);
            assertThat(savedNotifications).extracting(Notification::getWaitingId)
                    .containsExactlyInAnyOrder(2L, 4L);
        }

        @Test
        @DisplayName("성공: 빈 이벤트 리스트가 수신되면 아무 작업도 하지 않는다")
        void handleWaitingEvents_empty_list() {
            // given
            List<WaitingEvent> events = List.of();

            // when
            notificationEventListener.handleWaitingEvents(events);

            // then
            verify(notificationService, never()).sendAsync(any(WaitingEvent.class));
            verify(notificationService, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("성공: 저장되는 Notification의 메시지와 target이 올바르게 설정된다")
        void handleWaitingEvents_notification_fields() {
            // given
            List<WaitingEvent> events = List.of(
                    WaitingEvent.of(100L, "맛있는 식당", 1L, "010-1111-1111", 5, 1L, 2, WaitingEvent.EventType.CALLED)
            );

            // when
            notificationEventListener.handleWaitingEvents(events);

            // then
            verify(notificationService).saveAll(notificationListCaptor.capture());
            Notification saved = notificationListCaptor.getValue().getFirst();

            assertThat(saved.getRestaurantId()).isEqualTo(100L);
            assertThat(saved.getWaitingId()).isEqualTo(1L);
            assertThat(saved.getMessage()).isEqualTo("호출 알림");
            assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SENT);
        }
    }
}
