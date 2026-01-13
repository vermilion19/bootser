package com.booster.notificationservice.domain;

import com.booster.notificationservice.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NotificationRepository 테스트")
@Transactional
class NotificationRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
    }

    @Nested
    @DisplayName("기본 CRUD 테스트")
    class BasicCrud {

        @Test
        @DisplayName("성공: Notification을 저장하고 ID가 자동 생성된다")
        void save_success() {
            // given
            Notification notification = Notification.builder()
                    .restaurantId(100L)
                    .waitingId(1L)
                    .target("SLACK")
                    .message("호출 알림")
                    .status(NotificationStatus.SENT)
                    .build();

            // when
            Notification saved = notificationRepository.save(notification);

            // then
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getRestaurantId()).isEqualTo(100L);
            assertThat(saved.getWaitingId()).isEqualTo(1L);
            assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SENT);
        }

        @Test
        @DisplayName("성공: 여러 Notification을 saveAll로 저장한다")
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
                            .build(),
                    Notification.builder()
                            .restaurantId(100L)
                            .waitingId(3L)
                            .target("SLACK")
                            .message("호출 알림 3")
                            .status(NotificationStatus.SENT)
                            .build()
            );

            // when
            List<Notification> savedList = notificationRepository.saveAll(notifications);

            // then
            assertThat(savedList).hasSize(3);
            assertThat(savedList).allMatch(n -> n.getId() != null);
        }

        @Test
        @DisplayName("성공: ID로 Notification을 조회한다")
        void findById_success() {
            // given
            Notification notification = Notification.builder()
                    .restaurantId(100L)
                    .waitingId(1L)
                    .target("SLACK")
                    .message("호출 알림")
                    .status(NotificationStatus.SENT)
                    .build();
            Notification saved = notificationRepository.save(notification);

            // when
            Notification found = notificationRepository.findById(saved.getId()).orElseThrow();

            // then
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getWaitingId()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("updateStatusFailedByWaitingIds 메서드")
    class UpdateStatusFailedByWaitingIds {

        @Test
        @DisplayName("성공: waitingIds에 해당하는 모든 Notification의 상태를 FAILED로 변경한다")
        void updateStatusFailed_success() {
            // given
            Notification n1 = notificationRepository.save(
                    Notification.builder()
                            .restaurantId(100L)
                            .waitingId(1L)
                            .target("SLACK")
                            .message("호출 알림 1")
                            .status(NotificationStatus.SENT)
                            .build()
            );
            Notification n2 = notificationRepository.save(
                    Notification.builder()
                            .restaurantId(100L)
                            .waitingId(2L)
                            .target("SLACK")
                            .message("호출 알림 2")
                            .status(NotificationStatus.SENT)
                            .build()
            );
            Notification n3 = notificationRepository.save(
                    Notification.builder()
                            .restaurantId(100L)
                            .waitingId(3L)
                            .target("SLACK")
                            .message("호출 알림 3")
                            .status(NotificationStatus.SENT)
                            .build()
            );

            // when
            int updatedCount = notificationRepository.updateStatusFailedByWaitingIds(List.of(1L, 2L));

            // then
            assertThat(updatedCount).isEqualTo(2);

            Notification updated1 = notificationRepository.findById(n1.getId()).orElseThrow();
            Notification updated2 = notificationRepository.findById(n2.getId()).orElseThrow();
            Notification notUpdated = notificationRepository.findById(n3.getId()).orElseThrow();

            assertThat(updated1.getStatus()).isEqualTo(NotificationStatus.FAILED);
            assertThat(updated2.getStatus()).isEqualTo(NotificationStatus.FAILED);
            assertThat(notUpdated.getStatus()).isEqualTo(NotificationStatus.SENT);
        }

        @Test
        @DisplayName("성공: 존재하지 않는 waitingId를 포함해도 에러 없이 처리된다")
        void updateStatusFailed_with_nonexistent_ids() {
            // given
            notificationRepository.save(
                    Notification.builder()
                            .restaurantId(100L)
                            .waitingId(1L)
                            .target("SLACK")
                            .message("호출 알림")
                            .status(NotificationStatus.SENT)
                            .build()
            );

            // when
            int updatedCount = notificationRepository.updateStatusFailedByWaitingIds(List.of(1L, 999L, 888L));

            // then
            assertThat(updatedCount).isEqualTo(1);
        }

        @Test
        @DisplayName("성공: 빈 리스트를 전달하면 0건이 업데이트된다")
        void updateStatusFailed_empty_list() {
            // given
            notificationRepository.save(
                    Notification.builder()
                            .restaurantId(100L)
                            .waitingId(1L)
                            .target("SLACK")
                            .message("호출 알림")
                            .status(NotificationStatus.SENT)
                            .build()
            );

            // when
            int updatedCount = notificationRepository.updateStatusFailedByWaitingIds(List.of());

            // then
            assertThat(updatedCount).isEqualTo(0);
        }

        @Test
        @DisplayName("성공: 이미 FAILED 상태인 Notification도 업데이트 카운트에 포함된다")
        void updateStatusFailed_already_failed() {
            // given
            notificationRepository.save(
                    Notification.builder()
                            .restaurantId(100L)
                            .waitingId(1L)
                            .target("SLACK")
                            .message("호출 알림")
                            .status(NotificationStatus.FAILED)
                            .build()
            );

            // when
            int updatedCount = notificationRepository.updateStatusFailedByWaitingIds(List.of(1L));

            // then
            assertThat(updatedCount).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("findAllByWaitingIdIn 메서드")
    class FindAllByWaitingIdIn {

        @Test
        @DisplayName("성공: waitingIds에 해당하는 모든 Notification을 조회한다")
        void findAllByWaitingIdIn_success() {
            // given
            notificationRepository.save(
                    Notification.builder()
                            .restaurantId(100L)
                            .waitingId(1L)
                            .target("SLACK")
                            .message("호출 알림 1")
                            .status(NotificationStatus.SENT)
                            .build()
            );
            notificationRepository.save(
                    Notification.builder()
                            .restaurantId(100L)
                            .waitingId(2L)
                            .target("SLACK")
                            .message("호출 알림 2")
                            .status(NotificationStatus.SENT)
                            .build()
            );
            notificationRepository.save(
                    Notification.builder()
                            .restaurantId(100L)
                            .waitingId(3L)
                            .target("SLACK")
                            .message("호출 알림 3")
                            .status(NotificationStatus.SENT)
                            .build()
            );

            // when
            List<Notification> found = notificationRepository.findAllByWaitingIdIn(List.of(1L, 3L));

            // then
            assertThat(found).hasSize(2);
            assertThat(found).extracting(Notification::getWaitingId)
                    .containsExactlyInAnyOrder(1L, 3L);
        }

        @Test
        @DisplayName("성공: 존재하지 않는 waitingId만 전달하면 빈 리스트를 반환한다")
        void findAllByWaitingIdIn_nonexistent() {
            // given
            notificationRepository.save(
                    Notification.builder()
                            .restaurantId(100L)
                            .waitingId(1L)
                            .target("SLACK")
                            .message("호출 알림")
                            .status(NotificationStatus.SENT)
                            .build()
            );

            // when
            List<Notification> found = notificationRepository.findAllByWaitingIdIn(List.of(999L, 888L));

            // then
            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("성공: 빈 리스트를 전달하면 빈 리스트를 반환한다")
        void findAllByWaitingIdIn_empty_list() {
            // given
            notificationRepository.save(
                    Notification.builder()
                            .restaurantId(100L)
                            .waitingId(1L)
                            .target("SLACK")
                            .message("호출 알림")
                            .status(NotificationStatus.SENT)
                            .build()
            );

            // when
            List<Notification> found = notificationRepository.findAllByWaitingIdIn(List.of());

            // then
            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("성공: 다양한 상태의 Notification을 모두 조회한다")
        void findAllByWaitingIdIn_various_status() {
            // given
            notificationRepository.save(
                    Notification.builder()
                            .restaurantId(100L)
                            .waitingId(1L)
                            .target("SLACK")
                            .message("호출 알림 1")
                            .status(NotificationStatus.PENDING)
                            .build()
            );
            notificationRepository.save(
                    Notification.builder()
                            .restaurantId(100L)
                            .waitingId(2L)
                            .target("SLACK")
                            .message("호출 알림 2")
                            .status(NotificationStatus.SENT)
                            .build()
            );
            notificationRepository.save(
                    Notification.builder()
                            .restaurantId(100L)
                            .waitingId(3L)
                            .target("SLACK")
                            .message("호출 알림 3")
                            .status(NotificationStatus.FAILED)
                            .build()
            );

            // when
            List<Notification> found = notificationRepository.findAllByWaitingIdIn(List.of(1L, 2L, 3L));

            // then
            assertThat(found).hasSize(3);
            assertThat(found).extracting(Notification::getStatus)
                    .containsExactlyInAnyOrder(
                            NotificationStatus.PENDING,
                            NotificationStatus.SENT,
                            NotificationStatus.FAILED
                    );
        }
    }
}
