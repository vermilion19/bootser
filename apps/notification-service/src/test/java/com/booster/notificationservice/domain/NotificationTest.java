package com.booster.notificationservice.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Notification 도메인 엔티티 테스트")
class NotificationTest {

    @Nested
    @DisplayName("Builder 생성 테스트")
    class BuilderCreate {

        @Test
        @DisplayName("성공: 유효한 파라미터로 Notification 엔티티를 생성한다")
        void create_success() {
            // given
            Long restaurantId = 100L;
            Long waitingId = 1L;
            String target = "SLACK";
            String message = "호출 알림";

            // when
            Notification notification = Notification.builder()
                    .restaurantId(restaurantId)
                    .waitingId(waitingId)
                    .target(target)
                    .message(message)
                    .status(NotificationStatus.PENDING)
                    .build();

            // then
            assertThat(notification.getRestaurantId()).isEqualTo(restaurantId);
            assertThat(notification.getWaitingId()).isEqualTo(waitingId);
            assertThat(notification.getTarget()).isEqualTo(target);
            assertThat(notification.getMessage()).isEqualTo(message);
            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
            assertThat(notification.getSentAt()).isNull();
        }

        @Test
        @DisplayName("성공: SENT 상태로 생성할 수 있다")
        void create_with_sent_status() {
            // when
            Notification notification = Notification.builder()
                    .restaurantId(100L)
                    .waitingId(1L)
                    .target("SLACK")
                    .message("호출 알림")
                    .status(NotificationStatus.SENT)
                    .build();

            // then
            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        }

        @Test
        @DisplayName("성공: FAILED 상태로 생성할 수 있다")
        void create_with_failed_status() {
            // when
            Notification notification = Notification.builder()
                    .restaurantId(100L)
                    .waitingId(1L)
                    .target("SLACK")
                    .message("발송 실패 (DLQ 수신)")
                    .status(NotificationStatus.FAILED)
                    .build();

            // then
            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        }
    }

    @Nested
    @DisplayName("markAsSent 메서드")
    class MarkAsSent {

        @Test
        @DisplayName("성공: PENDING 상태에서 markAsSent()를 호출하면 SENT 상태로 변경되고 sentAt이 설정된다")
        void markAsSent_success() {
            // given
            Notification notification = Notification.builder()
                    .restaurantId(100L)
                    .waitingId(1L)
                    .target("SLACK")
                    .message("호출 알림")
                    .status(NotificationStatus.PENDING)
                    .build();

            LocalDateTime beforeCall = LocalDateTime.now();

            // when
            notification.markAsSent();

            // then
            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(notification.getSentAt()).isNotNull();
            assertThat(notification.getSentAt()).isAfterOrEqualTo(beforeCall);
        }

