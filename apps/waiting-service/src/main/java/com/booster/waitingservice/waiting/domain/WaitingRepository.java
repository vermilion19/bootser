package com.booster.waitingservice.waiting.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface WaitingRepository extends JpaRepository<Waiting,Long> {
    // 특정 식당의 특정 손님이 대기 중인지 확인 (중복 등록 방지용)
    boolean existsByRestaurantIdAndGuestPhoneAndStatus(Long restaurantId, String guestPhone, WaitingStatus status);

    // 손님의 진행 중인 대기 정보 조회
    Optional<Waiting> findByRestaurantIdAndGuestPhoneAndStatus(Long restaurantId, String guestPhone, WaitingStatus status);

    // 오늘의 마지막 대기 번호 조회 (동시성 처리는 Facade의 락이 담당)
    // "오늘 생성된(createdAt >= startOfDay) 데이터 중 가장 큰 waitingNumber 조회"
    @Query("SELECT MAX(w.waitingNumber) FROM Waiting w " +
            "WHERE w.restaurantId = :restaurantId " +
            "AND w.createdAt >= :startOfDay")
    Integer findMaxWaitingNumber(@Param("restaurantId") Long restaurantId,
                                 @Param("startOfDay") LocalDateTime startOfDay);

    // 3. 내 앞의 대기 팀 수 계산 (나보다 번호가 작고, 상태가 WAITING인 사람 수)
    @Query("SELECT COUNT(w) FROM Waiting w " +
            "WHERE w.restaurantId = :restaurantId " +
            "AND w.status = 'WAITING' " +
            "AND w.waitingNumber < :myWaitingNumber")
    Long countAhead(@Param("restaurantId") Long restaurantId,
                    @Param("myWaitingNumber") int myWaitingNumber);

}
