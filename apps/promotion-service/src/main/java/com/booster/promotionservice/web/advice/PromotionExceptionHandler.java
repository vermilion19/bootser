package com.booster.promotionservice.web.advice;

import com.booster.core.web.exception.CoreException;
import com.booster.core.web.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class PromotionExceptionHandler {

    /**
     * CoreException (커스텀 비즈니스 예외) 처리
     */
    @ExceptionHandler(CoreException.class)
    public ResponseEntity<ApiResponse<Void>> handleCoreException(CoreException e) {
        log.warn("CoreException: [{}] {}", e.getErrorCode().getCode(), e.getMessage());
        return ResponseEntity
                .status(e.getErrorCode().getStatus())
                .body(ApiResponse.error(e.getErrorCode()));
    }

    /**
     * Valid 검증 실패 예외
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        FieldError firstError = bindingResult.getFieldError();

        String logMessage = firstError != null
                ? String.format("[%s] %s", firstError.getField(), firstError.getDefaultMessage())
                : "Validation failed";

        String clientMessage = firstError != null
                ? firstError.getDefaultMessage()
                : "잘못된 요청입니다.";

        log.warn("Validation Error: {}", logMessage);

        return ResponseEntity
                .status(400)
                .body(ApiResponse.error(clientMessage));
    }

    /**
     * JSON 파싱 에러
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleJsonException(HttpMessageNotReadableException e) {
        log.warn("Json Parsing Error: {}", e.getMessage());
        return ResponseEntity
                .status(400)
                .body(ApiResponse.error("요청 포맷이 올바르지 않습니다. (JSON 형식 확인)"));
    }

    /**
     * 그 외 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unhandled Exception: ", e);
        return ResponseEntity
                .status(500)
                .body(ApiResponse.error("서버 내부 오류가 발생했습니다."));
    }
}