        @Test
        @DisplayName("성공: FAILED 상태에서도 markAsSent()를 호출하면 SENT로 변경된다")
        void markAsSent_from_failed() {
            // given
            Notification notification = Notification.builder()
                    .restaurantId(100L)
                    .waitingId(1L)
                    .target("SLACK")
                    .message("호출 알림")
                    .status(NotificationStatus.FAILED)
                    .build();

            // when
            notification.markAsSent();

            // then
            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(notification.getSentAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("markAsFailed 메서드")
    class MarkAsFailed {

        @Test
        @DisplayName("성공: PENDING 상태에서 markAsFailed()를 호출하면 FAILED 상태로 변경된다")
        void markAsFailed_success() {
            // given
            Notification notification = Notification.builder()
                    .restaurantId(100L)
                    .waitingId(1L)
                    .target("SLACK")
                    .message("호출 알림")
                    .status(NotificationStatus.PENDING)
                    .build();

            // when
            notification.markAsFailed();

            // then
            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        }

        @Test
        @DisplayName("성공: SENT 상태에서도 markAsFailed()를 호출하면 FAILED로 변경된다")
        void markAsFailed_from_sent() {
            // given
            Notification notification = Notification.builder()
                    .restaurantId(100L)
                    .waitingId(1L)
                    .target("SLACK")
                    .message("호출 알림")
                    .status(NotificationStatus.SENT)
                    .build();

            // when
            notification.markAsFailed();

            // then
            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        }

        @Test
        @DisplayName("성공: 이미 FAILED 상태에서 markAsFailed()를 호출해도 FAILED 상태를 유지한다")
        void markAsFailed_idempotent() {
            // given
            Notification notification = Notification.builder()
                    .restaurantId(100L)
                    .waitingId(1L)
                    .target("SLACK")
                    .message("호출 알림")
                    .status(NotificationStatus.FAILED)
                    .build();

            // when
            notification.markAsFailed();

            // then
            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        }
    }

    @Nested
    @DisplayName("generateId 메서드")
    class GenerateId {

        @Test
        @DisplayName("성공: ID가 null이면 generateId()가 Snowflake ID를 생성한다")
        void generateId_when_null() {
            // given
            Notification notification = Notification.builder()
                    .restaurantId(100L)
                    .waitingId(1L)
                    .target("SLACK")
                    .message("호출 알림")
                    .status(NotificationStatus.PENDING)
                    .build();

            assertThat(notification.getId()).isNull();

            // when
            notification.generateId();

            // then
            assertThat(notification.getId()).isNotNull();
            assertThat(notification.getId()).isGreaterThan(0L);
        }

        @Test
        @DisplayName("성공: 동일한 엔티티에 generateId()를 두 번 호출해도 ID는 변경되지 않는다")
        void generateId_idempotent() {
            // given
            Notification notification = Notification.builder()
                    .restaurantId(100L)
                    .waitingId(1L)
                    .target("SLACK")
                    .message("호출 알림")
                    .status(NotificationStatus.PENDING)
                    .build();

            notification.generateId();
            Long firstId = notification.getId();

            // when
            notification.generateId();

            // then
            assertThat(notification.getId()).isEqualTo(firstId);
        }

        @Test
        @DisplayName("성공: 서로 다른 엔티티의 generateId()는 유일한 ID를 생성한다")
        void generateId_unique() {
            // given
            Notification notification1 = Notification.builder()
                    .restaurantId(100L)
                    .waitingId(1L)
                    .target("SLACK")
                    .message("호출 알림 1")
                    .status(NotificationStatus.PENDING)
                    .build();

            Notification notification2 = Notification.builder()
                    .restaurantId(100L)
                    .waitingId(2L)
                    .target("SLACK")
                    .message("호출 알림 2")
                    .status(NotificationStatus.PENDING)
                    .build();

            // when
            notification1.generateId();
            notification2.generateId();

            // then
            assertThat(notification1.getId()).isNotEqualTo(notification2.getId());
        }
    }

    @Nested
    @DisplayName("상태 전이 시나리오 테스트")
    class StateTransition {

        @Test
        @DisplayName("정상 플로우: PENDING -> SENT")
        void pending_to_sent_flow() {
            // given
            Notification notification = Notification.builder()
                    .restaurantId(100L)
                    .waitingId(1L)
                    .target("SLACK")
                    .message("호출 알림")
                    .status(NotificationStatus.PENDING)
                    .build();

            // when
            notification.markAsSent();

            // then
            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(notification.getSentAt()).isNotNull();
        }

        @Test
        @DisplayName("실패 플로우: PENDING -> FAILED")
        void pending_to_failed_flow() {
            // given
            Notification notification = Notification.builder()
                    .restaurantId(100L)
                    .waitingId(1L)
                    .target("SLACK")
                    .message("호출 알림")
                    .status(NotificationStatus.PENDING)
                    .build();

            // when
            notification.markAsFailed();

            // then
            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        }

        @Test
        @DisplayName("재시도 성공 플로우: PENDING -> FAILED -> SENT")
        void retry_success_flow() {
            // given
            Notification notification = Notification.builder()
                    .restaurantId(100L)
                    .waitingId(1L)
                    .target("SLACK")
                    .message("호출 알림")
                    .status(NotificationStatus.PENDING)
                    .build();

            // when
            notification.markAsFailed();
            notification.markAsSent(); // 재시도 후 성공

            // then
            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(notification.getSentAt()).isNotNull();
        }
    }
}
