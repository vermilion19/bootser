package com.booster.waitingservice.waiting.application;

import com.booster.waitingservice.waiting.application.dto.PostponeCommand;
import com.booster.waitingservice.waiting.web.dto.request.RegisterWaitingRequest;
import com.booster.waitingservice.waiting.web.dto.response.RegisterWaitingResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("WaitingRegisterFacade 테스트")
class WaitingRegisterFacadeTest {

    @Mock
    private WaitingService waitingService;

    @InjectMocks
    private WaitingRegisterFacade waitingRegisterFacade;

    @Nested
    @DisplayName("register 메서드")
    class Register {

        @Test
        @DisplayName("성공: WaitingService.registerInternal을 호출하고 결과를 반환한다")
        void register_success() {
            // given
            RegisterWaitingRequest request = new RegisterWaitingRequest(1L, "010-1234-5678", 2);
            RegisterWaitingResponse expectedResponse = new RegisterWaitingResponse(100L, 1, 1L);

            given(waitingService.registerInternal(request)).willReturn(expectedResponse);

            // when
            RegisterWaitingResponse result = waitingRegisterFacade.register(request);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(result.id()).isEqualTo(100L);
            assertThat(result.waitingNumber()).isEqualTo(1);
            assertThat(result.rank()).isEqualTo(1L);
            verify(waitingService).registerInternal(request);
        }

        @Test
        @DisplayName("성공: 대기열 등록 시 Service가 정확히 1번 호출된다")
        void register_serviceCalledOnce() {
            // given
            RegisterWaitingRequest request = new RegisterWaitingRequest(1L, "010-9999-9999", 4);
            RegisterWaitingResponse expectedResponse = new RegisterWaitingResponse(200L, 5, 5L);

            given(waitingService.registerInternal(request)).willReturn(expectedResponse);

            // when
            waitingRegisterFacade.register(request);

            // then
            verify(waitingService).registerInternal(request);
        }
    }

    @Nested
    @DisplayName("postpone 메서드")
    class Postpone {

        @Test
        @DisplayName("성공: WaitingService.postponeInternal을 호출하고 결과를 반환한다")
        void postpone_success() {
            // given
            Long waitingId = 100L;
            Long restaurantId = 1L;
            PostponeCommand command = new PostponeCommand(waitingId, restaurantId);
            RegisterWaitingResponse expectedResponse = new RegisterWaitingResponse(200L, 10, 10L);

            given(waitingService.postponeInternal(waitingId)).willReturn(expectedResponse);

            // when
            RegisterWaitingResponse result = waitingRegisterFacade.postpone(command);

            // then
            assertThat(result).isEqualTo(expectedResponse);
            assertThat(result.id()).isEqualTo(200L); // 새 ID 발급
            assertThat(result.waitingNumber()).isEqualTo(10); // 맨 뒤 번호
            verify(waitingService).postponeInternal(waitingId);
        }

        @Test
        @DisplayName("성공: 순서 미루기 시 새로운 대기번호를 반환한다")
        void postpone_newWaitingNumber() {
            // given
            PostponeCommand command = new PostponeCommand(100L, 1L);
            RegisterWaitingResponse expectedResponse = new RegisterWaitingResponse(300L, 15, 15L);

            given(waitingService.postponeInternal(100L)).willReturn(expectedResponse);

            // when
            RegisterWaitingResponse result = waitingRegisterFacade.postpone(command);

            // then
            assertThat(result.waitingNumber()).isEqualTo(15);
            assertThat(result.rank()).isEqualTo(15L);
        }
    }

    @Nested
    @DisplayName("분산 락 관련 테스트 (통합 테스트에서 검증)")
    class DistributedLockTests {

        @Test
        @DisplayName("register 메서드는 @DistributedLock 어노테이션이 적용되어 있다")
        void register_hasDistributedLockAnnotation() throws NoSuchMethodException {
            // given
            var method = WaitingRegisterFacade.class.getMethod("register", RegisterWaitingRequest.class);

            // then
            assertThat(method.isAnnotationPresent(
                    com.booster.storage.redis.lock.DistributedLock.class)).isTrue();
        }

        @Test
        @DisplayName("postpone 메서드는 @DistributedLock 어노테이션이 적용되어 있다")
        void postpone_hasDistributedLockAnnotation() throws NoSuchMethodException {
            // given
            var method = WaitingRegisterFacade.class.getMethod("postpone", PostponeCommand.class);

            // then
            assertThat(method.isAnnotationPresent(
                    com.booster.storage.redis.lock.DistributedLock.class)).isTrue();
        }
    }
}