package com.booster.storage.redis.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitingUser {

    private String userId;
    private String queueKey; // 어떤 이벤트/상품의 대기열인지 구분
    private Long timestamp;  // 진입 시간 (Score로 활용)

    public static WaitingUser of(String userId, String queueKey) {
        return WaitingUser.builder()
                .userId(userId)
                .queueKey(queueKey)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
