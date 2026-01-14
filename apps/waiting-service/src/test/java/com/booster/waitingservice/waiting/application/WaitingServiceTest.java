package com.booster.waitingservice.waiting.application;

import com.booster.core.web.event.WaitingEvent;
import com.booster.storage.redis.domain.WaitingUser;
import com.booster.storage.redis.repository.RedissonRankingRepository;
import com.booster.waitingservice.waiting.application.dto.PostponeCommand;
import com.booster.waitingservice.waiting.domain.Waiting;
import com.booster.waitingservice.waiting.domain.WaitingRepository;
import com.booster.waitingservice.waiting.domain.WaitingStatus;
import com.booster.waitingservice.waiting.domain.outbox.OutboxRepository;
import com.booster.waitingservice.waiting.exception.DuplicateWaitingException;
import com.booster.waitingservice.waiting.exception.InvalidWaitingStatusException;
import com.booster.waitingservice.waiting.web.dto.request.RegisterWaitingRequest;
import com.booster.waitingservice.waiting.web.dto.response.CursorPageResponse;
import com.booster.waitingservice.waiting.web.dto.response.RegisterWaitingResponse;
import com.booster.waitingservice.waiting.web.dto.response.WaitingDetailResponse;
import com.booster.waitingservice.waiting.web.dto.response.WaitingListResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WaitingServiceTest {

    private static final Logger log = LoggerFactory.getLogger(WaitingServiceTest.class);
    @Mock
    private WaitingRepository waitingRepository;
    @Mock
    private RedissonRankingRepository rankingRepository;
    @Mock
    ApplicationEventPublisher eventPublisher;
    @Mock
    RestaurantCacheService restaurantCacheService;
    @Mock
    OutboxRepository outboxRepository;

    @InjectMocks
    private WaitingService waitingService;

    @Captor // 1. 이벤트를 가로챌 캡처 선언
    ArgumentCaptor<WaitingEvent> eventCaptor;

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

        verify(waitingRepository).save(any(Waiting.class));
        verify(rankingRepository).add(any(WaitingUser.class));
        ArgumentCaptor<WaitingEvent> eventCaptor = ArgumentCaptor.forClass(WaitingEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
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
                .isInstanceOf(DuplicateWaitingException.class);
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
        waiting.call();
        given(waitingRepository.findById(waitingId)).willReturn(Optional.of(waiting));

        // when
        waitingService.enter(waitingId);

        // then
        assertThat(waiting.getStatus()).isEqualTo(WaitingStatus.ENTERED);
        verify(rankingRepository).remove(any(WaitingUser.class)); // Redis 삭제 검증
        ArgumentCaptor<WaitingEvent> eventCaptor = ArgumentCaptor.forClass(WaitingEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
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
        ArgumentCaptor<WaitingEvent> eventCaptor = ArgumentCaptor.forClass(WaitingEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
    }

    @Test
    @DisplayName("순서 미루기: 기존 대기 취소(CANCEL) 및 신규 대기 등록(REGISTER) 이벤트가 발행된다.")
    void postpone() {
        // given (기존 설정 유지)
        Long oldWaitingId = 100L;
        Long restaurantId = 1L;
        PostponeCommand command = new PostponeCommand(oldWaitingId, restaurantId);

        Waiting oldWaiting = Waiting.create(oldWaitingId, restaurantId, "010-1234-5678", 2, 5);

        given(waitingRepository.findById(oldWaitingId)).willReturn(Optional.of(oldWaiting));
        given(waitingRepository.findMaxWaitingNumber(eq(restaurantId), any(LocalDateTime.class)))
                .willReturn(10);
        given(rankingRepository.getRank(any())).willReturn(10L);

        // when
        RegisterWaitingResponse response = waitingService.postponeInternal(command.waitingId());

        // then
        // 1. 기존 검증 (상태, 번호 등)
        assertThat(oldWaiting.getStatus()).isEqualTo(WaitingStatus.CANCELED);
        assertThat(response.waitingNumber()).isEqualTo(11);

        // [핵심 수정] 이벤트 검증
        ArgumentCaptor<WaitingEvent> eventCaptor = ArgumentCaptor.forClass(WaitingEvent.class);

        // "send 메서드가 총 2번 호출되었는지 검증하고, 그때 넘어간 파라미터를 다 캡처해라"
//        verify(eventProducer, times(2)).send(eventCaptor.capture());
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());

        // 캡처된 값들을 순서대로 리스트로 꺼냅니다.
        List<WaitingEvent> events = eventCaptor.getAllValues();

        // [첫 번째 이벤트] -> 취소(CANCEL)여야 함
        WaitingEvent cancelEvent = events.get(0);
        assertThat(cancelEvent.type()).isEqualTo(WaitingEvent.EventType.CANCEL);
        assertThat(cancelEvent.waitingId()).isEqualTo(oldWaitingId);

        // [두 번째 이벤트] -> 등록(REGISTER)이어야 함
        WaitingEvent registerEvent = events.get(1);
        assertThat(registerEvent.type()).isEqualTo(WaitingEvent.EventType.REGISTER);
        assertThat(registerEvent.waitingNumber()).isEqualTo(11); // 새로 발급된 번호 확인
    }

    @Test
    @DisplayName("입장 실패: 호출되지 않은(WAITING) 상태에서 입장을 시도하면 예외가 발생한다.")
    void enter_fail_invalid_status() {
        // given
        Long waitingId = 100L;
        // 상태를 CALLED로 바꾸지 않음 (그냥 WAITING 상태)
        Waiting waiting = Waiting.create(waitingId, 1L, "010-1234-5678", 2, 5);

        given(waitingRepository.findById(waitingId)).willReturn(Optional.of(waiting));

        // when & then
        // 예외가 터지는지 검증
        assertThatThrownBy(() -> waitingService.enter(waitingId))
                .isInstanceOf(InvalidWaitingStatusException.class);

        // (중요) 예외가 터졌으므로 Redis 삭제나 이벤트 발행은 절대 일어나면 안 됨
        verify(rankingRepository, never()).remove(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("대기 호출: 대기 중(WAITING)인 손님을 호출하면 상태가 CALLED로 변경되고 알림 이벤트가 발행된다.")
    void call() {
        // given
        Long waitingId = 100L;
        // 초기 상태는 WAITING이어야 함
        Waiting waiting = Waiting.create(waitingId, 1L, "010-1234-5678", 2, 5);

        given(waitingRepository.findById(waitingId)).willReturn(Optional.of(waiting));

        // when
        waitingService.call(waitingId);

        // then
        // 1. 상태 변경 검증 (WAITING -> CALLED)
        assertThat(waiting.getStatus()).isEqualTo(WaitingStatus.CALLED);

        // 2. 이벤트 발행 검증 (EventType.CALLED)
        ArgumentCaptor<WaitingEvent> eventCaptor = ArgumentCaptor.forClass(WaitingEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        WaitingEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.type()).isEqualTo(WaitingEvent.EventType.CALLED);
        assertThat(publishedEvent.waitingId()).isEqualTo(waitingId);
    }

    @Test
    @DisplayName("대기 호출 실패: 대기 중(WAITING)이 아닌 상태에서 호출하면 예외가 발생한다.")
    void call_fail_invalid_status() {
        // given
        Long waitingId = 100L;
        Waiting waiting = Waiting.create(waitingId, 1L, "010-1234-5678", 2, 5);

        waiting.call(); // 상태: WAITING -> CALLED

        given(waitingRepository.findById(waitingId)).willReturn(Optional.of(waiting));

        // when & then
        // CALLED 상태에서 또 call()을 하면 InvalidWaitingStatusException이 터져야 함
        assertThatThrownBy(() -> waitingService.call(waitingId))
                .isInstanceOf(InvalidWaitingStatusException.class);

        // 이벤트는 절대 발행되면 안 됨
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("대기 등록 시 식당 이름을 캐시에서 조회하여 이벤트에 포함해야 한다")
    void register_check_restaurant_name() {
        // given
        Long restaurantId = 1L;
        String expectedName = "맛있는 식당";

        given(restaurantCacheService.getRestaurantName(restaurantId))
                .willReturn(expectedName);

        RegisterWaitingRequest registerWaitingRequest = new RegisterWaitingRequest(restaurantId, "010-1234-5678", 2);

        // when
        waitingService.registerInternal(registerWaitingRequest); // 테스트 대상 메서드 실행

        verify(eventPublisher).publishEvent(eventCaptor.capture());

        WaitingEvent capturedEvent = eventCaptor.getValue();

        assertThat(capturedEvent.restaurantName()).isEqualTo(expectedName);
        assertThat(capturedEvent.restaurantId()).isEqualTo(restaurantId);
        verify(restaurantCacheService, times(1)).getRestaurantName(restaurantId);
    }

    // ===== 대기 목록 조회 (커서 기반 페이지네이션) 테스트 =====

    @Test
    @DisplayName("대기 목록 조회 성공: 첫 페이지 조회 시 커서 없이 데이터를 반환한다")
    void getWaitingList_first_page() {
        // given
        Long restaurantId = 1L;
        int size = 3;

        List<Waiting> waitings = List.of(
                Waiting.create(1L, restaurantId, "010-1111-1111", 2, 1),
                Waiting.create(2L, restaurantId, "010-2222-2222", 3, 2),
                Waiting.create(3L, restaurantId, "010-3333-3333", 4, 3),
                Waiting.create(4L, restaurantId, "010-4444-4444", 2, 4) // size + 1개 (다음 페이지 존재 확인용)
        );

        given(waitingRepository.findByRestaurantIdAndStatusWithCursor(
                eq(restaurantId), eq(WaitingStatus.WAITING), isNull(), eq(size + 1)))
                .willReturn(waitings);
        given(waitingRepository.countByRestaurantIdAndStatus(restaurantId, WaitingStatus.WAITING))
                .willReturn(10L);

        // when
        CursorPageResponse<WaitingListResponse> response = waitingService.getWaitingList(restaurantId, null, size);

        // then
        assertThat(response.content()).hasSize(3);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isEqualTo("3"); // 마지막 요소의 waitingNumber
        assertThat(response.totalCount()).isEqualTo(10L);
        assertThat(response.size()).isEqualTo(3);

        // 첫 번째 요소 검증
        WaitingListResponse firstItem = response.content().getFirst();
        assertThat(firstItem.waitingNumber()).isEqualTo(1);
        assertThat(firstItem.guestPhone()).isEqualTo("010-1111-1111");
    }

    @Test
    @DisplayName("대기 목록 조회 성공: 커서를 사용하여 다음 페이지를 조회한다")
    void getWaitingList_with_cursor() {
        // given
        Long restaurantId = 1L;
        Integer cursor = 3; // waitingNumber 3 이후 데이터 조회
        int size = 2;

        List<Waiting> waitings = List.of(
                Waiting.create(4L, restaurantId, "010-4444-4444", 2, 4),
                Waiting.create(5L, restaurantId, "010-5555-5555", 3, 5)
        );

        given(waitingRepository.findByRestaurantIdAndStatusWithCursor(
                eq(restaurantId), eq(WaitingStatus.WAITING), eq(cursor), eq(size + 1)))
                .willReturn(waitings);
        given(waitingRepository.countByRestaurantIdAndStatus(restaurantId, WaitingStatus.WAITING))
                .willReturn(5L);

        // when
        CursorPageResponse<WaitingListResponse> response = waitingService.getWaitingList(restaurantId, cursor, size);

        // then
        assertThat(response.content()).hasSize(2);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.totalCount()).isEqualTo(5L);
    }

    @Test
    @DisplayName("대기 목록 조회 성공: 데이터가 없으면 빈 목록을 반환한다")
    void getWaitingList_empty() {
        // given
        Long restaurantId = 1L;
        int size = 20;

        given(waitingRepository.findByRestaurantIdAndStatusWithCursor(
                eq(restaurantId), eq(WaitingStatus.WAITING), isNull(), eq(size + 1)))
                .willReturn(List.of());
        given(waitingRepository.countByRestaurantIdAndStatus(restaurantId, WaitingStatus.WAITING))
                .willReturn(0L);

        // when
        CursorPageResponse<WaitingListResponse> response = waitingService.getWaitingList(restaurantId, null, size);

        // then
        assertThat(response.content()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.totalCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("대기 목록 조회 성공: 요청한 size가 100을 초과하면 100개로 제한된다")
    void getWaitingList_size_limit() {
        // given
        Long restaurantId = 1L;
        int requestedSize = 200; // 100 초과
        int limitedSize = 100;

        given(waitingRepository.findByRestaurantIdAndStatusWithCursor(
                eq(restaurantId), eq(WaitingStatus.WAITING), isNull(), eq(limitedSize + 1)))
                .willReturn(List.of());
        given(waitingRepository.countByRestaurantIdAndStatus(restaurantId, WaitingStatus.WAITING))
                .willReturn(0L);

        // when
        CursorPageResponse<WaitingListResponse> response = waitingService.getWaitingList(restaurantId, null, requestedSize);

        // then
        assertThat(response.size()).isEqualTo(100);
        verify(waitingRepository).findByRestaurantIdAndStatusWithCursor(
                eq(restaurantId), eq(WaitingStatus.WAITING), isNull(), eq(101)); // 100 + 1
    }

    @Test
    @DisplayName("대기 목록 조회 성공: 마지막 페이지는 hasNext가 false이다")
    void getWaitingList_last_page() {
        // given
        Long restaurantId = 1L;
        int size = 10;

        // size보다 적은 개수 반환 (마지막 페이지)
        List<Waiting> waitings = List.of(
                Waiting.create(8L, restaurantId, "010-8888-8888", 2, 8),
                Waiting.create(9L, restaurantId, "010-9999-9999", 3, 9)
        );

        given(waitingRepository.findByRestaurantIdAndStatusWithCursor(
                eq(restaurantId), eq(WaitingStatus.WAITING), eq(7), eq(size + 1)))
                .willReturn(waitings);
        given(waitingRepository.countByRestaurantIdAndStatus(restaurantId, WaitingStatus.WAITING))
                .willReturn(9L);

        // when
        CursorPageResponse<WaitingListResponse> response = waitingService.getWaitingList(restaurantId, 7, size);

        // then
        assertThat(response.content()).hasSize(2);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }
}