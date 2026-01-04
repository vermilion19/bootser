package com.booster.waitingservice.waiting.application;

import com.booster.storage.redis.domain.WaitingUser;
import com.booster.storage.redis.repository.RedissonRankingRepository;
import com.booster.waitingservice.waiting.application.dto.PostponeCommand;
import com.booster.waitingservice.waiting.domain.Waiting;
import com.booster.waitingservice.waiting.domain.WaitingRepository;
import com.booster.waitingservice.waiting.domain.WaitingStatus;
import com.booster.waitingservice.waiting.web.dto.request.RegisterWaitingRequest;
import com.booster.waitingservice.waiting.web.dto.response.RegisterWaitingResponse;
import com.booster.waitingservice.waiting.web.dto.response.WaitingDetailResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WaitingServiceTest {

    @Mock
    private WaitingRepository waitingRepository;
    @Mock

    private RedissonRankingRepository rankingRepository;

    @InjectMocks
    private WaitingService waitingService;

    @Test
    @DisplayName("대기열 등록 성공: 첫 번째 손님이면 대기번호 1번을 부여받고 Redis에 등록된다.")
    void register_success_first_guest() {
        // given
        Long restaurantId = 1L;
        RegisterWaitingRequest request = new RegisterWaitingRequest(restaurantId, "010-1234-5678", 2);

        // 1. 중복 아님
        given(waitingRepository.existsByRestaurantIdAndGuestPhoneAndStatus(any(), any(), any()))
                .willReturn(false);
        // 2. 오늘 대기자 없음 (null -> 1번 채번)
        given(waitingRepository.findMaxWaitingNumber(eq(restaurantId), any(LocalDateTime.class)))
                .willReturn(null);
        // 3. Redis에 등록 후 조회 시 1등 반환
        given(rankingRepository.getRank(any())).willReturn(1L);

        // when
        RegisterWaitingResponse response = waitingService.registerInternal(request);

        // then
        assertThat(response.waitingNumber()).isEqualTo(1);
        assertThat(response.rank()).isEqualTo(1L);

        // 검증: DB 저장 호출됨?
        verify(waitingRepository).save(any(Waiting.class));
        // 검증: Redis 등록 호출됨?
        verify(rankingRepository).add(any(WaitingUser.class));
    }

    @Test
    @DisplayName("대기열 등록 성공: 이미 5명이 있으면 6번을 부여받는다.")
    void register_success_nth_guest() {
        // given
        Long restaurantId = 1L;
        RegisterWaitingRequest request = new RegisterWaitingRequest(restaurantId, "010-9999-9999", 4);

        given(waitingRepository.existsByRestaurantIdAndGuestPhoneAndStatus(any(), any(), any()))
                .willReturn(false);
        // 가장 마지막 번호가 5번이라고 가정
        given(waitingRepository.findMaxWaitingNumber(eq(restaurantId), any(LocalDateTime.class)))
                .willReturn(5);
        // Redis 조회 시 6등(내 앞 5명) 반환
        given(rankingRepository.getRank(any())).willReturn(6L);

        // when
        RegisterWaitingResponse response = waitingService.registerInternal(request);

        // then
        assertThat(response.waitingNumber()).isEqualTo(6); // 5 + 1
        assertThat(response.rank()).isEqualTo(6L);

        verify(rankingRepository).add(any(WaitingUser.class));
    }

    @Test
    @DisplayName("대기열 등록 실패: 이미 줄 서 있는 경우 예외가 발생한다.")
    void register_fail_duplicate() {
        // given
        RegisterWaitingRequest request = new RegisterWaitingRequest(1L, "010-1234-5678", 2);

        given(waitingRepository.existsByRestaurantIdAndGuestPhoneAndStatus(any(), any(), any()))
                .willReturn(true); // 이미 존재함

        // when & then
        assertThatThrownBy(() -> waitingService.registerInternal(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 대기 중인 식당입니다.");
    }

    @Test
    @DisplayName("내 대기 상태 조회 (Cache Hit): Redis에 데이터가 있으면 DB Count 쿼리를 실행하지 않는다.")
    void getWaiting_cache_hit() {
        // given
        Long waitingId = 100L;
        Long restaurantId = 1L;
        Waiting waiting = Waiting.create(waitingId, restaurantId, "010-1234-5678", 2, 10);

        given(waitingRepository.findById(waitingId)).willReturn(Optional.of(waiting));
        // Redis에 데이터가 있음 (5등)
        given(rankingRepository.getRank(any(WaitingUser.class))).willReturn(5L);

        // when
        WaitingDetailResponse response = waitingService.getWaiting(waitingId);

        // then
        assertThat(response.rank()).isEqualTo(5L);
        assertThat(response.waitingNumber()).isEqualTo(10);

        verify(waitingRepository, never()).countAhead(any(), anyInt());
    }

    @Test
    @DisplayName("내 대기 상태 조회 (Cache Miss): Redis에 데이터가 없으면 DB에서 조회 후 복구(Self-Healing)한다.")
    void getWaiting_cache_miss_self_healing() {
        // given
        Long waitingId = 100L;
        Waiting waiting = Waiting.create(waitingId, 1L, "010-1234-5678", 2, 10);

        given(waitingRepository.findById(waitingId)).willReturn(Optional.of(waiting));
        // Redis 데이터 소실 (null 반환)
        given(rankingRepository.getRank(any(WaitingUser.class))).willReturn(null);
        // DB Count 쿼리 실행 (내 앞에 4명 있음 -> 나는 5등)
        given(waitingRepository.countAhead(any(), eq(10))).willReturn(4L);

        // when
        WaitingDetailResponse response = waitingService.getWaiting(waitingId);

        // then
        assertThat(response.rank()).isEqualTo(5L);

        verify(rankingRepository).add(any(WaitingUser.class));
    }

    @Test
    @DisplayName("입장 처리: DB 상태 변경 및 Redis 대기열 삭제가 수행된다.")
    void enter() {
        // given
        Long waitingId = 100L;
        Waiting waiting = Waiting.create(waitingId, 1L, "010-1234-5678", 2, 5);

        given(waitingRepository.findById(waitingId)).willReturn(Optional.of(waiting));

        // when
        waitingService.enter(waitingId);

        // then
        assertThat(waiting.getStatus()).isEqualTo(WaitingStatus.ENTERED);
        verify(rankingRepository).remove(any(WaitingUser.class)); // Redis 삭제 검증
    }

    @Test
    @DisplayName("대기 취소: DB 상태 변경 및 Redis 대기열 삭제가 수행된다.")
    void cancel() {
        // given
        Long waitingId = 100L;
        Waiting waiting = Waiting.create(waitingId, 1L, "010-1234-5678", 2, 5);

        given(waitingRepository.findById(waitingId)).willReturn(Optional.of(waiting));

        // when
        waitingService.cancel(waitingId);

        // then
        assertThat(waiting.getStatus()).isEqualTo(WaitingStatus.CANCELED);
        verify(rankingRepository).remove(any(WaitingUser.class)); // Redis 삭제 검증
    }

    @Test
    @DisplayName("순서 미루기: 기존 대기 취소/삭제 후 새로운 대기를 생성/등록한다.")
    void postpone() {
        // given
        Long oldWaitingId = 100L;
        Long restaurantId = 1L;
        PostponeCommand command = new PostponeCommand(oldWaitingId, restaurantId);

        Waiting oldWaiting = Waiting.create(oldWaitingId, restaurantId, "010-1234-5678", 2, 5);

        given(waitingRepository.findById(oldWaitingId)).willReturn(Optional.of(oldWaiting));
        // 새 번호 채번 (현재 10번까지 있다고 가정 -> 11번)
        given(waitingRepository.findMaxWaitingNumber(eq(restaurantId), any(LocalDateTime.class)))
                .willReturn(10);
        // 새 순서 조회 (맨 뒤로 갔으니 10등)
        given(rankingRepository.getRank(any())).willReturn(10L);

        // when
        RegisterWaitingResponse response = waitingService.postponeInternal(command.waitingId());

        // then
        // 1. 기존 대기는 취소되었는가?
        assertThat(oldWaiting.getStatus()).isEqualTo(WaitingStatus.CANCELED);

        // 2. 새로운 번호를 받았는가?
        assertThat(response.waitingNumber()).isEqualTo(11);

        // 3. Redis 작업 검증
        verify(rankingRepository).remove(any(WaitingUser.class)); // 삭제 1회 (기존)
        verify(rankingRepository).add(any(WaitingUser.class));    // 등록 1회 (신규)
    }

}