package com.booster.promotionservice.application.dto;

import java.time.LocalDateTime;

public record CreateCouponRequest(
        String title,
        Integer totalQuantity,
        Integer discountAmount,
        LocalDateTime startAt,
        LocalDateTime endAt
) {
}
