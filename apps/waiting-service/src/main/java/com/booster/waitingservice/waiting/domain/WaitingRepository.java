package com.booster.waitingservice.waiting.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WaitingRepository extends JpaRepository<Waiting,Long> {
    // 특정 식당의 특정 손님이 대기 중인지 확인 (중복 등록 방지용)
    boolean existsByRestaurantIdAndGuestPhoneAndStatus(Long restaurantId, String guestPhone, WaitingStatus status);

    // 손님의 진행 중인 대기 정보 조회
    Optional<Waiting> findByRestaurantIdAndGuestPhoneAndStatus(Long restaurantId, String guestPhone, WaitingStatus status);
}
