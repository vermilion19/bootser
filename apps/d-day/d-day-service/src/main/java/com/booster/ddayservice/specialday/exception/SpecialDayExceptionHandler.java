package com.booster.ddayservice.specialday.exception;

import com.booster.core.web.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class SpecialDayExceptionHandler {

    @ExceptionHandler(SpecialDayException.class)
    public ResponseEntity<ApiResponse<Void>> handleSpecialDayException(SpecialDayException e) {
        log.warn("SpecialDayException: {}", e.getMessage());
        return ResponseEntity
                .status(e.getErrorCode().getStatus())
                .body(ApiResponse.error(e.getErrorCode()));
    }
}
