package com.booster.promotionservice.coupon.web.dto.request;

import com.booster.promotionservice.coupon.application.dto.IssueCouponCommand;
import jakarta.validation.constraints.NotNull;

public record IssueCouponRequest(
        @NotNull(message = "사용자 ID는 필수입니다.")
        Long userId
) {
    public IssueCouponCommand toCommand(Long couponPolicyId) {
        return IssueCouponCommand.of(couponPolicyId, userId);
    }
}
