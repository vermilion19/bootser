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

    @DistributedLock(key = "'waiting:restaurant:' + #request.restaurantId()")
    public RegisterWaitingResponse register(RegisterWaitingRequest request) {
        return waitingService.registerInternal(request);
    }

    @DistributedLock(key = "'waiting:restaurant:' + #command.restaurantId()")
    public RegisterWaitingResponse postpone(PostponeCommand request) {
        return waitingService.postponeInternal(request.waitingId());
    }
}
