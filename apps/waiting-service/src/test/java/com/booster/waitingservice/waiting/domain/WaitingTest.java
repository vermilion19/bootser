package com.booster.waitingservice.waiting.domain;

import com.booster.waitingservice.waiting.exception.InvalidWaitingStatusException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Waiting 도메인 엔티티 테스트")
class WaitingTest {

    @Nested
    @DisplayName("create 메서드")
    class Create {

        @Test
        @DisplayName("성공: 유효한 파라미터로 Waiting 엔티티를 생성하면 초기 상태는 WAITING이다")
        void create_success() {
            // given
            Long id = 1L;
            Long restaurantId = 100L;
            String guestPhone = "010-1234-5678";
            int partySize = 2;
            int waitingNumber = 1;

            // when
            Waiting waiting = Waiting.create(id, restaurantId, guestPhone, partySize, waitingNumber);

            // then
            assertThat(waiting.getId()).isEqualTo(id);
            assertThat(waiting.getRestaurantId()).isEqualTo(restaurantId);
            assertThat(waiting.getGuestPhone()).isEqualTo(guestPhone);
            assertThat(waiting.getPartySize()).isEqualTo(partySize);
            assertThat(waiting.getWaitingNumber()).isEqualTo(waitingNumber);
            assertThat(waiting.getStatus()).isEqualTo(WaitingStatus.WAITING);
        }

