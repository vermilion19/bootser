package com.booster.promotionservice.coupon.web.advice;

import com.booster.core.web.exception.CoreException;
import com.booster.core.web.response.ApiResponse;
import com.booster.promotionservice.coupon.exception.AlreadyIssuedCouponException;
import com.booster.promotionservice.coupon.exception.CouponNotFoundException;
import com.booster.promotionservice.coupon.exception.CouponPolicyNotFoundException;
import com.booster.promotionservice.coupon.exception.CouponSoldOutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice(basePackages = "com.booster.promotionservice.coupon")
public class CouponExceptionHandler {

    @ExceptionHandler(CouponSoldOutException.class)
    public ResponseEntity<ApiResponse<Void>> handleCouponSoldOut(CouponSoldOutException e) {
        log.warn("쿠폰 소진: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(e.getErrorCode()));
    }

    @ExceptionHandler(AlreadyIssuedCouponException.class)
    public ResponseEntity<ApiResponse<Void>> handleAlreadyIssued(AlreadyIssuedCouponException e) {
        log.warn("중복 발급 시도: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(e.getErrorCode()));
    }

    @ExceptionHandler({CouponNotFoundException.class, CouponPolicyNotFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(CoreException e) {
        log.warn("쿠폰 조회 실패: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getErrorCode()));
    }
}
