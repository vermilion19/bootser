package com.booster.queryburst.order.web.advice;

import com.booster.core.web.response.ApiResponse;
import com.booster.queryburst.common.ratelimit.RateLimitExceededException;
import com.booster.queryburst.lock.LockAcquisitionException;
import com.booster.queryburst.order.exception.DuplicateRequestException;
import com.booster.queryburst.product.domain.StaleTokenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class OrderExceptionHandler {

    /**
     * Rate Limit 초과 → 429 Too Many Requests.
     *
     * 단위 시간 내 허용된 요청 수를 초과. Kafka abuse-detection 토픽에 이벤트도 발행된다.
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimitExceededException(RateLimitExceededException e) {
        log.warn("[주문] Rate Limit 초과: key={}", e.getRateLimitKey());
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error("요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요."));
    }

    /**
     * 중복 요청 (동일 Idempotency-Key 처리 중) → 409 Conflict.
     */
    @ExceptionHandler(DuplicateRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateRequestException(DuplicateRequestException e) {
        log.warn("[주문] 중복 요청 감지: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("동일한 요청이 처리 중입니다. 잠시 후 결과를 확인해주세요."));
    }

    /**
     * 분산 락 획득 실패 → 429 Too Many Requests.
     *
     * 동일 상품에 대한 요청이 몰려 waitTime 내에 락을 못 얻은 경우.
     * 클라이언트는 잠시 후 재시도할 수 있다.
     */
    @ExceptionHandler(LockAcquisitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleLockAcquisitionException(LockAcquisitionException e) {
        log.warn("[주문] 락 획득 실패: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error("현재 요청이 많습니다. 잠시 후 다시 시도해주세요."));
    }

    /**
     * 펜싱 토큰 검증 실패 → 409 Conflict.
     *
     * GC pause 또는 네트워크 지연으로 락이 만료된 후 도착한 오래된 요청.
     * 클라이언트는 처음부터 다시 주문 요청을 시작해야 한다.
     */
    @ExceptionHandler(StaleTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleStaleTokenException(StaleTokenException e) {
        log.warn("[주문] 펜싱 토큰 검증 실패 (stale writer): {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("요청이 만료되었습니다. 다시 시도해주세요."));
    }

    /**
     * 재고 부족 → 409 Conflict.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalStateException(IllegalStateException e) {
        log.warn("[주문] 비즈니스 규칙 위반: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 존재하지 않는 회원/상품 → 404 Not Found.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("[주문] 잘못된 요청: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage()));
    }
}
