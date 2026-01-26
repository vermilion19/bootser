package com.booster.core.webflux.exception;

import com.booster.core.webflux.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

/**
 * @RestController 기반 WebFlux 애플리케이션을 위한 예외 핸들러
 *
 * 사용 방법:
 * - @RestController를 사용하는 경우 이 핸들러가 동작합니다.
 * - RouterFunction 방식을 사용하는 경우 GlobalExceptionHandler가 동작합니다.
 */
@Slf4j
@RestControllerAdvice
public class ReactiveExceptionHandler {

    @ExceptionHandler(CoreException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleCoreException(CoreException e) {
        log.warn("CoreException: {}", e.getMessage());
        return Mono.just(ResponseEntity
                .status(e.getErrorCode().getStatus())
                .body(ApiResponse.error(e.getErrorCode())));
    }

    /**
     * WebFlux Validation 예외 처리
     * - DTO의 유효성 검사가 실패할 경우 발생
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleValidationException(WebExchangeBindException e) {
        String message = e.getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse("잘못된 요청입니다.");

        log.warn("Validation Error: {}", message);

        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message)));
    }

    /**
     * 요청 파싱 오류 (JSON 형식 오류 등)
     */
    @ExceptionHandler(ServerWebInputException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleInputException(ServerWebInputException e) {
        log.warn("Input Error: {}", e.getMessage());
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("요청 포맷이 올바르지 않습니다.")));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleException(Exception e) {
        log.error("Unhandled Exception: ", e);
        return Mono.just(ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("서버 내부 오류가 발생했습니다.")));
    }
}
