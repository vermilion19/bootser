package com.booster.waitingservice.waiting.web.dto.response;

import com.booster.waitingservice.waiting.domain.Waiting;
import com.booster.waitingservice.waiting.domain.WaitingStatus;

public record WaitingDetailResponse(
        Long id,
        Long restaurantId,
        String guestPhone,
        int waitingNumber,
        WaitingStatus status,
        Long rank, // 내 앞의 대기 팀 수 (입장/취소 상태면 null)
        int partySize) {
    public static WaitingDetailResponse of(Waiting waiting, Long rank) {
        return new WaitingDetailResponse(
                waiting.getId(),
                waiting.getRestaurantId(),
                waiting.getGuestPhone(),
                waiting.getWaitingNumber(),
                waiting.getStatus(),
                rank,
                waiting.getPartySize()
        );
    }
}
