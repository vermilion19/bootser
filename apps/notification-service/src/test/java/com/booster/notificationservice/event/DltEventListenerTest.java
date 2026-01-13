package com.booster.notificationservice.event;

import com.booster.core.web.event.WaitingEvent;
import com.booster.notificationservice.application.NotificationService;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DltEventListener 테스트")
class DltEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private DltEventListener dltEventListener;

    @Captor
    private ArgumentCaptor<List<WaitingEvent>> eventListCaptor;

    @Nested
    @DisplayName("handleDlt 메서드")
    class HandleDlt {

        @Test
        @DisplayName("성공: DLT에서 이벤트가 수신되면 markAsFailedBulk가 호출된다")
        void handleDlt_success() {
            // given
            List<WaitingEvent> events = List.of(
                    WaitingEvent.of(100L, "식당1", 1L, "010-1111-1111", 1, 1L, 2, WaitingEvent.EventType.CALLED)
            );

            // when
            dltEventListener.handleDlt(events);

            // then
            verify(notificationService).markAsFailedBulk(eventListCaptor.capture());
            List<WaitingEvent> capturedEvents = eventListCaptor.getValue();
            assertThat(capturedEvents).hasSize(1);
            assertThat(capturedEvents.getFirst().waitingId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("성공: 여러 DLT 이벤트가 수신되면 모두 전달된다")
        void handleDlt_multiple_events() {
            // given
            List<WaitingEvent> events = List.of(
                    WaitingEvent.of(100L, "식당1", 1L, "010-1111-1111", 1, 1L, 2, WaitingEvent.EventType.CALLED),
                    WaitingEvent.of(100L, "식당1", 2L, "010-2222-2222", 2, 2L, 3, WaitingEvent.EventType.CALLED),
                    WaitingEvent.of(100L, "식당1", 3L, "010-3333-3333", 3, 3L, 4, WaitingEvent.EventType.CALLED)
            );

            // when
            dltEventListener.handleDlt(events);

            // then
            verify(notificationService).markAsFailedBulk(eventListCaptor.capture());
            List<WaitingEvent> capturedEvents = eventListCaptor.getValue();
            assertThat(capturedEvents).hasSize(3);
            assertThat(capturedEvents).extracting(WaitingEvent::waitingId)
                    .containsExactlyInAnyOrder(1L, 2L, 3L);
        }

        @Test
        @DisplayName("성공: 빈 DLT 이벤트 리스트도 처리된다")
        void handleDlt_empty_list() {
            // given
            List<WaitingEvent> events = List.of();

            // when
            dltEventListener.handleDlt(events);

            // then
            verify(notificationService).markAsFailedBulk(eventListCaptor.capture());
            assertThat(eventListCaptor.getValue()).isEmpty();
        }

        @Test
        @DisplayName("성공: 다양한 이벤트 타입이 혼합되어도 모두 처리된다")
        void handleDlt_mixed_event_types() {
            // given
            List<WaitingEvent> events = List.of(
                    WaitingEvent.of(100L, "식당1", 1L, "010-1111-1111", 1, 1L, 2, WaitingEvent.EventType.CALLED),
                    WaitingEvent.of(100L, "식당1", 2L, "010-2222-2222", 2, 2L, 3, WaitingEvent.EventType.REGISTER),
                    WaitingEvent.of(100L, "식당1", 3L, "010-3333-3333", 3, 3L, 4, WaitingEvent.EventType.CANCEL)
            );

            // when
            dltEventListener.handleDlt(events);

            // then
            verify(notificationService).markAsFailedBulk(eventListCaptor.capture());
            List<WaitingEvent> capturedEvents = eventListCaptor.getValue();
            assertThat(capturedEvents).hasSize(3);
        }

        @Test
        @DisplayName("성공: markAsFailedBulk는 정확히 한 번만 호출된다")
        void handleDlt_called_once() {
            // given
            List<WaitingEvent> events = List.of(
                    WaitingEvent.of(100L, "식당1", 1L, "010-1111-1111", 1, 1L, 2, WaitingEvent.EventType.CALLED)
            );

            // when
            dltEventListener.handleDlt(events);

            // then
            verify(notificationService, times(1)).markAsFailedBulk(anyList());
        }
    }
}
