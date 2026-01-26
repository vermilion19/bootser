package com.booster.firstcomefirstserved.common.error;

import com.booster.firstcomefirstserved.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. 비즈니스 로직 예외 (우리가 만든 BusinessException)
    @ExceptionHandler(BusinessException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleBusinessException(BusinessException e) {
        log.warn("Business Exception: {}", e.getMessage());
        return Mono.just(
                ResponseEntity.badRequest().body(ApiResponse.fail(e.getErrorCode().getMessage()))
        );
    }

    // 2. 요청 값 검증 실패 (Request DTO의 @NotNull, @Min 등)
    // WebFlux에서는 MethodArgumentNotValidException 대신 WebExchangeBindException이 발생함
    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleBindException(WebExchangeBindException e) {
        String errorMessage = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        log.warn("Validation Failed: {}", errorMessage);
        return Mono.just(
                ResponseEntity
                        .badRequest()
                        .body(ApiResponse.fail(errorMessage))
        );
    }

    // 3. 그 외 알 수 없는 예외
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleException(Exception e) {
        log.error("Unhandled Exception: ", e);
        return Mono.just(
                ResponseEntity
                        .internalServerError()
                        .body(ApiResponse.fail("서버 내부 오류가 발생했습니다."))
        );
    }
}
