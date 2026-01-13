package com.booster.restaurantservice.restaurant.exception;

import com.booster.core.web.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RestaurantErrorCode implements ErrorCode {

    // 대기 서비스 전용 에러들
    RESTAURANT_FULL(400, "W-001", "이미 대기 중인 식당입니다.");
    private final int status;
    private final String code;
    private final String message;
}
