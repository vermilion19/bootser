package com.booster.storage.redis.domain;

public record WaitingUser(
        Long restaurantId,  // Key 생성용 (waiting:ranking:{id})
        Long waitingId,     // Value (Member) - 실제 저장될 값
        int waitingNumber   // Score - 정렬 기준 (Time 대신 Number 사용!)
) {
    public static WaitingUser of(Long restaurantId, Long waitingId, int waitingNumber) {
        return new WaitingUser(restaurantId, waitingId, waitingNumber);
    }

    // Redis Key 생성 메서드를 DTO 내부에 두면 관리가 편합니다.
    public String getQueueKey() {
        return "waiting:ranking:" + restaurantId;
    }
}
