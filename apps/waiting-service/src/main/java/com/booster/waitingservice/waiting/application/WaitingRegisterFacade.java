package com.booster.waitingservice.waiting.application;

import com.booster.storage.redis.lock.DistributedLock;
import com.booster.waitingservice.waiting.application.dto.PostponeCommand;
import com.booster.waitingservice.waiting.web.dto.request.RegisterWaitingRequest;
import com.booster.waitingservice.waiting.web.dto.response.RegisterWaitingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WaitingRegisterFacade {
    private final WaitingService waitingService;
    private final RestaurantCacheService restaurantCacheService;

    @DistributedLock(key = "'waiting:restaurant:' + #request.restaurantId()")
    public RegisterWaitingResponse register(RegisterWaitingRequest request) {
        // 트랜잭션 시작 전에 식당명을 미리 조회 (DB 커넥션 점유 없음)
        String restaurantName = restaurantCacheService.getRestaurantName(request.restaurantId());
        return waitingService.registerInternal(request, restaurantName);
    }

    @DistributedLock(key = "'waiting:restaurant:' + #command.restaurantId()")
    public RegisterWaitingResponse postpone(PostponeCommand request) {
        // 트랜잭션 시작 전에 식당명을 미리 조회 (DB 커넥션 점유 없음)
        String restaurantName = restaurantCacheService.getRestaurantName(request.restaurantId());
        return waitingService.postponeInternal(request.waitingId(), restaurantName);
    }
}