        @Test
        @DisplayName("실패: 인원수가 0명이면 IllegalArgumentException이 발생한다")
        void create_fail_partySize_zero() {
            // given
            int invalidPartySize = 0;

            // when & then
            assertThatThrownBy(() ->
                    Waiting.create(1L, 100L, "010-1234-5678", invalidPartySize, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("1명 이상");
        }

        @Test
        @DisplayName("실패: 인원수가 음수이면 IllegalArgumentException이 발생한다")
        void create_fail_partySize_negative() {
            // given
            int invalidPartySize = -1;

            // when & then
            assertThatThrownBy(() ->
                    Waiting.create(1L, 100L, "010-1234-5678", invalidPartySize, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("1명 이상");
        }
    }

    @Nested
    @DisplayName("call 메서드")
    class Call {

        @Test
        @DisplayName("성공: WAITING 상태에서 call()을 호출하면 CALLED 상태로 변경된다")
        void call_success() {
            // given
            Waiting waiting = Waiting.create(1L, 100L, "010-1234-5678", 2, 1);
            assertThat(waiting.getStatus()).isEqualTo(WaitingStatus.WAITING);

            // when
            waiting.call();

            // then
            assertThat(waiting.getStatus()).isEqualTo(WaitingStatus.CALLED);
        }

        @Test
        @DisplayName("실패: CALLED 상태에서 call()을 호출하면 InvalidWaitingStatusException이 발생한다")
        void call_fail_already_called() {
            // given
            Waiting waiting = Waiting.create(1L, 100L, "010-1234-5678", 2, 1);
            waiting.call(); // WAITING -> CALLED

            // when & then
            assertThatThrownBy(waiting::call)
                    .isInstanceOf(InvalidWaitingStatusException.class);
        }

        @Test
        @DisplayName("실패: ENTERED 상태에서 call()을 호출하면 InvalidWaitingStatusException이 발생한다")
        void call_fail_already_entered() {
            // given
            Waiting waiting = Waiting.create(1L, 100L, "010-1234-5678", 2, 1);
            waiting.call();
            waiting.enter(); // CALLED -> ENTERED

            // when & then
            assertThatThrownBy(waiting::call)
                    .isInstanceOf(InvalidWaitingStatusException.class);
        }

        @Test
        @DisplayName("실패: CANCELED 상태에서 call()을 호출하면 InvalidWaitingStatusException이 발생한다")
        void call_fail_already_canceled() {
            // given
            Waiting waiting = Waiting.create(1L, 100L, "010-1234-5678", 2, 1);
            waiting.cancel(); // WAITING -> CANCELED

            // when & then
            assertThatThrownBy(waiting::call)
                    .isInstanceOf(InvalidWaitingStatusException.class);
        }
    }

    @Nested
    @DisplayName("enter 메서드")
    class Enter {

        @Test
        @DisplayName("성공: CALLED 상태에서 enter()를 호출하면 ENTERED 상태로 변경된다")
        void enter_success() {
            // given
            Waiting waiting = Waiting.create(1L, 100L, "010-1234-5678", 2, 1);
            waiting.call(); // WAITING -> CALLED

            // when
            waiting.enter();

            // then
            assertThat(waiting.getStatus()).isEqualTo(WaitingStatus.ENTERED);
        }

        @Test
        @DisplayName("실패: WAITING 상태에서 enter()를 호출하면 InvalidWaitingStatusException이 발생한다")
        void enter_fail_not_called() {
            // given
            Waiting waiting = Waiting.create(1L, 100L, "010-1234-5678", 2, 1);

            // when & then
            assertThatThrownBy(waiting::enter)
                    .isInstanceOf(InvalidWaitingStatusException.class);
        }

        @Test
        @DisplayName("실패: ENTERED 상태에서 enter()를 호출하면 InvalidWaitingStatusException이 발생한다")
        void enter_fail_already_entered() {
            // given
            Waiting waiting = Waiting.create(1L, 100L, "010-1234-5678", 2, 1);
            waiting.call();
            waiting.enter(); // CALLED -> ENTERED

            // when & then
            assertThatThrownBy(waiting::enter)
                    .isInstanceOf(InvalidWaitingStatusException.class);
        }

        @Test
        @DisplayName("실패: CANCELED 상태에서 enter()를 호출하면 InvalidWaitingStatusException이 발생한다")
        void enter_fail_canceled() {
            // given
            Waiting waiting = Waiting.create(1L, 100L, "010-1234-5678", 2, 1);
            waiting.cancel(); // WAITING -> CANCELED

            // when & then
            assertThatThrownBy(waiting::enter)
                    .isInstanceOf(InvalidWaitingStatusException.class);
        }
    }

    @Nested
    @DisplayName("cancel 메서드")
    class Cancel {

        @Test
        @DisplayName("성공: WAITING 상태에서 cancel()을 호출하면 CANCELED 상태로 변경된다")
        void cancel_success_from_waiting() {
            // given
            Waiting waiting = Waiting.create(1L, 100L, "010-1234-5678", 2, 1);

            // when
            waiting.cancel();

            // then
            assertThat(waiting.getStatus()).isEqualTo(WaitingStatus.CANCELED);
        }

        @Test
        @DisplayName("성공: CALLED 상태에서 cancel()을 호출하면 CANCELED 상태로 변경된다")
        void cancel_success_from_called() {
            // given
            Waiting waiting = Waiting.create(1L, 100L, "010-1234-5678", 2, 1);
            waiting.call(); // WAITING -> CALLED

            // when
            waiting.cancel();

            // then
            assertThat(waiting.getStatus()).isEqualTo(WaitingStatus.CANCELED);
        }

        @Test
        @DisplayName("실패: ENTERED 상태에서 cancel()을 호출하면 IllegalStateException이 발생한다")
        void cancel_fail_already_entered() {
            // given
            Waiting waiting = Waiting.create(1L, 100L, "010-1234-5678", 2, 1);
            waiting.call();
            waiting.enter(); // CALLED -> ENTERED

            // when & then
            assertThatThrownBy(waiting::cancel)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("이미 입장한 손님");
        }
    }

    @Nested
    @DisplayName("상태 전이 시나리오 테스트")
    class StateTransition {

        @Test
        @DisplayName("정상 플로우: WAITING -> CALLED -> ENTERED")
        void normal_flow() {
            // given
            Waiting waiting = Waiting.create(1L, 100L, "010-1234-5678", 2, 1);

            // when & then
            assertThat(waiting.getStatus()).isEqualTo(WaitingStatus.WAITING);

            waiting.call();
            assertThat(waiting.getStatus()).isEqualTo(WaitingStatus.CALLED);

            waiting.enter();
            assertThat(waiting.getStatus()).isEqualTo(WaitingStatus.ENTERED);
        }

        @Test
        @DisplayName("취소 플로우: WAITING -> CANCELED")
        void cancel_flow_from_waiting() {
            // given
            Waiting waiting = Waiting.create(1L, 100L, "010-1234-5678", 2, 1);

            // when
            waiting.cancel();

            // then
            assertThat(waiting.getStatus()).isEqualTo(WaitingStatus.CANCELED);
        }

        @Test
        @DisplayName("호출 후 취소 플로우: WAITING -> CALLED -> CANCELED")
        void cancel_flow_from_called() {
            // given
            Waiting waiting = Waiting.create(1L, 100L, "010-1234-5678", 2, 1);
            waiting.call();

            // when
            waiting.cancel();

            // then
            assertThat(waiting.getStatus()).isEqualTo(WaitingStatus.CANCELED);
        }
    }
}