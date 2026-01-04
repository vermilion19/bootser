package com.booster.waitingservice.waiting.web.dto.response;

import com.booster.waitingservice.waiting.domain.Waiting;

public record RegisterWaitingResponse(
        Long id,
        int waitingNumber,
        Long rank
) {
    public static RegisterWaitingResponse of(Waiting waiting, Long rank) {
        return new RegisterWaitingResponse(
                waiting.getId(),
                waiting.getWaitingNumber(),
                rank
        );
    }
}
