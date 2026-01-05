package com.booster.waitingservice.waiting.application;

import com.booster.common.SnowflakeGenerator;
import com.booster.core.web.event.WaitingEvent;
import com.booster.storage.redis.domain.WaitingUser;
import com.booster.storage.redis.repository.RedissonRankingRepository;
import com.booster.waitingservice.waiting.domain.Waiting;
import com.booster.waitingservice.waiting.domain.WaitingRepository;
import com.booster.waitingservice.waiting.domain.WaitingStatus;
import com.booster.waitingservice.waiting.exception.DuplicateWaitingException;
import com.booster.waitingservice.waiting.web.dto.request.RegisterWaitingRequest;
import com.booster.waitingservice.waiting.web.dto.response.RegisterWaitingResponse;
import com.booster.waitingservice.waiting.web.dto.response.WaitingDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@Transactional
@RequiredArgsConstructor
public class WaitingService {

    private final WaitingRepository waitingRepository;
    private final RedissonRankingRepository rankingRepository;
//    private final WaitingEventProducer eventProducer;
    private final ApplicationEventPublisher eventPublisher;

    public RegisterWaitingResponse registerInternal(RegisterWaitingRequest request) {
        validateDuplicate(request.restaurantId(), request.guestPhone());

        int nextWaitingNumber = getNextWaitingNumber(request.restaurantId());

        Waiting waiting = Waiting.create(
                SnowflakeGenerator.nextId(),
                request.restaurantId(),
                request.guestPhone(),
                request.partySize(),
                nextWaitingNumber
        );

        waitingRepository.save(waiting);
        WaitingUser waitingUser = WaitingUser.of(
                waiting.getRestaurantId(),
                waiting.getId(),
                waiting.getWaitingNumber()
        );
        rankingRepository.add(waitingUser);
        Long rank = rankingRepository.getRank(waitingUser);

        publishEvent(waiting, rank, WaitingEvent.EventType.REGISTER);

        return RegisterWaitingResponse.of(waiting, rank);
    }

    /**
     * 내 대기 상태 조회
     */
    public WaitingDetailResponse getWaiting(Long waitingId) {
        Waiting waiting = findById(waitingId);

        Long rank = null;
        if (waiting.getStatus() == WaitingStatus.WAITING) {
            WaitingUser waitingUser = WaitingUser.of(
                    waiting.getRestaurantId(),
                    waiting.getId(),
                    waiting.getWaitingNumber()
            );

            // 1. Redis에서 순서 조회 (매우 빠름)
            rank = rankingRepository.getRank(waitingUser);

            // 2. Self-Healing: Redis 데이터가 유실되었을 경우 DB에서 복구
            if (rank == null) {
                // DB에서 Count 계산
                long count = waitingRepository.countAhead(waiting.getRestaurantId(), waiting.getWaitingNumber());
                rank = count + 1;
                // 다시 Redis에 등록해둠 (다음 조회부턴 Redis에서 나가도록)
                rankingRepository.add(waitingUser);
            }
        }

        return WaitingDetailResponse.of(waiting, rank);
    }

    /**
     * 입장 처리 (점주용)
     */
    public void enter(Long waitingId) {
        Waiting waiting = findById(waitingId);
        waiting.enter();

        WaitingUser waitingUser = WaitingUser.of(waiting.getRestaurantId(), waiting.getId(), waiting.getWaitingNumber());
        rankingRepository.remove(waitingUser);
        publishEvent(waiting, null, WaitingEvent.EventType.ENTER);
    }

    /**
     * 대기 취소 (손님/점주 공용)
     */
    public void cancel(Long waitingId) {
        Waiting waiting = findById(waitingId);
        waiting.cancel();

        WaitingUser waitingUser = WaitingUser.of(waiting.getRestaurantId(), waiting.getId(), waiting.getWaitingNumber());
        rankingRepository.remove(waitingUser);
        publishEvent(waiting, null, WaitingEvent.EventType.CANCEL);
    }

    /**
     * 순서 미루기 (Internal)
     * 로직: 기존 대기 취소 -> 맨 뒤로 재등록
     * 주의: Facade에서 락을 잡고 들어와야 함
     */
    public RegisterWaitingResponse postponeInternal(Long waitingId) {
        // 1. 기존 대기 조회
        Waiting originalWaiting = findById(waitingId);

        // 2. 기존 대기 취소 (CANCELED 처리)
        originalWaiting.cancel();
        WaitingUser oldUser = WaitingUser.of(
                originalWaiting.getRestaurantId(),
                originalWaiting.getId(),
                originalWaiting.getWaitingNumber()
        );
        rankingRepository.remove(oldUser);
        publishEvent(originalWaiting, null, WaitingEvent.EventType.CANCEL);

        // 3. 새로운 번호 채번 (맨 뒤)
        int nextNumber = getNextWaitingNumber(originalWaiting.getRestaurantId());

        // 4. 새로운 엔티티 생성 (새 ID 발급)
        Waiting newWaiting = Waiting.create(
                SnowflakeGenerator.nextId(),
                originalWaiting.getRestaurantId(),
                originalWaiting.getGuestPhone(),
                originalWaiting.getPartySize(),
                nextNumber
        );

        waitingRepository.save(newWaiting);

        // 5. 신규 대기 Redis 등록
        WaitingUser newUser = WaitingUser.of(
                newWaiting.getRestaurantId(),
                newWaiting.getId(),
                newWaiting.getWaitingNumber()
        );
        rankingRepository.add(newUser);
        Long rank = rankingRepository.getRank(newUser);
        publishEvent(newWaiting, rank, WaitingEvent.EventType.REGISTER);
        return RegisterWaitingResponse.of(newWaiting, rank);
    }

    public void call(Long waitingId) {
        Waiting waiting = findById(waitingId);
        waiting.call();
        publishEvent(waiting, null, WaitingEvent.EventType.CALLED);
    }

    // 공통: 엔티티 조회 (없으면 예외)
    private Waiting findById(Long id) {
        return waitingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("대기 정보를 찾을 수 없습니다."));
    }

    private void validateDuplicate(Long restaurantId, String guestPhone) {
        if (waitingRepository.existsByRestaurantIdAndGuestPhoneAndStatus(
                restaurantId, guestPhone, WaitingStatus.WAITING)) {
            throw new DuplicateWaitingException();
        }
    }

    private int getNextWaitingNumber(Long restaurantId) {
        Integer maxNumber = waitingRepository.findMaxWaitingNumber(
                restaurantId,
                LocalDate.now().atStartOfDay() // 오늘 00:00:00 이후
        );
        return (maxNumber == null) ? 1 : maxNumber + 1;
    }

    private void publishEvent(Waiting waiting, Long rank, WaitingEvent.EventType type) {
        eventPublisher.publishEvent(
                WaitingEvent.of(
                        waiting.getRestaurantId(),
                        waiting.getId(),
                        waiting.getGuestPhone(),
                        waiting.getWaitingNumber(),
                        rank,
                        type
                )
        );
    }

}
